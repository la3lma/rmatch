 SHELL := /bin/bash

 .PHONY: build test ci bench-micro bench-macro bench-java profile fmt spotless spotbugs

build:
	mvn -q -B spotless:apply
	mvn -U -q -B -DskipTests -Dspotbugs.skip=true package

test:
	mvn -q -B spotless:apply
	mvn -q -B verify

bench-micro:
	JMH_FORKS=1 \
    JMH_WARMUP_IT=1 \
    JMH_IT=2 \
    JMH_WARMUP=1s \
    JMH_MEASURE=1s \
    JMH_THREADS=1 \
    JMH_INCLUDE='no\.rmz\.rmatch\.benchmarks\.CompileAndMatchBench\.buildMatcher' \
    scripts/run_jmh.sh -p patternCount=10
    # scripts/run_jmh.sh

bench-macro:
	MAX_REGEXPS=10000 scripts/run_macro_with_memory.sh

bench-java:
	MAX_REGEXPS=10000 scripts/run_java_benchmark_with_memory.sh

profile:
	DUR=30; scripts/profile_async_profiler.sh $$DUR

fmt:
	mvn -q -B spotless:apply

spotless:
	mvn -q -B spotless:apply

spotbugs:
	mvn -q -B spotbugs:check
