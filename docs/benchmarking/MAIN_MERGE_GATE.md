# Main Merge Gate

This repository has a dedicated GitHub Actions workflow for merge gating:

- Workflow file: `.github/workflows/main-gate.yml`
- Workflow name: `Main Gate`
- Trigger: pull requests targeting `main`/`master` (and merge queue)

## What It Enforces

1. `Compile (Java 25)`
   - Command: `./mvnw -q -B -DskipTests -Dspotbugs.skip=true clean package`
2. `Smoke (min workload)`
   - Command: `./mvnw -q -B -pl rmatch -Dtest=no.rmz.rmatch.ordinaryuse.OrdinaryUsageSmokeTest test`
   - Command: `./mvnw -q -B -pl rmatch-tester exec:java -Dexec.mainClass=no.rmz.rmatch.performancetests.BenchmarkTheWutheringHeightsCorpusWithMemory -Dexec.args="25"`

## Critical Repository Setting (Required)

To make this a hard gate (no broken merge to `main`), set branch protection:

1. GitHub repository `Settings` -> `Branches`
2. Edit/create protection rule for `main`
3. Enable `Require status checks to pass before merging`
4. Mark both checks as required:
   - `Main Gate / Compile (Java 25)`
   - `Main Gate / Smoke (min workload)`
5. Optionally enable `Require branches to be up to date before merging`

Without branch protection required checks, workflows run but do not block merge.
