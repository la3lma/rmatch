# Top-Level Makefile Target Evaluation (2026-03-05)

## Summary

The top-level `Makefile` now has a `help` target (as default goal) and explicit target labels:

- `[core]` = day-to-day build/test/gate operations
- `[perf]` = performance/benchmark operations worth keeping
- `[legacy]` = old or transitional flows that should be reviewed for retirement

Current policy recommendation:

- Keep `[core]` targets as stable public interface.
- Keep `[perf]` targets, but prefer the branch-gate flow (`gate-baseline` / `gate-candidate`) for routine changes.
- Keep `[legacy]` targets temporarily; deprecate in docs now and remove after one cleanup cycle once no active docs/workflows depend on them.

## Keep (Core)

- `help`
- `build`
- `test`
- `ci`
- `fmt`
- `spotless`
- `spotbugs`
- `setup-visualization-env`
- `readme-gcp-snapshot`
- `gate-baseline`
- `gate-candidate`

Reason: these are direct developer entrypoints for correctness and the current branch-vs-main gate workflow.

## Keep (Performance)

- `bench-micro`
- `bench-macro`
- `bench-java`
- `bench-suite`
- `bench-gc-experiments`
- `bench-gc-experiments-fast`
- `validate-gc`
- `bench-dispatch`
- `bench-enhanced`
- `bench-enhanced-quick`
- `bench-enhanced-full`
- `bench-enhanced-arch`
- `profile`

Reason: these are still referenced by active optimization docs and useful for controlled performance experiments.

## Keep For Now, But Marked Legacy

- `charts`
- `visualize-benchmarks`
- `test-run-once`
- `test-run-full`
- `test-run-mini`
- `pre-test-run`

Reason: they are still referenced historically, but overlap with newer benchmarking/reporting flows and include behavior we likely do not want as defaults (for example aggressive local cache reset in `pre-test-run`).

## Deprecation Mapping (Legacy -> Preferred)

- `charts` -> `readme-gcp-snapshot` + framework reporting under `benchmarking/framework/regex_bench_framework`
- `visualize-benchmarks` -> framework report generation (`make report`, web report targets in framework)
- `test-run-once` -> `bench-enhanced-quick` (or `gate-baseline` for branch checks)
- `test-run-full` / `test-run-mini` -> `bench-suite` / `bench-enhanced-full`
- `pre-test-run` -> explicit `mvn clean install` only when needed (avoid cache wipes)

## Recommended Follow-Up Cleanup (Approval Needed)

1. **Deprecate and later remove** `charts` + `visualize-benchmarks` after finalizing the chart pipeline around:
   - `make readme-gcp-snapshot`
   - `benchmarking/framework/regex_bench_framework` reporting scripts
2. **Deprecate and later remove** `test-run-once`, `test-run-full`, `test-run-mini` when equivalent coverage is confirmed through:
   - `bench-*` targets
   - `gate-*` targets
3. **Replace `pre-test-run` behavior** with a safer variant that does not wipe local Maven cache unless explicitly requested.

## Notes

- `ci` was previously listed in `.PHONY` but not implemented; it now exists and maps to `test`.
- No targets were removed in this step; only clarity and evaluation metadata were added.
