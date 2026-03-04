# Title

Add first-class memory observability to benchmark campaigns

## Hypothesis

Capturing consistent per-run memory telemetry (peak RSS/heap and allocation hints) will enable
better optimization decisions and explain runtime outliers under high-load scenarios.

## Expected Gain

- Runtime: indirect (better optimization targeting)
- Memory: improved visibility and optimization potential
- Stability: improved diagnosis of OOM and congestion events

## Proposed Change

1. Extend benchmark harness run records with memory fields.
2. Collect memory snapshots at key lifecycle points (startup, warmup, run, teardown).
3. Include memory plots and tables in workload reports and paper outputs.

Impacted areas:

- benchmark engine wrappers
- run database schema and ingestion
- reporting layer (HTML/LaTeX summaries)

## Risk Notes

1. Sampling overhead may slightly perturb runtime.
2. Cross-platform memory APIs may differ and require normalization.

## Measurement Plan

- Workloads: canonical baseline + selected 10K high-load cases
- Engines: rmatch, re2j, java-native-naive
- Cohort/machine: single cohort initially
- Acceptance threshold: memory data completeness with negligible timing overhead

## Evidence

- Missing-memory reporting gap noted in status report and campaign retrospectives.

## Status

`inbox`
