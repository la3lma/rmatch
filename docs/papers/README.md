# Papers

This directory contains writeups related to the current benchmarking and architecture work around `rmatch`.

## Available Papers

### 1) `status-report-2026-03-03`

- Source: `docs/papers/status-report-2026-03-03/status-report-2026-03-03.tex`
- PDF: `docs/papers/status-report-2026-03-03/status-report-2026-03-03.pdf`
- Scope:
  - Current system architecture and optimization layers
  - Benchmarking methodology and fairness controls
  - Combined workload results table (including partial 10K coverage)
  - Runtime modeling and dominance-map interpretation
  - Current limitations (including missing memory benchmarking in reporting)

### 2) `but-what-about-rust`

- Source: `docs/papers/but-what-about-rust/but-what-about-rust.tex`
- PDF: `docs/papers/but-what-about-rust/but-what-about-rust.pdf`
- Scope:
  - Research plan for a Rust-compatible implementation using current Java code as scaffolding
  - Required scaffolding artifacts for Codex-assisted generation
  - Validation protocol, experiment design, and threats to validity
  - Literature-anchored references for translation validation, Rust safety, and benchmarking rigor

### 3) `refactoring-engine-paper`

- Source: `docs/papers/refactoring-engine-paper/refactoring-engine-paper.tex`
- PDF: `docs/papers/refactoring-engine-paper/refactoring-engine-paper.pdf`
- Scope:
  - Current optimization loop used in this repository
  - Explicit correctness and performance gate sequence
  - A/B decision rule on fixed 10K/1MB workload
  - Future generalization into an automatable refactoring engine

## Build

Each paper directory has its own `Makefile`.

Examples:

```bash
make -C docs/papers/status-report-2026-03-03 pdf
make -C docs/papers/but-what-about-rust pdf
make -C docs/papers/refactoring-engine-paper pdf
```
