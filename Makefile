 SHELL := /bin/bash

 .PHONY: build test ci bench-micro bench-macro bench-java charts profile fmt spotless spotbugs visualize-benchmarks setup-visualization-env

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
