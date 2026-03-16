SHELL := /bin/bash

# Optimal GC settings based on experimentation (see GC_OPTIMIZATION_RESULTS.md)
# G1 with Compact Object Headers provides 5-12% performance improvement
JAVA_GC_OPTS := -XX:+UseCompactObjectHeaders
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

.PHONY: help build test ci bench-micro bench-macro bench-java bench-suite test-run-once charts readme-gcp-snapshot profile fmt spotless spotbugs visualize-benchmarks setup-visualization-env bench-gc-experiments bench-gc-experiments-fast validate-gc bench-dispatch bench-enhanced bench-enhanced-quick bench-enhanced-full bench-enhanced-arch gate-baseline gate-candidate pre-test-run test-run-full test-run-mini release-central-preflight release-central-profile-check release-central-publish

help: ## [core] Show available top-level targets
	@echo "Top-level rmatch Make targets"
	@echo "Usage: make <target>"
	@echo ""
	@echo "Core:"
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_.-]+:.*## / {if ($$2 ~ /^\[core\]/) printf "  %-28s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""
	@echo "Performance:"
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_.-]+:.*## / {if ($$2 ~ /^\[perf\]/) printf "  %-28s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""
	@echo "Legacy (kept for compatibility, planned for cleanup):"
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_.-]+:.*## / {if ($$2 ~ /^\[legacy\]/) printf "  %-28s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: ## [core] Build project artifacts (skip tests)
	mvn -q -B spotless:apply
	mvn -U -q -B -DskipTests -Dspotbugs.skip=true package

test: ## [core] Run full verify build
	mvn -q -B spotless:apply
	mvn -q -B verify

ci: test ## [core] Local CI equivalent

bench-micro: ## [perf] Run focused micro benchmark (CompileAndMatchBench.buildMatcher)
	MAVEN_OPTS="$(JAVA_GC_OPTS)" \
    JMH_FORKS=1 \
    JMH_WARMUP_IT=1 \
    JMH_IT=2 \
    JMH_WARMUP=1s \
    JMH_MEASURE=1s \
    JMH_THREADS=1 \
    JMH_INCLUDE='no\.rmz\.rmatch\.benchmarks\.CompileAndMatchBench\.buildMatcher' \
    scripts/run_jmh.sh -p patternCount=10
    # scripts/run_jmh.sh

bench-macro: ## [perf] Run macro benchmark for rmatch (10K regexps)
	MAVEN_OPTS="$(JAVA_GC_OPTS)" MAX_REGEXPS=10000 scripts/run_macro_with_memory.sh

bench-java: ## [perf] Run macro benchmark for java.util.regex baseline (10K regexps)
	MAVEN_OPTS="$(JAVA_GC_OPTS)" MAX_REGEXPS=10000 scripts/run_java_benchmark_with_memory.sh

bench-suite: ## [perf] Run comprehensive JMH suite + visualization
	@echo "Cleaning up any stale JMH lock files..."
	@scripts/cleanup_jmh_locks.sh
	@echo "Running comprehensive JMH benchmark suite..."
	JMH_FORKS=1 \
    JMH_WARMUP_IT=1 \
    JMH_IT=2 \
    JMH_WARMUP=1s \
    JMH_MEASURE=2s \
    JMH_THREADS=1 \
    scripts/run_jmh_suite.sh comprehensive
	@echo "Generating visualizations for the test suite..."
	$(MAKE) visualize-benchmarks

test-run-once: pre-test-run ## [legacy] One-pass quick JMH run with visualization
	@echo "Cleaning up any stale JMH lock files..."
	@scripts/cleanup_jmh_locks.sh
	@echo "Running quick JMH benchmark suite (single iteration per test)..."
	JMH_FORKS=1 \
    JMH_WARMUP_IT=0 \
    JMH_IT=1 \
    JMH_WARMUP=0s \
    JMH_MEASURE=1s \
    JMH_THREADS=1 \
    scripts/run_jmh_suite.sh quick
	@echo "Generating visualizations for the quick test suite..."
	$(MAKE) visualize-benchmarks

charts: ## [legacy] Generate legacy macro/java chart artifacts and README perf table
	python3 scripts/generate_macro_performance_plot.py
	python3 scripts/generate_java_performance_plot.py
	python3 scripts/generate_performance_comparison_plot.py
	python3 scripts/update_readme_performance_table.py

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

pre-test-run: ## [legacy] Reset local rmatch Maven cache and reinstall
	rm -rf ~/.m2/repository/no/rmz/rmatch
	mvn -q -B spotless:apply
	./mvnw clean install

test-run-full: pre-test-run ## [legacy] Run full JMH script with bash tracing
	time bash -x   ./scripts/run_jmh.sh

test-run-mini: pre-test-run ## [legacy] Run mini JMH suite with bash tracing
	time bash -x   ./scripts/run_jmh_mini_suite.sh

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

visualize-benchmarks: setup-visualization-env ## [legacy] Generate JMH visualization bundle to performance-graphs
	@echo "Generating JMH benchmark visualizations..."
	@mkdir -p performance-graphs
	@scripts/run_visualization.sh
	@echo "Visualizations saved to performance-graphs/"

bench-gc-experiments: ## [perf] Run full GC experiment matrix
	@echo "Running GC experiments with different configurations..."
	@echo "This will test: G1 (default), ZGC Generational, Shenandoah, and Compact Object Headers variants"
	scripts/run_gc_experiments.sh both all

bench-gc-experiments-fast: ## [perf] Run reduced/fast GC experiment matrix
	@echo "Running FAST GC experiments with minimal parameters for quick testing..."
	@echo "This tests the most important configs: G1 vs G1+CompactHeaders vs ZGC+Generational"
	scripts/run_gc_experiments_fast.sh both important

validate-gc: ## [perf] Validate JVM GC flags on current JDK
	@echo "Validating GC configurations for Java 25..."
	scripts/validate_gc_configs.sh

bench-dispatch: ## [perf] Run dispatch optimization JMH benchmarks
	@echo "Running dispatch optimization benchmarks..."
	@echo "This tests modern Java language features: pattern matching, switch expressions, etc."
	JMH_FORKS=3 JMH_WARMUP_IT=5 JMH_IT=10 scripts/run_dispatch_benchmarks.sh

# Enhanced JMH-based benchmarks with modern performance analysis
bench-enhanced: ## [perf] Run standard enhanced JMH benchmark set
	@echo "Running standard enhanced benchmarks with JMH Extended Testing Framework..."
	MAVEN_OPTS="$(JAVA_GC_OPTS)" scripts/run_enhanced_benchmarks.sh standard

bench-enhanced-quick: ## [perf] Run quick enhanced benchmark validation
	@echo "Running quick enhanced benchmarks for validation..."
	MAVEN_OPTS="$(JAVA_GC_OPTS)" scripts/run_enhanced_benchmarks.sh quick

bench-enhanced-full: ## [perf] Run full enhanced benchmark suite
	@echo "Running full enhanced benchmark suite with all matcher types..."
	MAVEN_OPTS="$(JAVA_GC_OPTS)" scripts/run_enhanced_benchmarks.sh full

bench-enhanced-arch: ## [perf] Run architecture-aware enhanced benchmark suite
	@echo "Running architecture-aware benchmarks for cross-platform comparison..."
	MAVEN_OPTS="$(JAVA_GC_OPTS)" scripts/run_enhanced_benchmarks.sh architecture

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
