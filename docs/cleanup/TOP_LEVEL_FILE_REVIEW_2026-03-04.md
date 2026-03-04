# Top-Level File Review (2026-03-04)

Scope: every regular file directly under repository root (`/Volumes/SynologyScsi1/git/rmatch`).

Legend:
- `Keep (root)`: stays at repository root.
- `Keep (move)`: keep file, but move out of root in a future cleanup step.
- `Archive candidate`: move to `archive/` (non-destructive).
- `Remove local`: safe local removal (ignored/generated/editor artifact).

| File | Purpose (best estimate) | Recommendation | Rationale | Confidence |
|---|---|---|---|---|
| `.DS_Store` | macOS Finder metadata | Keep (root) | Owner preference: keep locally; ignore enforced in `.gitignore` and `.dockerignore` (including subdirectories) | High |
| `.dockerignore` | Controls Docker build context | Keep (root) | Needed for deterministic/small image build payloads | High |
| `.gcloudignore` | Controls Cloud Build upload payload | Keep (root) | Used by GCP image publishing workflow; prevents huge uploads | High |
| `.gitignore` | Primary repository ignore rules | Keep (root) | Standard project control file | High |
| `.gitignore-performance` | Legacy, narrow ignore rules for performance JSON outputs | Keep (move) | Functionally redundant with `.gitignore`; move to docs/archive for historical reference | Medium |
| `CODE_OF_CONDUCT.md` | Community standards | Keep (root) | Standard OSS metadata expected at root | High |
| `LICENSE` | Project license terms | Keep (root) | Standard OSS metadata expected at root | High |
| `Makefile` | Root build/test/benchmark command entrypoints | Keep (root) | Active developer entrypoint for build/test/charts | High |
| `README.md` | Primary project landing page and usage docs | Keep (root) | Standard root entrypoint; referenced by users and automation | High |
| `TODO.md` | Active cross-cutting backlog notes (includes current benchmark correctness notes) | Keep (move) | Content is active, but root clutter is high; move to `plans/` or `docs/cleanup/` later | Medium |
| `benchmarks.ipynb` | Historical exploratory benchmark analysis notebook | Archive candidate | Not in active build/test path; likely scar tissue from older benchmark flow | Medium |
| `checkstyle-unused-imports.xml` | Checkstyle configuration for Maven modules | Keep (root) | Referenced by `pom.xml`, `rmatch-tester/pom.xml`, and `benchmarks/jmh/pom.xml` | High |
| `foo.sh` | Ad hoc script to run `GitHubActionPerformanceTestRunner` | Archive candidate | Not referenced by build/CI and duplicates managed entrypoints | High |
| `java_performance_timeline.png` | Generated benchmark chart published in README/CI artifacts | Archive candidate | Owner directive: move top-level PNG artifacts to attic | High |
| `measurements.sqlite` | Local analysis database used by notebook workflows | Archive candidate | Owner directive: move top-level SQLite/DB files to attic | High |
| `mvnw` | Maven wrapper (Unix) | Keep (root) | Standard project bootstrap binary | High |
| `mvnw.cmd` | Maven wrapper (Windows) | Keep (root) | Standard project bootstrap binary for Windows | High |
| `performance_comparison.png` | Generated benchmark chart published in README/CI artifacts | Archive candidate | Owner directive: move top-level PNG artifacts to attic | High |
| `performance_timeline.png` | Generated benchmark chart published in README/CI artifacts | Archive candidate | Owner directive: move top-level PNG artifacts to attic | High |
| `pom.xml` | Root Maven parent/build configuration | Keep (root) | Core build definition | High |
| `requirements.txt` | Python deps for root chart-generation workflows | Keep (root) | Used by GitHub workflows and docs/scripts; can be split later if desired | High |
| `rmatch-parent.iml` | IntelliJ module metadata | Remove local | IDE artifact; already ignored in `.gitignore` | High |
| `summarize-large-corpus-trials.sql` | Historical SQL summary query for old measurement DB | Archive candidate | Appears notebook-only and not in current harness/report pipeline | Medium |
| `test_jit_comparison.sh` | One-off JIT tuning experiment script | Archive candidate | Ad hoc benchmark script, not in managed benchmark harness | High |
| `test_profile_guided.sh` | One-off profile-guided benchmark experiment script | Archive candidate | Ad hoc benchmark script, not in managed benchmark harness | High |
| `test_warmup_benchmark.sh` | One-off warmup/JIT experiment harness | Archive candidate | Ad hoc benchmark script, not in managed benchmark harness | High |
| `validate_jit_config.sh` | One-off validation script for selected JIT options | Archive candidate | Ad hoc benchmark script, not in managed benchmark harness | High |

## Additional note

- `qodana.yaml`: intentionally removed by repository owner and should stay removed.

## Suggested next conservative batch

1. Move archive candidates from root into `archive/2026-03-repo-cleanup/root/legacy-benchmark-scripts-and-notebooks/` with manifest update.
2. Move `TODO.md` into `plans/` (or `docs/cleanup/`) and leave a short pointer in `README.md`.
3. Move top-level chart PNGs to attic and then retarget chart scripts/workflows to `charts/` paths if needed.
