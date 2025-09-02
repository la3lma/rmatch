# Makefile for Global Data Access System PRD
# Author: rmatch Project Team
# Description: Build system for LaTeX PRD document

# Configuration
LATEX = pdflatex
BIBTEX = bibtex
MAIN_TEX = global-data-access-system-prd
BIB_FILE = references.bib
BUILD_DIR = build
OUTPUT_DIR = output

# Derived variables
MAIN_PDF = $(MAIN_TEX).pdf
MAIN_AUX = $(MAIN_TEX).aux
MAIN_BBL = $(MAIN_TEX).bbl
MAIN_LOG = $(MAIN_TEX).log

# Default target
all: $(MAIN_PDF)

# Create directories
$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(OUTPUT_DIR):
	mkdir -p $(OUTPUT_DIR)

# Main PDF compilation with bibliography
$(MAIN_PDF): $(MAIN_TEX).tex $(BIB_FILE) | $(BUILD_DIR) $(OUTPUT_DIR)
	@echo "=== Building PRD Document ==="
	@echo "Step 1: First LaTeX pass..."
	$(LATEX) -output-directory=$(BUILD_DIR) $(MAIN_TEX).tex
	@echo "Step 2: Processing bibliography..."
	cp $(BIB_FILE) $(BUILD_DIR)/
	cd $(BUILD_DIR) && $(BIBTEX) $(MAIN_TEX) || echo "Bibliography processing completed (no citations found)"
	@echo "Step 3: Second LaTeX pass (for citations)..."
	$(LATEX) -output-directory=$(BUILD_DIR) $(MAIN_TEX).tex
	@echo "Step 4: Final LaTeX pass (for cross-references)..."
	$(LATEX) -output-directory=$(BUILD_DIR) $(MAIN_TEX).tex
	@echo "Step 5: Moving final PDF..."
	cp $(BUILD_DIR)/$(MAIN_PDF) $(OUTPUT_DIR)/
	cp $(BUILD_DIR)/$(MAIN_PDF) ./
	@echo "=== Build Complete ==="
	@echo "PDF available at: $(OUTPUT_DIR)/$(MAIN_PDF)"

# Fast build without bibliography (for development)
fast: $(MAIN_TEX).tex | $(BUILD_DIR) $(OUTPUT_DIR)
	@echo "=== Fast Build (No Bibliography) ==="
	$(LATEX) -output-directory=$(BUILD_DIR) $(MAIN_TEX).tex
	cp $(BUILD_DIR)/$(MAIN_PDF) $(OUTPUT_DIR)/
	cp $(BUILD_DIR)/$(MAIN_PDF) ./
	@echo "=== Fast Build Complete ==="

# Clean build artifacts
clean:
	@echo "=== Cleaning Build Artifacts ==="
	rm -rf $(BUILD_DIR)
	rm -f $(MAIN_PDF)
	rm -f *.aux *.bbl *.blg *.log *.out *.toc *.fdb_latexmk *.fls *.synctex.gz
	@echo "=== Clean Complete ==="

# Deep clean including output
distclean: clean
	rm -rf $(OUTPUT_DIR)
	@echo "=== Deep Clean Complete ==="

# Watch mode for development (requires inotify-tools)
watch:
	@echo "=== Starting Watch Mode ==="
	@echo "Watching for changes to $(MAIN_TEX).tex and $(BIB_FILE)"
	@echo "Press Ctrl+C to stop"
	while inotifywait -e modify $(MAIN_TEX).tex $(BIB_FILE) 2>/dev/null; do \
		make fast; \
		echo "=== Rebuilt at $$(date) ==="; \
	done

# View the generated PDF (platform-dependent)
view: $(MAIN_PDF)
	@echo "=== Opening PDF ==="
	@if command -v open >/dev/null 2>&1; then \
		open $(MAIN_PDF); \
	elif command -v xdg-open >/dev/null 2>&1; then \
		xdg-open $(MAIN_PDF); \
	elif command -v evince >/dev/null 2>&1; then \
		evince $(MAIN_PDF) &; \
	else \
		echo "Please open $(MAIN_PDF) manually"; \
	fi

# Check for required tools
check-tools:
	@echo "=== Checking Required Tools ==="
	@command -v $(LATEX) >/dev/null 2>&1 || { echo "ERROR: $(LATEX) not found. Please install LaTeX."; exit 1; }
	@command -v $(BIBTEX) >/dev/null 2>&1 || { echo "ERROR: $(BIBTEX) not found. Please install BibTeX."; exit 1; }
	@echo "All required tools found."

# Install LaTeX dependencies (Ubuntu/Debian)
install-deps-ubuntu:
	@echo "=== Installing LaTeX Dependencies (Ubuntu/Debian) ==="
	sudo apt-get update
	sudo apt-get install -y \
		texlive-latex-base \
		texlive-latex-extra \
		texlive-fonts-recommended \
		texlive-fonts-extra \
		texlive-bibtex-extra \
		texlive-science \
		latexmk

# Install LaTeX dependencies (macOS via Homebrew)
install-deps-macos:
	@echo "=== Installing LaTeX Dependencies (macOS) ==="
	brew install --cask mactex-no-gui
	@echo "You may need to restart your terminal for PATH updates"

# Validate LaTeX syntax (requires lacheck if available)
validate:
	@echo "=== Validating LaTeX Syntax ==="
	@if command -v lacheck >/dev/null 2>&1; then \
		lacheck $(MAIN_TEX).tex; \
	else \
		echo "lacheck not available, skipping validation"; \
		echo "Consider installing it for syntax checking"; \
	fi

# Count words in the document
wordcount:
	@echo "=== Word Count ==="
	@if command -v texcount >/dev/null 2>&1; then \
		texcount -brief $(MAIN_TEX).tex; \
	else \
		echo "texcount not available"; \
		echo "Approximate count using basic tools:"; \
		cat $(MAIN_TEX).tex | \
			sed 's/\\[a-zA-Z]*{[^}]*}//g' | \
			sed 's/\\[a-zA-Z]*//g' | \
			sed 's/{[^}]*}//g' | \
			wc -w; \
	fi

# Generate build info
info:
	@echo "=== Build Information ==="
	@echo "Main document: $(MAIN_TEX).tex"
	@echo "Bibliography: $(BIB_FILE)"
	@echo "Output PDF: $(MAIN_PDF)"
	@echo "Build directory: $(BUILD_DIR)"
	@echo "Output directory: $(OUTPUT_DIR)"
	@echo "LaTeX compiler: $(LATEX)"
	@echo "BibTeX compiler: $(BIBTEX)"
	@echo ""
	@echo "Available targets:"
	@echo "  all          - Build complete PDF with bibliography"
	@echo "  fast         - Quick build without bibliography"
	@echo "  clean        - Remove build artifacts"
	@echo "  distclean    - Remove all generated files"
	@echo "  watch        - Watch files and rebuild on changes"
	@echo "  view         - Open generated PDF"
	@echo "  check-tools  - Verify required tools are installed"
	@echo "  validate     - Check LaTeX syntax"
	@echo "  wordcount    - Count words in document"
	@echo "  info         - Show this information"

# Help target
help: info

# Mark targets as phony
.PHONY: all fast clean distclean watch view check-tools install-deps-ubuntu install-deps-macos validate wordcount info help

# Default goal
.DEFAULT_GOAL := all