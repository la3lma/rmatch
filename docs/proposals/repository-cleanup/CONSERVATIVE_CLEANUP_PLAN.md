# Conservative Repository Cleanup Plan

## Purpose
Clean up repository structure for accessibility while preserving all essential functionality, all benchmark/test harness capability, and all historical material (no deletions in this plan).

## Cleanup Objectives
1. Keep core regex library work as the primary face of the repository.
2. Keep benchmark harness and performance workflows fully operational.
3. Preserve all historical material, but move non-essential and obsolete artifacts away from top-level view.
4. Introduce a dedicated location for optimization ideas/backlog, including half-baked proposals.
5. Keep paper writeups under `docs/papers/` (not top-level).
6. Execute in small, reversible steps with frequent commits and validation gates.

## Constraints and Guardrails
1. No destructive cleanup in this phase (no permanent deletion of historical content).
2. Every structural step is followed by fast validation commands.
3. Every phase ends with a commit.
4. Start from known-good baseline tests before any structural move.
5. Do not alter active benchmark semantics during cleanup.

## Working Definition of Essential Functionality
1. Java modules and builds:
   - `rmatch`
   - `rmatch-tester`
   - `benchmarking/jmh`
   - root `pom.xml` module wiring
2. Benchmark harness and GCP orchestration:
   - `benchmarking/framework/regex_bench_framework`
3. Active scripts used by build/benchmark/report flows:
   - root `Makefile`
   - root `scripts/`
   - framework `Makefile` and `scripts/`
4. Existing results and reports needed for current analysis continuity.

## Proposed High-Level Faces
1. Core Library: `rmatch/` as primary engineering focus.
2. Benchmarking Platform: `benchmarking/framework/regex_bench_framework/` as performance lab.
3. Optimization Backlog: new `plans/optimization-backlog/` for idea intake and evaluation queue.
4. Documentation and Papers: `docs/` with paper writeups in `docs/papers/`.
5. Historical/Legacy: new `archive/` as a quarantine zone for scar tissue and legacy artifacts.

## Target Top-Level Shape (After Cleanup)
1. Keep:
   - `rmatch/`
   - `rmatch-tester/`
   - `benchmarking/`
   - `benchmarking/framework/`
   - `scripts/`
   - `pom.xml`, `Makefile`, `README.md`, `LICENSE`, Maven wrappers
2. Add:
   - `docs/` for curated active documentation
   - `docs/papers/` for writeups and publication-prep material
   - `plans/optimization-backlog/` for optimization proposal lifecycle
   - `archive/` for old/obsolete/historical artifacts
3. Reduce top-level clutter by moving:
   - old standalone markdowns, backup files, ad hoc assets, and historical artifacts into `docs/` or `archive/`

## Proposed Directory Policy
1. `docs/`: currently relevant operational and design docs.
   - paper manuscripts and generated PDFs live under `docs/papers/`
2. `plans/optimization-backlog/`:
   - `README.md` (workflow/rules)
   - `inbox/` (new ideas)
   - `screened/` (ready for experiment design)
   - `validated/` (proven wins)
   - `rejected/` (did not improve or too risky)
3. `archive/`:
   - date-stamped batches, for example `archive/2026-03-repo-cleanup/`
   - each batch has a manifest file with original paths and move rationale.

## Phase Plan

### Phase 0: Freeze and Baseline
1. Wait for currently running GCP probe(s) to reach terminal state and sync results.
2. Capture baseline commit/tag before cleanup start.
3. Run baseline checks:
   - `mvn -q -B -DskipTests -Dspotbugs.skip=true package`
   - `mvn -q -B -pl rmatch,rmatch-tester test`
   - `cd benchmarking/framework/regex_bench_framework && make test-unit && make test-quick`
4. Record outputs in `docs/cleanup/baseline-checks.md`.
5. Commit: `cleanup: baseline validation before repository reorganization`.

### Phase 1: Inventory and Classification (No Moves Yet)
1. Build file inventory of tracked content by category:
   - core code
   - active docs
   - benchmark assets
   - generated artifacts
   - legacy/unclear files
2. Produce move manifest draft:
   - source path
   - destination path
   - reason
   - risk notes
