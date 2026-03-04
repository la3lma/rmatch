#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRAMEWORK_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CONTROL="$FRAMEWORK_DIR/scripts/gcp_benchmark_control.py"
PYTHON_BIN="$FRAMEWORK_DIR/.venv/bin/python"

MODE="${1:-run}"            # run|batch
TARGET_ID="${2:-}"          # run_id or batch_id
INTERVAL_SECONDS="${INTERVAL_SECONDS:-20}"

if [[ "$MODE" != "run" && "$MODE" != "batch" ]]; then
  echo "ERROR: mode must be 'run' or 'batch'" >&2
  exit 1
fi

if [[ ! -x "$PYTHON_BIN" ]]; then
  echo "ERROR: missing venv python: $PYTHON_BIN" >&2
  exit 1
fi

find_log_file() {
  local id="$1"
  local log_a="$FRAMEWORK_DIR/gcp_runs/post_run_${id}.nohup.log"
  local log_b="$FRAMEWORK_DIR/gcp_runs/post_run_${id}.log"
  if [[ -f "$log_a" ]]; then
    echo "$log_a"
    return 0
  fi
  if [[ -f "$log_b" ]]; then
    echo "$log_b"
    return 0
  fi
  echo ""
}

while true; do
  if [[ -t 1 ]]; then
    clear || true
  fi
  echo "============================================================"
  echo "GCP Live Monitor ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
  echo "Mode: $MODE | Interval: ${INTERVAL_SECONDS}s"
  if [[ -n "$TARGET_ID" ]]; then
    echo "Target: $TARGET_ID"
  else
    echo "Target: latest local manifest"
  fi
  echo "============================================================"
  echo ""

  if [[ "$MODE" == "run" ]]; then
    cmd=(status)
    [[ -n "$TARGET_ID" ]] && cmd+=(--run-id "$TARGET_ID")
    "$PYTHON_BIN" "$CONTROL" "${cmd[@]}" || true
  else
    cmd=(status-batch)
    [[ -n "$TARGET_ID" ]] && cmd+=(--batch-id "$TARGET_ID")
    "$PYTHON_BIN" "$CONTROL" "${cmd[@]}" || true
  fi

  if [[ "$MODE" == "run" && -n "$TARGET_ID" ]]; then
    log_file="$(find_log_file "$TARGET_ID")"
    if [[ -n "$log_file" ]]; then
      echo ""
      echo "---- Post-run Pipeline Log (tail: $log_file) ----"
      tail -n 12 "$log_file" || true
    fi
  fi

  echo ""
  echo "Press Ctrl-C to stop."
  sleep "$INTERVAL_SECONDS"
done
