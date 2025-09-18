# Extended Testing Proposal for rmatch

This directory contains a comprehensive proposal for improving the rmatch performance testing infrastructure. The proposal addresses the issue "Suggest more tests" by providing a systematic framework for diverse and challenging performance testing.

## Quick Start

### Prerequisites
- LaTeX distribution (pdflatex, bibtex)
- Java (for PlantUML)
- Python 3.12+ 
- curl (for downloading PlantUML)

### Building the Proposal

```bash
# Install dependencies and build complete document
make all

# Or build components individually
make venv          # Set up Python environment
make diagrams      # Generate PlantUML diagrams
make charts        # Generate Python charts
make validate      # Check LaTeX syntax
```

### Generated Content

The build process creates:
- **proposal.pdf** - Complete LaTeX document (requires LaTeX)
- **illustrations/** - Charts and diagrams
  - PlantUML architecture diagrams
  - Python-generated performance charts
- **tasks/** - Sequential implementation tasks (001-006)

## Content Overview

### Main Proposal Document
- **Problem Analysis**: Current testing limitations including legacy CSV logging infrastructure
- **Proposed Framework**: Comprehensive testing architecture based on modern JMH infrastructure
- **Implementation Plan**: 6 sequential tasks emphasizing modernization and bioinformatics integration
- **Enhanced Dataset Coverage**: Bioinformatics data from NCBI, UniProt, Ensembl plus established benchmarks
- **Expected Benefits**: 10x performance improvement potential with diverse testing scenarios

### Sequential Tasks for Implementation
1. **Foundation Infrastructure** (6-8 weeks) - **PRIORITY: Remove legacy CSV logging, modernize to JMH**
2. **Pattern Library Development** (8-10 weeks) - **Enhanced with bioinformatics patterns**
3. **Input Corpus Diversification** (8-9 weeks) - **Major focus: Biology/gene sequences, established benchmarks**
4. **Advanced Metrics Collection** (8-10 weeks) - **JMH-based metrics only**
5. **CI/CD Integration** (7-9 weeks) - **Full GitHub Actions compatibility across all corpora**
6. **Documentation & Training** (8-10 weeks)

### Technical Architecture
- **Modern JMH-Based Test Orchestrator**: Coordinates test execution without legacy CSV dependencies
- **Enhanced Pattern Library**: 600+ categorized regex patterns including bioinformatics sequences
- **Diversified Corpus Manager**: Multi-domain test data with biology datasets (NCBI, UniProt, Ensembl)
- **Established Benchmark Integration**: Canterbury Corpus, Large Text Compression Benchmark
- **Advanced Metrics Collector**: JMH-based performance analysis with GitHub Actions compatibility
- **CI/CD Integration**: GitHub Actions automation

## Key Benefits

**Infrastructure Modernization:**
- Eliminates legacy CSV logging for consistent, reliable measurements
- Full JMH integration for accurate microbenchmarks and GitHub Actions compatibility
- Modern CI/CD pipeline with automated corpus management

**Comprehensive Dataset Coverage:**
- **Bioinformatics Integration**: Real-world biological sequence data from major repositories
- **Established Benchmarks**: Canterbury Corpus and Large Text Compression Benchmark for standardized comparisons  
- **Diverse Test Scenarios**: Multiple domains (literature, code, logs, structured data, biology)

**Development Process Enhancement:**
- Automated regression detection across all corpus types
- Continuous performance monitoring with GitHub Actions workflows
- Evidence-based optimization prioritization with concrete references and methodologies

## Implementation Strategy

The proposal is designed for incremental implementation:
- Each task builds on previous work
- Continuous value delivery throughout development
- Risk mitigation through phased approach
- Clear success criteria for each phase

## Files and Structure

```
proposals/extended-testing/
├── proposal.tex              # Main LaTeX document
├── Makefile                  # Build automation
├── requirements.txt          # Python dependencies
├── references.bib           # Bibliography
├── .gitignore              # Exclude build artifacts
├── plantuml/               # Architecture diagrams
├── python/                 # Chart generation scripts
├── illustrations/          # Generated charts and diagrams
└── tasks/                  # Sequential implementation tasks
    ├── 001_foundation_infrastructure.md
    ├── 002_pattern_library_development.md
    ├── 003_input_corpus_diversification.md
    ├── 004_advanced_metrics_collection.md
    ├── 005_cicd_integration.md
    └── 006_documentation_training.md
```

## Next Steps

1. **Review** the main proposal document and task descriptions
2. **Prioritize** tasks based on project needs and resources
3. **Begin implementation** starting with Task 001
4. **Iterate** through tasks with continuous validation
5. **Measure impact** using the proposed metrics framework

This proposal provides the foundation for systematic performance improvement of the rmatch library through comprehensive testing and optimization guidance.