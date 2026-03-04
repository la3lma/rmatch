# Phase D Smoke Execution (Docker)

## Date
- 2026-02-28

## Scope
- Executed up to Phase D only (local Docker smoke).
- No Google Cloud execution was performed.

## Command
```bash
cd /Volumes/SynologyScsi1/git/rmatch/extended-benchmarking/regex_bench_framework
./scripts/run_phase4_smoke_local_docker.sh
```

## Result
- Exit code: `0`
- Smoke matrix completed: `6/6` successful runs, `0` failed
- Output directory:
  - `results/phase4_smoke_20260228_154030`

## Engines Covered
- `java-native-naive`
- `re2j`
- `rmatch`

## Notes
- `rmatch` build path was updated to compile against the container runtime Java major version.
- Host `~/.m2` is no longer mounted in the local Docker smoke script to avoid class-version mismatch contamination.

## Known Caveat
- Local host `.venv` is currently architecture-incompatible (`x86_64` wheels on an ARM host), so host-side CLI validation failed.
- Containerized run is the canonical path and completed successfully.
