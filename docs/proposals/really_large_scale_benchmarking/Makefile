# Makefile for LaTeX document compilation with BibTeX
# Author: Claude Code
# Date: 2025-12-18

# Variables
MAIN_TEX = Large-Scale-Regex-Benchmarking-Research
SIMPLE_TEX = Large-Scale-Regex-Benchmarking-Research-Simple
TEX_FILE = $(MAIN_TEX).tex
SIMPLE_TEX_FILE = $(SIMPLE_TEX).tex
PDF_FILE = $(MAIN_TEX).pdf
SIMPLE_PDF_FILE = $(SIMPLE_TEX).pdf
BIB_FILE = references.bib
MD_FILE = Large-Scale-Regex-Benchmarking-Research.md

# LaTeX compiler settings
LATEX = pdflatex
BIBTEX = bibtex
LATEX_FLAGS = -interaction=nonstopmode -halt-on-error

# Auxiliary files to clean
AUX_FILES = *.aux *.log *.bbl *.blg *.out *.toc *.lof *.lot *.fls *.fdb_latexmk *.synctex.gz *.nav *.snm *.vrb

# Default target - use simple version that works reliably
.PHONY: all
all: $(SIMPLE_PDF_FILE)

# Main PDF compilation with BibTeX
$(PDF_FILE): $(TEX_FILE) $(BIB_FILE)
	@echo "Building LaTeX document with BibTeX references..."
	$(LATEX) $(LATEX_FLAGS) $(TEX_FILE)
	$(BIBTEX) $(MAIN_TEX)
	$(LATEX) $(LATEX_FLAGS) $(TEX_FILE)
	$(LATEX) $(LATEX_FLAGS) $(TEX_FILE)
	@echo "PDF compilation complete: $(PDF_FILE)"

# Simple version compilation (reliable)
$(SIMPLE_PDF_FILE): $(SIMPLE_TEX_FILE)
	@echo "Building simplified LaTeX document..."
	$(LATEX) $(LATEX_FLAGS) $(SIMPLE_TEX_FILE)
	@echo "Simple compilation complete: $(SIMPLE_PDF_FILE)"

# Quick compilation without BibTeX (for draft versions)
.PHONY: quick
quick: $(SIMPLE_TEX_FILE)
	@echo "Quick compilation without BibTeX..."
	$(LATEX) $(LATEX_FLAGS) $(SIMPLE_TEX_FILE)
	@echo "Quick compilation complete: $(SIMPLE_PDF_FILE)"

# Full version with citations (may have issues)
.PHONY: full
full: $(TEX_FILE) $(BIB_FILE)
	@echo "Attempting full version with citations..."
	$(LATEX) $(LATEX_FLAGS) $(TEX_FILE)
	$(BIBTEX) $(MAIN_TEX)
	$(LATEX) $(LATEX_FLAGS) $(TEX_FILE)
	$(LATEX) $(LATEX_FLAGS) $(TEX_FILE)
	@echo "Full compilation complete: $(PDF_FILE)"

# Force rebuild (clean first, then build)
.PHONY: rebuild
rebuild: clean all

# Clean auxiliary files
.PHONY: clean
clean:
	@echo "Cleaning auxiliary files..."
	rm -f $(AUX_FILES)
	@echo "Clean complete."

# Clean everything including PDF
.PHONY: distclean
distclean: clean
	@echo "Removing PDF files..."
	rm -f $(PDF_FILE) $(SIMPLE_PDF_FILE)
	@echo "Distclean complete."

# View PDF (opens with default PDF viewer)
.PHONY: view
view: $(SIMPLE_PDF_FILE)
	@echo "Opening PDF..."
	@if command -v open >/dev/null 2>&1; then \
		open $(SIMPLE_PDF_FILE); \
	elif command -v xdg-open >/dev/null 2>&1; then \
		xdg-open $(SIMPLE_PDF_FILE); \
	elif command -v evince >/dev/null 2>&1; then \
		evince $(SIMPLE_PDF_FILE) &; \
	else \
		echo "No suitable PDF viewer found. Please open $(SIMPLE_PDF_FILE) manually."; \
	fi

# Check LaTeX syntax without building
.PHONY: check
check: $(TEX_FILE)
	@echo "Checking LaTeX syntax..."
	$(LATEX) $(LATEX_FLAGS) -draftmode $(TEX_FILE)
	@echo "Syntax check complete."

