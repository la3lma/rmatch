#!/usr/bin/env bash
set -euo pipefail

# Script to clean up stale JMH lock files before running benchmarks
# This prevents "Another JMH instance might be running" errors

echo "Checking for stale JMH lock files..." >&2

# Common JMH lock file locations
lock_locations=(
    "/tmp/jmh.lock"
    "${TMPDIR:-/tmp}/jmh.lock"
    "/var/folders/*/T/jmh.lock"
)

removed_count=0

for pattern in "${lock_locations[@]}"; do
    # Use glob expansion to find actual files matching the pattern
    for lock_file in $pattern; do
        # Check if the file actually exists (glob expansion might not match anything)
        if [[ -f "$lock_file" ]]; then
            echo "Found stale JMH lock file: $lock_file" >&2

            # Check if any JMH processes are actually running
            if pgrep -f "org.openjdk.jmh.Main" >/dev/null 2>&1; then
                echo "Warning: JMH processes are still running. Not removing lock file: $lock_file" >&2
                echo "Running JMH processes:" >&2
                pgrep -fl "org.openjdk.jmh.Main" >&2 || true
            else
                echo "Removing stale JMH lock file: $lock_file" >&2
                rm -f "$lock_file"
                removed_count=$((removed_count + 1))
            fi
        fi
    done
done

if [[ $removed_count -gt 0 ]]; then
    echo "Cleaned up $removed_count stale JMH lock file(s)" >&2
else
    echo "No stale JMH lock files found" >&2
fi

echo "JMH lock cleanup complete" >&2