# Regex Benchmarking Framework Makefile
# Provides convenient targets for setup, building, and running benchmarks

SHELL := /bin/bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c

# Configuration
VENV_DIR := .venv
PYTHON := $(VENV_DIR)/bin/python
PIP := $(VENV_DIR)/bin/pip
REGEX_BENCH := $(VENV_DIR)/bin/regex-bench
GCP_CONTROL := scripts/gcp_benchmark_control.py
GCP_PYTHON := $(PYTHON)

# Engine directories
ENGINES_DIR := engines
ENGINE_DIRS := $(wildcard $(ENGINES_DIR)/*)

# Results and output
RESULTS_DIR := results
REPORTS_DIR := reports
TIMESTAMP := $(shell date +%Y%m%d_%H%M%S)

# Platform detection
UNAME_S := $(shell uname -s)
UNAME_M := $(shell uname -m)
PLATFORM := $(UNAME_S)_$(UNAME_M)

# GCP benchmark control defaults (override with make VAR=value)
GCP_ZONE ?= europe-north2-a
GCP_BUCKET ?= run-cl-rmatch-bench
GCP_CONFIG ?= test_matrix/canonical_baseline_v1.json
GCP_IMAGE_URI ?=
GCP_DATA_URI ?=
GCP_RUN_ID ?=
GCP_BATCH_ID ?=
GCP_MACHINE_TYPE ?= e2-standard-8
GCP_MACHINE_TIER ?=
GCP_CONFIGS ?= $(GCP_CONFIG)
GCP_REQUIRE_ENGINES ?= rmatch,re2j,java-native-naive
GCP_ARGS ?=
GCP_CAMPAIGN_ID ?=
GCP_TIMEOUT_FACTOR ?= 4
GCP_POLL_SECONDS ?= 60
GCP_ITERATIONS ?= 2
GCP_WARMUP ?= 1
GCP_MAX_LANES ?= 3
GCP_DETACH ?= 1

# Local change gate defaults (override with make VAR=value)
GATE_CONFIG ?= test_matrix/stable_10k_moderate_rmatch.json
GATE_ENGINE ?= rmatch
GATE_METRIC ?= scanning_ns
GATE_MAX_SLOWDOWN ?= 1.10
GATE_BASELINE_BRANCH ?= main
GATE_BASELINE_DIR ?=
GATE_SKIP_SMOKE ?= 0
GATE_SKIP_REBUILD ?= 0

# Colors for output
RED := \033[0;31m
GREEN := \033[0;32m
YELLOW := \033[1;33m
BLUE := \033[0;34m
BOLD := \033[1m
NC := \033[0m # No Color

.DEFAULT_GOAL := help

##@ Setup and Dependencies

.PHONY: setup
setup: venv install-deps build-engines ## Complete setup: create venv, install deps, build engines
	@echo "$(GREEN)✓ Setup complete!$(NC)"
	@echo "$(BLUE)Platform: $(PLATFORM)$(NC)"
	@echo "$(BLUE)Python: $(shell $(PYTHON) --version)$(NC)"
	@echo ""
	@echo "$(BOLD)Quick start:$(NC)"
	@echo "  make test-quick     # Run quick validation"
	@echo "  make bench-phase1   # Run Phase 1 benchmarks"
	@echo "  make report         # Generate benchmark report"

.PHONY: venv
venv: venv-sanity $(VENV_DIR)/pyvenv.cfg ## Create Python virtual environment

.PHONY: venv-sanity
venv-sanity: ## Validate venv architecture and remove incompatible environments
	@if [ -f "$(VENV_DIR)/pyvenv.cfg" ]; then \
		host_arch="$$(uname -m)"; \
		venv_arch="$$( $(PYTHON) -c 'import platform; print(platform.machine())' 2>/dev/null || echo unknown )"; \
		if [ "$$venv_arch" != "$$host_arch" ]; then \
			echo "$(YELLOW)⚠ Rebuilding venv: python arch ($$venv_arch) != host arch ($$host_arch)$(NC)"; \
			rm -rf "$(VENV_DIR)"; \
			exit 0; \
		fi; \
		numpy_so="$$(find "$(VENV_DIR)"/lib -path '*/site-packages/numpy/_core/_multiarray_umath*.so' -print -quit 2>/dev/null || true)"; \
		if [ -n "$$numpy_so" ]; then \
			if command -v lipo >/dev/null 2>&1; then \
				if ! lipo -archs "$$numpy_so" 2>/dev/null | grep -qw "$$host_arch"; then \
					echo "$(YELLOW)⚠ Rebuilding venv: NumPy extension arch mismatch for $$host_arch$(NC)"; \
					rm -rf "$(VENV_DIR)"; \
				fi; \
			elif ! file "$$numpy_so" | grep -q "$$host_arch"; then \
				echo "$(YELLOW)⚠ Rebuilding venv: NumPy extension arch mismatch for $$host_arch$(NC)"; \
				rm -rf "$(VENV_DIR)"; \
			fi; \
		fi; \
	fi

$(VENV_DIR)/pyvenv.cfg:
	@echo "$(BLUE)Creating Python virtual environment...$(NC)"
	python3 -m venv $(VENV_DIR)
	$(PIP) install --upgrade pip setuptools wheel

.PHONY: install-deps
install-deps: venv ## Install Python dependencies
	@echo "$(BLUE)Installing Python dependencies...$(NC)"
	$(PIP) install -e .

.PHONY: clean-venv
clean-venv: ## Remove virtual environment
	@echo "$(YELLOW)Removing virtual environment...$(NC)"
	rm -rf $(VENV_DIR)

##@ Engine Management

.PHONY: build-rmatch
build-rmatch: ## Build and install rmatch to local Maven repository
	@echo "$(BLUE)Building and installing rmatch to .m2 repository...$(NC)"
	@cd ../.. && mvn clean install -DskipTests
	@echo "$(GREEN)✓ rmatch installed to local .m2 repository$(NC)"
	@echo "$(BLUE)Coordinates: no.rmz:rmatch:1.1-SNAPSHOT$(NC)"

.PHONY: build-engines
build-engines: venv ## Build all available engines
	@echo "$(BLUE)Building all engines...$(NC)"
	@bash -c 'for engine_dir in $(ENGINE_DIRS); do \
		if [ -d "$$engine_dir" ]; then \
			engine_name=$$(basename "$$engine_dir"); \
			echo "Building engine: $$engine_name"; \
			if [ -f "$$engine_dir/build.sh" ]; then \
				(cd "$$engine_dir" && bash build.sh); \
			else \
				echo "  No build.sh found for $$engine_name"; \
			fi; \
		fi; \
	done'

.PHONY: build-%
build-%: venv ## Build specific engine (e.g., make build-java-native)
	@engine_name=$(patsubst build-%,%,$@)
	@engine_dir=$(ENGINES_DIR)/$$engine_name
	@if [ ! -d "$$engine_dir" ]; then
		echo "$(RED)✗ Engine directory not found: $$engine_dir$(NC)"
		exit 1
	fi
	@echo "$(BLUE)Building engine: $$engine_name$(NC)"
	@if [ -f "$$engine_dir/build.sh" ]; then
		cd "$$engine_dir" && bash build.sh
		echo "$(GREEN)✓ Built $$engine_name$(NC)"
	else
		echo "$(YELLOW)⚠ No build.sh found for $$engine_name$(NC)"
	fi

.PHONY: check-engines
check-engines: venv ## Check which engines are available
	@echo "$(BOLD)Checking engine availability:$(NC)"
	@$(REGEX_BENCH) check-engines || true

.PHONY: clean-engines
clean-engines: ## Clean all engine build artifacts
	@echo "$(YELLOW)Cleaning engine build artifacts...$(NC)"
	@for engine_dir in $(ENGINE_DIRS); do
		if [ -d "$$engine_dir/.build" ]; then
			echo "  Cleaning $$engine_dir"
			rm -rf "$$engine_dir/.build"
		fi
	done

##@ Testing and Validation

.PHONY: test-quick
test-quick: venv build-engines ## Run quick validation tests
	@echo "$(BLUE)Running quick validation tests...$(NC)"
	@mkdir -p $(RESULTS_DIR)/quick_$(TIMESTAMP)
	$(REGEX_BENCH) -v run-phase \
		--config test_matrix/quick_validation.json \
		--output $(RESULTS_DIR)/quick_$(TIMESTAMP)
	@echo "$(GREEN)✓ Quick tests complete$(NC)"

.PHONY: test-correctness
test-correctness: venv build-engines ## Run correctness validation across engines
	@echo "$(BLUE)Running correctness validation...$(NC)"
	@mkdir -p $(RESULTS_DIR)/correctness_$(TIMESTAMP)
	$(REGEX_BENCH) validate-correctness \
		--config test_matrix/correctness.json \
		--output $(RESULTS_DIR)/correctness_$(TIMESTAMP) \
		--reference-engine java-native
	@echo "$(GREEN)✓ Correctness validation complete$(NC)"

.PHONY: test-unit
test-unit: venv ## Run Python unit tests
	@echo "$(BLUE)Running Python unit tests...$(NC)"
	$(VENV_DIR)/bin/pytest tests/ -v

.PHONY: gate-local-baseline
gate-local-baseline: install-deps ## Create local gate baseline (smoke + 10K moderate probe)
	@echo "$(BLUE)Running local change gate baseline...$(NC)"
	@cmd="$(PYTHON) scripts/local_change_gate.py baseline --config $(GATE_CONFIG) --engine $(GATE_ENGINE) --metric $(GATE_METRIC) --baseline-branch $(GATE_BASELINE_BRANCH)"; \
	if [ "$(GATE_SKIP_SMOKE)" = "1" ]; then \
		cmd="$$cmd --skip-smoke"; \
	fi; \
	if [ "$(GATE_SKIP_REBUILD)" = "1" ]; then \
		cmd="$$cmd --skip-rebuild"; \
	fi; \
	echo "$$cmd"; \
	eval "$$cmd"

.PHONY: gate-local-candidate
gate-local-candidate: install-deps ## Run local candidate gate and fail on performance regression
	@echo "$(BLUE)Running local change gate candidate check...$(NC)"
	@cmd="$(PYTHON) scripts/local_change_gate.py candidate --config $(GATE_CONFIG) --engine $(GATE_ENGINE) --metric $(GATE_METRIC) --max-slowdown $(GATE_MAX_SLOWDOWN) --baseline-branch $(GATE_BASELINE_BRANCH)"; \
	if [ -n "$(GATE_BASELINE_DIR)" ]; then \
		cmd="$$cmd --baseline-dir $(GATE_BASELINE_DIR)"; \
	fi; \
	if [ "$(GATE_SKIP_SMOKE)" = "1" ]; then \
		cmd="$$cmd --skip-smoke"; \
	fi; \
	if [ "$(GATE_SKIP_REBUILD)" = "1" ]; then \
		cmd="$$cmd --skip-rebuild"; \
	fi; \
	echo "$$cmd"; \
	eval "$$cmd"

##@ Benchmarking

.PHONY: setup-optimized-data
setup-optimized-data: ## Setup git-stored stable patterns and corpus files
	@echo "$(BLUE)Setting up optimized benchmark data with git-stored stable patterns...$(NC)"
	@mkdir -p benchmark_suites/corpora benchmark_suites/log_mining
	@# Use git-stored stable patterns (first choice)
	@if [ -f benchmark_suites/stable_patterns/patterns_10000.txt ]; then \
		echo "$(GREEN)✓ Found git-stored stable patterns - using those!$(NC)"; \
		cp benchmark_suites/stable_patterns/patterns_10000.txt benchmark_suites/log_mining/; \
		cp benchmark_suites/stable_patterns/patterns_10000_metadata.json benchmark_suites/log_mining/; \
		head -100 benchmark_suites/stable_patterns/patterns_10000.txt > benchmark_suites/log_mining/patterns_100.txt; \
		head -1000 benchmark_suites/stable_patterns/patterns_10000.txt > benchmark_suites/log_mining/patterns_1000.txt; \
		head -10 benchmark_suites/stable_patterns/patterns_10000.txt > benchmark_suites/log_mining/patterns_10.txt; \
		echo "$(GREEN)✓ Created all pattern sizes from stable git patterns$(NC)"; \
	elif [ -f benchmark_suites/log_mining/patterns_100_fixed.txt ]; then \
		echo "$(YELLOW)⚠ Stable patterns not found, using existing fixed patterns$(NC)"; \
	else \
		echo "$(YELLOW)⚠ Using fallback pattern generation$(NC)"; \
		$(REGEX_BENCH) generate-patterns --suite log_mining --sizes 100,1000,10000 --output benchmark_suites/log_mining/; \
	fi
	@# Ensure we have corpus files
	@if [ ! -f benchmark_suites/corpora/corpus_100MB.txt ]; then \
		echo "$(BLUE)Generating 100MB corpus...$(NC)"; \
		$(REGEX_BENCH) setup-corpora --sizes 100MB --types synthetic --output benchmark_suites/corpora/; \
	fi
	@echo "$(GREEN)✓ Optimized data ready with git-stored patterns$(NC)"

.PHONY: bench-phase1-optimized
bench-phase1-optimized: venv build-engines setup-optimized-data ## Run Phase 1 benchmarks with pre-validated data
	@echo "$(BLUE)Running OPTIMIZED Phase 1 benchmarks...$(NC)"
	@echo "$(GREEN)✓ Using pre-validated patterns and corpus$(NC)"
	@echo "$(YELLOW)This may take 30-60 minutes...$(NC)"
	@OUTPUT_DIR=$(RESULTS_DIR)/phase1_optimized_$(TIMESTAMP); \
	mkdir -p "$$OUTPUT_DIR/data"; \
	echo "$(BLUE)Pre-populating ALL required pattern files...$(NC)"; \
	if [ -f benchmark_suites/log_mining/patterns_10.txt ]; then \
		cp benchmark_suites/log_mining/patterns_10.txt "$$OUTPUT_DIR/data/patterns_10.txt"; \
		cp benchmark_suites/log_mining/patterns_10_metadata.json "$$OUTPUT_DIR/data/patterns_10_metadata.json" 2>/dev/null || echo "⚠ No patterns_10_metadata.json"; \
		echo "✓ Copied git-stored patterns_10.txt with metadata"; \
	else \
		echo "$(YELLOW)⚠ No git-stored patterns_10.txt, will generate$(NC)"; \
	fi; \
	if [ -f benchmark_suites/log_mining/patterns_100.txt ]; then \
		cp benchmark_suites/log_mining/patterns_100.txt "$$OUTPUT_DIR/data/patterns_100.txt"; \
		cp benchmark_suites/log_mining/patterns_100_metadata.json "$$OUTPUT_DIR/data/patterns_100_metadata.json" 2>/dev/null || echo "⚠ No patterns_100_metadata.json"; \
		echo "✓ Copied git-stored patterns_100.txt with metadata"; \
	else \
		echo "$(YELLOW)⚠ No git-stored patterns_100.txt, will generate$(NC)"; \
	fi; \
	if [ -f benchmark_suites/log_mining/patterns_1000.txt ]; then \
		cp benchmark_suites/log_mining/patterns_1000.txt "$$OUTPUT_DIR/data/patterns_1000.txt"; \
		cp benchmark_suites/log_mining/patterns_1000_metadata.json "$$OUTPUT_DIR/data/patterns_1000_metadata.json" 2>/dev/null || echo "⚠ No patterns_1000_metadata.json"; \
		echo "✓ Copied git-stored patterns_1000.txt with metadata"; \
	else \
		echo "$(YELLOW)⚠ No git-stored patterns_1000.txt, will generate$(NC)"; \
	fi; \
	echo "$(BLUE)Creating metadata files with phase1.json seed (12345)...$(NC)"; \
	python3 create_pattern_metadata.py "$$OUTPUT_DIR/data"; \
	if [ -f benchmark_suites/corpora/corpus_synthetic_1MB.txt ]; then \
		cp benchmark_suites/corpora/corpus_synthetic_1MB.txt "$$OUTPUT_DIR/data/corpus_synthetic_controllable_1MB.txt"; \
		echo "✓ Copied corpus_synthetic_1MB.txt as corpus_synthetic_controllable_1MB.txt"; \
	fi; \
	if [ -f benchmark_suites/corpora/corpus_synthetic_10MB.txt ]; then \
		cp benchmark_suites/corpora/corpus_synthetic_10MB.txt "$$OUTPUT_DIR/data/corpus_synthetic_controllable_10MB.txt"; \
		echo "✓ Copied corpus_synthetic_10MB.txt as corpus_synthetic_controllable_10MB.txt"; \
	fi; \
	if [ -f benchmark_suites/corpora/corpus_synthetic_100MB.txt ]; then \
		cp benchmark_suites/corpora/corpus_synthetic_100MB.txt "$$OUTPUT_DIR/data/corpus_synthetic_controllable_100MB.txt"; \
		echo "✓ Copied corpus_synthetic_100MB.txt as corpus_synthetic_controllable_100MB.txt"; \
	fi; \
	echo "$(GREEN)✓ Pre-validated data copied to $$OUTPUT_DIR/data$(NC)"; \
	ls -la "$$OUTPUT_DIR/data/"; \
	$(REGEX_BENCH) run-phase \
		--config test_matrix/phase1.json \
		--output "$$OUTPUT_DIR" \
		--engines rmatch,re2j,java-native-optimized,java-native-unfair \
		--parallel 4
	@echo "$(GREEN)✓ Optimized Phase 1 benchmarks complete$(NC)"
	@echo "$(BLUE)Results: $(RESULTS_DIR)/phase1_optimized_$(TIMESTAMP)$(NC)"

.PHONY: bench-10k-fast
bench-10k-fast: venv build-engines ## Run fast 10K pattern benchmark with all engines
	@echo "$(BLUE)Running FAST 10K pattern benchmark...$(NC)"
	@echo "$(GREEN)✓ Using timeout-fixed patterns with all engines$(NC)"
	@echo "$(YELLOW)This may take 30-45 minutes...$(NC)"
	@mkdir -p $(RESULTS_DIR)/fast_10k_$(TIMESTAMP)/data
	@# Copy our timeout-fixed patterns
	@echo "$(BLUE)Copying timeout-fixed 10K patterns...$(NC)"
	@if [ -f benchmark_suites/log_mining/patterns_10000_fixed.txt ]; then
		cp benchmark_suites/log_mining/patterns_10000_fixed.txt $(RESULTS_DIR)/fast_10k_$(TIMESTAMP)/data/patterns_10000.txt
	else
		echo "$(RED)✗ Timeout-fixed patterns not found$(NC)"
		exit 1
	fi
	@# Ensure corpus exists
	@if [ ! -f benchmark_suites/corpora/corpus_100MB.txt ]; then
		$(REGEX_BENCH) setup-corpora --sizes 100MB --types synthetic --output benchmark_suites/corpora/
	fi
	@cp benchmark_suites/corpora/corpus_100MB.txt $(RESULTS_DIR)/fast_10k_$(TIMESTAMP)/data/
	@echo "$(GREEN)✓ Data ready$(NC)"
	$(REGEX_BENCH) run-phase \
		--config test_matrix/ultimate_10k_sequential.json \
		--output $(RESULTS_DIR)/fast_10k_$(TIMESTAMP) \
		--engines rmatch,re2j,java-native-optimized,java-native-unfair \
		--parallel 1
	@echo "$(GREEN)✓ Fast 10K benchmark complete$(NC)"
	@echo "$(BLUE)Results: $(RESULTS_DIR)/fast_10k_$(TIMESTAMP)$(NC)"

.PHONY: bench-phase1
bench-phase1: bench-phase1-optimized ## Run Phase 1 comprehensive benchmarks (redirects to optimized version)

.PHONY: bench-phase1-original
bench-phase1-original: venv build-engines ## Run ORIGINAL Phase 1 benchmarks (may be slow)
	@echo "$(BLUE)Running ORIGINAL Phase 1 benchmarks...$(NC)"
	@echo "$(YELLOW)This may take 30-60 minutes...$(NC)"
	@mkdir -p $(RESULTS_DIR)/phase1_$(TIMESTAMP)
	$(REGEX_BENCH) run-phase \
		--config test_matrix/phase1.json \
		--output $(RESULTS_DIR)/phase1_$(TIMESTAMP)
	@echo "$(GREEN)✓ Phase 1 benchmarks complete$(NC)"
	@echo "$(BLUE)Results: $(RESULTS_DIR)/phase1_$(TIMESTAMP)$(NC)"

.PHONY: bench-phase2
bench-phase2: venv build-engines ## Run Phase 2 extended benchmarks
	@echo "$(BLUE)Running Phase 2 benchmarks...$(NC)"
	@echo "$(YELLOW)This may take 2-4 hours...$(NC)"
	@mkdir -p $(RESULTS_DIR)/phase2_$(TIMESTAMP)
	$(REGEX_BENCH) run-phase \
		--config test_matrix/phase2.json \
		--output $(RESULTS_DIR)/phase2_$(TIMESTAMP) \
		--parallel 2
	@echo "$(GREEN)✓ Phase 2 benchmarks complete$(NC)"

.PHONY: bench-custom
bench-custom: venv build-engines ## Run custom benchmark configuration
	@echo "$(BLUE)Running custom benchmark...$(NC)"
	@if [ -z "$(CONFIG)" ]; then
		echo "$(RED)✗ Please specify CONFIG file: make bench-custom CONFIG=path/to/config.json$(NC)"
		exit 1
	fi
	@mkdir -p $(RESULTS_DIR)/custom_$(TIMESTAMP)
	$(REGEX_BENCH) run-phase \
		--config $(CONFIG) \
		--output $(RESULTS_DIR)/custom_$(TIMESTAMP) \
		--verbose
	@echo "$(GREEN)✓ Custom benchmark complete$(NC)"

.PHONY: bench-single
bench-single: venv build-engines ## Run single engine benchmark
	@echo "$(BLUE)Running single engine benchmark...$(NC)"
	@if [ -z "$(ENGINE)" ]; then
		echo "$(RED)✗ Please specify ENGINE: make bench-single ENGINE=java-native$(NC)"
		exit 1
	fi
	@mkdir -p $(RESULTS_DIR)/single_$(TIMESTAMP)
	$(REGEX_BENCH) run-single \
		--engine $(ENGINE) \
		--patterns benchmark_suites/log_mining/patterns_100.txt \
		--corpus benchmark_suites/corpora/sample_10mb.txt \
		--output $(RESULTS_DIR)/single_$(TIMESTAMP) \
		--iterations 5
	@echo "$(GREEN)✓ Single engine benchmark complete$(NC)"

##@ Process Management

.PHONY: process-status
process-status: venv ## Show benchmark process status and cleanup orphans
	@latest_db_file=$$(find $(RESULTS_DIR) -maxdepth 2 -type f -name "jobs.db" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2-); \
	if [ -n "$(OUTPUT)" ]; then \
		target_dir="$(OUTPUT)"; \
	elif [ -n "$$latest_db_file" ]; then \
		target_dir=$$(dirname "$$latest_db_file"); \
	else \
		target_dir=""; \
	fi; \
	if [ -n "$$target_dir" ]; then \
		echo "$(BLUE)Checking processes for: $$target_dir$(NC)"; \
		$(REGEX_BENCH) process-status --output "$$target_dir"; \
	else \
		echo "$(YELLOW)⚠ No jobs.db found under $(RESULTS_DIR).$(NC)"; \
		echo "$(BLUE)For GCP run progress, use: make gcp-live GCP_RUN_ID=<run_id>$(NC)"; \
		echo "$(BLUE)Or inspect latest run: make gcp-status$(NC)"; \
	fi

.PHONY: process-cleanup
process-cleanup: venv ## Clean up orphaned benchmark processes
	@latest_db_file=$$(find $(RESULTS_DIR) -maxdepth 2 -type f -name "jobs.db" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2-); \
	if [ -n "$(OUTPUT)" ]; then \
		target_dir="$(OUTPUT)"; \
	elif [ -n "$$latest_db_file" ]; then \
		target_dir=$$(dirname "$$latest_db_file"); \
	else \
		target_dir=""; \
	fi; \
	if [ -n "$$target_dir" ]; then \
		echo "$(BLUE)Cleaning processes for: $$target_dir$(NC)"; \
		$(REGEX_BENCH) process-status --output "$$target_dir" --cleanup; \
	else \
		echo "$(YELLOW)⚠ No jobs.db found under $(RESULTS_DIR); nothing to clean up.$(NC)"; \
	fi

.PHONY: process-monitor
process-monitor: venv ## Monitor and update process status
	@latest_db_file=$$(find $(RESULTS_DIR) -maxdepth 2 -type f -name "jobs.db" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2-); \
	if [ -n "$(OUTPUT)" ]; then \
		target_dir="$(OUTPUT)"; \
	elif [ -n "$$latest_db_file" ]; then \
		target_dir=$$(dirname "$$latest_db_file"); \
	else \
		target_dir=""; \
	fi; \
	if [ -n "$$target_dir" ]; then \
		echo "$(BLUE)Monitoring processes for: $$target_dir$(NC)"; \
		$(REGEX_BENCH) process-status --output "$$target_dir" --monitor; \
	else \
		echo "$(YELLOW)⚠ No jobs.db found under $(RESULTS_DIR).$(NC)"; \
		echo "$(BLUE)For GCP run progress, use: make gcp-live GCP_RUN_ID=<run_id>$(NC)"; \
		echo "$(BLUE)Or inspect latest run: make gcp-status$(NC)"; \
	fi

##@ GCP Control

.PHONY: gcp
gcp: venv ## Run GCP control command in project venv (usage: make gcp CMD="list")
	@if [ -z "$(CMD)" ]; then \
		echo "$(RED)✗ Missing CMD. Example: make gcp CMD=\"status --run-id <id>\"$(NC)"; \
		exit 1; \
	fi
	$(GCP_PYTHON) $(GCP_CONTROL) $(CMD)

.PHONY: gcp-list
gcp-list: venv ## List known GCP benchmark runs
	$(GCP_PYTHON) $(GCP_CONTROL) list

.PHONY: gcp-list-batches
gcp-list-batches: venv ## List known GCP benchmark batches
	$(GCP_PYTHON) $(GCP_CONTROL) list-batches

.PHONY: gcp-status
gcp-status: venv ## Show run status (optional: GCP_RUN_ID=<id>)
	@cmd="status"; \
	if [ -n "$(GCP_RUN_ID)" ]; then \
		cmd="$$cmd --run-id $(GCP_RUN_ID)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-status-batch
gcp-status-batch: venv ## Show batch status (optional: GCP_BATCH_ID=<id>)
	@cmd="status-batch"; \
	if [ -n "$(GCP_BATCH_ID)" ]; then \
		cmd="$$cmd --batch-id $(GCP_BATCH_ID)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-watch
gcp-watch: venv ## Watch run status (optional: GCP_RUN_ID=<id>, INTERVAL=<sec>)
	@cmd="watch"; \
	if [ -n "$(GCP_RUN_ID)" ]; then \
		cmd="$$cmd --run-id $(GCP_RUN_ID)"; \
	fi; \
	if [ -n "$(INTERVAL)" ]; then \
		cmd="$$cmd --interval $(INTERVAL)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-watch-batch
gcp-watch-batch: venv ## Watch batch status (optional: GCP_BATCH_ID=<id>, INTERVAL=<sec>)
	@cmd="watch-batch"; \
	if [ -n "$(GCP_BATCH_ID)" ]; then \
		cmd="$$cmd --batch-id $(GCP_BATCH_ID)"; \
	fi; \
	if [ -n "$(INTERVAL)" ]; then \
		cmd="$$cmd --interval $(INTERVAL)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-live
gcp-live: venv ## Live TUI-style monitor for run status (optional: GCP_RUN_ID=<id>, INTERVAL=<sec>)
	@interval="$${INTERVAL:-20}"; \
	INTERVAL_SECONDS="$$interval" bash scripts/gcp_live_monitor.sh run "$(GCP_RUN_ID)"

.PHONY: gcp-live-batch
gcp-live-batch: venv ## Live TUI-style monitor for batch status (optional: GCP_BATCH_ID=<id>, INTERVAL=<sec>)
	@interval="$${INTERVAL:-20}"; \
	INTERVAL_SECONDS="$$interval" bash scripts/gcp_live_monitor.sh batch "$(GCP_BATCH_ID)"

.PHONY: gcp-publish-image
gcp-publish-image: venv ## Publish Docker image to Artifact Registry
	@cmd="publish-image"; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-publish-data
gcp-publish-data: venv ## Publish dataset bundle to GCS (default uses GCP_CONFIG)
	@cmd="publish-data --zone $(GCP_ZONE) --bucket $(GCP_BUCKET) --config $(GCP_CONFIG)"; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-start
gcp-start: venv ## Start GCP run (defaults: GCP_CONFIG, GCP_ZONE, GCP_BUCKET)
	@cmd="start --zone $(GCP_ZONE) --bucket $(GCP_BUCKET) --config $(GCP_CONFIG) --machine-type $(GCP_MACHINE_TYPE)"; \
	if [ -n "$(GCP_MACHINE_TIER)" ]; then \
		cmd="$$cmd --machine-tier $(GCP_MACHINE_TIER)"; \
	fi; \
	if [ -n "$(GCP_IMAGE_URI)" ]; then \
		cmd="$$cmd --image-uri $(GCP_IMAGE_URI)"; \
	fi; \
	if [ -n "$(GCP_DATA_URI)" ]; then \
		cmd="$$cmd --data-uri $(GCP_DATA_URI)"; \
	fi; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-resume
gcp-resume: venv ## Resume stopped/terminated GCP run VM (optional: GCP_RUN_ID=<id>)
	@cmd="resume"; \
	if [ -n "$(GCP_RUN_ID)" ]; then \
		cmd="$$cmd --run-id $(GCP_RUN_ID)"; \
	fi; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-start-batch
gcp-start-batch: venv ## Start batch runs (one VM per config) using GCP_CONFIGS=cfg1,cfg2,...
	@cmd="start-batch --zone $(GCP_ZONE) --bucket $(GCP_BUCKET) --configs $(GCP_CONFIGS) --machine-type $(GCP_MACHINE_TYPE)"; \
	if [ -n "$(GCP_MACHINE_TIER)" ]; then \
		cmd="$$cmd --machine-tier $(GCP_MACHINE_TIER)"; \
	fi; \
	if [ -n "$(GCP_BATCH_ID)" ]; then \
		cmd="$$cmd --batch-id $(GCP_BATCH_ID)"; \
	fi; \
	if [ -n "$(GCP_IMAGE_URI)" ]; then \
		cmd="$$cmd --image-uri $(GCP_IMAGE_URI)"; \
	fi; \
	if [ -n "$(GCP_DATA_URI)" ]; then \
		cmd="$$cmd --data-uri $(GCP_DATA_URI)"; \
	fi; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-start-smoke
gcp-start-smoke: venv ## Start a tiny smoke run on GCP
	@cmd="start --zone $(GCP_ZONE) --bucket $(GCP_BUCKET) --config test_matrix/smoke_phase4_tiny.json --machine-type e2-standard-4"; \
	if [ -n "$(GCP_IMAGE_URI)" ]; then \
		cmd="$$cmd --image-uri $(GCP_IMAGE_URI)"; \
	fi; \
	if [ -n "$(GCP_DATA_URI)" ]; then \
		cmd="$$cmd --data-uri $(GCP_DATA_URI)"; \
	fi; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-cancel
gcp-cancel: venv ## Request graceful cancel (optional: GCP_RUN_ID=<id>, GCP_ARGS="--force-stop")
	@cmd="cancel"; \
	if [ -n "$(GCP_RUN_ID)" ]; then \
		cmd="$$cmd --run-id $(GCP_RUN_ID)"; \
	fi; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-cancel-batch
gcp-cancel-batch: venv ## Request graceful cancel for a batch (optional: GCP_BATCH_ID=<id>)
	@cmd="cancel-batch"; \
	if [ -n "$(GCP_BATCH_ID)" ]; then \
		cmd="$$cmd --batch-id $(GCP_BATCH_ID)"; \
	fi; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-stop
gcp-stop: venv ## Immediately stop VM (optional: GCP_RUN_ID=<id>)
	@cmd="stop"; \
	if [ -n "$(GCP_RUN_ID)" ]; then \
		cmd="$$cmd --run-id $(GCP_RUN_ID)"; \
	fi; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-stop-batch
gcp-stop-batch: venv ## Immediately stop VMs in a batch (optional: GCP_BATCH_ID=<id>)
	@cmd="stop-batch"; \
	if [ -n "$(GCP_BATCH_ID)" ]; then \
		cmd="$$cmd --batch-id $(GCP_BATCH_ID)"; \
	fi; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-cohort-report
gcp-cohort-report: venv ## Show comparable cohorts and pairwise coverage (optional: GCP_BATCH_ID=<id>)
	@cmd="cohort-report --require-engines $(GCP_REQUIRE_ENGINES)"; \
	if [ -n "$(GCP_BATCH_ID)" ]; then \
		cmd="$$cmd --batch-id $(GCP_BATCH_ID)"; \
	fi; \
	if [ -n "$(GCP_MACHINE_TIER)" ]; then \
		cmd="$$cmd --tier $(GCP_MACHINE_TIER)"; \
	fi; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	$(GCP_PYTHON) $(GCP_CONTROL) $$cmd

.PHONY: gcp-10k-campaign-start
gcp-10k-campaign-start: venv ## Start dynamic 10K campaign (rmatch+re2j baseline, java-naive with 4x cutoffs)
	@campaign_id="$(GCP_CAMPAIGN_ID)"; \
	if [ -z "$$campaign_id" ]; then \
		campaign_id="10k_dynamic_$(TIMESTAMP)"; \
	fi; \
	log_path="gcp_runs/campaign_$${campaign_id}.log"; \
	pid_path="gcp_runs/campaign_$${campaign_id}.pid"; \
	cmd="$(PWD)/$(GCP_PYTHON) $(PWD)/scripts/gcp_10k_dynamic_campaign.py --campaign-id $$campaign_id --zone $(GCP_ZONE) --bucket $(GCP_BUCKET) --machine-type $(GCP_MACHINE_TYPE) --timeout-factor $(GCP_TIMEOUT_FACTOR) --poll-seconds $(GCP_POLL_SECONDS) --iterations $(GCP_ITERATIONS) --warmup $(GCP_WARMUP) --max-lanes $(GCP_MAX_LANES)"; \
	if [ -n "$(GCP_IMAGE_URI)" ]; then \
		cmd="$$cmd --image-uri $(GCP_IMAGE_URI)"; \
	fi; \
	if [ -n "$(GCP_DATA_URI)" ]; then \
		cmd="$$cmd --data-uri $(GCP_DATA_URI)"; \
	fi; \
	if [ -n "$(GCP_ARGS)" ]; then \
		cmd="$$cmd $(GCP_ARGS)"; \
	fi; \
	echo "$(BLUE)Campaign ID: $$campaign_id$(NC)"; \
	echo "$(BLUE)Log file: $$log_path$(NC)"; \
	if [ "$(GCP_DETACH)" = "1" ]; then \
		nohup bash -lc "cd $(PWD) && $$cmd" > "$$log_path" 2>&1 & \
		echo $$! > "$$pid_path"; \
		echo "$(GREEN)✓ Started detached campaign (pid $$(cat $$pid_path))$(NC)"; \
		echo "$(CYAN)Use: make gcp-10k-campaign-status GCP_CAMPAIGN_ID=$$campaign_id$(NC)"; \
		echo "$(CYAN)Use: make gcp-10k-campaign-log GCP_CAMPAIGN_ID=$$campaign_id$(NC)"; \
	else \
		bash -lc "cd $(PWD) && $$cmd" | tee "$$log_path"; \
	fi

.PHONY: gcp-10k-campaign-status
gcp-10k-campaign-status: venv ## Show dynamic 10K campaign status (optional: GCP_CAMPAIGN_ID=<id>)
	@state_file=""; \
	if [ -n "$(GCP_CAMPAIGN_ID)" ]; then \
		state_file="gcp_runs/campaign_$(GCP_CAMPAIGN_ID).json"; \
	else \
		state_file="$$(ls -1t gcp_runs/campaign_*.json 2>/dev/null | head -1)"; \
	fi; \
	if [ -z "$$state_file" ] || [ ! -f "$$state_file" ]; then \
		echo "$(RED)✗ No campaign state file found$(NC)"; \
		exit 1; \
	fi; \
	echo "$(BLUE)Campaign state: $$state_file$(NC)"; \
	$(GCP_PYTHON) -c "import json,sys; d=json.load(open(sys.argv[1])); print('Campaign:',d.get('campaign_id','-')); print('Updated :',d.get('updated_at','-')); print('Machine :',d.get('machine_type','-')); print('Batch   :',d.get('baseline_batch_id','-')); print('Data URI:',d.get('data_uri','-')); print(''); s=d.get('status_by_run',{}); rows=sorted(s.values(), key=lambda r:(r.get('size',''), r.get('role',''), r.get('run_id',''))); print('Runs:'); [print(f\"  {r.get('run_id','-')}  {r.get('size','-'):<5} {r.get('role','-'):<26} {r.get('status','-'):<12} {r.get('completed_jobs',0):>3}/{r.get('total_jobs',0):<3} eta={r.get('remaining_seconds',-1)}\") for r in rows] if rows else print('  (no run status yet)'); t=d.get('java_timeouts_seconds',{}); print(''); print('Java dynamic timeouts (s):'); [print(f'  {k}: {t[k]}') for k in sorted(t.keys())] if t else print('  (none yet)'); sk=d.get('java_skipped',{}); print('Java skipped:'); [print(f'  {k}: {sk[k]}') for k in sorted(sk.keys())] if sk else print('  (none)')" "$$state_file"

.PHONY: gcp-10k-campaign-log
gcp-10k-campaign-log: ## Tail dynamic 10K campaign log (optional: GCP_CAMPAIGN_ID=<id>, LINES=80, FOLLOW=1)
	@log_file=""; \
	if [ -n "$(GCP_CAMPAIGN_ID)" ]; then \
		log_file="gcp_runs/campaign_$(GCP_CAMPAIGN_ID).log"; \
	else \
		log_file="$$(ls -1t gcp_runs/campaign_*.log 2>/dev/null | head -1)"; \
	fi; \
	if [ -z "$$log_file" ] || [ ! -f "$$log_file" ]; then \
		echo "$(RED)✗ No campaign log found$(NC)"; \
		exit 1; \
	fi; \
	lines="$${LINES:-80}"; \
	if [ "$${FOLLOW:-0}" = "1" ]; then \
		echo "$(BLUE)Following log: $$log_file$(NC)"; \
		tail -n "$$lines" -f "$$log_file"; \
	else \
		echo "$(BLUE)Log: $$log_file$(NC)"; \
		tail -n "$$lines" "$$log_file"; \
	fi

##@ Analysis and Reporting

.PHONY: report
report: install-deps ## Generate benchmark report from latest results
	@echo "$(BLUE)Generating benchmark report...$(NC)"
	@latest_result=""; \
	for candidate in $$(find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -nr | cut -d' ' -f2-); do \
		if [ -f "$$candidate/raw_results/benchmark_results.json" ] || [ -f "$$candidate/summary.json" ] || [ -f "$$candidate/jobs.db" ]; then \
			latest_result="$$candidate"; \
			break; \
		fi; \
	done; \
	if [ -z "$$latest_result" ]; then \
		echo "$(RED)✗ No benchmark result directories with benchmark artifacts found in $(RESULTS_DIR)$(NC)"; \
		exit 1; \
	fi; \
	echo "$(BLUE)Analyzing results: $$latest_result$(NC)"; \
	report_dir="$(REPORTS_DIR)/report_$(TIMESTAMP)"; \
	mkdir -p "$$report_dir"; \
	$(REGEX_BENCH) generate-report \
		--input "$$latest_result" \
		--output "$$report_dir" \
		--format html \
		--include-charts; \
	if command -v gcloud >/dev/null 2>&1; then \
		echo "$(BLUE)Capturing GCP run status snapshot...$(NC)"; \
		$(GCP_PYTHON) scripts/gcp_run_status_report.py \
			--bucket "$(GCP_BUCKET)" \
			--output "$$report_dir/gcp_run_status_snapshot.md" || \
			echo "$(YELLOW)⚠ Could not generate GCP run status snapshot$(NC)"; \
	else \
		echo "$(YELLOW)⚠ gcloud not found; skipping GCP run status snapshot$(NC)"; \
	fi
	@echo "$(GREEN)✓ Report generated: $(REPORTS_DIR)/report_$(TIMESTAMP)$(NC)"

.PHONY: report-workload-compare
report-workload-compare: venv ## Generate workload-vs-engine comparison report from latest results
	@echo "$(BLUE)Generating workload engine comparison report...$(NC)"
	@input_dir="$(RUN)"; \
	if [ -z "$$input_dir" ]; then \
		for candidate in $$(find $(RESULTS_DIR) -maxdepth 1 -mindepth 1 -type d 2>/dev/null | while read -r d; do \
			if [ -f "$$d/run_manifest.json" ]; then \
				printf "0 %s\n" "$$d"; \
			else \
				printf "1 %s\n" "$$d"; \
			fi; \
		done | sort -n -k1,1 -k2,2r | cut -d' ' -f2-); do \
			if [ -f "$$candidate/raw_results/benchmark_results.json" ] || [ -f "$$candidate/raw_results/benchmark_results.partial.json" ] || [ -f "$$candidate/jobs.db" ]; then \
				input_dir="$$candidate"; \
				break; \
			fi; \
		done; \
	fi; \
	if [ -z "$$input_dir" ]; then \
		echo "$(RED)✗ No benchmark result directories with raw final/partial results or jobs.db found in $(RESULTS_DIR)$(NC)"; \
		exit 1; \
	fi; \
	if [ ! -d "$$input_dir" ]; then \
		echo "$(RED)✗ RUN directory not found: $$input_dir$(NC)"; \
		exit 1; \
	fi; \
	report_dir="$(OUT)"; \
	if [ -z "$$report_dir" ]; then \
		report_dir="$(REPORTS_DIR)/workload_comparison_$(TIMESTAMP)"; \
	fi; \
	echo "$(BLUE)Analyzing results: $$input_dir$(NC)"; \
	mkdir -p "$$report_dir"; \
	$(GCP_PYTHON) scripts/generate_workload_engine_comparison.py \
		--input "$$input_dir" \
		--output "$$report_dir"; \
	echo "$(GREEN)✓ Workload comparison report generated: $$report_dir$(NC)"

.PHONY: report-workload-web
report-workload-web: report-workload-compare ## Generate workload comparison webpage (HTML + CSV + Markdown)

.PHONY: report-workload-web-all
report-workload-web-all: venv ## Generate all-runs workload comparison webpage (aggregates entire results/)
	@echo "$(BLUE)Generating all-runs workload engine comparison report...$(NC)"
	@results_root="$(RESULTS_ROOT)"; \
	if [ -z "$$results_root" ]; then \
		results_root="$(RESULTS_DIR)"; \
	fi; \
	report_dir="$(OUT)"; \
	if [ -z "$$report_dir" ]; then \
		report_dir="$(REPORTS_DIR)/workload_comparison_all_$(TIMESTAMP)"; \
	fi; \
	mkdir -p "$$report_dir"; \
	$(GCP_PYTHON) scripts/generate_workload_engine_comparison_all.py \
		--results-dir "$$results_root" \
		--output "$$report_dir"; \
	echo "$(GREEN)✓ All-runs workload report generated: $$report_dir$(NC)"

.PHONY: report-model-paper
report-model-paper: venv ## Generate a compact LaTeX paper with scaling model + 2D dominance map
	@echo "$(BLUE)Generating modeling paper (LaTeX)...$(NC)"
	@report_dir="$(OUT)"; \
	if [ -z "$$report_dir" ]; then \
		report_dir="$(REPORTS_DIR)/workload_all_live"; \
	fi; \
	if [ ! -f "$$report_dir/all_runs_workload_engine.csv" ]; then \
		echo "$(YELLOW)No all-runs CSV in $$report_dir; generating workload report first...$(NC)"; \
		$(MAKE) --no-print-directory report-workload-web-all OUT="$$report_dir"; \
	fi; \
	output_tex="$${report_dir}/modeling_dominance_note.tex"; \
	$(GCP_PYTHON) scripts/generate_modeling_paper.py \
		--input-csv "$$report_dir/all_runs_workload_engine.csv" \
		--output-tex "$$output_tex" \
		$${COHORT:+--cohort "$$COHORT"} \
		$${ENGINES:+--engines "$$ENGINES"}; \
	if command -v pdflatex >/dev/null 2>&1; then \
		echo "$(BLUE)Compiling PDF with pdflatex...$(NC)"; \
		if ( cd "$$report_dir" && pdflatex -interaction=nonstopmode -halt-on-error "$$(basename "$$output_tex")" >/dev/null && pdflatex -interaction=nonstopmode -halt-on-error "$$(basename "$$output_tex")" >/dev/null ); then \
			if [ -f "$$report_dir/modeling_dominance_note.pdf" ]; then \
				echo "$(GREEN)✓ PDF generated: $$report_dir/modeling_dominance_note.pdf$(NC)"; \
			else \
				echo "$(YELLOW)⚠ pdflatex completed but PDF file not found. Check $$report_dir/modeling_dominance_note.log$(NC)"; \
			fi; \
		else \
			echo "$(YELLOW)⚠ pdflatex failed. TeX generated: $$output_tex (see $$report_dir/modeling_dominance_note.log)$(NC)"; \
		fi; \
	else \
		echo "$(YELLOW)⚠ pdflatex not found; generated TeX only: $$output_tex$(NC)"; \
	fi; \
	echo "$(BLUE)Refreshing web report to expose paper links/status...$(NC)"; \
	$(MAKE) --no-print-directory report-workload-web-all OUT="$$report_dir"

.PHONY: report-standalone
report-standalone: ## Generate benchmark report using standalone generator
	@echo "$(BLUE)Generating benchmark report (standalone)...$(NC)"
	@latest_result=$$(find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2- || find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" | sort | tail -1); \
	if [ -z "$$latest_result" ]; then \
		echo "$(RED)✗ No benchmark results found in $(RESULTS_DIR)$(NC)"; \
		exit 1; \
	fi; \
	echo "$(BLUE)Analyzing results: $$latest_result$(NC)"; \
	sed "s|results/quick_20251219_140928|$$latest_result|" standalone_report_test.py | python3
	@echo "$(GREEN)✓ Report generated: test_reports/benchmark_report.html$(NC)"

.PHONY: report-live
report-live: venv ## Generate live progress report for running benchmarks
	@echo "$(BLUE)Generating live progress report...$(NC)"
	@latest_result=$$(find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2- || find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" | sort | tail -1); \
	if [ -z "$$latest_result" ]; then \
		echo "$(RED)✗ No benchmark results found in $(RESULTS_DIR)$(NC)"; \
		exit 1; \
	fi; \
	echo "$(GREEN)Monitoring: $$latest_result$(NC)"; \
	$(REGEX_BENCH) generate-report \
		--input "$$latest_result" \
		--output "reports/live_$(notdir $$latest_result)" \
		--live
	@echo "$(GREEN)✓ Live progress report generated$(NC)"
	@echo "$(CYAN)📄 Open the report in your browser and it will auto-refresh every 30 seconds$(NC)"

.PHONY: compare
compare: venv ## Compare two benchmark runs
	@echo "$(BLUE)Comparing benchmark runs...$(NC)"
	@if [ -z "$(BASELINE)" ] || [ -z "$(COMPARISON)" ]; then
		echo "$(RED)✗ Please specify BASELINE and COMPARISON directories$(NC)"
		echo "$(BLUE)Example: make compare BASELINE=results/phase1_20241219_120000 COMPARISON=results/phase1_20241219_140000$(NC)"
		exit 1
	fi
	@mkdir -p $(REPORTS_DIR)
	$(REGEX_BENCH) compare-runs \
		--baseline $(BASELINE) \
		--comparison $(COMPARISON) \
		--output $(REPORTS_DIR)/comparison_$(TIMESTAMP) \
		--format html
	@echo "$(GREEN)✓ Comparison report: $(REPORTS_DIR)/comparison_$(TIMESTAMP)$(NC)"

.PHONY: stats
stats: venv ## Show statistics from latest benchmark
	@latest_result=$$(find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2- || find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" | sort | tail -1); \
	if [ -z "$$latest_result" ]; then \
		echo "$(RED)✗ No benchmark results found$(NC)"; \
		exit 1; \
	fi; \
	echo "$(BOLD)Latest Benchmark Statistics:$(NC)"; \
	$(REGEX_BENCH) show-stats --input "$$latest_result"

##@ Pattern and Corpus Management

.PHONY: generate-patterns
generate-patterns: venv ## Generate pattern suites
	@echo "$(BLUE)Generating pattern suites...$(NC)"
	$(REGEX_BENCH) generate-patterns \
		--suite log_mining \
		--sizes 10,100,1000,10000 \
		--output benchmark_suites/log_mining/
	$(REGEX_BENCH) generate-patterns \
		--suite security_signatures \
		--sizes 10,100,1000 \
		--output benchmark_suites/security_signatures/
	@echo "$(GREEN)✓ Pattern suites generated$(NC)"

.PHONY: generate-corpora
generate-corpora: venv ## Generate or download test corpora
	@echo "$(BLUE)Setting up test corpora...$(NC)"
	@mkdir -p benchmark_suites/corpora
	$(REGEX_BENCH) setup-corpora \
		--sizes 1MB,10MB,100MB \
		--types synthetic,logs,natural_language \
		--output benchmark_suites/corpora/
	@echo "$(GREEN)✓ Test corpora ready$(NC)"

##@ Maintenance and Cleanup

.PHONY: clean
clean: clean-engines ## Clean build artifacts (keep venv)
	@echo "$(YELLOW)Cleaning build artifacts...$(NC)"
	@find . -name "*.pyc" -delete
	@find . -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true
	@rm -rf build/ dist/ *.egg-info/

.PHONY: clean-all
clean-all: clean clean-venv ## Clean everything including virtual environment
	@echo "$(YELLOW)Cleaning all artifacts...$(NC)"
	@rm -rf $(RESULTS_DIR)/* $(REPORTS_DIR)/*

.PHONY: clean-results
clean-results: ## Clean benchmark results (keep latest 5)
	@echo "$(YELLOW)Cleaning old benchmark results...$(NC)"
	@cd $(RESULTS_DIR) && ls -1t | tail -n +6 | xargs -r rm -rf
	@echo "$(GREEN)✓ Kept latest 5 result sets$(NC)"

.PHONY: update-deps
update-deps: venv ## Update Python dependencies
	@echo "$(BLUE)Updating Python dependencies...$(NC)"
	$(PIP) install --upgrade pip setuptools wheel
	$(PIP) install -e . --upgrade
	@test -f requirements-dev.txt && $(PIP) install -r requirements-dev.txt --upgrade || true

##@ Information and Help

.PHONY: status
status: ## Show system status and configuration
	@echo "$(BOLD)Regex Benchmarking Framework Status$(NC)"
	@echo "$(BLUE)Platform:$(NC) $(PLATFORM)"
	@echo "$(BLUE)Python:$(NC) $(shell python3 --version 2>/dev/null || echo 'Not found')"
	@echo "$(BLUE)Virtual env:$(NC) $(if $(shell test -d $(VENV_DIR) && echo 'yes'),✓ Active,✗ Not created)"
	@echo ""
	@echo "$(BOLD)Available Engines:$(NC)"
	@bash -c 'for engine_dir in $(ENGINE_DIRS); do \
		engine_name=$$(basename $$engine_dir); \
		if [ -f "$$engine_dir/engine.json" ]; then \
			build_status=$$(if [ -d "$$engine_dir/.build" ]; then echo "$(GREEN)✓$(NC)"; else echo "$(YELLOW)○$(NC)"; fi); \
			echo "  $$build_status $$engine_name"; \
		fi; \
	done'
	@echo ""
	@echo "$(BOLD)Recent Results:$(NC)"
	@bash -c 'find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" 2>/dev/null | sort -r | head -5 | while read dir; do \
		echo "  $$(basename $$dir)"; \
	done || echo "  No results found"'

.PHONY: info
info: ## Show detailed system information for debugging
	@echo "$(BOLD)System Information$(NC)"
	@echo "$(BLUE)OS:$(NC) $$(uname -a)"
	@echo "$(BLUE)Python:$(NC) $$(python3 --version) at $$(which python3)"
	@echo "$(BLUE)Available RAM:$(NC) $$(free -h 2>/dev/null | grep Mem | awk '{print $$2}' || system_profiler SPHardwareDataType 2>/dev/null | grep Memory | awk '{print $$2 $$3}' || echo 'Unknown')"
	@echo "$(BLUE)CPU:$(NC) $$(sysctl -n machdep.cpu.brand_string 2>/dev/null || grep -m1 'model name' /proc/cpuinfo 2>/dev/null | cut -d: -f2 | sed 's/^ *//' || echo 'Unknown')"
	@echo "$(BLUE)Cores:$(NC) $$(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 'Unknown')"
	@echo ""
	@echo "$(BOLD)Required Tools:$(NC)"
	@command -v java >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) java: $$(java -version 2>&1 | head -1)" || echo "  $(RED)✗$(NC) java: not found"
	@command -v javac >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) javac: $$(javac -version 2>&1)" || echo "  $(RED)✗$(NC) javac: not found"
	@command -v gcc >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) gcc: $$(gcc --version 2>&1 | head -1)" || echo "  $(RED)✗$(NC) gcc: not found"
	@command -v g++ >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) g++: $$(g++ --version 2>&1 | head -1)" || echo "  $(RED)✗$(NC) g++: not found"
	@command -v go >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) go: $$(go version)" || echo "  $(RED)✗$(NC) go: not found"
	@command -v python3 >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) python3: $$(python3 --version)" || echo "  $(RED)✗$(NC) python3: not found"
	@command -v make >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) make: $$(make --version | head -1)" || echo "  $(RED)✗$(NC) make: not found"

