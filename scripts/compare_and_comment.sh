#!/usr/bin/env bash
set -euo pipefail

# Enhanced compare and comment script with pass/fail logic and GitHub status checks.
# Baseline files (choose whichever exists):
#   benchmarks/baseline/jmh-baseline.json
#   $BASELINE_JSON (env var)
# If GITHUB_TOKEN and PR context are present, will try to post a PR comment using gh or curl.

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

CURR_JSON=${1:-}
if [[ -z "$CURR_JSON" ]]; then
  # pick the latest result
  CURR_JSON=$(ls -t benchmarks/results/jmh-*.json | head -n1 2>/dev/null || echo "")
fi

# Check for the new PR performance summary first
PR_SUMMARY_FILE="benchmarks/results/pr-performance-summary.md"
if [[ -f "$PR_SUMMARY_FILE" ]]; then
  echo "Using performance summary from GitHubActionPerformanceTestRunner"
  OUT_MD="$PR_SUMMARY_FILE"
else
  # Fallback to existing JMH comparison logic
  BASELINE_JSON=${BASELINE_JSON:-}
  if [[ -z "$BASELINE_JSON" ]]; then
    if [[ -f benchmarks/baseline/jmh-baseline.json ]]; then
      BASELINE_JSON=benchmarks/baseline/jmh-baseline.json
    else
      echo "No baseline JSON found. Skipping comparison." >&2
      exit 0
    fi
  fi

  if [[ -z "$CURR_JSON" || ! -f "$CURR_JSON" ]]; then
    echo "No current results file found: $CURR_JSON" >&2
    exit 1
  fi

  if ! command -v jq >/dev/null 2>&1; then
    echo "ERROR: jq is required." >&2
    exit 1
  fi

  mkdir -p benchmarks/results
  OUT_MD=benchmarks/results/perf-summary.md

  echo "Comparing $CURR_JSON vs $BASELINE_JSON"

  # Build a map from benchmark name -> score for both files (existing logic preserved)
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
fi

echo "Performance comparison written to: $OUT_MD"

# Determine exit code based on performance result
EXIT_CODE=0
if [[ -f "$PR_SUMMARY_FILE" ]]; then
  # Extract status from the summary file
  if grep -q "❌ FAIL" "$PR_SUMMARY_FILE"; then
    EXIT_CODE=1
    echo "Performance check FAILED - regression detected"
  elif grep -q "⚠️ WARNING" "$PR_SUMMARY_FILE"; then
    EXIT_CODE=2
    echo "Performance check WARNING - within noise threshold"  
  elif grep -q "✅ PASS" "$PR_SUMMARY_FILE"; then
    EXIT_CODE=0
    echo "Performance check PASSED"
  fi
fi

# Try to comment on PR if possible
if [[ -n "${GITHUB_TOKEN:-}" && -n "${GITHUB_REPOSITORY:-}" && -f "${GITHUB_EVENT_PATH:-}" ]]; then
  pr_number=$(jq -r '.number // .pull_request.number // empty' "$GITHUB_EVENT_PATH" 2>/dev/null || true)
  if [[ -n "$pr_number" ]]; then
    if [[ -f "$PR_SUMMARY_FILE" ]]; then
      body=$(cat "$PR_SUMMARY_FILE")
    else
      body=$(printf "Performance comparison for %s vs baseline\n\n" "$(basename "$CURR_JSON")"; cat "$OUT_MD")
    fi
    
    if command -v gh >/dev/null 2>&1; then
      gh pr comment "$pr_number" --body "$body" || true
    else
      api_url="https://api.github.com/repos/${GITHUB_REPOSITORY}/issues/${pr_number}/comments"
      curl -sS -H "Authorization: Bearer ${GITHUB_TOKEN}" \
           -H "Accept: application/vnd.github+json" \
           -d "$(jq -Rn --arg b "$body" '{body: $b}')" \
           "$api_url" >/dev/null || true
    fi
    
    # Set GitHub check status
    if command -v gh >/dev/null 2>&1; then
      case $EXIT_CODE in
        0) gh api repos/:owner/:repo/statuses/$(git rev-parse HEAD) \
             -f state=success -f description="Performance check passed" \
             -f context="performance-check" || true ;;
        1) gh api repos/:owner/:repo/statuses/$(git rev-parse HEAD) \
             -f state=failure -f description="Performance regression detected" \
             -f context="performance-check" || true ;;
        2) gh api repos/:owner/:repo/statuses/$(git rev-parse HEAD) \
             -f state=success -f description="Performance within acceptable bounds" \
             -f context="performance-check" || true ;;
      esac
    fi
  fi
fi

# For GitHub Actions workflow, treat WARNING (exit code 2) as success (exit code 0)
# This prevents spurious CI failures while still allowing the differentiation in logs
if [[ $EXIT_CODE -eq 2 ]]; then
  echo "Exiting with success (0) instead of warning (2) for CI compatibility"
  exit 0
else
  exit $EXIT_CODE
fi
