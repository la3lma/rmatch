#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <patterns-file> <corpus-file>" >&2
  exit 1
fi

PATTERNS=$1
CORPUS=$2
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/.build"
SRC_FILE="$SCRIPT_DIR/JavaRegexBenchmark.java"
CLASS_NAME="JavaRegexBenchmark"

mkdir -p "$BUILD_DIR"

if [[ ! -f "$BUILD_DIR/${CLASS_NAME}.class" || "$SRC_FILE" -nt "$BUILD_DIR/${CLASS_NAME}.class" ]]; then
  javac -d "$BUILD_DIR" "$SRC_FILE"
fi

java -cp "$BUILD_DIR" "$CLASS_NAME" "$PATTERNS" "$CORPUS"
