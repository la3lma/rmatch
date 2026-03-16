SHELL := /bin/bash

PERFTEST_REPO_DIR ?= ../rmatch-perftest
REGEX_BENCH_FRAMEWORK_DIR ?= $(PERFTEST_REPO_DIR)/benchmarking/framework/regex_bench_framework
PERFTEST_REPORT_DIR ?= $(REGEX_BENCH_FRAMEWORK_DIR)/reports/workload_all_live
SNAPSHOT_OUTPUT_DIR ?= /tmp/rmatch-gcp-snapshot
SNAPSHOT_MD_OUTPUT ?= $(SNAPSHOT_OUTPUT_DIR)/LATEST_PERFORMANCE_TESTS_10K_REGEX_PATTERNS_GOOGLE_COMPUTE_NODE.md
GATE_CONFIG ?= test_matrix/stable_10k_moderate_rmatch.json
GATE_ENGINE ?= rmatch
GATE_METRIC ?= scanning_ns
GATE_MAX_SLOWDOWN ?= 1.10
GATE_BASELINE_BRANCH ?= main
GATE_BASELINE_DIR ?=
GATE_SKIP_SMOKE ?= 0
GATE_SKIP_REBUILD ?= 0

.DEFAULT_GOAL := help

.PHONY: help build test ci readme-gcp-snapshot fmt spotless spotbugs setup-visualization-env gate-baseline gate-candidate release-central-preflight release-central-profile-check release-central-publish

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

ci: test ## [core] Local CI equivalent

readme-gcp-snapshot: setup-visualization-env ## [core] Generate README snapshot from latest GCP comparable matrix
	@mkdir -p "$(SNAPSHOT_OUTPUT_DIR)"
	@scripts/.venv/bin/python scripts/generate_readme_gcp_snapshot.py \
		--matrix-csv $(PERFTEST_REPORT_DIR)/cohort_workload_engine_matrix.csv \
		--cohort 'e2-standard-8|x86_64' \
		--patterns 10000 \
		--output-dir "$(SNAPSHOT_OUTPUT_DIR)" \
		--md-output "$(SNAPSHOT_MD_OUTPUT)"
	@echo "Snapshot artifacts written to $(SNAPSHOT_OUTPUT_DIR)"

profile: ## [perf] Run async-profiler capture (default 30s)
	DUR=30; scripts/profile_async_profiler.sh $$DUR

fmt: ## [core] Apply code formatting (spotless)
	mvn -q -B spotless:apply

spotless: ## [core] Apply spotless formatting
	mvn -q -B spotless:apply

spotbugs: ## [core] Run spotbugs checks
	mvn -q -B spotbugs:check

setup-visualization-env: ## [core] Ensure scripts/.venv exists and is architecture-compatible
	@echo "Setting up Python virtual environment for benchmark visualization..."
	@if [ ! -d "scripts/.venv" ]; then \
		echo "Creating virtual environment..."; \
		python3 -m venv --clear scripts/.venv; \
		echo "Upgrading pip..."; \
		scripts/.venv/bin/pip install --upgrade pip; \
		echo "Installing dependencies with platform-specific binaries..."; \
		scripts/.venv/bin/pip install --no-cache-dir --force-reinstall --only-binary=all -r scripts/requirements.txt; \
		echo "Virtual environment created successfully at scripts/.venv"; \
	else \
		echo "Checking virtual environment compatibility..."; \
		if ! scripts/.venv/bin/python -c "import pandas, numpy, matplotlib, seaborn" 2>/dev/null; then \
			echo "Virtual environment incompatible, recreating..."; \
			rm -rf scripts/.venv; \
			python3 -m venv --clear scripts/.venv; \
			scripts/.venv/bin/pip install --upgrade pip; \
			scripts/.venv/bin/pip install --no-cache-dir --force-reinstall --only-binary=all -r scripts/requirements.txt; \
		else \
			echo "Virtual environment is compatible"; \
		fi; \
	fi

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
