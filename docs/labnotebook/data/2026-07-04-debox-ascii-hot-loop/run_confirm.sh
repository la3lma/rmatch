#!/bin/bash
# Multi-MB confirmation stage. Usage: run_confirm.sh <variantLabel> <rmatchJar> <outCsv>
# Only run if the initial cascade showed a positive effect.
set -u
SCRATCH=/private/tmp/claude-501/-Volumes-SynologyScsi1-git-rmatch/454baf91-4d9c-49bc-b594-8146d68c9ec5/scratchpad/bench
VARIANT="$1"
JAR="$2"
OUT="$3"
REGEXPS=/Volumes/SynologyScsi1/git/rmatch/rmatch-tester/corpus/real-words-in-wuthering-heights.txt
CORPUS=/Volumes/SynologyScsi1/git/rmatch/rmatch-tester/corpus/wuthr10.txt
CP="$SCRATCH/classes:$JAR:$(cat $SCRATCH/cp.txt)"

run_cell () { # nRegexps mult warmup measured impl
  echo ">>> confirm cell: n=$1 mult=$2 impl=$5" >&2
  java -Xmx8g -XX:+UseG1GC -cp "$CP" CascadeBench "$VARIANT" "$REGEXPS" "$1" "$CORPUS" "$2" "$3" "$4" "$5" \
    | grep '^RESULT,' >> "$OUT"
  if [ "${PIPESTATUS[0]}" -ne 0 ]; then
    echo "!!! confirm cell FAILED: n=$1 mult=$2 impl=$5" >&2
  fi
}

# Multi-MB corpora: 10x = ~6.8MB, 20x = ~13.5MB
run_cell  1000 10 1 3 single
run_cell  1000 20 1 2 single
run_cell  5000 10 1 2 single
run_cell  5000 20 1 2 single
run_cell 10000 10 1 2 single
# Production path on the big corpora
run_cell 10000 10 1 2 factory
run_cell 10000 20 1 2 factory

echo "DONE confirm variant=$VARIANT" >&2