.PHONY: help
help: ## Display this help message
	@awk 'BEGIN {FS = ":.*##"; printf "\n$(BOLD)Regex Benchmarking Framework$(NC)\n\n"} /^[a-zA-Z_-]+:.*?##/ { printf "  $(BLUE)%-15s$(NC) %s\n", $$1, $$2 } /^##@/ { printf "\n$(BOLD)%s$(NC)\n", substr($$0, 5) } ' $(MAKEFILE_LIST)
	@echo ""

# Utility targets for advanced users
.PHONY: dev-setup
dev-setup: setup ## Setup for development with additional tools
	$(PIP) install -r requirements-dev.txt
	@echo "$(GREEN)✓ Development setup complete$(NC)"

.PHONY: docker-build
docker-build: ## Build Docker image for cross-platform testing
	@echo "$(BLUE)Building Docker image...$(NC)"
	docker build -t regex-bench-framework .
	@echo "$(GREEN)✓ Docker image built$(NC)"

.PHONY: docker-test
docker-test: docker-build ## Run tests in Docker container
	@echo "$(BLUE)Running tests in Docker...$(NC)"
	docker run --rm -v $(PWD)/results:/app/results regex-bench-framework make test-quick

# ============================================================
# Lifecycle-Aware Benchmark Targets (Job Queue System)
# ============================================================

