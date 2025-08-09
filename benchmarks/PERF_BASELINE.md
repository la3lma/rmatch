# Performance Baseline

Use a dedicated Linux host where possible. Pin CPU governor to `performance`.
JDK: Temurin 21.x. Run 2+ forks, 5–10s per measurement iteration.
Store canonical baselines:
- `benchmarks/baseline/jmh-baseline.json`
- `benchmarks/baseline/macro-baseline.json`
