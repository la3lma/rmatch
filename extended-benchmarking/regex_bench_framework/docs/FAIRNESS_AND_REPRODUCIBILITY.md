# Fairness and Reproducibility Protocol

## Purpose

This document defines what the current benchmark lane does to make comparisons fair, and what limitations still exist.

Scope:
- Canonical comparator set: `rmatch`, `re2j`, `java-native-naive`
- Canonical baseline matrix: `test_matrix/canonical_baseline_v1.json`
- Containerized execution on GCP with run control via `scripts/gcp_benchmark_control.py`

## Fairness Controls Currently Enforced

### 1) Fixed comparator set and API contract

- Only the canonical engines are used in the active baseline lane.
- All engines are executed through the same framework interface (`command` + standardized metric parsing in `engine.json`):
  - `COMPILATION_NS`
  - `ELAPSED_NS`
  - `MATCHES`
  - `MEMORY_BYTES`
  - `PATTERNS_COMPILED`
  - `PATTERNS_FAILED`
- `check-engines` runs before benchmark execution in the GCP startup flow.

Why this helps:
- Avoids mixing placeholder or unstable engines.
- Keeps metrics shape consistent across engines.

### 2) Canonical matrix and deterministic data generation inputs

- Canonical matrix pins:
  - pattern counts: `10, 100, 1000`
  - corpus sizes: `1MB, 10MB, 100MB`
  - iterations per combination: `3`
  - warmup iterations (declared): `1`
- Config includes fixed seed `12345` for pattern generation metadata.

Why this helps:
- Comparisons are run over the same declared workload structure.

### 3) Versioned data bundles with hash

- Data is published as a versioned bundle to GCS (`datasets/<dataset_id>/data_bundle.tar.gz`).
- Each dataset has a manifest and `bundle_sha256`.
- Runs reference dataset URI directly in run manifest (`data_uri`).

Why this helps:
- Input data identity is explicit and portable.
- Runs can be traced back to an exact dataset artifact.

### 4) Transfer/setup excluded from timed benchmark section

- In the GCP startup flow, dataset download/extraction and any resume-state restoration occur before benchmark execution (`regex-bench job-start`).
- Timed engine metrics are engine-reported compile/scan values, not cloud transfer time.

Why this helps:
- Removes network jitter and object-store transfer variance from measured engine performance.

### 5) Reproducible runtime environment

- Benchmarks run from a Docker image with pinned base family (`ubuntu:24.04`) and pinned major JDK (`openjdk-21-jdk`).
- JVM bytecode is built in-container for portability, then run on the VM's native architecture/JVM.
- Baseline runs are launched with one benchmark process lane (`--parallel 1` in startup script).

Why this helps:
- Reduces host-level dependency drift.
- Avoids internal multi-engine contention during a single run.

### 6) Machine comparability labels and cohort filtering

- Each run manifest records machine identity fields (`machine_type`, `machine_tier`, `architecture`).
- `cohort-report` groups by:
  - config name
  - machine tier/type
  - architecture
  - data URI
  - image URI
- Pairwise successful engine coverage is reported per cohort and globally.

Why this helps:
- Prevents accidental apples-to-oranges comparisons.
- Makes it explicit which runs are truly comparable.

### 7) Memory pressure observability

- VM state reports:
  - memory headroom
  - swap usage
  - OOM signal detection
  - congestion recommendation (e.g., escalate tier)
- This signal is surfaced in `status`, `status-batch`, and `cohort-report`.

Why this helps:
- Allows exclusion/annotation of runs under memory stress.
- Supports fair comparisons by rerunning stressed cohorts at higher memory tiers.

### 8) Run provenance artifacts

For each run, the system stores:
- local run manifest: `gcp_runs/<run_id>.json`
- remote run manifest: `gs://.../runs/<run_id>/meta/run_manifest.json`
- live/final state: `gs://.../runs/<run_id>/state/state.json`
- logs: `gs://.../runs/<run_id>/logs/run.log`
- outputs: `gs://.../runs/<run_id>/output/*`
- durable job ledger: `gs://.../runs/<run_id>/output/jobs.db` (periodic SQLite snapshot)
- durable event stream: `gs://.../runs/<run_id>/output/logs/transaction_log_*.jsonl`

Why this helps:
- Enables independent audit and post-hoc validity checks.
- Allows interrupted long runs to resume and reuse already completed jobs.

### 9) Explicit timeout and stall policy

- Jobs use an explicit hard wall-clock timeout (`execution_plan.timeout_per_job`).
- Jobs can also use a stall timeout (`execution_plan.stall_timeout_seconds`) that terminates runs with no measurable progress.
- Timeout/stall outcomes are persisted as timeout failures with notes and raw output/log context where available.

Why this helps:
- Prevents campaign deadlocks caused by hung jobs.
- Preserves failure evidence for later hole-filling/rerun decisions.

## Publication Rules (Recommended)

For tables/plots intended for publication:

1. Compare only runs in the same cohort key:
   - same config
   - same data URI
   - same image URI
   - same machine tier/type
   - same architecture
2. Require pairwise successful coverage for compared engines.
3. Exclude runs with `memory_congested=true` or `oom_signals=true` from primary figures.
   - If included, label them explicitly as stressed-system results.
4. Report run IDs and dataset IDs for every figure.
5. Keep smoke runs (`phase4_smoke_tiny`) out of performance claims.

## Current Limitations (Must Be Disclosed)

These are important for scientific transparency:

1. `warmup_iterations` is declared in config but is not currently executed by `BenchmarkRunner`.
2. Run order is not randomized across engines/combinations; ordering effects may exist.
3. Run manifest does not yet persist container image digest and config hash explicitly (URI/tag is recorded).
4. Run manifest does not currently record repository commit SHA directly for GCP control-plane manifests.
5. `publish-data` currently derives counts/sizes from config but sources pattern files from `benchmark_suites/log_mining` paths in implementation.
6. RE2J launcher path does not currently apply explicit JVM flags in the generated run script in the same way as `rmatch` and `java-native-naive`.
7. Cross-engine regex feature compatibility is not guaranteed for arbitrary patterns; pattern compile failures are tracked but semantic parity across different regex dialects remains a threat to validity.

## Pre-Publication Hardening Checklist

Before a formal publication release, complete:

1. Implement actual warmup execution semantics.
2. Add randomized or blocked execution ordering controls.
3. Persist image digest + explicit config hash in run manifest.
4. Persist source commit SHA and dirty/clean status in run manifest.
5. Make `publish-data` source selection follow declared suite explicitly.
6. Normalize JVM flags policy across all Java engines (or document intentional differences).
7. Add a "publication export" command that packages:
   - run manifest
   - dataset manifest
   - engine status
   - raw results
   - analysis
   - cohort report snapshot