.PHONY: bench-status bench-start bench-continue bench-reset
.PHONY: bench-phase1-new bench-phase1-resume bench-stop

# Status and control operations
bench-status: venv ## Show status of benchmark runs and jobs
	@echo "$(BLUE)=== Benchmark Status ===$(NC)"
	@LATEST_DB=$$(find results -name "jobs.db" -type f -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2- || echo ""); \
	if [ -n "$$LATEST_DB" ]; then \
		echo "📁 Using latest database: $$LATEST_DB"; \
		LATEST_DIR=$$(dirname "$$LATEST_DB"); \
		$(REGEX_BENCH) job-status --output "$$LATEST_DIR"; \
	else \
		$(REGEX_BENCH) job-status || echo "💡 No benchmark databases found. Run 'make bench-start' to begin."; \
	fi

bench-start: venv build-engines ## Resume existing run or start production benchmark with 10K patterns and 1GB corpus
	@echo "$(BLUE)=== Production Benchmark: Resume or Start ===$(NC)"
	@LATEST_DB=$$(find results -name "jobs.db" -type f -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2- || echo ""); \
	if [ -n "$$LATEST_DB" ]; then \
		LATEST_DIR=$$(dirname "$$LATEST_DB"); \
		echo "📁 Found existing database: $$LATEST_DB"; \
		if $(REGEX_BENCH) job-status --output "$$LATEST_DIR" --quiet | grep -q "RUNNING"; then \
			echo "$(GREEN)Resuming incomplete benchmark run...$(NC)"; \
			$(REGEX_BENCH) job-continue --output "$$LATEST_DIR"; \
		else \
			echo "$(YELLOW)Latest run is complete, starting fresh...$(NC)"; \
			$(MAKE) bench-start-fresh; \
		fi \
	else \
		echo "$(BLUE)No existing runs found, starting fresh...$(NC)"; \
		$(MAKE) bench-start-fresh; \
	fi

