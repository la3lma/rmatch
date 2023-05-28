#!/usr/bin/env bash

# This script will run the BenchmarkLargeCorpus test 10 times, with the
# same corpus, and the same series id. This is useful for testing
# the stability of the test, and to see if there are any memory leaks.
# Mostly the former.  There can be huge variability in the
# performance of the test, so it's a good idea to run it several times
# and do some statistics on it.  It may als be an even better idea to
# run the two tests (java and regexp) separately, but that's not
# implemented yet.

## Set up the parameters
BASEDIR="$(dirname "$0")/.."

cd "$BASEDIR" || exit 1

JARFILE="target/rmatch-tester-1.1-SNAPSHOT-jar-with-dependencies.jar"

if [ ! -f "$JARFILE" ]; then
    echo "Jar '${JARFILE}' file not found. Please run 'mvn package' first."
    exit 1
fi

JAVA="java"

SERIES_ID="$(uuidgen)"

if [ -z "$SERIES_ID" ]; then
    echo "Could not generate a series id. Bailing out."
    exit 1
fi

NO_OF_REGEXS=10000

## Run a test series

LENGTH_OF_SERIES=3
echo "Running test series ${SERIES_ID}"

iteration=0
while [ "$iteration" -lt "${LENGTH_OF_SERIES}" ]; do
    iteration=$(( iteration + 1 ))
    echo
    echo "-------------------------------"
    echo "Running iteration ${iteration}"
    # shellcheck disable=SC1009
    "$JAVA" -jar "$JARFILE"  "${NO_OF_REGEXS}" "corpus/unique-words.txt" "${SERIES_ID}" "corpus/crime-and-punishment-by-dostoyevsky.txt"
done

echo "Done with test series ${SERIES_ID}"

