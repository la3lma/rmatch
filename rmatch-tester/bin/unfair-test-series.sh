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
STEPSIZE=800
NO_OF_REGEXPS=$(wc -l "$WORDS" | awk '{print $1}')

function secondsSinceEpoch {
  echo $(date -j -f "%a %b %d %T %Z %Y" "`date`" "+%s")
}

function runTest {
  local CLASSNAME="$1"
  local NOOFREGEXPS="$2"
  local   start=$(secondsSinceEpoch)
  java  -Xmx7G  -disableassertions -server -cp "$JARFILEPATH" "no.rmz.rmatch.performancetests.$CLASSNAME" "$NOOFREGEXPS"  >  /dev/null 2>&1
  local stop=$(secondsSinceEpoch)
  local duration
  ((duration = stop - start))
  echo "$duration"
}

currentNoOfRegexps=$STARTINDEX
runIdx=1
while [  "$currentNoOfRegexps"  -le "$NO_OF_REGEXPS" ] ; do
   echo "Running tests for $currentNoOfRegexps regexps"

   unfairDuration=$(runTest TestJavaRegexpUnfairly             $currentNoOfRegexps)
   rmatchDuration=$(runTest BenchmarkTheWutheringHeightsCorpus $currentNoOfRegexps)
#  rmatchDuration=$(runTest MultiJavaRegexpMatchDetector        $currentNoOfRegexps)



   ((runIdx = runIdx + 1))
   echo "   Duration of run $runIdx with $currentnoOfRegexps was unfair=$unfairDuration seconds, rmatch=$rmatchDuration"
   echo "$currentNoOfRegexps, $rmatchDuration, $unfairDuration" >> "$LOGFILE"
   ((currentNoOfRegexps = $currentNoOfRegexps + $STEPSIZE))
done

