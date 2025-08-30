# Makefile for Software (MDPI) Article
# Builds software-mdpi-article.pdf from software-mdpi-article.tex

SHELL := /bin/bash

# Main target - build the PDF
.PHONY: default clean

default: software-mdpi-article.pdf

# Build the PDF from LaTeX source
software-mdpi-article.pdf: software-mdpi-article.tex references.bib
	@echo "Building PDF from LaTeX source..."
	pdflatex software-mdpi-article.tex
	bibtex software-mdpi-article
	pdflatex software-mdpi-article.tex
	pdflatex software-mdpi-article.tex
	@echo "PDF build complete: software-mdpi-article.pdf"

# Clean intermediate files
clean:
	@echo "Cleaning intermediate files..."
	rm -f *.aux *.bbl *.blg *.log *.out *.toc *.fdb_latexmk *.fls *.synctex.gz
	@echo "Clean complete"

# Clean all generated files including PDF
clean-all: clean
	rm -f software-mdpi-article.pdf
	@echo "All generated files removed"

# Help target
help:
	@echo "Available targets:"
	@echo "  default (or no target): Build software-mdpi-article.pdf"
	@echo "  clean: Remove intermediate files (.aux, .log, etc.)"
	@echo "  clean-all: Remove all generated files including PDF"
	@echo "  help: Show this help message"