# Regex Benchmarking Framework 2.0

A comprehensive, scientific benchmarking framework for comparing regex engines across realistic workloads.

## Supported Engines

- **Java Native** (`java.util.regex`) - Industry standard baseline
- **rmatch** - High-performance regex engine
- **RE2 C++** - Google's linear-time regex engine
- **RE2J** - Java port of RE2
- **Go RE2** - Go wrapper for RE2
- **Hyperscan** - Intel's multi-pattern high-performance engine

## Quick Start

```bash
# Install the framework
pip install -e .

# Run Phase 1 benchmarks
regex-bench run-phase test_matrix/phase1.json

# Generate reports
regex-bench report results/phase1_20241219/ --output reports/
```

## Architecture

```
regex-bench-framework/
├── engines/              # Engine implementations
├── benchmark_suites/     # Pattern generators & test suites
├── test_matrix/         # Test configurations
├── regex_bench/         # Python orchestration framework
└── results/             # Benchmark outputs
```

## Methodology

This framework follows scientific benchmarking principles:

1. **Reproducible** - Fixed seeds, versioned datasets
2. **Fair** - Engine-specific optimizations, common subset comparisons
3. **Statistical** - Multiple runs, confidence intervals, significance testing
4. **Realistic** - Large pattern sets, real corpora, production scenarios

## Engine Integration

Each engine is containerized with:
- Standardized input/output format
- Build scripts and dependency management
- Configuration metadata
- Platform compatibility detection

## Pattern Suites

- **Log Mining**: Timestamps, IPs, URLs, error codes
- **Security Signatures**: IDS rules, malware patterns
- **Pathological**: Backtracking stress tests
- **Real World**: Curated open-source pattern sets

## License

Apache 2.0 License