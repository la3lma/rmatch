# Benchmark Results Archive Policy

## Purpose

Keep active benchmark execution and reporting paths simple, while preserving historical campaigns
without deletion.

## Scope

This policy applies to:

1. `results/`
2. `reports/`
3. `gcp_runs/`

## Active vs Historical

1. Active campaign artifacts:
   - currently used by live monitoring, report generation, or rerun workflows
   - remain in `results/` and `reports/`
2. Historical campaign artifacts:
   - no longer needed for active reruns or live report updates
   - move to `attic/<YYYY-MM>/...` inside `regex_bench_framework/`

## Runtime Files

1. PID marker files under `gcp_runs/` are runtime-only and not versioned.
2. `.pytest_cache/` is runtime-only and not versioned.

## Move Rules

1. Never delete campaign artifacts during cleanup phases.
2. Move in batches and keep manifest notes in:
   - `archive/2026-03-repo-cleanup/MOVED_MANIFEST.md` (repo-wide)
3. Do not move files required by:
   - `make gcp-list`
   - `make gcp-status`
   - active `make report-*` workflows

## Validation After Moves

After any archive batch in the harness:

1. `make -n gcp-list`
2. `make -n gcp-status`
3. Run a quick Java smoke check at repo root:
   - `mvn -q -B -pl rmatch -Dtest=no.rmz.rmatch.ordinaryuse.OrdinaryUsageSmokeTest test`
