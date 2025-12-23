#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/.build"
RUNNER="$BUILD_DIR/run_rmatch_benchmark.sh"

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

# Check arguments
if [ $# -ne 2 ]; then
    echo "Usage: $0 <patterns-file> <corpus-file>" >&2
    exit 1
fi

PATTERNS_FILE="$1"
CORPUS_FILE="$2"

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

# Set JVM options for consistent performance
export JAVA_OPTS="-XX:+UseG1GC -Xmx2g -server"

# Create a subprocess-safe wrapper that ensures proper termination
# This works around Python subprocess.Popen issues with Java processes
"$RUNNER" "$PATTERNS_FILE" "$CORPUS_FILE" &
JAVA_PID=$!

# Wait for the Java process with proper signal handling
wait $JAVA_PID
RESULT=$?

# Ensure clean exit
exit $RESULT