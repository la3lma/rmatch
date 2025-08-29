LATEX=pdflatex
BIBTEX=bibtex
TARGET=final-article
SOURCES=$(TARGET).tex
REFERENCES=references.bib

.PHONY: all clean view

all: $(TARGET).pdf

$(TARGET).pdf: $(SOURCES) $(REFERENCES)
	$(LATEX) $(TARGET)
	$(BIBTEX) $(TARGET)
	$(LATEX) $(TARGET)
	$(LATEX) $(TARGET)

clean:
	rm -f *.aux *.bbl *.blg *.log *.out *.toc *.pdf

view: $(TARGET).pdf
	open $(TARGET).pdf || xdg-open $(TARGET).pdf || evince $(TARGET).pdf