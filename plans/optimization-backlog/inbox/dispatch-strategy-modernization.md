# Title

Dispatch strategy modernization based on measured hotspots

## Hypothesis

Selective dispatch refactors (instead of blanket modernization) can reduce runtime overhead in high
frequency matcher paths.

## Expected Gain

- Runtime: low-to-moderate improvement in targeted hotspots
- Memory: neutral
- Stability: neutral if semantic equivalence is preserved

## Proposed Change

Use benchmark-driven selection of dispatch sites:

1. Keep current forms where previous measurements showed regressions.
2. Apply modern Java dispatch constructs only in spots with demonstrated wins.
3. Validate each change in isolation.

Impacted areas:

- matching engine dispatch branches
- char/range handling branches

## Risk Notes

1. Over-generalized refactors can regress real workloads.
2. Micro-benchmark improvements may not transfer to production-scale runs.

## Measurement Plan

- Workloads: canonical baseline + 10K campaigns where feasible
- Engines: rmatch compared against itself before/after change
- Cohort/machine: single cohort first (same architecture), then optional cross-cohort validation
- Acceptance threshold: statistically stable runtime improvement with no correctness regression

## Evidence

- `docs/optimization/DISPATCH_OPTIMIZATION_GUIDE.md`
- `docs/optimization/DISPATCH_OPTIMIZATION_RESULTS.md`

## Status

`inbox`