bench-start-fresh: venv build-engines ## Always start fresh production benchmark with 10K patterns and 1GB corpus
	@echo "$(BLUE)=== Starting Fresh Production Benchmark Run (10K patterns, 1GB corpus, fast engines only) ===$(NC)"
	@mkdir -p $(RESULTS_DIR)/production_$(TIMESTAMP)
	$(REGEX_BENCH) job-start \
		--config test_matrix/production_fast_engines.json \
		--output $(RESULTS_DIR)/production_$(TIMESTAMP)

bench-continue: venv ## Continue interrupted benchmark run
	@echo "$(BLUE)=== Continuing Latest Interrupted Run ===$(NC)"
	@LATEST_DB=$$(find results -name "jobs.db" -type f -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2- || echo ""); \
	if [ -n "$$LATEST_DB" ]; then \
		echo "📁 Using latest database: $$LATEST_DB"; \
		LATEST_DIR=$$(dirname "$$LATEST_DB"); \
		$(REGEX_BENCH) job-continue --output "$$LATEST_DIR"; \
	else \
		echo "$(RED)No benchmark databases found. Use 'make bench-start' for new run.$(NC)"; \
		exit 1; \
	fi

bench-resume-production: venv ## Resume the specific cleaned production run (20251221_115843_4eeeed1a)
	@echo "$(BLUE)=== Resuming Cleaned Production Run ===$(NC)"
	@echo "$(GREEN)Resuming run: 20251221_115843_4eeeed1a$(NC)"
	@echo "$(GREEN)Output dir: results/production_20251221_115840$(NC)"
	$(REGEX_BENCH) job-continue --run-id 20251221_115843_4eeeed1a --output results/production_20251221_115840

