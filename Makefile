 SHELL := /bin/bash

# Optimal GC settings based on experimentation (see GC_OPTIMIZATION_RESULTS.md)
# G1 with Compact Object Headers provides 5-12% performance improvement
JAVA_GC_OPTS := -XX:+UseCompactObjectHeaders

.PHONY: build test ci bench-micro bench-macro bench-java bench-suite test-run-once charts profile fmt spotless spotbugs visualize-benchmarks setup-visualization-env bench-gc-experiments bench-gc-experiments-fast validate-gc bench-dispatch bench-enhanced bench-enhanced-quick bench-enhanced-full bench-enhanced-arch

build:
	mvn -q -B spotless:apply
	mvn -U -q -B -DskipTests -Dspotbugs.skip=true package

test:
	mvn -q -B spotless:apply
	mvn -q -B verify

bench-micro:
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

bench-macro:
	MAVEN_OPTS="$(JAVA_GC_OPTS)" MAX_REGEXPS=10000 scripts/run_macro_with_memory.sh

bench-java:
	MAVEN_OPTS="$(JAVA_GC_OPTS)" MAX_REGEXPS=10000 scripts/run_java_benchmark_with_memory.sh

bench-suite:
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

test-run-once: pre-test-run
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

charts:
	python3 scripts/generate_macro_performance_plot.py
	python3 scripts/generate_java_performance_plot.py
	python3 scripts/generate_performance_comparison_plot.py
	python3 scripts/update_readme_performance_table.py

profile:
	DUR=30; scripts/profile_async_profiler.sh $$DUR

fmt:
	mvn -q -B spotless:apply

spotless:
	mvn -q -B spotless:apply

spotbugs:
	mvn -q -B spotbugs:check

pre-test-run:
	rm -rf ~/.m2/repository/no/rmz/rmatch
	mvn -q -B spotless:apply
	./mvnw clean install

test-run-full: pre-test-run
	time bash -x   ./scripts/run_jmh.sh

test-run-mini: pre-test-run
	time bash -x   ./scripts/run_jmh_mini_suite.sh

setup-visualization-env:
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

visualize-benchmarks: setup-visualization-env
	@echo "Generating JMH benchmark visualizations..."
	@mkdir -p performance-graphs
	@scripts/run_visualization.sh
	@echo "Visualizations saved to performance-graphs/"

bench-gc-experiments:
	@echo "Running GC experiments with different configurations..."
	@echo "This will test: G1 (default), ZGC Generational, Shenandoah, and Compact Object Headers variants"
	scripts/run_gc_experiments.sh both all

bench-gc-experiments-fast:
	@echo "Running FAST GC experiments with minimal parameters for quick testing..."
	@echo "This tests the most important configs: G1 vs G1+CompactHeaders vs ZGC+Generational"
	scripts/run_gc_experiments_fast.sh both important

validate-gc:
	@echo "Validating GC configurations for Java 25..."
	scripts/validate_gc_configs.sh

bench-dispatch:
	@echo "Running dispatch optimization benchmarks..."
	@echo "This tests modern Java language features: pattern matching, switch expressions, etc."
	JMH_FORKS=3 JMH_WARMUP_IT=5 JMH_IT=10 scripts/run_dispatch_benchmarks.sh

# Enhanced JMH-based benchmarks with modern performance analysis
bench-enhanced:
	@echo "Running standard enhanced benchmarks with JMH Extended Testing Framework..."
	MAVEN_OPTS="$(JAVA_GC_OPTS)" scripts/run_enhanced_benchmarks.sh standard

bench-enhanced-quick:
	@echo "Running quick enhanced benchmarks for validation..."
	MAVEN_OPTS="$(JAVA_GC_OPTS)" scripts/run_enhanced_benchmarks.sh quick

bench-enhanced-full:
	@echo "Running full enhanced benchmark suite with all matcher types..."
	MAVEN_OPTS="$(JAVA_GC_OPTS)" scripts/run_enhanced_benchmarks.sh full

bench-enhanced-arch:
	@echo "Running architecture-aware benchmarks for cross-platform comparison..."
	MAVEN_OPTS="$(JAVA_GC_OPTS)" scripts/run_enhanced_benchmarks.sh architecture
