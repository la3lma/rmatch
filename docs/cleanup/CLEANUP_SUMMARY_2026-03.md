# Cleanup Summary (2026-03)

## Scope

Conservative, non-destructive repository cleanup with emphasis on:

1. preserving core matcher and benchmark functionality
2. reducing top-level clutter
3. making optimization planning and historical material easier to navigate

## Completed Work

### Phase 0/1/2 Foundations

1. Cleanup baseline and inventory artifacts maintained under `docs/cleanup/`.
2. Scaffolding created and retained:
   - `docs/`
   - `plans/optimization-backlog/`
   - `archive/2026-03-repo-cleanup/`

### Phase 3: Active Doc Relocation

Moved root active docs into curated sections:

1. `docs/architecture/`
2. `docs/benchmarking/`
3. `docs/optimization/`

Updated:

1. root `README.md` links to moved docs
2. `docs/README.md` section index
3. `docs/cleanup/repo-move-manifest-draft.csv` statuses

### Phase 4: Legacy Artifact Archival

Moved non-essential root artifacts to:

1. `archive/2026-03-repo-cleanup/root/`

Artifacts archived:

1. `README.md.backup`
2. `examples-for-visualization.py~`
3. `foo.sh~`
4. `java-maven-files.tgz`
5. `rmatch-infra-bootstrap.tgz`

Manifest updates:

1. `archive/2026-03-repo-cleanup/MOVED_MANIFEST.md`
2. `docs/cleanup/repo-move-manifest-draft.csv`

### Phase 5: Harness Clarity Pass (Conservative)

1. Added benchmark harness archival policy:
   - `extended-benchmarking/regex_bench_framework/docs/RESULTS_ARCHIVE_POLICY.md`
2. Added runtime-noise ignore rules:
   - `extended-benchmarking/regex_bench_framework/.gitignore`
   - `.pytest_cache/`, `gcp_runs/*.pid`, `gcp_runs/*.nohup.pid`

### Phase 6: Optimization Backlog Consolidation

Added structured inbox items:

1. `plans/optimization-backlog/inbox/dispatch-strategy-modernization.md`
2. `plans/optimization-backlog/inbox/fastpath-prefilter-threshold-tuning.md`
3. `plans/optimization-backlog/inbox/gc-runtime-profile-selection.md`
4. `plans/optimization-backlog/inbox/large-workload-timeout-heuristics.md`
5. `plans/optimization-backlog/inbox/memory-observability-in-benchmarks.md`

Updated backlog index:

1. `plans/optimization-backlog/README.md`

### Phase 7: Top-Level Declutter Execution

Executed the top-level review move recommendations:

1. Root `MOVE TO ATTIC` entries archived under:
   - `archive/2026-03-repo-cleanup/root/legacy-top-level-2026-03-04/`
2. Root `KEEP (RELOCATE)` entries moved into curated locations:
   - `analysis/` -> `docs/analysis/`
   - `proposals/` -> `docs/proposals/`
   - `TODO.md` -> `plans/TODO.md`
   - `.gitignore-performance` -> `docs/cleanup/legacy-root-files/.gitignore-performance`
3. Root chart PNG and SQLite artifacts archived per owner directive.
4. Tracked IDE/notebook remnants moved to archive:
   - `.idea/dictionaries/rmz.xml`
   - `.ipynb_checkpoints/benchmarks-checkpoint.ipynb`

Manifest updates:

1. `archive/2026-03-repo-cleanup/MOVED_MANIFEST.md`
2. `docs/cleanup/repo-move-manifest-draft.csv`

## Validation Results

Executed and passing:

1. `mvn -q -B -DskipTests -Dspotbugs.skip=true package`
2. `mvn -q -B -pl rmatch,rmatch-tester test`
3. targeted smoke checks:
   - `mvn -q -B -pl rmatch -Dtest=no.rmz.rmatch.ordinaryuse.OrdinaryUsageSmokeTest test`

Harness command sanity (non-executing):

1. `make -n gcp-list` (framework)
2. `make -n gcp-status` (framework)

## Known Baseline Caveats (Unchanged by Cleanup)

In `extended-benchmarking/regex_bench_framework`:

1. `make test-unit` expects `tests/` directory that is currently absent.
2. `make test-quick` invokes `.venv/bin/regex-bench` but target dependencies only guarantee venv
   bootstrap and engine builds.

These are pre-existing workflow issues and remain recorded in:

1. `docs/cleanup/baseline-checks.md`

## Current Repository Faces

1. Core library: `rmatch/`
2. Testing harness: `rmatch-tester/`
3. Benchmark platform: `extended-benchmarking/regex_bench_framework/`
4. Documentation and papers: `docs/`
5. Optimization planning: `plans/optimization-backlog/`
6. Historical quarantine: `archive/`
