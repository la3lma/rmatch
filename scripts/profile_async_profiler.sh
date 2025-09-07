#!/usr/bin/env bash
set -euo pipefail

# Profile the macro benchmark with async-profiler.
# Requires async-profiler installed; set ASYNC_PROFILER_HOME to its directory.
# Usage: scripts/profile_async_profiler.sh [duration_sec]
# Default duration: 30s

DUR=${1:-30}

if [[ -z "$(which asprof)" ]]; then
  echo "ERROR: Asprof not installed" >&2
  exit 1
fi

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

mkdir -p benchmarks/results profiles
stamp=$(date -u +"%Y%m%dT%H%M%SZ")
SVG_OUT="profiles/macro-cpu-${stamp}.svg"

# Start macro benchmark in the background
set +e
MAX_REGEXPS=${MAX_REGEXPS:-100000}
# start macro benchmark in background
scripts/run_macro.sh &
macro_job=$!

# find the JVM PID by main class (wait up to ~15s)
target_class='no.rmz.rmatch.performancetests.BenchmarkTheWutheringHeightsCorpus'
pid=""
for _ in $(seq 1 150); do
  pid=$(jps -l | awk -v cls="$target_class" '$2==cls{print $1; exit}')
  [[ -n "$pid" ]] && break
  sleep 0.1
done

if [[ -z "$pid" ]]; then
  echo "ERROR: Could not find JVM PID for $target_class"
  kill "$macro_job" 2>/dev/null || true
  exit 1
fi

echo "Profiling PID $pid for ${DUR}s..."
asprof -d "$DUR" -e cpu -f "$SVG_OUT" "$pid"

# wait for the macro to finish (don’t fail pipeline if it exits non-zero)
wait "$macro_job" || true
echo "CPU flamegraph: $SVG_OUT"
