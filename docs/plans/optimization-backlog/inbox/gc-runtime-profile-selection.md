# Title

GC runtime profile selection for benchmark and production guidance

## Hypothesis

Selecting GC profile by workload region (pattern count and corpus size) can improve runtime and/or
memory behavior versus a one-size-fits-all configuration.

## Expected Gain

- Runtime: low-to-moderate, configuration-dependent
- Memory: moderate reduction possible in some configurations
- Stability: improved predictability if profile selection is repeatable

## Proposed Change

1. Build a small decision matrix for GC options by workload class.
2. Validate against canonical and high-load campaigns.
3. Provide recommended defaults and override guidance.

Impacted areas:

- benchmark runtime settings
- production recommendation documentation

## Risk Notes

1. Profile complexity may increase operational burden.
2. Gains may vary significantly by architecture/JDK distribution.

## Measurement Plan

- Workloads: canonical + selected high-load scenarios
- Engines: rmatch primarily (java-native/re2j as context where relevant)
- Cohort/machine: single cohort decisions first, cross-cohort notes second
- Acceptance threshold: measurable gain or meaningful variance reduction

## Evidence

- `docs/optimization/GC_EXPERIMENTS.md`
- `docs/optimization/GC_OPTIMIZATION_RESULTS.md`
- `docs/optimization/QUICKSTART_GC.md`

## Status

`inbox`
