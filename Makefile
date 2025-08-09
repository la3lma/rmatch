SHELL := /bin/bash

.PHONY: build test ci bench-micro bench-macro profile fmt spotbugs

build:
	./mvnw -q -B -DskipTests package || mvn -q -B -DskipTests package

test:
	./mvnw -q -B verify || mvn -q -B verify

bench-micro:
	scripts/run_jmh.sh

bench-macro:
	MAX_REGEXPS?=10000; MAX_REGEXPS=$$MAX_REGEXPS scripts/run_macro.sh

profile:
	DUR?=30; ASYNC_PROFILER_HOME=$$ASYNC_PROFILER_HOME scripts/profile_async_profiler.sh $$DUR

fmt:
	./mvnw -q -B spotless:apply || mvn -q -B spotless:apply

spotbugs:
	./mvnw -q -B spotbugs:check || mvn -q -B spotbugs:check
