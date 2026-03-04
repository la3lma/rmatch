# Optimization Backlog

This directory tracks optimization ideas from hypothesis to decision with preserved evidence.

## Workflow

1. Add new ideas to `inbox/` using `idea-template.md`.
2. Move to `screened/` when an experiment plan is defined.
3. Move to `validated/` when measured improvement is confirmed.
4. Move to `rejected/` when no improvement or unacceptable risk is found.

## Required Metadata

Each idea document should include:

- hypothesis
- expected gain
- required changes
- measurement plan
- status
- evidence links (run IDs, report paths, notes)

## Rules

- Do not delete ideas during evaluation campaigns.
- Keep experimental claims tied to benchmark evidence.
- Prefer small, reversible code changes per optimization probe.

## Current Index

| Idea | Status | Source Notes |
|---|---|---|
| `inbox/dispatch-strategy-modernization.md` | `inbox` | `docs/optimization/DISPATCH_OPTIMIZATION_GUIDE.md`, `docs/optimization/DISPATCH_OPTIMIZATION_RESULTS.md` |
| `inbox/fastpath-prefilter-threshold-tuning.md` | `inbox` | `docs/optimization/FASTPATH_OPTIMIZATION.md`, `docs/optimization/FASTPATH_PERFORMANCE_ANALYSIS.md` |
| `inbox/gc-runtime-profile-selection.md` | `inbox` | `docs/optimization/GC_EXPERIMENTS.md`, `docs/optimization/GC_OPTIMIZATION_RESULTS.md`, `docs/optimization/QUICKSTART_GC.md` |
| `inbox/large-workload-timeout-heuristics.md` | `inbox` | dynamic 10K campaign notes and timeout guardrail discussions |
| `inbox/memory-observability-in-benchmarks.md` | `inbox` | known reporting gap noted in status report and campaign discussions |
