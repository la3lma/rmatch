#!/bin/sh
# http://java.sun.com/performance/reference/whitepapers/tuning.html

#MEMUSE=1800m
#java -XX:+UseParallelGC  -Xmx${MEMUSE} -Xms${MEMUSE} -Xmn2g -server -cp ./target/regexpfilter-1.0-SNAPSHOT.jar no.rmz.regexpfilter.runnables.HandleTheWutheringHeightsCorpus

ENTRYPOINT=no.rmz.rmatch.performancetests.HandleTheWutheringHeightsCorpus
JARFILE=./target/rmatch-tester-1.0-SNAPSHOT.jar
java  -Xms4096m -Xmx4096m -Xmn512m  -server -cp $JARFILE $ENTRYPOINT


# Use jmap -dump:format=b pid or something to dump the heap when the program has crashed (and is just hanging there)