bench-reset: venv ## Reset job queue (clear all jobs)
	@echo "$(YELLOW)=== Resetting Job Queue ===$(NC)"
	@read -p "This will clear all queued and failed jobs. Continue? (y/N): " confirm; \
	if [ "$$confirm" = "y" ] || [ "$$confirm" = "Y" ]; then \
		$(REGEX_BENCH) job-reset --confirm; \
		echo "$(GREEN)Job queue reset completed.$(NC)"; \
	else \
		echo "Reset cancelled."; \
	fi

bench-stop: ## Graceful shutdown request (placeholder for future implementation)
	@echo "$(YELLOW)=== Graceful Shutdown Requested ===$(NC)"
	@echo "$(YELLOW)Note: Current jobs will complete, new jobs won't start$(NC)"
	@echo "$(BLUE)Use 'make bench-status' to monitor progress$(NC)"

bench-cleanup: venv ## Clean up invalid benchmark results and resume the run
	@echo "$(BLUE)=== Cleaning Invalid Results and Resuming Run ===$(NC)"
	@latest_dir=$$(find results -maxdepth 1 -type d -name "*_*" | grep -v ".DS_Store" | sort | tail -n1); \
	if [ -z "$$latest_dir" ]; then \
		echo "$(RED)No benchmark results directories found$(NC)"; \
		exit 1; \
	fi; \
	echo "$(BLUE)Using latest results directory: $$latest_dir$(NC)"; \
	$(PYTHON) cleanup_and_resume.py "$$latest_dir" --all --report

