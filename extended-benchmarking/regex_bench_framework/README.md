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

## Canonical Plan

- See `docs/PHASED_BENCHMARKING_PLAN.md` for the active phased plan (A-F).
- See `docs/FAIRNESS_AND_REPRODUCIBILITY.md` for the fairness/reproducibility protocol and publication checklist.
- Local Docker smoke test (Phase D):
  - `./scripts/run_phase4_smoke_local_docker.sh`
- GCP run control (Phase E operations):
  - `make gcp-list`, `make gcp-status`, `make gcp-watch`
  - `make gcp-live` (continuous local monitor view)
  - `make gcp-list-batches`, `make gcp-status-batch`, `make gcp-watch-batch`
  - `make gcp-live-batch` (continuous local monitor view for batches)
  - `make gcp-publish-image`, `make gcp-publish-data`, `make gcp-start`, `make gcp-resume`
  - `make gcp-start-batch GCP_CONFIGS=test_matrix/a.json,test_matrix/b.json`
  - `make gcp-cancel-batch`, `make gcp-stop-batch`
  - `make gcp-cohort-report GCP_BATCH_ID=<batch_id>`
  - All `gcp-*` make targets run via the project venv (`.venv`) to avoid global Python usage.
  - details: `docs/GCP_RUN_CONTROL.md`

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
