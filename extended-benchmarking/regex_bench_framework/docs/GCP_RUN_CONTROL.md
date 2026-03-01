# GCP Run Control Utility

Utility script:

- `scripts/gcp_benchmark_control.py`

## Purpose

This script is the control plane for benchmark runs on Google Compute Engine:

- launch a run (`start`)
- launch a batch (`start-batch`) with one VM per config
- resume a stopped run VM (`resume`)
- inspect live progress and ETA (`status`)
- inspect batch progress and aggregate ETA (`status-batch`)
- watch progress (`watch`)
- watch batch progress (`watch-batch`)
- request safe cancellation (`cancel`)
- request safe cancellation for all runs in a batch (`cancel-batch`)
- force stop VM if needed (`stop`)
- force stop all VMs in a batch (`stop-batch`)
- analyze comparable cohorts and pairwise engine coverage (`cohort-report`)

It stores per-run metadata locally in:

- `gcp_runs/<run_id>.json`

and per-batch metadata in:

- `gcp_runs/batch_<batch_id>.json`

and live state remotely in:

- `gs://<bucket>/runs/<run_id>/state/state.json`

## Quick Start

```bash
cd /Volumes/SynologyScsi1/git/rmatch/extended-benchmarking/regex_bench_framework

# 0) Ensure local venv exists (all gcp-* targets use .venv/bin/python)
make venv

# 1) Publish Docker image
make gcp-publish-image

# 2) Publish test data bundle (patterns + corpora)
make gcp-publish-data GCP_CONFIG=test_matrix/canonical_baseline_v1.json

# 3) Start a run (uses latest published image/data by default)
make gcp-start GCP_CONFIG=test_matrix/canonical_baseline_v1.json

# 3b) Optional: force a comparability tier label
make gcp-start GCP_CONFIG=test_matrix/canonical_baseline_v1.json GCP_MACHINE_TIER=M

# 4) Observe progress + ETA
make gcp-status
make gcp-watch INTERVAL=20
# or a continuously refreshing local monitor view:
make gcp-live INTERVAL=20

# 5) Safe cancel if runtime is too long
make gcp-cancel GCP_ARGS="--wait-seconds 300 --force-stop"

# 5b) Resume a stopped run without discarding completed jobs
make gcp-resume GCP_RUN_ID=<run_id>

# 6) Start a parallel batch (one VM per config)
make gcp-start-batch \
  GCP_CONFIGS=test_matrix/canonical_baseline_v1.json,test_matrix/smoke_phase4_tiny.json

# 7) Observe/stop a batch
make gcp-status-batch
make gcp-watch-batch INTERVAL=20
make gcp-live-batch INTERVAL=20
make gcp-cancel-batch GCP_ARGS="--wait-seconds 300 --force-stop"

# 8) Validate comparability cohorts and pairwise coverage
make gcp-cohort-report GCP_BATCH_ID=<batch_id>
```

## Makefile Variables

- `GCP_ZONE` (default: `europe-north2-a`)
- `GCP_BUCKET` (default: `run-cl-rmatch-bench`)
- `GCP_CONFIG` (default: `test_matrix/canonical_baseline_v1.json`)
- `GCP_MACHINE_TYPE` (default: `n2-standard-8`)
- `GCP_RUN_ID` (used by status/watch/cancel/stop when set)
- `GCP_BATCH_ID` (used by status-batch/watch-batch/cancel-batch/stop-batch when set)
- `GCP_CONFIGS` (comma-separated configs for `gcp-start-batch`)
- `GCP_MACHINE_TIER` (optional tier override: `S|M|L|XL`)
- `GCP_REQUIRE_ENGINES` (engine set for pairwise comparability checks)
- `GCP_ARGS` (append raw args to the underlying control command)

## Notes

- `publish-image` builds and pushes to Artifact Registry.
- `publish-data` uploads a versioned data bundle to GCS.
- `start` launches a VM that pulls image + data bundle and runs a job-queue benchmark (`job-start`).
- VM startup restores any previously synced `output/` artifacts from the run root before execution.
- Each run records `machine_tier` and memory telemetry in its remote state JSON.
- `cohort-report --tier` also works with older manifests by inferring tier from `machine_type` when needed.
- The VM periodically publishes progress and ETA to the run state JSON.
- ETA is stall-aware and based on recent+global progress rates (`eta_model`, `eta_confidence`, `no_progress_seconds`).
- Run state also reports whether the benchmark process is alive (`benchmark_process_alive`) to separate slow compute from idle/crash cases.
- During execution, partial artifacts are checkpointed to GCS (`output/raw_results/benchmark_results.partial.json`, `output/summary.partial.json`, rolling `logs/run.log`).
- Durable run state is also synced continuously: `output/jobs.db` (SQLite backup snapshot) and `output/logs/transaction_log_*.jsonl`.
- `resume` starts a terminated/stopped VM for a prior run so it can continue from the synced `jobs.db` state instead of rerunning completed jobs.
- If memory congestion or OOM signals are detected, state includes a tier-escalation recommendation.
- `cancel` is safe-first: it requests graceful shutdown before any force stop.
- Java benchmark engines are built as JVM bytecode (default target Java 21), so
  the same engine artifacts are portable across CPU architectures as long as the
  runtime JVM version is compatible.
- Startup configures Docker auth for Artifact Registry automatically (`gcloud auth configure-docker <region>-docker.pkg.dev`).
- If image pull still fails, ensure the VM service account has Artifact Registry read access.
- `make report` now also writes `gcp_run_status_snapshot.md` into the report directory, including terminated VM run statuses and artifact availability.
