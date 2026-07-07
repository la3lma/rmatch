SHELL := /bin/bash
MVN ?= ./mvnw

PERFTEST_REPO_DIR ?= ../rmatch-perftest
REGEX_BENCH_FRAMEWORK_DIR ?= $(PERFTEST_REPO_DIR)/benchmarking/framework/regex_bench_framework
PERFTEST_DOCKER_SMOKE_SCRIPT ?= $(REGEX_BENCH_FRAMEWORK_DIR)/scripts/run_phase4_smoke_local_docker.sh
GATE_CONFIG ?= test_matrix/stable_10k_moderate_rmatch.json
GATE_ENGINE ?= rmatch
GATE_METRIC ?= scanning_ns
GATE_MAX_SLOWDOWN ?= 1.10
GATE_BASELINE_BRANCH ?= main
GATE_BASELINE_DIR ?=
GATE_SKIP_SMOKE ?= 0
GATE_SKIP_REBUILD ?= 0

.DEFAULT_GOAL := help

.PHONY: help main main-local main-docker build test clean profile fmt spotless spotbugs javadocs release-central-javadoc-check
.PHONY: perf-local-setup perf-local-smoke perf-local-baseline perf-local-candidate perf-docker-smoke
.PHONY: gate-baseline gate-candidate
.PHONY: release-central-preflight release-central-profile-check release-central-publish

help: ## [core] Show available top-level targets
	@echo "Top-level rmatch Make targets"
	@echo "Usage: make <target>"
	@echo ""
	@echo "Main workflows:"
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_.-]+:.*## / {if ($$2 ~ /^\[main\]/) printf "  %-28s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""
	@echo "Core:"
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_.-]+:.*## / {if ($$2 ~ /^\[core\]/) printf "  %-28s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""
	@echo "Performance, local:"
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_.-]+:.*## / {if ($$2 ~ /^\[perf-local\]/) printf "  %-28s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""
	@echo "Performance, Docker:"
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_.-]+:.*## / {if ($$2 ~ /^\[perf-docker\]/) printf "  %-28s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

main: main-docker ## [main] Run the default Docker-backed processing path

main-local: test ## [main] Run the main processing path locally, without Docker
	$(MVN) -q -B -DskipTests install
	$(MAKE) perf-local-smoke

main-docker: perf-docker-smoke ## [main] Run the main processing path in Docker

build: ## [core] Build project artifacts (skip tests)
	$(MVN) -q -B spotless:apply
	$(MVN) -U -q -B -DskipTests -Dspotbugs.skip=true package

test: ## [core] Run full verify build
	$(MVN) -q -B spotless:apply
	$(MVN) -q -B verify

clean: ## [core] Remove all Maven build artifacts from the repository
	$(MVN) -q -B clean
	find . -type d -name target -prune -exec rm -rf {} +

profile: ## [perf-local] Run async-profiler capture locally (default 30s)
	DUR=30; scripts/profile_async_profiler.sh $$DUR

fmt: ## [core] Apply code formatting (spotless)
	$(MVN) -q -B spotless:apply

spotless: ## [core] Apply spotless formatting
	$(MVN) -q -B spotless:apply

spotbugs: ## [core] Run spotbugs checks
	$(MVN) -q -B spotbugs:check

javadocs: ## [core] Generate browsable local API docs under rmatch/target/reports/apidocs
	rm -rf rmatch/target/reports/apidocs
	$(MVN) -q -B -pl rmatch -am -DskipTests -Dspotbugs.skip=true javadoc:javadoc

release-central-javadoc-check: ## [core] Verify Central profile builds the javadoc jar without signing/uploading
	$(MVN) -q -B -pl rmatch -am -Pcentral-release -DskipTests -Dspotbugs.skip=true -Dgpg.skip=true verify

perf-local-setup: ## [perf-local] Prepare the local benchmark framework venv and engines
	$(MAKE) -C $(REGEX_BENCH_FRAMEWORK_DIR) setup

perf-local-smoke: ## [perf-local] Run benchmark smoke processing locally, without Docker
	$(MAKE) -C $(REGEX_BENCH_FRAMEWORK_DIR) test-quick

perf-local-baseline: ## [perf-local] Capture local performance baseline from main branch
	$(MAKE) -C $(REGEX_BENCH_FRAMEWORK_DIR) gate-local-baseline \
		GATE_CONFIG="$(GATE_CONFIG)" \
		GATE_ENGINE="$(GATE_ENGINE)" \
		GATE_METRIC="$(GATE_METRIC)" \
		GATE_BASELINE_BRANCH="$(GATE_BASELINE_BRANCH)" \
		GATE_SKIP_SMOKE="$(GATE_SKIP_SMOKE)" \
		GATE_SKIP_REBUILD="$(GATE_SKIP_REBUILD)"

perf-local-candidate: ## [perf-local] Compare current branch against saved local baseline
	$(MAKE) -C $(REGEX_BENCH_FRAMEWORK_DIR) gate-local-candidate \
		GATE_CONFIG="$(GATE_CONFIG)" \
		GATE_ENGINE="$(GATE_ENGINE)" \
		GATE_METRIC="$(GATE_METRIC)" \
		GATE_MAX_SLOWDOWN="$(GATE_MAX_SLOWDOWN)" \
		GATE_BASELINE_BRANCH="$(GATE_BASELINE_BRANCH)" \
		GATE_BASELINE_DIR="$(GATE_BASELINE_DIR)" \
		GATE_SKIP_SMOKE="$(GATE_SKIP_SMOKE)" \
		GATE_SKIP_REBUILD="$(GATE_SKIP_REBUILD)"

perf-docker-smoke: ## [perf-docker] Run benchmark smoke processing in Docker
	bash $(PERFTEST_DOCKER_SMOKE_SCRIPT)

gate-baseline: perf-local-baseline ## [perf-local] Alias for perf-local-baseline

gate-candidate: perf-local-candidate ## [perf-local] Alias for perf-local-candidate

release-central-preflight: ## [core] Full non-performance regression check before Maven Central release
	$(MVN) -q -B verify

release-central-profile-check: ## [core] Verify Central release profile wiring without GPG signing
	$(MVN) -q -B -pl rmatch -am -Pcentral-release -DskipTests -Dspotbugs.skip=true -Dgpg.skip=true verify

release-central-publish: ## [core] Publish parent+rmatch to Maven Central (requires token+GPG setup and non-SNAPSHOT version)
	$(MVN) -B -pl rmatch -am -Pcentral-release -DskipTests -Dspotbugs.skip=true -Dgpg.skip=false deploy
