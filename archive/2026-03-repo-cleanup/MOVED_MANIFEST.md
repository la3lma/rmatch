# 2026-03 Repository Cleanup Move Manifest

This file tracks non-destructive moves performed in the 2026-03 cleanup campaign.

## Moves Completed

1. `papers/` -> `docs/papers/`
   - Reason: keep papers under documentation hierarchy as cleanup amendment.
   - Risk: relative path breakage inside LaTeX includes.
   - Mitigation: updated status report LaTeX include path after move.

2. Root active documentation -> `docs/{architecture,benchmarking,optimization}/`
   - Reason: reduce root-level clutter and group docs by purpose.
   - Scope:
     - architecture: `ARCHITECTURE_AWARE_BENCHMARKING.md`, `IMPLEMENTATION_SUMMARY.md`
     - benchmarking: `ENHANCED_TESTING_FRAMEWORK.md`, `OPTIMAL_CONFIGURATION_GUIDE.md`,
       `PERFORMANCE_AUTOMATION.md`, `PERFORMANCE_CHARTS.md`, `PERFORMANCE_TESTING.md`,
       `PERFORMANCE_TESTING_LESSONS.md`, `PERFORMANCE_TRACKING.md`
     - optimization: `DISPATCH_OPTIMIZATION_GUIDE.md`, `DISPATCH_OPTIMIZATION_RESULTS.md`,
       `FASTPATH_OPTIMIZATION.md`, `FASTPATH_PERFORMANCE_ANALYSIS.md`, `GC_EXPERIMENTS.md`,
       `GC_OPTIMIZATION_RESULTS.md`, `QUICKSTART_GC.md`
   - Risk: stale links from root docs.
   - Mitigation: updated root `README.md` and `docs/README.md`; move manifest status switched to
     `moved`.

## Pending Move Groups

Pending groups are tracked in:

- `docs/cleanup/repo-move-manifest-draft.csv`
