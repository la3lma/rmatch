#!/bin/bash
# File-based runner for rmatch that bypasses subprocess pipe issues
# This script writes output to files instead of stdout/stderr to avoid
# Python subprocess.communicate() hanging on Java process termination

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="$SCRIPT_DIR/.build/run_rmatch_benchmark.sh"

# Check arguments
if [ $# -ne 4 ]; then
    echo "Usage: $0 <patterns-file> <corpus-file> <stdout-file> <stderr-file>" >&2
    exit 1
fi

PATTERNS_FILE="$1"
CORPUS_FILE="$2"
STDOUT_FILE="$3"
STDERR_FILE="$4"

# Check if binary exists, build if not
if [ ! -f "$RUNNER" ]; then
    echo "rmatch benchmark not built. Building now..." >&2
    bash "$SCRIPT_DIR/build.sh"
fi

# Verify the runner exists
if [ ! -f "$RUNNER" ]; then
    echo "ERROR: rmatch benchmark build failed" >&2
    exit 1
fi

# Convert relative paths to absolute paths
if [[ ! "$PATTERNS_FILE" = /* ]]; then
    PATTERNS_FILE="$(pwd)/$PATTERNS_FILE"
fi

if [[ ! "$CORPUS_FILE" = /* ]]; then
    CORPUS_FILE="$(pwd)/$CORPUS_FILE"
fi

# Verify input files exist
if [ ! -f "$PATTERNS_FILE" ]; then
    echo "ERROR: Patterns file not found: $PATTERNS_FILE" >&2
    exit 1
fi

if [ ! -f "$CORPUS_FILE" ]; then
    echo "ERROR: Corpus file not found: $CORPUS_FILE" >&2
    exit 1
fi

# Create output directory if needed
mkdir -p "$(dirname "$STDOUT_FILE")"
mkdir -p "$(dirname "$STDERR_FILE")"

# Set JVM options for consistent performance
export JAVA_OPTS="-XX:+UseG1GC -Xmx2g -server"

# Run rmatch with output redirected to files
# This bypasses the subprocess pipe deadlock issue
"$RUNNER" "$PATTERNS_FILE" "$CORPUS_FILE" 1>"$STDOUT_FILE" 2>"$STDERR_FILE"

# Ensure the exit code is preserved
exit $?