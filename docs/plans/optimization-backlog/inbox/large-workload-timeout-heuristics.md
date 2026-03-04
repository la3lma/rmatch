# Title

Adaptive timeout and stall-guard heuristics for large workload campaigns

## Hypothesis

Combining model-based runtime estimates with stall detection can reduce wasted compute while
preserving valuable partial data from long-running campaigns.

## Expected Gain

- Runtime: lower wall-clock waste on doomed runs
- Memory: neutral
- Stability: improved campaign reliability and operator visibility

## Proposed Change

1. Calibrate timeout factors from log-linear model predictions per engine/workload class.
2. Keep hard upper bounds plus stall cutoff guardrails.
3. Persist partial completion and failure metadata for rerun planning.

Impacted areas:

- campaign scheduler and watchdog logic
- reporting of terminated/failed work units

## Risk Notes

1. Aggressive cutoffs can drop useful data.
2. Under-tuned cutoffs can still waste compute at high loads.

## Measurement Plan

- Workloads: high-load 10K campaigns (1MB, 10MB, 100MB and later extensions)
- Engines: rmatch, re2j, java-native-naive
- Cohort/machine: consistent cohort for calibration
- Acceptance threshold: reduced stall time with equal or better retained-result coverage

## Evidence

- Existing campaign guardrail implementation notes and dynamic sequencing discussions.
- Live monitor/ETA behavior observations from 10K runs.

## Status

`inbox`
