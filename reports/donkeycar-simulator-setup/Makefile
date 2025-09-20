# Makefile for Donkeycar Simulator Setup Report
# Generates LaTeX document with PlantUML diagrams and Python illustrations

SHELL := /bin/bash

# Directories
SRC_DIR := .
PLANTUML_DIR := plantuml
PYTHON_DIR := python
ILLUSTRATIONS_DIR := illustrations
BUILD_DIR := build
VENV_DIR := .venv

# Files
TEX_FILE := report.tex
BIB_FILE := references.bib
PDF_FILE := report.pdf

# PlantUML files
PLANTUML_FILES := $(wildcard $(PLANTUML_DIR)/*.puml)
PLANTUML_PNGS := $(PLANTUML_FILES:$(PLANTUML_DIR)/%.puml=$(ILLUSTRATIONS_DIR)/%.png)

# Python chart generation
PYTHON_SCRIPT := $(PYTHON_DIR)/generate_diagrams.py
PYTHON_CHARTS := $(ILLUSTRATIONS_DIR)/donkeycar_architecture.png \
                 $(ILLUSTRATIONS_DIR)/simulator_workflow.png \
                 $(ILLUSTRATIONS_DIR)/lerobot_integration.png \
                 $(ILLUSTRATIONS_DIR)/mac_setup_flow.png

# Tools
PLANTUML_JAR := plantuml.jar
PLANTUML_URL := https://github.com/plantuml/plantuml/releases/download/v1.2023.12/plantuml-1.2023.12.jar
PDFLATEX := pdflatex
BIBTEX := bibtex
PYTHON := python3
VENV_PYTHON := $(VENV_DIR)/bin/python
VENV_PIP := $(VENV_DIR)/bin/pip

# Default target
.PHONY: all
all: $(PDF_FILE)

# Main PDF generation rule
$(PDF_FILE): $(TEX_FILE) $(BIB_FILE) $(PLANTUML_PNGS) $(PYTHON_CHARTS)
	@echo "Building LaTeX document..."
	@mkdir -p $(BUILD_DIR)
	$(PDFLATEX) -output-directory=$(BUILD_DIR) $(TEX_FILE)
	cd $(BUILD_DIR) && cp ../$(BIB_FILE) . && $(BIBTEX) $(basename $(TEX_FILE))
	$(PDFLATEX) -output-directory=$(BUILD_DIR) $(TEX_FILE)
	$(PDFLATEX) -output-directory=$(BUILD_DIR) $(TEX_FILE)
	@cp $(BUILD_DIR)/$(PDF_FILE) .
	@echo "✓ PDF generated: $(PDF_FILE)"

# Virtual environment management
$(VENV_DIR):
	@echo "Creating Python virtual environment..."
	@$(PYTHON) -m venv $(VENV_DIR)
	@echo "✓ Virtual environment created in $(VENV_DIR)"

# Install Python dependencies in virtual environment
$(VENV_DIR)/installed: $(VENV_DIR) requirements.txt
	@echo "Installing Python dependencies..."
	@$(VENV_PIP) install --upgrade pip
	@$(VENV_PIP) install -r requirements.txt
	@touch $(VENV_DIR)/installed
	@echo "✓ Python dependencies installed"

.PHONY: venv
venv: $(VENV_DIR)/installed

.PHONY: venv-clean
venv-clean:
	@echo "Removing virtual environment..."
	@rm -rf $(VENV_DIR)
	@echo "✓ Virtual environment removed"

# Create illustrations directory
$(ILLUSTRATIONS_DIR):
	@mkdir -p $(ILLUSTRATIONS_DIR)

# Download PlantUML jar if not present
$(PLANTUML_JAR):
	@echo "Downloading PlantUML..."
	@curl -L -o $(PLANTUML_JAR) $(PLANTUML_URL)
	@echo "✓ PlantUML downloaded"

# Generate PNG from PlantUML files
$(ILLUSTRATIONS_DIR)/%.png: $(PLANTUML_DIR)/%.puml $(PLANTUML_JAR) | $(ILLUSTRATIONS_DIR)
	@echo "Generating diagram: $@"
	@java -jar $(PLANTUML_JAR) -tpng -o ../$(ILLUSTRATIONS_DIR) $<
	@echo "✓ Generated: $@"

# Generate Python charts
$(PYTHON_CHARTS): $(PYTHON_SCRIPT) $(VENV_DIR)/installed | $(ILLUSTRATIONS_DIR)
	@echo "Generating Python illustrations..."
	@$(VENV_PYTHON) $(PYTHON_SCRIPT)
	@echo "✓ Python illustrations generated"

# Phony targets for grouping
.PHONY: diagrams charts illustrations
diagrams: $(PLANTUML_PNGS)
charts: $(PYTHON_CHARTS)
illustrations: diagrams charts

# Clean build artifacts but keep illustrations and venv
.PHONY: clean
clean:
	@echo "Cleaning build artifacts..."
	@rm -rf $(BUILD_DIR)
	@rm -f $(PDF_FILE)
	@echo "✓ Build artifacts cleaned"

# Clean everything including illustrations and venv
.PHONY: distclean
distclean: clean venv-clean
	@echo "Cleaning illustrations..."
	@rm -rf $(ILLUSTRATIONS_DIR)
	@rm -f $(PLANTUML_JAR)
	@echo "✓ All artifacts cleaned"

# Validate LaTeX syntax
.PHONY: validate
validate:
	@echo "Validating LaTeX syntax..."
	@$(PDFLATEX) -output-directory=$(BUILD_DIR) -draftmode $(TEX_FILE)
	@echo "✓ LaTeX syntax valid"

# Create references.bib if it doesn't exist
$(BIB_FILE):
	@echo "Creating empty bibliography file..."
	@touch $(BIB_FILE)
	@echo "✓ Bibliography file created"

# Help target
.PHONY: help
help:
	@echo "Available targets:"
	@echo "  all              - Build complete PDF report (default)"
	@echo "  diagrams         - Generate PlantUML diagrams only"
	@echo "  charts           - Generate Python charts only"
	@echo "  illustrations    - Generate all illustrations"
	@echo "  validate         - Check LaTeX syntax without building PDF"
	@echo "  venv             - Create Python virtual environment and install dependencies"
	@echo "  venv-clean       - Remove Python virtual environment"
	@echo "  clean            - Clean build artifacts (keep illustrations and venv)"
	@echo "  distclean        - Clean everything including illustrations and venv"
	@echo "  check-deps       - Check if all required tools are available"
	@echo "  install-python-deps - Install Python dependencies (same as venv)"
	@echo "  help             - Show this help message"
	@echo ""
	@echo "Requirements:"
	@echo "  - LaTeX distribution (pdflatex, bibtex)"
	@echo "  - Java (for PlantUML)"
	@echo "  - Python 3.13+ (virtual environment will be created automatically)"
	@echo "  - curl (for downloading PlantUML)"

# Check dependencies
.PHONY: check-deps
check-deps:
	@echo "Checking build dependencies..."
	@command -v pdflatex >/dev/null 2>&1 || { echo "✗ pdflatex not found"; exit 1; }
	@command -v bibtex >/dev/null 2>&1 || { echo "✗ bibtex not found"; exit 1; }
	@command -v java >/dev/null 2>&1 || { echo "✗ java not found"; exit 1; }
	@command -v python3 >/dev/null 2>&1 || { echo "✗ python3 not found"; exit 1; }
	@command -v curl >/dev/null 2>&1 || { echo "✗ curl not found"; exit 1; }
	@echo "✓ All dependencies available"

# Install Python dependencies (alias for venv)
.PHONY: install-python-deps
install-python-deps: venv