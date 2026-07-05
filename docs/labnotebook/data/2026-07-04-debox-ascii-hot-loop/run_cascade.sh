#!/bin/bash
# Cascade benchmark runner. Usage: run_cascade.sh <variantLabel> <rmatchJar> <outCsv>
set -u
SCRATCH=/private/tmp/claude-501/-Volumes-SynologyScsi1-git-rmatch/454baf91-4d9c-49bc-b594-8146d68c9ec5/scratchpad/bench
VARIANT="$1"
JAR="$2"
OUT="$3"
REGEXPS=/Volumes/SynologyScsi1/git/rmatch/rmatch-tester/corpus/real-words-in-wuthering-heights.txt
CORPUS=/Volumes/SynologyScsi1/git/rmatch/rmatch-tester/corpus/wuthr10.txt
CP="$SCRATCH/classes:$JAR:$(cat $SCRATCH/cp.txt)"

run_cell () { # nRegexps mult warmup measured impl
  echo ">>> cell: n=$1 mult=$2 impl=$5" >&2
  java -Xmx8g -XX:+UseG1GC -cp "$CP" CascadeBench "$VARIANT" "$REGEXPS" "$1" "$CORPUS" "$2" "$3" "$4" "$5" \
    | grep '^RESULT,' >> "$OUT"
  if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    echo "!!! cell FAILED: n=$1 mult=$2 impl=$5" >&2
  fi
}

# Single-engine cascade (undiluted view of the hot loop)
run_cell   100 1 2 5 single
run_cell  1000 1 2 5 single
run_cell  1000 4 1 3 single
run_cell  5000 1 1 3 single
run_cell  5000 4 1 2 single
run_cell 10000 1 1 2 single
run_cell 10000 4 1 2 single

# Production path (MultiMatcher via factory)
run_cell  1000 1 2 5 factory
run_cell  5000 1 1 3 factory
run_cell 10000 1 1 2 factory
run_cell 10000 4 1 2 factory

echo "DONE variant=$VARIANT" >&2
