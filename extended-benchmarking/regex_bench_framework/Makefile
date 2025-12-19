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

.PHONY: bench-phase1
bench-phase1: venv build-engines ## Run Phase 1 comprehensive benchmarks
	@echo "$(BLUE)Running Phase 1 benchmarks...$(NC)"
	@echo "$(YELLOW)This may take 30-60 minutes...$(NC)"
	@mkdir -p $(RESULTS_DIR)/phase1_$(TIMESTAMP)
	$(REGEX_BENCH) run-phase \
		--config test_matrix/phase1.json \
		--output $(RESULTS_DIR)/phase1_$(TIMESTAMP) \
		--parallel 4 \
		--verbose
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
		--parallel 2 \
		--verbose
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

##@ Analysis and Reporting

.PHONY: report
report: venv ## Generate benchmark report from latest results
	@echo "$(BLUE)Generating benchmark report...$(NC)"
	@latest_result=$$(find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" | sort | tail -1); \
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
	@latest_result=$$(find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" | sort | tail -1); \
	if [ -z "$$latest_result" ]; then \
		echo "$(RED)✗ No benchmark results found in $(RESULTS_DIR)$(NC)"; \
		exit 1; \
	fi; \
	echo "$(BLUE)Analyzing results: $$latest_result$(NC)"; \
	sed "s|results/quick_20251219_140928|$$latest_result|" standalone_report_test.py | python3
	@echo "$(GREEN)✓ Report generated: test_reports/benchmark_report.html$(NC)"

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
	@latest_result=$$(find $(RESULTS_DIR) -maxdepth 1 -type d -name "*_*" | sort | tail -1)
	@if [ -z "$$latest_result" ]; then
		echo "$(RED)✗ No benchmark results found$(NC)"
		exit 1
	fi
	@echo "$(BOLD)Latest Benchmark Statistics:$(NC)"
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

# Include optional local configuration
-include Makefile.local