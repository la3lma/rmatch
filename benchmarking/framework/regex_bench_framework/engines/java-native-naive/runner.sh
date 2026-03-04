#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="$SCRIPT_DIR/.build/run_java_native_naive.sh"

if [ ! -f "$RUNNER" ]; then
    echo "Java Native (Naive) benchmark not built. Building now..." >&2
    bash "$SCRIPT_DIR/build.sh"
fi

if [ ! -f "$RUNNER" ]; then
    echo "ERROR: Java Native (Naive) benchmark build failed" >&2
    exit 1
fi

if [ $# -ne 2 ]; then
    echo "Usage: $0 <patterns-file> <corpus-file>" >&2
    exit 1
fi

PATTERNS_FILE="$1"
CORPUS_FILE="$2"

if [ ! -f "$PATTERNS_FILE" ]; then
    echo "ERROR: Patterns file not found: $PATTERNS_FILE" >&2
    exit 1
fi

if [ ! -f "$CORPUS_FILE" ]; then
    echo "ERROR: Corpus file not found: $CORPUS_FILE" >&2
    exit 1
fi

exec "$RUNNER" "$PATTERNS_FILE" "$CORPUS_FILE"