# Count words in the document (requires texcount)
.PHONY: wordcount
wordcount: $(TEX_FILE)
	@if command -v texcount >/dev/null 2>&1; then \
		echo "Word count for $(TEX_FILE):"; \
		texcount -brief $(TEX_FILE); \
	else \
		echo "texcount not found. Install with: brew install texcount (macOS) or apt-get install texcount (Ubuntu)"; \
	fi

# Validate BibTeX file
.PHONY: bibcheck
bibcheck: $(BIB_FILE)
	@echo "Checking BibTeX file..."
	@if command -v biber >/dev/null 2>&1; then \
		biber --tool --validate-datamodel $(BIB_FILE); \
	else \
		echo "biber not found. BibTeX syntax validation skipped."; \
	fi

# Show help
.PHONY: help
help:
	@echo "Available targets:"
	@echo "  all        - Build the simplified PDF (default, reliable)"
	@echo "  quick      - Quick compilation of simple version"
	@echo "  full       - Build complete PDF with BibTeX citations (experimental)"
	@echo "  rebuild    - Clean and rebuild everything"
	@echo "  clean      - Remove auxiliary files (.aux, .log, etc.)"
	@echo "  distclean  - Remove all generated files including PDFs"
	@echo "  view       - Open the PDF with default viewer"
	@echo "  check      - Check LaTeX syntax without building"
	@echo "  wordcount  - Count words in the document (requires texcount)"
	@echo "  bibcheck   - Validate BibTeX file (requires biber)"
	@echo "  help       - Show this help message"
	@echo ""
	@echo "Document versions:"
	@echo "  Simple:    $(SIMPLE_PDF_FILE) (reliable, no citations)"
	@echo "  Full:      $(PDF_FILE) (with citations, may have issues)"
	@echo ""
	@echo "Example usage:"
	@echo "  make           # Build the simple PDF"
	@echo "  make full      # Try to build with citations"
	@echo "  make view      # Build and view the PDF"
	@echo "  make clean     # Clean temporary files"

# Dependency checking
.PHONY: deps
deps:
	@echo "Checking dependencies..."
	@command -v $(LATEX) >/dev/null 2>&1 || { echo "pdflatex not found. Please install LaTeX (e.g., TeX Live or MiKTeX)."; exit 1; }
	@command -v $(BIBTEX) >/dev/null 2>&1 || { echo "bibtex not found. Please install LaTeX (e.g., TeX Live or MiKTeX)."; exit 1; }
	@echo "All required dependencies found."

# File existence checks
$(TEX_FILE):
	@test -f $(TEX_FILE) || { echo "Error: $(TEX_FILE) not found!"; exit 1; }

$(BIB_FILE):
	@test -f $(BIB_FILE) || { echo "Error: $(BIB_FILE) not found!"; exit 1; }

# Build and view in one command
.PHONY: preview
preview: all view

# Continuous compilation (watches for file changes)
.PHONY: watch
watch:
	@if command -v latexmk >/dev/null 2>&1; then \
		echo "Starting continuous compilation (Ctrl+C to stop)..."; \
		latexmk -pdf -pvc $(TEX_FILE); \
	else \
		echo "latexmk not found. Install with: brew install latexmk (macOS) or apt-get install latexmk (Ubuntu)"; \
		echo "Alternatively, use 'make' repeatedly to recompile."; \
	fi

# Print document statistics
.PHONY: stats
stats: $(PDF_FILE)
	@echo "Document statistics for $(PDF_FILE):"
	@if command -v pdfinfo >/dev/null 2>&1; then \
		pdfinfo $(PDF_FILE) | grep -E "(Pages|PDF version|File size|Creator|Producer)"; \
	else \
		ls -lh $(PDF_FILE) | awk '{print "File size: " $$5}'; \
	fi
	@if command -v texcount >/dev/null 2>&1; then \
		echo "Word count:"; \
		texcount -brief $(TEX_FILE); \
	fi

# Archive the project
.PHONY: archive
archive: distclean
	@echo "Creating project archive..."
	@tar -czf $(MAIN_TEX)-$(shell date +%Y%m%d).tar.gz *.tex *.bib Makefile README* *.md 2>/dev/null || true
	@echo "Archive created: $(MAIN_TEX)-$(shell date +%Y%m%d).tar.gz"