bench-cleanup-dry: venv ## Preview what would be cleaned up without actually doing it
	@echo "$(YELLOW)=== DRY RUN: Preview Cleanup ===$(NC)"
	@latest_dir=$$(find results -maxdepth 1 -type d -name "*_*" | grep -v ".DS_Store" | sort | tail -n1); \
	if [ -z "$$latest_dir" ]; then \
		echo "$(RED)No benchmark results directories found$(NC)"; \
		exit 1; \
	fi; \
	echo "$(YELLOW)Previewing cleanup for: $$latest_dir$(NC)"; \
	$(PYTHON) cleanup_and_resume.py "$$latest_dir" --all --dry-run --report

bench-cleanup-suspicious: venv ## Clean up only suspicious results (fast <2s completions)
	@echo "$(BLUE)=== Cleaning Suspicious Results ===$(NC)"
	@latest_dir=$$(find results -maxdepth 1 -type d -name "*_*" | grep -v ".DS_Store" | sort | tail -n1); \
	if [ -z "$$latest_dir" ]; then \
		echo "$(RED)No benchmark results directories found$(NC)"; \
		exit 1; \
	fi; \
	echo "$(BLUE)Using latest results directory: $$latest_dir$(NC)"; \
	$(PYTHON) cleanup_and_resume.py "$$latest_dir" --invalidate-suspicious --report

