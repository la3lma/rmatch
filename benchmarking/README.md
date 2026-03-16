# Benchmarking Workspace

This directory contains the in-repo benchmarking entry points that remain in active use for `rmatch`.

## Purpose

- Provide reproducible performance measurements for `rmatch`.
- Compare `rmatch` against reference engines (notably `java.util.regex` and `re2j`) on the same workloads.
- Support both local regression gating and larger campaign-style benchmark runs (including GCP-backed runs).

## Which Benchmark Stack To Use

- **Primary (active):** [`framework/regex_bench_framework/`](framework/regex_bench_framework/)
  - This is the current benchmark orchestration system retained in this repository.
  - For full campaign work, use the dedicated `rmatch-perftest` repository.

## Directory Map

- [`framework/`](framework/): contains the active `regex_bench_framework`.
- [`results/`](results/): retained placeholder for local outputs where needed.
- Historical benchmarking subtrees previously under `benchmarking/` were soft-deleted to:
  - [`../archive/2026-03-perftest-extraction/soft-delete-2026-03-10/`](../archive/2026-03-perftest-extraction/soft-delete-2026-03-10/)

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

### 2. Framework-Local Operations (in this repo)

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
- Legacy/historical outputs are preserved in:
  - `archive/` (outside this directory tree)

When making claims, prefer comparable cohorts and documented matrix configurations from the active framework docs.

## Practical Guidance

- Treat benchmark data as append-only unless explicitly cleaning/resetting with a documented policy.
- Keep local Python environments isolated (`.venv` inside `regex_bench_framework`).
- Record config file, commit SHA, hardware cohort, and run ID for every benchmark campaign you intend to compare or publish.
