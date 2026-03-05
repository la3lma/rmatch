# Benchmarking Workspace

This directory contains all benchmarking-related code, configs, and generated result artifacts for `rmatch`.

## Purpose

- Provide reproducible performance measurements for `rmatch`.
- Compare `rmatch` against reference engines (notably `java.util.regex` and `re2j`) on the same workloads.
- Support both local regression gating and larger campaign-style benchmark runs (including GCP-backed runs).

## Which Benchmark Stack To Use

- **Primary (active):** [`framework/regex_bench_framework/`](framework/regex_bench_framework/)
  - This is the current benchmark orchestration system used for reports, campaigns, and run-control tooling.
- **Secondary (legacy/simple):** [`framework/`](framework/)
  - Older generic harness kept for reference and lightweight experiments.
- **Microbenchmarks / JMH-focused:** [`jmh/`](jmh/)
  - JMH module and related benchmark artifacts.

If you are unsure, start with `framework/regex_bench_framework`.

## Directory Map

- [`baseline/`](baseline/): saved baseline snapshots used for regression comparison context.
- [`framework/`](framework/): benchmarking harnesses and the active `regex_bench_framework`.
- [`jmh/`](jmh/): JMH benchmark module and generated artifacts.
- [`results/`](results/): historical result outputs (mostly legacy/JMH-era artifacts).

## Recommended Workflows

### 1. Local Regression Gate (from repo root)

Use this for branch-vs-`main` performance checks before merging:

```bash
git checkout main
make gate-baseline

# switch to candidate branch
make gate-candidate
```

Notes:

- These targets are defined in the top-level `Makefile` and delegate into `framework/regex_bench_framework`.
- Default gate metric is `scanning_ns` for `rmatch` on the stable 10K local probe config.

### 2. Framework-Local Operations

From [`framework/regex_bench_framework/`](framework/regex_bench_framework/):

```bash
make setup
make test-quick
make report
```

Useful references:

- [`framework/regex_bench_framework/QUICKSTART.md`](framework/regex_bench_framework/QUICKSTART.md)
- [`framework/regex_bench_framework/docs/PHASED_BENCHMARKING_PLAN.md`](framework/regex_bench_framework/docs/PHASED_BENCHMARKING_PLAN.md)
- [`framework/regex_bench_framework/docs/FAIRNESS_AND_REPRODUCIBILITY.md`](framework/regex_bench_framework/docs/FAIRNESS_AND_REPRODUCIBILITY.md)
- [`framework/regex_bench_framework/docs/GCP_RUN_CONTROL.md`](framework/regex_bench_framework/docs/GCP_RUN_CONTROL.md)

## Results and Reporting Conventions

- Active campaign outputs live under:
  - `framework/regex_bench_framework/results/`
  - `framework/regex_bench_framework/reports/`
- Legacy/historical outputs are kept under:
  - `results/`
  - `archive/` (outside this directory tree)

When making claims, prefer comparable cohorts and documented matrix configurations from the active framework docs.

## Practical Guidance

- Treat benchmark data as append-only unless explicitly cleaning/resetting with a documented policy.
- Keep local Python environments isolated (`.venv` inside `regex_bench_framework`).
- Record config file, commit SHA, hardware cohort, and run ID for every benchmark campaign you intend to compare or publish.
