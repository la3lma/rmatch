# Repository Inventory (Phase 1 Draft)

Timestamp (UTC): 2026-03-04T17:26Z

## Scope

This inventory is the first-pass classification from the conservative cleanup plan.
No destructive actions are included. It is intended to guide phased moves.

## Top-Level Classification

### Core Code and Build

- `rmatch/`
- `rmatch-tester/`
- `benchmarks/`
- `pom.xml`
- `mvnw`, `mvnw.cmd`
- root `Makefile`

### Benchmark Harness and Data Platform

- `extended-benchmarking/regex_bench_framework/`
- root `scripts/` (shared scripting and benchmarking helpers)

### Documentation and Plans

- `README.md`
- root topical markdown documents (performance and optimization notes)
- `docs/proposals/` (relocated from top-level during cleanup)
- `docs/` (new home for curated docs and papers)

### Historical and Legacy Candidates

- backup files (`*.backup`, `*~`)
- standalone tarballs
- ad hoc legacy assets at repository root

## Initial Findings

1. The benchmark control plane is active and must be preserved as-is during moves.
2. Root-level markdown clutter is high and should be moved under `docs/` in controlled batches.
3. Paper writeups were moved from top-level `papers/` to `docs/papers/` per cleanup amendment.
4. Legacy and generated artifacts need quarantine under `archive/2026-03-repo-cleanup/` with manifest tracking.

## Baseline Validation Snapshot

See `docs/cleanup/baseline-checks.md` for pre-move command outcomes. Current baseline is mixed:

- packaging command succeeds
- module tests and framework quick/unit entrypoints currently fail due pre-existing issues

This is recorded as baseline state before structural cleanup changes.
