# Phased Benchmarking Plan (A-F)

## Scope
This plan defines a clean, reproducible benchmark lane for `rmatch` that avoids placeholder engines and local machine instability.

## Phase A: Reset Baseline (Attic + New Canonical Lane)
- Objective: stop relying on historical scar tissue for decisions.
- Actions:
  - Create an `attic/` area for non-canonical artifacts.
  - Keep legacy tracked files in-repo for history, but exclude them from canonical execution.
  - Define canonical test matrices under `test_matrix/` (new files only).
- Exit criteria:
  - A fresh canonical matrix exists and is used by default for new baseline work.
  - Placeholder paths are not part of canonical runs.

## Phase B: Canonical Comparator Set
- Objective: compare only trusted engines with stable interfaces.
- Canonical engines:
  - `rmatch`
  - `re2j` (Google RE2J)
  - `java-native-naive` (real `java.util.regex` baseline, not placeholder)
- Exit criteria:
  - All three engines build and execute through the framework CLI.
  - Output format is consistent across engines.

## Phase C: Container Runtime
- Objective: run benchmarks in one reproducible environment locally and in cloud.
- Actions:
  - Build a Docker image with Java, Maven, Python, and framework sources.
  - Use scriptable entry points for smoke and baseline runs.
- Exit criteria:
  - Image builds locally and can execute benchmark commands end-to-end.

## Phase D: Local Docker Smoke Test
- Objective: verify pipeline stability (not performance quality).
- Matrix:
  - Patterns: 5, 10
  - Corpus: 100KB
  - Iterations: 1
  - Engines: `java-native-naive`, `re2j`, `rmatch`
- Exit criteria:
  - The run completes without crashes.
  - Result artifacts are produced under `results/`.

## Phase E: Google Execution (Not Run in This Step)
- Objective: run the exact same containerized flow on Google for reproducible performance datasets.
- Constraints:
  - Requires explicit credentials and budget approval.
  - Must capture full run manifest metadata (commit SHA, config hash, machine type, region, image digest).

## Phase F: Cost-Controlled Expansion
- Objective: scale from smoke to larger matrices safely.
- Actions:
  - Expand pattern counts and corpus sizes gradually.
  - Enforce cost/time guardrails per run.
  - Only promote matrices that complete reliably.

## Initial Cost Envelope (Google Estimate Model)
### Assumptions
- Baseline matrix `canonical_baseline_v1`:
  - 81 measured runs (`3 pattern counts x 3 corpus sizes x 3 engines x 3 iterations`)
  - 27 warmup runs (`... x 1 warmup`)
- First run has setup overhead (dependency/cache warmup, container/image startup).
- Larger or more complex regex suites can increase runtime materially.

### Expected Wall-Clock
- Cold run (first run on a fresh worker): about `2.0` to `3.5` hours.
- Warm run (cache/image/data already local): about `1.0` to `2.0` hours.

### Cost Formula
- `run_cost ~= (vm_hourly_rate + disk_hourly_rate) * wall_clock_hours + network_egress`

### Example Cost Bands
- At `$0.50/hour`: warm `~$0.50-$1.00`, cold `~$1.00-$1.75`.
- At `$1.25/hour`: warm `~$1.25-$2.50`, cold `~$2.50-$4.38`.
- At `$2.50/hour`: warm `~$2.50-$5.00`, cold `~$5.00-$8.75`.

### Recommendation
- Keep this as a planning envelope only.
- Before Phase E execution, replace hourly rates with current Google pricing for the exact machine type/region you choose.

## Large Dataset and Pattern Strategy
### Decision
Do **not** bake large corpora/pattern datasets into the Docker image.

### Why
- Large images are slow to build/pull and expensive to maintain.
- Dataset updates should not require image rebuilds.
- Timing fairness requires separating data transfer from timed benchmark execution.

### Recommended Flow
1. Store large datasets in Google Cloud Storage (GCS), versioned by content hash and manifest.
2. At job start, copy datasets from GCS to local disk on the VM/container host.
3. Verify checksums before execution.
4. Run benchmarks only after local staging is complete.
5. Start timing from engine execution, not from data transfer.

### Timed vs Untimed Sections
- Untimed:
  - dataset download/staging
  - checksum validation
  - environment setup
- Timed:
  - engine compilation and scanning work reported by benchmark framework

## Artifacts Added for This Plan
- `test_matrix/smoke_phase4_tiny.json`
- `test_matrix/canonical_baseline_v1.json`
- `engines/java-native-naive/*`
- `Dockerfile`
- `scripts/run_phase4_smoke_local_docker.sh`
- `scripts/run_phase4_smoke_in_container.sh`
