#!/bin/sh

# Our task is to run the unfair test with an increasing number of matches
# to run, and to run that in lock-step with a test using rmatch,
# measuring the time for each of these and dropping the result
# into the logfile

SHELLHOME=$(dirname "$0")
TESTHOME=$(dirname "$SHELLHOME")

VERSION_UNDER_TEST="1.1-SNAPSHOT"

# Find the jarfile to use
JARFILENAME=rmatch-tester-$VERSION_UNDER_TEST.jar
JARFILEPATH="$TESTHOME/target/$JARFILENAME"

if [ ! -f "$JARFILEPATH" ] ; then
 echo "Couldn't find jarfile $JARFILEPATH, and cannot proceed without one."
 echo "Perhaps you should run 'mvn clean install' and then try again?"
 exit 1
fi

WORDS="$TESTHOME"/corpus/real-words-in-wuthering-heights.txt

LOGDIR=logs
if [ ! -d "$LOGDIR" ] ; then
    mkdir -p "$LOGDIR"
fi

DATETIME=$(date "+%Y-%m-%d-%H:%M:%S")
LOGFILE="${LOGDIR}/logfile-${DATETIME}.csv"


# Making sure that we can assume that an empty
# logfile exists before we start the test run
if [ -f "$LOGFILE" ] ; then
 echo "Oops.  The logfile ${LOGFILE} already exists. I don't dare to overwrite. Bailing out."
 echo 1
else
 touch "$LOGFILE"
fi

# We will start with a single regular expression, and then we will
# progress in steps of a hundred, adding more regular expressions
# to each test.

STARTINDEX=1
STEPSIZE=100
NO_OF_REGEXPS=$(wc -l "$WORDS" | awk '{print $1}')


millisSinceEpoch() {
    gdate +%s%N | cut -b1-13
}

secondsSinceEpoch() {
  date -j -f "%a %b %d %T %Z %Y" "$(date)" "+%s"
}

runTest() {
  local CLASSNAME="$1"
  local NOOFREGEXPS="$2"
  local start=$(millisSinceEpoch)
  java  -Xmx7G  -disableassertions -server -cp "$JARFILEPATH" "no.rmz.rmatch.performancetests.$CLASSNAME" "$NOOFREGEXPS"  >  /dev/null 2>&1
  local stop=$(millisSinceEpoch)
  local duration
  ((duration = stop - start))
  echo "$duration"
}


plotResults() {
    PLOTDIR=plots

    if [ ! -d "$PLOTDIR" ] ; then
	mkdir -p "$PLOTDIR"
    fi

    PLOTFILE="${PLOTDIR}/results-${DATETIME}.ps"
    echo "Logging gnuplot visualization of test run results in plotfile ${PLOTFILE}"
    if [ -f "${PLOTFILE}" ] ; then
	    rm "${PLOTFILE}"
    fi

    gnuplot -e "logfile='${LOGFILE}'; set terminal postscript landscape color" bin/plot-graph.gp > "${PLOTFILE}"
}

echo "NoOfRegexps,javaMillis,regexMillis,quotient" >> "$LOGFILE"

currentNoOfRegexps=$STARTINDEX
runIdx=1
while [  "$currentNoOfRegexps"  -le "$NO_OF_REGEXPS" ] ; do
   echo "Running tests for $currentNoOfRegexps regexps"

   unfairDuration=$(runTest TestJavaRegexpUnfairly             $currentNoOfRegexps)
   rmatchDuration=$(runTest BenchmarkTheWutheringHeightsCorpus $currentNoOfRegexps)

   ((runIdx =  runIdx + 1))
   ratio=$(bc -l <<< "scale=2; $rmatchDuration/$unfairDuration")
   echo "   Duration of run $runIdx with $currentnoOfRegexps was unfair=$unfairDuration milliseconds, rmatch=$rmatchDuration, ratio=$ratio"
   echo "$currentNoOfRegexps, $rmatchDuration, $unfairDuration", "$ratio" >> "$LOGFILE"
   ((currentNoOfRegexps = currentNoOfRegexps + STEPSIZE))

   # We do this incrementally so that we can observe how a test is
   # going even if it isn't done.  Also, we want partial results
   # even if the test fails some ways into the testrun.
   plotResults
done
