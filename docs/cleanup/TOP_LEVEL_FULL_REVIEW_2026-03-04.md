# Top-Level Full Review (Files + Directories)

Timestamp: 2026-03-04
Scope: every top-level entry in `/Volumes/SynologyScsi1/git/rmatch`.

Recommendation labels:
- `KEEP`: keep at top level.
- `KEEP (RELOCATE)`: keep content, but move out of top level in a later cleanup step.
- `MOVE TO ATTIC`: preserve under `archive/` (non-destructive).
- `REMOVE LOCAL`: local/editor/generated artifact; should not remain in workspace root.

## Files

| Entry | What it does (best estimate) | Recommendation | Notes |
|---|---|---|---|
| `.DS_Store` | macOS Finder metadata | KEEP | Owner preference: keep local Finder metadata files; enforce ignore patterns in `.gitignore` and `.dockerignore` (including subdirectories). |
| `.dockerignore` | Docker build context filter | KEEP | Required for deterministic/small container builds. |
| `.gcloudignore` | Cloud Build upload filter | KEEP | Required for controlled GCP build payloads. |
| `.gitignore` | Main ignore policy | KEEP | Core repository hygiene. |
| `.gitignore-performance` | Legacy ignore subset for perf outputs | KEEP (RELOCATE) | Redundant with `.gitignore`; move to cleanup history docs. |
| `CODE_OF_CONDUCT.md` | Community policy | KEEP | Standard root OSS metadata. |
| `LICENSE` | License terms | KEEP | Standard root OSS metadata. |
| `Makefile` | Root build/test/bench entrypoints | KEEP | Active project entrypoint. |
| `README.md` | Main project landing page | KEEP | Active root documentation. |
| `TODO.md` | Active backlog and notes | KEEP (RELOCATE) | Active content; better under `docs/plans/` or `docs/cleanup/`. |
| `benchmarks.ipynb` | Historical notebook analysis | MOVE TO ATTIC | Old exploratory flow; not in current controlled harness. |
| `checkstyle-unused-imports.xml` | Checkstyle config used by Maven modules | KEEP | Referenced from multiple `pom.xml` files. |
| `foo.sh` | Ad hoc benchmark shell wrapper | MOVE TO ATTIC | Not part of current controlled run pipeline. |
| `java_performance_timeline.png` | Generated chart shown in README/workflows | MOVE TO ATTIC | Owner directive: move top-level PNG files to attic. |
| `measurements.sqlite` | Local benchmark DB scratch artifact | MOVE TO ATTIC | Owner directive: move top-level SQLite/DB files to attic. |
| `mvnw` | Maven wrapper (Unix) | KEEP | Standard build bootstrap. |
| `mvnw.cmd` | Maven wrapper (Windows) | KEEP | Standard build bootstrap. |
| `performance_comparison.png` | Generated chart shown in README/workflows | MOVE TO ATTIC | Owner directive: move top-level PNG files to attic. |
| `performance_timeline.png` | Generated chart shown in README/workflows | MOVE TO ATTIC | Owner directive: move top-level PNG files to attic. |
| `pom.xml` | Root Maven build definition | KEEP | Core build definition. |
| `requirements.txt` | Python deps for chart scripts/workflows | KEEP | Used by chart-generation workflows/scripts. |
| `rmatch-parent.iml` | IntelliJ module file | REMOVE LOCAL | IDE artifact; intentionally ignored. |
| `summarize-large-corpus-trials.sql` | Legacy SQL summary query for old measurement flows | MOVE TO ATTIC | Useful historically, not part of current controlled harness. |
| `test_jit_comparison.sh` | One-off JIT experiment script | MOVE TO ATTIC | Historical tuning script, not in stable CI flow. |
| `test_profile_guided.sh` | One-off profiling script | MOVE TO ATTIC | Historical tuning script, not in stable CI flow. |
| `test_warmup_benchmark.sh` | One-off warmup/JIT script | MOVE TO ATTIC | Historical tuning script, not in stable CI flow. |
| `validate_jit_config.sh` | One-off JIT validation script | MOVE TO ATTIC | Historical tuning script, not in stable CI flow. |

## Directories

| Entry | What it does (best estimate) | Recommendation | Notes |
|---|---|---|---|
| `.claude/` | Local assistant settings (untracked) | REMOVE LOCAL | Personal/local config; should not sit at repo root. |
| `.git/` | Git metadata | KEEP | Mandatory VCS internals. |
| `.github/` | CI/workflows, templates, repo automation | KEEP | Active repository control plane. |
| `.idea/` | IntelliJ workspace data (mostly untracked) | REMOVE LOCAL | IDE-local state. One tracked file (`.idea/dictionaries/rmz.xml`) should be retired/moved to attic before full ignore cleanup. |
| `.ipynb_checkpoints/` | Notebook autosave checkpoint | MOVE TO ATTIC | Contains tracked historical checkpoint; not needed at root. |
| `.mvn/` | Maven wrapper config | KEEP | Required by `mvnw`. |
| `analysis/` | Technical analysis paper bundle (LaTeX/PlantUML and outputs) | KEEP (RELOCATE) | Keep content, but relocate under `docs/papers/` or `docs/analysis/` to reduce root clutter. |
| `archive/` | Official attic/quarantine for historical artifacts | KEEP | This is the intended non-destructive attic location. |
| `benchmarking/` | JMH module, baselines, legacy benchmark result history | KEEP | Active benchmark module exists here. Consider internal archival of older `benchmarking/results/*` batches. |
| `charts/` | Generated chart outputs (+legacy chart set) | KEEP (RELOCATE) | Keep for now due workflow references; later move to `docs/benchmarking/charts/` with workflow path update. |
| `docs/` | Curated documentation and papers | KEEP | Active docs home. |
| `benchmarking/framework/` | New large-scale benchmark platform and control plane | KEEP | Core of current benchmarking strategy. |
| `docs/plans/` | Optimization backlog and planning artifacts | KEEP | Active planning home; aligns with cleanup goals. |
| `prd-repo/` | Legacy PRD docs (single file) | MOVE TO ATTIC | Historical design artifact; no longer primary execution plan. |
| `proposals/` | Historical proposals and drafts | KEEP (RELOCATE) | Keep for now, but better moved under `docs/proposals/` (or archive stale ones) after curation. |
| `rmatch/` | Core regex matcher library module | KEEP | Primary product code. |
| `rmatch-tester/` | Test harness and matcher comparison utilities | KEEP | Primary testing module. Internal historical logs/plots can be archived later in-module. |
| `scripts/` | Build/benchmark/report helper scripts | KEEP | Active operational scripts live here. Local noise inside (`scripts/.venv`, `__pycache__`, backups) should be cleaned. |
| `venv/` | Local Python virtualenv | REMOVE LOCAL | Local environment, not project source. |
| `venv2/` | Local Python virtualenv | REMOVE LOCAL | Local environment, not project source. |

## Suggested next cleanup batch (conservative)

1. Move top-level `MOVE TO ATTIC` entries into `archive/2026-03-repo-cleanup/root/legacy-top-level-2026-03-04/` with manifest.
2. Remove/ignore local artifacts at root (IDE/venv leftovers and non-owner-kept OS artifacts).
3. Relocate `analysis/` and possibly `proposals/` under `docs/` in staged commits with quick test checks between steps.
4. In a separate pass, archive old data inside `benchmarking/results/`, `rmatch-tester/logs/`, and `rmatch-tester/plots/` using the same manifest policy.
