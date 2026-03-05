# Scripts Evaluation (2026-03-05)

## Scope

Reviewed tracked files under:

- `scripts/`
- `benchmarking/framework/regex_bench_framework/scripts/`
- `benchmarking/framework/scripts/`

Total tracked entries reviewed: **48**.

## Method

- Reference scan across Makefiles, workflows, docs, and repository sources.
- Syntax sanity checks:
  - `bash -n` for `*.sh`
  - `python3 -m py_compile` for `*.py`
- Quick CLI probe (`--help`) for Python CLIs where applicable.

## High-Level Result

- Not all scripts are actively used.
- Most shell/python scripts are syntactically valid.
- A subset of top-level chart/update scripts are wired but currently misaligned with the repo layout and likely unreliable without fixes.

## Category A: Active And Keep

These are wired into current Makefiles/workflows or current framework operations and should be kept.

### Top-level `scripts/`

- `cleanup_jmh_locks.sh`
- `compare_and_comment.sh`
- `profile_async_profiler.sh`
- `run_dispatch_benchmarks.sh`
- `run_enhanced_benchmarks.sh`
- `run_gc_experiments.sh`
- `run_gc_experiments_fast.sh`
- `run_java_benchmark_with_memory.sh`
- `run_jmh.sh`
- `run_jmh_mini_suite.sh`
- `run_jmh_suite.sh`
- `run_macro_with_memory.sh`
- `run_visualization.sh`
- `validate_gc_configs.sh`
- `visualize_benchmarks.py` (invoked by `run_visualization.sh`)
- `generate_readme_gcp_snapshot.py`
- `requirements.txt` (visualization venv dependency pinning)

### `benchmarking/framework/regex_bench_framework/scripts/`

- `gcp_10k_dynamic_campaign.py`
- `gcp_benchmark_control.py`
- `gcp_live_monitor.sh`
- `gcp_run_status_report.py`
- `generate_modeling_paper.py`
- `generate_workload_engine_comparison.py`
- `generate_workload_engine_comparison_all.py`
- `local_change_gate.py`
- `run_phase4_smoke_in_container.sh`
- `run_phase4_smoke_local_docker.sh`

### `benchmarking/framework/scripts/`

- `java_regex_runner.sh`
- `JavaRegexBenchmark.java` (support class for the runner)

## Category B: Active But Needs Fix

These are still wired from top-level Makefile/workflows/docs, but have drift.

- `scripts/generate_macro_performance_plot.py`
- `scripts/generate_java_performance_plot.py`
- `scripts/generate_performance_comparison_plot.py`
- `scripts/update_readme_performance_table.py`
- `scripts/fix_malformed_json.py`

Issue:

- These scripts use `project_root / "benchmarks" / "results"` (or equivalent), but the repo uses `benchmarking/results`.
- Result: no data found / ineffective behavior unless manually patched.

Additional drift:

- Legacy chart-producing scripts (`generate_performance_charts.py`, `generate_benchmarks_charts.py`) still target old non-GCP chart artifacts now moved to archive.
- Workflows still call some legacy chart scripts and tolerate failures (`|| echo ...`), which can hide breakage.

## Category C: Useful Manual Utilities (Not Wired)

These are not part of the main automated path but still potentially useful.

- `scripts/collect_system_info.sh`
- `scripts/run_macro.sh`
- `scripts/test_fastpath.sh`
- `scripts/run_comparative_benchmarks.sh`
- `benchmarking/framework/regex_bench_framework/scripts/post_run_collect_and_report.sh`

Recommendation: keep for now, but either wire them to explicit Make targets or move to a documented `tools/`/`attic` area.

## Category D: Likely Stale / Archive Candidates

- `scripts/create_sample_data.py`
- `scripts/generate_sample_performance_data.sh`
- `scripts/generate_performance_charts_new.sh`
- `scripts/update_performance_chart.sh`
- `scripts/README_VISUALIZATION.md` (if visualization flow is kept, update; if not, archive with related scripts)
- `scripts/compare_and_comment.sh.backup` (artifact)
- `scripts/run_visualization.sh~` (artifact)

## Approval Proposal

1. Keep Category A unchanged.
2. Fix Category B paths (`benchmarks/results` -> `benchmarking/results`) and align chart outputs with current GCP-only `charts/` policy.
3. For Category C:
   - either add explicit Make targets and docs
   - or archive under `archive/.../scripts-legacy-utils/`.
4. Archive/delete Category D, especially the two tracked editor/backup artifacts.
