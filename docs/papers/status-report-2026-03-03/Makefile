SHELL := /bin/bash

TEX := status-report-2026-03-03
PDF := $(TEX).pdf

.PHONY: all pdf clean distclean open

all: pdf

pdf: $(PDF)

$(PDF): $(TEX).tex
	@if command -v latexmk >/dev/null 2>&1; then \
		latexmk -pdf -interaction=nonstopmode -halt-on-error $(TEX).tex; \
	else \
		pdflatex -interaction=nonstopmode -halt-on-error $(TEX).tex; \
		pdflatex -interaction=nonstopmode -halt-on-error $(TEX).tex; \
	fi

clean:
	@if command -v latexmk >/dev/null 2>&1; then \
		latexmk -c $(TEX).tex; \
	else \
		rm -f $(TEX).aux $(TEX).fdb_latexmk $(TEX).fls $(TEX).log $(TEX).out $(TEX).toc; \
	fi

distclean: clean
	rm -f $(PDF)

open: $(PDF)
	@if command -v open >/dev/null 2>&1; then \
		open $(PDF); \
	elif command -v xdg-open >/dev/null 2>&1; then \
		xdg-open $(PDF); \
	else \
		echo "No opener found. PDF at: $(PWD)/$(PDF)"; \
	fi
