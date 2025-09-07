# Makefile for rmatch performance analysis report
# Generates PlantUML diagrams and compiles LaTeX to PDF

# Variables
PLANTUML_DIR = plantuml
ILLUSTRATIONS_DIR = illustrations
PLANTUML_JAR = plantuml.jar
PLANTUML_URL = https://sourceforge.net/projects/plantuml/files/plantuml.jar/download

# Find all PlantUML source files
PUML_SOURCES = $(wildcard $(PLANTUML_DIR)/*.puml)

# Generate target names for PNG and SVG files
PNG_TARGETS = $(patsubst $(PLANTUML_DIR)/%.puml,$(ILLUSTRATIONS_DIR)/%.png,$(PUML_SOURCES))
SVG_TARGETS = $(patsubst $(PLANTUML_DIR)/%.puml,$(ILLUSTRATIONS_DIR)/%.svg,$(PUML_SOURCES))

# Default target
all: analysis.pdf

# Download PlantUML if not present
$(PLANTUML_JAR):
	@echo "Downloading PlantUML..."
	curl -L $(PLANTUML_URL) -o $(PLANTUML_JAR)

# Ensure illustrations directory exists
$(ILLUSTRATIONS_DIR):
	mkdir -p $(ILLUSTRATIONS_DIR)

# Rule to generate PNG files from PlantUML
$(ILLUSTRATIONS_DIR)/%.png: $(PLANTUML_DIR)/%.puml $(PLANTUML_JAR) | $(ILLUSTRATIONS_DIR)
	@echo "Generating PNG: $@"
	java -jar $(PLANTUML_JAR) -tpng -o ../$(ILLUSTRATIONS_DIR) $<

# Rule to generate SVG files from PlantUML  
$(ILLUSTRATIONS_DIR)/%.svg: $(PLANTUML_DIR)/%.puml $(PLANTUML_JAR) | $(ILLUSTRATIONS_DIR)
	@echo "Generating SVG: $@"
	java -jar $(PLANTUML_JAR) -tsvg -o ../$(ILLUSTRATIONS_DIR) $<

# Generate all diagrams
diagrams: $(PNG_TARGETS) $(SVG_TARGETS)

# Compile LaTeX to PDF
analysis.pdf: analysis.tex references.bib $(PNG_TARGETS)
	@echo "Compiling LaTeX document..."
	pdflatex analysis.tex
	bibtex analysis
	pdflatex analysis.tex
	pdflatex analysis.tex
	@echo "Generated: analysis.pdf"

# Clean generated files
clean:
	rm -f analysis.aux analysis.bbl analysis.blg analysis.log analysis.out analysis.toc
	rm -rf $(ILLUSTRATIONS_DIR)

# Clean everything including PDF
distclean: clean
	rm -f analysis.pdf $(PLANTUML_JAR)

# Force rebuild of diagrams
rebuild-diagrams: 
	rm -rf $(ILLUSTRATIONS_DIR)
	$(MAKE) diagrams

# Development target - rebuild everything
rebuild: distclean all

# Show help
help:
	@echo "Available targets:"
	@echo "  all              - Generate diagrams and compile PDF (default)"
	@echo "  diagrams         - Generate all PlantUML diagrams (PNG and SVG)"
	@echo "  analysis.pdf     - Compile LaTeX document to PDF"
	@echo "  clean            - Remove temporary LaTeX files and diagrams"
	@echo "  distclean        - Remove all generated files including PDF"
	@echo "  rebuild-diagrams - Force regeneration of all diagrams"
	@echo "  rebuild          - Clean and rebuild everything"
	@echo "  help             - Show this help message"

# Check prerequisites
check-prereqs:
	@echo "Checking prerequisites..."
	@command -v java >/dev/null 2>&1 || { echo "Java is required but not installed. Please install Java 8+."; exit 1; }
	@command -v pdflatex >/dev/null 2>&1 || { echo "pdflatex is required but not installed. Please install a LaTeX distribution."; exit 1; }
	@command -v bibtex >/dev/null 2>&1 || { echo "bibtex is required but not installed. Please install a LaTeX distribution."; exit 1; }
	@echo "Prerequisites check passed."

# Phony targets
.PHONY: all diagrams clean distclean rebuild-diagrams rebuild help check-prereqs

# Dependencies
analysis.pdf: $(PNG_TARGETS)
$(PNG_TARGETS): $(PLANTUML_JAR)
$(SVG_TARGETS): $(PLANTUML_JAR)