bench-cleanup-timeouts: venv ## Skip redundant timeout jobs and update timeout values
	@echo "$(BLUE)=== Optimizing Timeout Handling ===$(NC)"
	@latest_dir=$$(find results -maxdepth 1 -type d -name "*_*" | grep -v ".DS_Store" | sort | tail -n1); \
	if [ -z "$$latest_dir" ]; then \
		echo "$(RED)No benchmark results directories found$(NC)"; \
		exit 1; \
	fi; \
	echo "$(BLUE)Using latest results directory: $$latest_dir$(NC)"; \
	$(PYTHON) cleanup_and_resume.py "$$latest_dir" --skip-redundant-timeouts --update-timeouts --report

bench-status-detailed: venv ## Show detailed status report with cleanup recommendations
	@echo "$(BLUE)=== Detailed Benchmark Status ===$(NC)"
	@latest_dir=$$(find results -maxdepth 1 -type d -name "*_*" | grep -v ".DS_Store" | sort | tail -n1); \
	if [ -z "$$latest_dir" ]; then \
		echo "$(RED)No benchmark results directories found$(NC)"; \
		exit 1; \
	fi; \
	echo "$(BLUE)Analyzing results directory: $$latest_dir$(NC)"; \
	$(PYTHON) cleanup_and_resume.py "$$latest_dir" --report

