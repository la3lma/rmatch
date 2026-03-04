#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  post_run_collect_and_report.sh <run_id> [local_results_dir]

Behavior:
  1) Waits for run terminal state in GCS state JSON.
  2) Syncs run output locally.
  3) Stops any still-running benchmark VMs.
  4) Generates a report for the synced result set.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

RUN_ID="$1"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRAMEWORK_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RUN_MANIFEST="$FRAMEWORK_DIR/gcp_runs/${RUN_ID}.json"

if [[ ! -f "$RUN_MANIFEST" ]]; then
  echo "ERROR: run manifest not found: $RUN_MANIFEST" >&2
  exit 1
fi

PROJECT="$(python3 - <<PY
import json
with open("$RUN_MANIFEST", "r", encoding="utf-8") as f:
    d=json.load(f)
print(d.get("project",""))
PY
)"
ZONE="$(python3 - <<PY
import json
with open("$RUN_MANIFEST", "r", encoding="utf-8") as f:
    d=json.load(f)
print(d.get("zone",""))
PY
)"
BUCKET="$(python3 - <<PY
import json
with open("$RUN_MANIFEST", "r", encoding="utf-8") as f:
    d=json.load(f)
print(d.get("bucket",""))
PY
)"

if [[ -z "$PROJECT" || -z "$ZONE" || -z "$BUCKET" ]]; then
  echo "ERROR: manifest missing project/zone/bucket: $RUN_MANIFEST" >&2
  exit 1
fi

RUN_ROOT="gs://${BUCKET}/runs/${RUN_ID}"
LOCAL_RESULTS_DIR="${2:-$FRAMEWORK_DIR/results/gcp_${RUN_ID}}"
REPORT_OUT_DIR="$FRAMEWORK_DIR/reports/report_${RUN_ID}_$(date +%Y%m%d_%H%M%S)"

echo "== Post-run automation started =="
echo "run_id=${RUN_ID}"
echo "project=${PROJECT} zone=${ZONE} bucket=${BUCKET}"
echo "run_root=${RUN_ROOT}"
echo "local_results_dir=${LOCAL_RESULTS_DIR}"
echo "report_out_dir=${REPORT_OUT_DIR}"

terminal=0
final_status="unknown"

while [[ $terminal -eq 0 ]]; do
  TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  STATE_JSON="$(gcloud storage cat "${RUN_ROOT}/state/state.json" 2>/dev/null || true)"
  if [[ -z "$STATE_JSON" ]]; then
    VM_STATUS="$(gcloud compute instances describe "rmatch-bench-${RUN_ID//_/-}" --project "$PROJECT" --zone "$ZONE" --format='value(status)' 2>/dev/null || echo UNKNOWN)"
    echo "${TS} state=missing vm=${VM_STATUS}"
    sleep 30
    continue
  fi

  final_status="$(printf '%s' "$STATE_JSON" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("status","unknown"))')"
  completed="$(printf '%s' "$STATE_JSON" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("completed_jobs",0))')"
  total="$(printf '%s' "$STATE_JSON" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("total_jobs",0))')"
  eta="$(printf '%s' "$STATE_JSON" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("remaining_seconds",-1))')"
  echo "${TS} status=${final_status} progress=${completed}/${total} eta_s=${eta}"

  case "$final_status" in
    completed|failed|cancelled|stopped)
      terminal=1
      ;;
    *)
      sleep 30
      ;;
  esac
done

echo "Run reached terminal status: ${final_status}"

mkdir -p "$LOCAL_RESULTS_DIR"

echo "Syncing output to local results dir..."
gcloud storage rsync --recursive "${RUN_ROOT}/output" "$LOCAL_RESULTS_DIR"

echo "Syncing state/log/meta artifacts..."
mkdir -p "$LOCAL_RESULTS_DIR/_gcp_meta"
gcloud storage cp "${RUN_ROOT}/state/state.json" "$LOCAL_RESULTS_DIR/_gcp_meta/state.json" >/dev/null 2>&1 || true
gcloud storage cp "${RUN_ROOT}/logs/run.log" "$LOCAL_RESULTS_DIR/_gcp_meta/run.log" >/dev/null 2>&1 || true
gcloud storage cp "${RUN_ROOT}/meta/run_manifest.json" "$LOCAL_RESULTS_DIR/_gcp_meta/run_manifest.json" >/dev/null 2>&1 || true

echo "Stopping any running benchmark VMs..."
RUNNING_INSTANCES="$(gcloud compute instances list \
  --project "$PROJECT" \
  --filter='labels.app=rmatch-bench AND status=RUNNING' \
  --format='value(name,zone)')"

if [[ -n "$RUNNING_INSTANCES" ]]; then
  while IFS=$'\t' read -r name zone; do
    [[ -z "${name:-}" ]] && continue
    [[ -z "${zone:-}" ]] && continue
    echo "Stopping VM: ${name} (${zone})"
    gcloud compute instances stop "$name" --project "$PROJECT" --zone "$zone" --quiet >/dev/null || true
  done <<< "$RUNNING_INSTANCES"
else
  echo "No running benchmark VMs found."
fi

echo "Generating report from synced results..."
cd "$FRAMEWORK_DIR"
make install-deps
mkdir -p "$REPORT_OUT_DIR"
.venv/bin/regex-bench generate-report \
  --input "$LOCAL_RESULTS_DIR" \
  --output "$REPORT_OUT_DIR" \
  --format html \
  --include-charts

echo "== Post-run automation complete =="
echo "status=${final_status}"
echo "results_dir=${LOCAL_RESULTS_DIR}"
echo "report_dir=${REPORT_OUT_DIR}"

