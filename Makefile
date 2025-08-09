 SHELL := /bin/bash

 .PHONY: build test ci bench-micro bench-macro profile fmt spotbugs

build:
	mvn -U -q -B -DskipTests -Dspotbugs.skip=true package

test:
	mvn -q -B verify

bench-micro:
	scripts/run_jmh.sh

bench-macro:
	MAX_REGEXPS?=10000; MAX_REGEXPS=$$MAX_REGEXPS scripts/run_macro.sh

profile:
	DUR?=30; ASYNC_PROFILER_HOME=$$ASYNC_PROFILER_HOME scripts/profile_async_profiler.sh $$DUR

fmt:
	mvn -q -B spotless:apply

spotbugs:
	mvn -q -B spotbugs:check
