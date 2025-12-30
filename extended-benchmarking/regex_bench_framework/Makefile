# Regex Benchmarking Framework Makefile
# Provides convenient targets for setup, building, and running benchmarks

SHELL := /bin/bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c

# Configuration
VENV_DIR := .venv
PYTHON := $(VENV_DIR)/bin/python
PIP := $(VENV_DIR)/bin/pip
REGEX_BENCH := arch -x86_64 $(VENV_DIR)/bin/regex-bench

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
venv: $(VENV_DIR)/pyvenv.cfg ## Create Python virtual environment

$(VENV_DIR)/pyvenv.cfg:
	@echo "$(BLUE)Creating Python virtual environment...$(NC)"
	python3 -m venv $(VENV_DIR)
	$(PIP) install --upgrade pip setuptools wheel

.PHONY: install-deps
install-deps: venv ## Install Python dependencies
	@echo "$(BLUE)Installing Python dependencies...$(NC)"
	$(PIP) install -e .
	@test -f requirements-dev.txt && $(PIP) install -r requirements-dev.txt || true

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
	@latest_result=$$(find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2- || find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" | sort | tail -1); \
	if [ -z "$$latest_result" ]; then \
		echo "$(YELLOW)⚠ No benchmark results found - checking current directory$(NC)"; \
		$(REGEX_BENCH) process-status; \
	else \
		echo "$(BLUE)Checking processes for: $$latest_result$(NC)"; \
		$(REGEX_BENCH) process-status --output "$$latest_result"; \
	fi

.PHONY: process-cleanup
process-cleanup: venv ## Clean up orphaned benchmark processes
	@latest_result=$$(find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2- || find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" | sort | tail -1); \
	if [ -z "$$latest_result" ]; then \
		echo "$(YELLOW)⚠ No benchmark results found - using current directory$(NC)"; \
		$(REGEX_BENCH) process-status --cleanup; \
	else \
		echo "$(BLUE)Cleaning processes for: $$latest_result$(NC)"; \
		$(REGEX_BENCH) process-status --output "$$latest_result" --cleanup; \
	fi

.PHONY: process-monitor
process-monitor: venv ## Monitor and update process status
	@latest_result=$$(find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2- || find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" | sort | tail -1); \
	if [ -z "$$latest_result" ]; then \
		echo "$(YELLOW)⚠ No benchmark results found - using current directory$(NC)"; \
		$(REGEX_BENCH) process-status --monitor; \
	else \
		echo "$(BLUE)Monitoring processes for: $$latest_result$(NC)"; \
		$(REGEX_BENCH) process-status --output "$$latest_result" --monitor; \
	fi

##@ Analysis and Reporting

.PHONY: report
report: venv ## Generate benchmark report from latest results
	@echo "$(BLUE)Generating benchmark report...$(NC)"
	@latest_result=$$(find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" -exec stat -f "%m %N" {} \; 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2- || find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" | sort | tail -1); \
	if [ -z "$$latest_result" ]; then \
		echo "$(RED)✗ No benchmark results found in $(RESULTS_DIR)$(NC)"; \
		exit 1; \
	fi; \
	echo "$(BLUE)Analyzing results: $$latest_result$(NC)"; \
	mkdir -p $(REPORTS_DIR); \
	$(REGEX_BENCH) generate-report \
		--input "$$latest_result" \
		--output $(REPORTS_DIR)/report_$(TIMESTAMP) \
		--format html \
		--include-charts
	@echo "$(GREEN)✓ Report generated: $(REPORTS_DIR)/report_$(TIMESTAMP)$(NC)"

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