bench-fix: bench-cleanup bench-continue ## Clean invalid results and immediately resume execution
	@echo "$(GREEN)=== Fix and Resume Completed ===$(NC)"

# Enhanced phase targets with lifecycle awareness
bench-phase1-new: bench-reset bench-start-fresh ## Start fresh Phase 1 benchmark
	@echo "$(GREEN)=== Started fresh Phase 1 benchmark ===$(NC)"

bench-phase1-resume: venv ## Resume or start Phase 1 benchmark
	@echo "$(BLUE)=== Phase 1: Resume or Start ===$(NC)"
	@if $(REGEX_BENCH) job-status --run-id-only --quiet >/dev/null 2>&1; then \
		echo "Found existing run, continuing..."; \
		$(MAKE) bench-continue; \
	else \
		echo "No existing run found, starting new..."; \
		$(MAKE) bench-start-fresh; \
	fi

# Maintain backward compatibility
bench-phase1: bench-phase1-resume ## Resume or start Phase 1 benchmark (default behavior)
	@echo "$(GREEN)=== Phase 1 completed or continuing ===$(NC)"

# Enhanced reporting with job context
report-latest: venv ## Generate report from latest benchmark run
	@echo "$(BLUE)=== Generating Report from Latest Run ===$(NC)"
	@RUN_ID=$$($(REGEX_BENCH) job-status --latest-run-id --quiet 2>/dev/null); \
	if [ -n "$$RUN_ID" ]; then \
		echo "$(GREEN)Generating report for run: $$RUN_ID$(NC)"; \
		$(REGEX_BENCH) generate-report \
			--input results \
			--output reports/report_$(TIMESTAMP) \
			--format html \
			--include-charts; \
	else \
		echo "$(RED)No completed runs found$(NC)"; \
		exit 1; \
	fi

report-partial: venv ## Generate report from partial/incomplete run
	@echo "$(BLUE)=== Generating Partial Report ===$(NC)"
	@RUN_ID=$$($(REGEX_BENCH) job-status --latest-run-id --quiet 2>/dev/null); \
	if [ -n "$$RUN_ID" ]; then \
		echo "$(YELLOW)Generating partial report for run: $$RUN_ID$(NC)"; \
		echo "$(YELLOW)Note: This may include incomplete data$(NC)"; \
		$(REGEX_BENCH) generate-report \
			--input results \
			--output reports/partial_report_$(TIMESTAMP) \
			--format html \
			--include-charts; \
	else \
		echo "$(RED)No runs found$(NC)"; \
		exit 1; \
	fi

.PHONY: analysis
analysis: venv ## Generate comprehensive performance analysis with memory usage
	@echo "$(BLUE)Generating comprehensive performance analysis...$(NC)"
	@latest_db=$$(find results -name "jobs.db" -type f | sort | tail -1); \
	if [ -z "$$latest_db" ]; then \
		echo "$(RED)❌ No database file found in results/$(NC)"; \
		exit 1; \
	fi; \
	echo "$(CYAN)📊 Analyzing: $$latest_db$(NC)"; \
	$(PYTHON) generate_performance_analysis.py "$$latest_db" \
		--output-dir reports/performance_analysis_$(TIMESTAMP)
	@echo "$(GREEN)✓ Performance analysis complete: reports/performance_analysis_$(TIMESTAMP)$(NC)"
	@echo "$(CYAN)📄 Open reports/performance_analysis_$(TIMESTAMP)/performance_report.html in your browser$(NC)"

##@ Database Backup and Management

.PHONY: backup-create
backup-create: ## Create a backup of all databases
	@echo "$(BLUE)=== Creating Database Backup ===$(NC)"
	@./backup_db.py create

.PHONY: backup-create-named
backup-create-named: ## Create a named backup (usage: make backup-create-named NAME=mybkp)
	@echo "$(BLUE)=== Creating Named Database Backup ===$(NC)"
	@if [ -z "$(NAME)" ]; then \
		echo "$(RED)Error: NAME parameter required. Usage: make backup-create-named NAME=mybkp$(NC)"; \
		exit 1; \
	fi
	@./backup_db.py create --name $(NAME)

.PHONY: backup-list
backup-list: ## List available database backups
	@echo "$(BLUE)=== Available Database Backups ===$(NC)"
	@./backup_db.py list

.PHONY: backup-restore
backup-restore: ## Restore a backup (usage: make backup-restore NAME=backup_name)
	@echo "$(BLUE)=== Restoring Database Backup ===$(NC)"
	@if [ -z "$(NAME)" ]; then \
		echo "$(RED)Error: NAME parameter required. Usage: make backup-restore NAME=backup_name$(NC)"; \
		echo "$(YELLOW)Available backups:$(NC)"; \
		./backup_db.py list; \
		exit 1; \
	fi
	@./backup_db.py restore $(NAME)

.PHONY: backup-restore-dry-run
backup-restore-dry-run: ## Show what would be restored without actually doing it
	@echo "$(BLUE)=== Database Backup Dry Run ===$(NC)"
	@if [ -z "$(NAME)" ]; then \
		echo "$(RED)Error: NAME parameter required. Usage: make backup-restore-dry-run NAME=backup_name$(NC)"; \
		echo "$(YELLOW)Available backups:$(NC)"; \
		./backup_db.py list; \
		exit 1; \
	fi
	@./backup_db.py restore $(NAME) --dry-run

.PHONY: backup-info
backup-info: ## Show database information (usage: make backup-info DB=path/to/db)
	@echo "$(BLUE)=== Database Information ===$(NC)"
	@if [ -z "$(DB)" ]; then \
		echo "$(RED)Error: DB parameter required. Usage: make backup-info DB=results/production_20251221_115840/jobs.db$(NC)"; \
		exit 1; \
	fi
	@./backup_db.py info $(DB)

.PHONY: backup-auto
backup-auto: ## Create automatic timestamped backup before risky operations
	@echo "$(BLUE)=== Creating Automatic Backup ===$(NC)"
	@./backup_db.py create --name auto

# Help for new targets
help-lifecycle: ## Show help for lifecycle-aware benchmark targets
	@echo "$(BLUE)Lifecycle-Aware Benchmark Targets:$(NC)"
	@echo "  $(GREEN)bench-status$(NC)          - Show status of benchmark runs and jobs"
	@echo "  $(GREEN)bench-start$(NC)           - Smart: resume existing run or start new if none found"
	@echo "  $(GREEN)bench-start-fresh$(NC)     - Always start completely fresh benchmark run"
	@echo "  $(GREEN)bench-continue$(NC)        - Continue interrupted benchmark run"
	@echo "  $(GREEN)bench-resume-production$(NC) - Resume specific cleaned production run"
	@echo "  $(GREEN)bench-reset$(NC)           - Reset job queue (clear all jobs)"
	@echo "  $(GREEN)bench-phase1-new$(NC)      - Start fresh Phase 1 benchmark"
	@echo "  $(GREEN)bench-phase1-resume$(NC)   - Resume or start Phase 1 benchmark"
	@echo "  $(GREEN)bench-phase1$(NC)          - Default: resume or start Phase 1"
	@echo "  $(GREEN)report-latest$(NC)         - Generate report from latest run"
	@echo "  $(GREEN)report-partial$(NC)        - Generate report including partial data"
	@echo "  $(GREEN)analysis$(NC)              - Generate comprehensive performance analysis with memory usage"
	@echo ""
	@echo "$(YELLOW)Example workflow:$(NC)"
	@echo "  1. make bench-start          # Smart start (resume if possible)"
	@echo "  2. make bench-status         # Check progress"
	@echo "  3. make bench-continue       # Resume if interrupted"
	@echo "  4. make report-latest        # Generate final report"

# Include optional local configuration
-include Makefile.local
