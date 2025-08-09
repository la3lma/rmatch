# Performance Baseline

To get stable numbers:

- Use a **dedicated Linux host**. Pin CPU governor to `performance`; avoid noisy neighbors.
- JDK: Temurin 21.x, same minor version for all runs.
- JVM flags: default first; record any custom flags with the result JSON.
- Warm cache: run once before recording.
- Run **at least 2 forks** for JMH and 5–10s per measurement iteration.

Store canonical baselines:

- `benchmarks/baseline/jmh-baseline.json`
- `benchmarks/baseline/macro-baseline.json`

- Re-generate after a significant design change.

