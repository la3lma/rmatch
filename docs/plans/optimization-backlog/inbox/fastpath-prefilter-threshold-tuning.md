# Title

Fastpath prefilter threshold and activation tuning

## Hypothesis

Prefilter strategy and threshold tuning can improve rmatch runtime under large pattern sets without
hurting lower-scale workloads.

## Expected Gain

- Runtime: moderate improvement at higher pattern counts
- Memory: uncertain (may increase in some configurations)
- Stability: neutral if matching semantics are preserved

## Proposed Change

1. Revisit prefilter activation thresholds using current campaign data.
2. Evaluate static defaults versus dynamic threshold heuristics.
3. Keep fallback path correctness-first for unsupported buffer types.

Impacted areas:

- fastpath prefilter activation
- candidate position generation and use

## Risk Notes

1. Mis-tuned thresholds can regress medium workloads.
2. Prefilter memory pressure may increase with very large pattern sets.

## Measurement Plan

- Workloads: canonical and 10K campaigns at multiple corpus sizes
- Engines: rmatch before/after
- Cohort/machine: fixed cohort for primary decision
- Acceptance threshold: improvement in target region with no regressions above tolerance elsewhere

## Evidence

- `docs/optimization/FASTPATH_OPTIMIZATION.md`
- `docs/optimization/FASTPATH_PERFORMANCE_ANALYSIS.md`

## Status

`inbox`
