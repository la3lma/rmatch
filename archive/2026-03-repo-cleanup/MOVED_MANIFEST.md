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

3. Root legacy artifacts -> `archive/2026-03-repo-cleanup/root/`
   - Reason: quarantine non-essential files without deletion.
   - Scope:
     - `README.md.backup`
     - `examples-for-visualization.py~`
     - `foo.sh~`
     - `java-maven-files.tgz`
     - `rmatch-infra-bootstrap.tgz`
   - Risk: hidden dependency on tarballs.
   - Mitigation: no runtime/build/test scripts reference these files; kept intact under archive.

4. Root PNG and SQLite artifacts -> `archive/2026-03-repo-cleanup/root/legacy-top-level-2026-03-04/`
   - Reason: owner directive to move top-level PNG and SQLite files to attic.
   - Scope:
     - `java_performance_timeline.png`
     - `performance_comparison.png`
     - `performance_timeline.png`
     - `measurements.sqlite`
   - Risk: stale references from docs/workflows expecting root-level PNG paths.
   - Mitigation: updated root `README.md` paths; follow-up planned to retarget script/workflow outputs.

5. Additional root historical artifacts -> `archive/2026-03-repo-cleanup/root/legacy-top-level-2026-03-04/`
   - Reason: execute top-level `MOVE TO ATTIC` recommendations from cleanup review.
   - Scope:
     - `benchmarks.ipynb`
     - `foo.sh`
     - `summarize-large-corpus-trials.sql`
     - `test_jit_comparison.sh`
     - `test_profile_guided.sh`
     - `test_warmup_benchmark.sh`
     - `validate_jit_config.sh`
     - `.ipynb_checkpoints/benchmarks-checkpoint.ipynb`
     - `prd-repo/PRD_PERFORMANCE_GITHUB_ACTION.md`
     - `.idea/dictionaries/rmz.xml`
   - Risk: stale references from historical docs and PR markdown links.
   - Mitigation: updated active PR summary link target and cleanup inventory docs.

6. Root keep-but-relocate artifacts moved into curated locations
   - Reason: reduce top-level clutter while preserving content in active doc/plan paths.
   - Scope:
     - `analysis/` -> `docs/analysis/`
     - `proposals/` -> `docs/proposals/`
     - `TODO.md` -> `plans/TODO.md`
     - `.gitignore-performance` -> `docs/cleanup/legacy-root-files/.gitignore-performance`
   - Risk: stale path references.
   - Mitigation: updated docs index, repository inventory notes, and ignore-path rules.

## Pending Move Groups

Pending groups are tracked in:

- `docs/cleanup/repo-move-manifest-draft.csv`
