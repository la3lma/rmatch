#!/usr/bin/env bash
set -euo pipefail

# Profile the macro benchmark with async-profiler.
# Requires async-profiler installed; set ASYNC_PROFILER_HOME to its directory.
# Usage: scripts/profile_async_profiler.sh [duration_sec]
# Default duration: 30s

DUR=${1:-30}

if [[ -z "${ASYNC_PROFILER_HOME:-}" ]]; then
  echo "ERROR: ASYNC_PROFILER_HOME not set. Install async-profiler and export ASYNC_PROFILER_HOME=/path" >&2
  exit 1
fi

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

mkdir -p benchmarks/results profiles
stamp=$(date -u +"%Y%m%dT%H%M%SZ")
SVG_OUT="profiles/macro-cpu-${stamp}.svg"

# Start macro benchmark in the background
set +e
MAX_REGEXPS=${MAX_REGEXPS:-10000}
( scripts/run_macro.sh ) &
macro_pid=$!
set -e

echo "Profiling PID $macro_pid for ${DUR}s..."
"$ASYNC_PROFILER_HOME"/profiler.sh -d "$DUR" -e cpu -f "$SVG_OUT" "$macro_pid"

# Wait for macro to end (but don't fail if it already did)
wait "$macro_pid" || true

echo "CPU flamegraph: $SVG_OUT"
