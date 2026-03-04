# Top-Level Post-Cleanup State (2026-03-04)

This snapshot records the repository root state after executing the cleanup moves from `TOP_LEVEL_FULL_REVIEW_2026-03-04.md`.

## Current Root Entries (Kept)

- `.DS_Store` (owner preference; ignored recursively)
- `.dockerignore`
- `.gcloudignore`
- `.github/`
- `.gitignore`
- `.mvn/`
- `CODE_OF_CONDUCT.md`
- `LICENSE`
- `Makefile`
- `README.md`
- `archive/`
- `benchmarks/`
- `charts/`
- `checkstyle-unused-imports.xml`
- `docs/`
- `extended-benchmarking/`
- `mvnw`
- `mvnw.cmd`
- `plans/`
- `pom.xml`
- `requirements.txt`
- `rmatch/`
- `rmatch-tester/`
- `scripts/`

## Executed Moves

### Moved to Attic

- `benchmarks.ipynb`
- `foo.sh`
- `summarize-large-corpus-trials.sql`
- `test_jit_comparison.sh`
- `test_profile_guided.sh`
- `test_warmup_benchmark.sh`
- `validate_jit_config.sh`
- `java_performance_timeline.png`
- `performance_comparison.png`
- `performance_timeline.png`
- `measurements.sqlite`
- `.ipynb_checkpoints/benchmarks-checkpoint.ipynb`
- `prd-repo/PRD_PERFORMANCE_GITHUB_ACTION.md`
- `.idea/dictionaries/rmz.xml`

Destination:
- `archive/2026-03-repo-cleanup/root/legacy-top-level-2026-03-04/`

### Relocated (Kept but not at root)

- `analysis/` -> `docs/analysis/`
- `proposals/` -> `docs/proposals/`
- `TODO.md` -> `plans/TODO.md`
- `.gitignore-performance` -> `docs/cleanup/legacy-root-files/.gitignore-performance`

## Deferred / Intentional Keep

- `charts/` remains at root for now due workflow/script path dependencies.
  - Planned later relocation target: `docs/benchmarking/charts/` with path rewiring in workflows/scripts.
