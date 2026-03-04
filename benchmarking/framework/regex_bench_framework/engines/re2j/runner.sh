#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/.build"
RUNNER="$BUILD_DIR/run_re2j_benchmark.sh"

# Check if binary exists, build if not
if [ ! -f "$RUNNER" ]; then
    echo "RE2J benchmark not built. Building now..." >&2
    bash "$SCRIPT_DIR/build.sh"
fi

# Verify the runner exists
if [ ! -f "$RUNNER" ]; then
    echo "ERROR: RE2J benchmark build failed" >&2
    exit 1
fi

# Check arguments
if [ $# -ne 2 ]; then
    echo "Usage: $0 <patterns-file> <corpus-file>" >&2
    exit 1
fi

PATTERNS_FILE="$1"
CORPUS_FILE="$2"

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

# Run the benchmark
exec "$RUNNER" "$PATTERNS_FILE" "$CORPUS_FILE"