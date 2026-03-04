# rmatch Performance Analysis

This directory contains a comprehensive technical analysis of the rmatch regular expression matching library, focusing on performance optimization opportunities.

## Contents

- **`analysis.tex`** - Complete LaTeX source for the technical report (20+ pages)
- **`references.bib`** - Academic bibliography with 20+ citations from regex/automata literature
- **`plantuml/*.puml`** - PlantUML source files for 7 detailed architectural diagrams
- **`Makefile`** - Automated build system for generating diagrams and PDF
- **`README.md`** - This file

## Key Findings

### Critical Performance Bottleneck Identified

The analysis identified a critical O(m×l) complexity bottleneck in `MatchSetImpl` constructor, where:
- m = number of regex patterns  
- l = length of input text
- Creates Match objects for ALL patterns at EVERY starting position
- Explicitly noted in code comments as "most egregious bug in the whole regexp package"

### Performance Optimization Strategy

**Three-Phase Roadmap for 10x+ Performance Improvement:**

1. **Phase 1 (Expected 3-5x improvement)**
   - Fix O(m×l) bottleneck with first-character heuristics
   - Replace heavyweight synchronized data structures  
   - Implement object pooling and lock-free operations

2. **Phase 2 (Expected 2-3x improvement)**
   - Hybrid Aho-Corasick algorithm for literal patterns
   - Bit-parallel NFA simulation for simple patterns
   - Shared prefix/suffix optimization

3. **Phase 3 (Expected 2-4x improvement)**  
   - SIMD vectorization using Java Vector API
   - Advanced DFA state minimization
   - Hardware-specific optimizations

**Total Expected Improvement: 12-60x performance gain**

## Building the Analysis

### Prerequisites

To build the complete PDF report and diagrams, you need:

```bash
# Java 8+ (for PlantUML)
sudo apt-get install default-jre

# LaTeX distribution  
sudo apt-get install texlive-latex-recommended texlive-latex-extra texlive-fonts-recommended

# Optional: biber for bibliography (falls back to bibtex)
sudo apt-get install biber
```

### Build Commands

```bash
# Generate all diagrams and compile PDF
make

# Generate only PlantUML diagrams (PNG and SVG)  
make diagrams

# Compile only the LaTeX document
make analysis.pdf

# Clean temporary files
make clean

# Clean everything including generated PDF
make distclean

# Check if prerequisites are installed
make check-prereqs

# Show all available targets
make help
```

### Alternative: Manual Building

If the automated build system doesn't work in your environment:

1. **Download PlantUML**: Get `plantuml.jar` from https://plantuml.com/download
2. **Generate diagrams**:
   ```bash
   java -jar plantuml.jar -tpng -o ../illustrations plantuml/*.puml
   ```
3. **Compile LaTeX**:
   ```bash  
   pdflatex analysis.tex
   bibtex analysis  
   pdflatex analysis.tex
   pdflatex analysis.tex
   ```

## Diagram Overview

The analysis includes 7 detailed PlantUML diagrams:

1. **`current-architecture.puml`** - Overview of rmatch's current architecture and bottlenecks
2. **`data-structure-overhead.puml`** - Visualization of performance overhead from data structures  
3. **`aho-corasick-comparison.puml`** - Comparison between current approach and Aho-Corasick
4. **`first-char-optimization.puml`** - Proposed first-character optimization strategy
5. **`hybrid-approach.puml`** - Hybrid Aho-Corasick + NFA architecture
6. **`simd-optimization.puml`** - SIMD vectorization approach for character processing
7. **`benchmark-strategy.puml`** - Comprehensive benchmarking and validation strategy

## Report Structure

The technical report (`analysis.tex`) contains:

1. **Executive Summary** - Key findings and recommendations
2. **Current Implementation Analysis** - Deep dive into existing algorithms
3. **Literature Review** - Survey of modern regex matching techniques  
4. **Proposed Optimization Strategy** - Detailed three-phase roadmap
5. **Implementation Roadmap** - Timeline and risk mitigation
6. **Benchmarking Strategy** - Validation and testing approach
7. **Conclusion** - Summary and expected outcomes

## Academic References

The bibliography includes seminal papers and modern research on:

- Thompson NFA construction (Thompson 1968)
- Aho-Corasick algorithm (Aho & Corasick 1975) 
- Bit-parallel regex matching (Myers 1999, Baeza-Yates & Gonnet 1992)
- Modern regex engines (Cox 2007, Intel Hyperscan 2016)
- String matching algorithms (Boyer-Moore, Knuth-Morris-Pratt)
- SIMD optimization techniques
- Automata theory and state minimization

## Next Steps

1. **Immediate (Phase 1)**: Implement first-character optimization to fix O(m×l) bottleneck
2. **Short-term (Phase 2)**: Add Aho-Corasick for literal patterns, bit-parallel simulation
3. **Long-term (Phase 3)**: SIMD vectorization and advanced state management
4. **Validation**: Comprehensive benchmarking against Java regex, RE2, PCRE, Hyperscan

This analysis provides the foundation for transforming rmatch from 10% of Java's regex performance to potentially 5-10x faster than standard implementations through systematic optimization of algorithms and data structures.