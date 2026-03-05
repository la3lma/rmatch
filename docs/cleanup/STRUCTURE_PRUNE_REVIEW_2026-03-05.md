# Structure Prune Review (2026-03-05)

## Scope

Reviewed the current repository tree to identify files/directories that appear to no longer serve active build/test/benchmark workflows.

Decision classes used:

- `DELETE` = remove (no historical value or clearly generated clutter)
- `ARCHIVE` = preserve history, but move out of active tree
- `KEEP` = part of active workflows or core docs

## High-Confidence Candidates

### 1) Local generated clutter (`DELETE`, local workspace only)

These are untracked/generated and should not remain in a clean workspace:

- `scripts/scripts/` (appears accidental; contains only nested `.venv` + `.DS_Store`)
- `scripts/scripts/.venv/` (nested accidental virtualenv, ~196 MB)
- `scripts/.venv/` (rebuildable virtualenv, ~184 MB)
- `benchmarking/framework/regex_bench_framework/.venv/` (rebuildable virtualenv, ~379 MB)
- `**/target/` build outputs (rebuildable)
- `scripts/__pycache__/` and other Python cache dirs

Note: this is local cleanup only; not a Git history change.

### 2) Tracked backup/editor/crash artifacts (`DELETE`)

These are classic accidental artifacts and not part of intended source:

- `benchmarking/jmh/#pom.xml#`
- `rmatch/rmatch.iml`
- `rmatch-tester/hs_err_pid19059.log`
- `rmatch-tester/hs_err_pid19059.jfr`
- `scripts/compare_and_comment.sh.backup`
- `scripts/run_visualization.sh~`
- `rmatch-tester/bin/makeListOfWordsInCorpus~`
- `docs/analysis/analysis.tex.backup`

### 3) Historical benchmark outputs in active source paths (`ARCHIVE`)

These have value as history, but should not live in active directories:

- `rmatch-tester/logs/` (24 tracked CSVs)
- `rmatch-tester/plots/` (86 tracked plot files)
- `rmatch-tester/measurements/` (3 tracked CSVs)
- `benchmarking/framework/regex_bench_framework/backups/` (old DB snapshots; 12 tracked files)
- `benchmarking/framework/regex_bench_framework/debug_pattern_reuse_20251219_234537/` (~111 MB)
- `benchmarking/framework/regex_bench_framework/timeout_fixed_report.html/`
- `benchmarking/framework/regex_bench_framework/timeout_fixed_CORRECTED_report.html/`
- `benchmarking/framework/regex_bench_framework/timeout_fixed_report.md/`
- `benchmarking/framework/regex_bench_framework/final_option_a_report.html`
- `benchmarking/framework/regex_bench_framework/timeout_fixed_benchmark_report.html`
- `benchmarking/framework/regex_bench_framework/benchmark_results.db` (currently 0B; stale)

Recommended archive destination pattern:

- `archive/2026-03-repo-cleanup/<original-path>/...`

## Medium-Confidence Candidates (Need explicit policy choice)

### 4) `benchmarking/results/` historical batches (`ARCHIVE` likely)

`benchmarking/results/` contains old sample and experiment outputs. Some docs still mention this path as a historical source.

Recommended policy:

- Keep only minimal current examples required for docs/tests.
- Move older dated result files to `archive/` with a manifest.

### 5) Legacy Claude helper directory inside framework (`ARCHIVE` optional)

- `benchmarking/framework/regex_bench_framework/.claude/`

Not used by build/test runtime. Could remain harmlessly, but if cleanup strictness is desired, move to `archive`.

## Keep (Active)

These remain core and should stay where they are:

- `rmatch/` (library)
- `rmatch-tester/src/` + `rmatch-tester/corpus/` (test harness and corpora)
- `benchmarking/framework/regex_bench_framework/regex_bench/` + `engines/` + `scripts/` + `test_matrix/` + `docs/`
- `benchmarking/jmh/src/` and `benchmarking/jmh/pom.xml`
- top-level `Makefile`, `pom.xml`, `.github/workflows/main-gate.yml`, docs under `docs/`
- `charts/gcp_*` current comparable plots

## Suggested Execution Order

1. Remove tracked backup/editor/crash artifacts.
2. Move historical benchmark outputs to `archive/` in one batch with a move manifest.
3. Clean local generated clutter (`.venv`, `target`, caches).
4. Run quick safety checks:
   - `make build`
   - `make test`
   - `make gate-baseline` (optional if baseline refresh intended)
