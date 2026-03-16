SHELL := /bin/bash

PERFTEST_REPO_DIR ?= ../rmatch-perftest
REGEX_BENCH_FRAMEWORK_DIR ?= $(PERFTEST_REPO_DIR)/benchmarking/framework/regex_bench_framework
GATE_CONFIG ?= test_matrix/stable_10k_moderate_rmatch.json
GATE_ENGINE ?= rmatch
GATE_METRIC ?= scanning_ns
GATE_MAX_SLOWDOWN ?= 1.10
GATE_BASELINE_BRANCH ?= main
GATE_BASELINE_DIR ?=
GATE_SKIP_SMOKE ?= 0
GATE_SKIP_REBUILD ?= 0

.DEFAULT_GOAL := help

.PHONY: help build test clean fmt spotless spotbugs gate-baseline gate-candidate release-central-preflight release-central-profile-check release-central-publish

help: ## [core] Show available top-level targets
	@echo "Top-level rmatch Make targets"
	@echo "Usage: make <target>"
	@echo ""
	@echo "Core:"
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_.-]+:.*## / {if ($$2 ~ /^\[core\]/) printf "  %-28s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: ## [core] Build project artifacts (skip tests)
	mvn -q -B spotless:apply
	mvn -U -q -B -DskipTests -Dspotbugs.skip=true package

test: ## [core] Run full verify build
	mvn -q -B spotless:apply
	mvn -q -B verify

clean: ## [core] Remove all Maven build artifacts from the repository
	./mvnw -q -B clean
	find . -type d -name target -prune -exec rm -rf {} +

profile: ## [perf] Run async-profiler capture (default 30s)
	DUR=30; scripts/profile_async_profiler.sh $$DUR

fmt: ## [core] Apply code formatting (spotless)
	mvn -q -B spotless:apply

spotless: ## [core] Apply spotless formatting
	mvn -q -B spotless:apply

spotbugs: ## [core] Run spotbugs checks
	mvn -q -B spotbugs:check

gate-baseline: ## [core] Capture local performance baseline from main branch
	$(MAKE) -C $(REGEX_BENCH_FRAMEWORK_DIR) gate-local-baseline \
		GATE_CONFIG="$(GATE_CONFIG)" \
		GATE_ENGINE="$(GATE_ENGINE)" \
		GATE_METRIC="$(GATE_METRIC)" \
		GATE_BASELINE_BRANCH="$(GATE_BASELINE_BRANCH)" \
		GATE_SKIP_SMOKE="$(GATE_SKIP_SMOKE)" \
		GATE_SKIP_REBUILD="$(GATE_SKIP_REBUILD)"

gate-candidate: ## [core] Compare current branch against saved baseline and fail on regression
	$(MAKE) -C $(REGEX_BENCH_FRAMEWORK_DIR) gate-local-candidate \
		GATE_CONFIG="$(GATE_CONFIG)" \
		GATE_ENGINE="$(GATE_ENGINE)" \
		GATE_METRIC="$(GATE_METRIC)" \
		GATE_MAX_SLOWDOWN="$(GATE_MAX_SLOWDOWN)" \
		GATE_BASELINE_BRANCH="$(GATE_BASELINE_BRANCH)" \
		GATE_BASELINE_DIR="$(GATE_BASELINE_DIR)" \
		GATE_SKIP_SMOKE="$(GATE_SKIP_SMOKE)" \
		GATE_SKIP_REBUILD="$(GATE_SKIP_REBUILD)"

release-central-preflight: ## [core] Full non-performance regression check before Maven Central release
	./mvnw -q -B verify

release-central-profile-check: ## [core] Verify Central release profile wiring without GPG signing
	./mvnw -q -B -pl rmatch -am -Pcentral-release -DskipTests -Dspotbugs.skip=true -Dgpg.skip=true verify

release-central-publish: ## [core] Publish parent+rmatch to Maven Central (requires token+GPG setup and non-SNAPSHOT version)
	./mvnw -B -pl rmatch -am -Pcentral-release -DskipTests -Dspotbugs.skip=true -Dgpg.skip=false deploy
