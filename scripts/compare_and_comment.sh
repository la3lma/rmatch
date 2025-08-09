#!/usr/bin/env bash
set -euo pipefail

# Compare current JMH JSON against a baseline and emit a Markdown summary.
# Baseline files (choose whichever exists):
#   benchmarks/baseline/jmh-baseline.json
#   $BASELINE_JSON (env var)
# If GITHUB_TOKEN and PR context are present, will try to post a PR comment using gh or curl.

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

CURR_JSON=${1:-}
if [[ -z "$CURR_JSON" ]]; then
  # pick the latest result
  CURR_JSON=$(ls -t benchmarks/results/jmh-*.json | head -n1)
fi

BASELINE_JSON=${BASELINE_JSON:-}
if [[ -z "$BASELINE_JSON" ]]; then
  if [[ -f benchmarks/baseline/jmh-baseline.json ]]; then
    BASELINE_JSON=benchmarks/baseline/jmh-baseline.json
  else
    echo "No baseline JSON found. Skipping comparison." >&2
    exit 0
  fi
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: jq is required." >&2
  exit 1
fi

mkdir -p benchmarks/results
OUT_MD=benchmarks/results/perf-summary.md

# Build a map from benchmark name -> score for both files
jq -r '.[] | [.benchmark, .primaryMetric.score] | @tsv' "$BASELINE_JSON" | sort > /tmp/baseline.tsv
jq -r '.[] | [.benchmark, .primaryMetric.score] | @tsv' "$CURR_JSON" | sort > /tmp/current.tsv

join -t $'\t' -j 1 /tmp/baseline.tsv /tmp/current.tsv | awk -F '\t' '
BEGIN { printf("| Benchmark | Baseline | Current | Δ (%%) |\n|---|---:|---:|---:|\n") }
{
  b=$2+0; c=$3+0; delta = (c-b)/b*100.0;
  printf("| %s | %.3f | %.3f | %.2f%% |\n", $1, b, c, delta);
}
' > "$OUT_MD"

printf "\n**Note**: positive Δ means higher score (slower if score=time). Verify units in JMH output.\n" >> "$OUT_MD"

echo "Wrote $OUT_MD"

# Try to comment on PR if possible
if [[ -n "${GITHUB_TOKEN:-}" && -n "${GITHUB_REPOSITORY:-}" && -f "${GITHUB_EVENT_PATH:-}" ]]; then
  pr_number=$(jq -r '.number // .pull_request.number // empty' "$GITHUB_EVENT_PATH" || true)
  if [[ -n "$pr_number" ]]; then
    body=$(printf "Performance comparison for %s vs baseline\n\n" "$(basename "$CURR_JSON")"; cat "$OUT_MD")
    if command -v gh >/dev/null 2>&1; then
      gh pr comment "$pr_number" --body "$body" || true
    else
      api_url="https://api.github.com/repos/${GITHUB_REPOSITORY}/issues/${pr_number}/comments"
      curl -sS -H "Authorization: Bearer ${GITHUB_TOKEN}" \
           -H "Accept: application/vnd.github+json" \
           -d "$(jq -Rn --arg b "$body" '{body: $b}')" \
           "$api_url" >/dev/null || true
    fi
  fi
fi
