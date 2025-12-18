#!/usr/bin/env bash
set -euo pipefail

# Run both macro and Java regex benchmarks for comprehensive comparison
# This ensures we always have comparison data for performance charts
# Env vars:
#   MAX_REGEXPS   - default 10000
#   EXTRA_ARGS    - extra args to pass to the Java main, space-separated (optional)

echo "🚀 Running comparative benchmarks (rmatch vs Java regex)..."
echo ""

# Run macro benchmark with Java benchmark enabled
RUN_JAVA_BENCHMARK=true ./scripts/run_macro_with_memory.sh "$@"

echo ""
echo "🎉 Comparative benchmarks completed!"
echo "📊 Chart data available for both rmatch and Java regex performance"