3. Deliverable:
   - `docs/cleanup/repo-inventory.md`
   - `docs/cleanup/repo-move-manifest-draft.csv`
4. Commit: `cleanup: add inventory and move manifest draft`.

### Phase 2: Introduce New Containers (Minimal Risk)
1. Create:
   - `docs/cleanup/`
   - `plans/optimization-backlog/{inbox,screened,validated,rejected}`
   - `archive/2026-03-repo-cleanup/`
2. Add governance docs:
   - `plans/optimization-backlog/README.md`
   - optimization idea template file
   - `archive/README.md` describing non-deletion policy
3. Validation:
   - repeat Phase 0 fast checks
4. Commit: `cleanup: add docs/plans/archive scaffolding`.

### Phase 3: Move Active Top-Level Docs into `docs/`
1. Move active topical markdowns from root to structured `docs/` sections.
2. Keep root `README.md` as primary entrypoint and update links.
3. Keep a short root `docs index` pointer page to avoid discoverability loss.
4. Validation:
   - root Maven checks
   - link check for moved docs (simple grep/path verification)
5. Commit: `cleanup: relocate active docs and update references`.

### Phase 4: Move Legacy/Scar-Tissue Artifacts to `archive/`
1. Move clearly non-essential root clutter to archive batch:
   - backup files (`*.backup`, `*~`)
   - standalone tarballs not needed by active flows
   - old ad hoc scripts/assets not in current workflows
2. Move legacy/historical material from other dirs when not used by active commands.
3. Keep a manifest:
   - `archive/2026-03-repo-cleanup/MOVED_MANIFEST.md`
4. Validation:
   - rerun fast checks
   - spot-check key make targets still resolve
5. Commit: `cleanup: archive legacy artifacts (non-destructive)`.

### Phase 5: Benchmark-Harness Clarity Pass (No Functional Changes)
1. In `benchmarking/framework/regex_bench_framework`, separate:
   - active configs/results/reports
   - historical campaigns and backups
2. Standardize archival location for old campaigns under framework `attic/` or root `archive/` (single policy).
3. Ensure all active report and run control targets still work:
   - `make gcp-list`
   - `make report-workload-web-all OUT=reports/workload_all_live`
4. Commit: `cleanup: organize benchmark harness historical material`.

### Phase 6: Optimization Backlog Consolidation
1. Move scattered optimization notes into `plans/optimization-backlog/inbox/`.
2. Add metadata header to each note:
   - hypothesis
   - expected gain
   - required changes
   - measurement plan
   - status
3. Add index table in `plans/optimization-backlog/README.md`.
4. Commit: `cleanup: consolidate optimization ideas backlog`.

### Phase 7: Post-Cleanup Validation and Hardening
1. Full regression checks (fast suite first, broader optional suite second).
2. Confirm benchmark harness still publishes and generates reports.
3. Update root `README.md` with new repository map.
4. Produce cleanup summary:
   - what moved
   - what stayed
   - what is now easier to navigate
5. Commit: `cleanup: final validation and documentation`.

## Commit Cadence and Rollback Strategy
1. One commit per phase minimum.
2. If a phase is large, split into sub-commits per atomic move group.
3. Tag checkpoints:
   - pre-cleanup tag
   - post-phase tags for quick rollback.
4. Never squash during cleanup campaign; preserve forensic history.

## Validation Matrix (Run Between Phases)
1. Root compile/package:
   - `mvn -q -B -DskipTests -Dspotbugs.skip=true package`
2. Root unit tests:
   - `mvn -q -B -pl rmatch,rmatch-tester test`
3. Framework unit/smoke:
   - `cd benchmarking/framework/regex_bench_framework && make test-unit && make test-quick`
4. Report generation sanity:
   - `cd benchmarking/framework/regex_bench_framework && make report-workload-web-all OUT=reports/workload_all_live`

## Non-Goals for This Cleanup
1. No algorithmic optimization work.
2. No benchmark methodology changes.
3. No deletion of old material.
4. No architecture refactor of runtime code.

## Execution Readiness
Execution should start after current GCP run set is complete and synced, so we avoid mixing active benchmark operations with structural repository moves.
