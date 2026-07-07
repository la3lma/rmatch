# agogo benchmark receipts, 2026-07-07

These receipts were produced on a 32-logical-CPU AMD Ryzen 9 9950X3D machine
using Docker.

The README-facing large-corpus run is:

- `readme-efficiency-large/README_EFFICIENCY_BENCHMARK.md`
- `readme-efficiency-large/readme-efficiency-summary.csv`
- `readme-efficiency-large/readme-efficiency-scanning.svg`
- `readme-efficiency-large/benchmark_results.json`

That run used `no.rmz:rmatch:1.9.1-SNAPSHOT` with deterministic literal-token
patterns over an 8 MiB corpus. Match counts agreed across `java-native-naive`,
`re2j`, and `rmatch` in every cell, so it is suitable as public performance
evidence for the README. It shows RE2J ahead at 1k patterns, and rmatch ahead at
5k and 10k patterns.

The earlier README-shape 1 MiB run is:

- `readme-efficiency/README_EFFICIENCY_BENCHMARK.md`
- `readme-efficiency/readme-efficiency-summary.csv`
- `readme-efficiency/readme-efficiency-scanning.svg`
- `readme-efficiency/benchmark_results.json`

That run also had matching counts across engines, but the workload was too
small to show the intended scaling advantage clearly. Treat it as a useful
sanity receipt rather than the front-page chart.

The completed 2.0-SNAPSHOT correctness-aligned sanity run is:

- `frontpage-10p-1mb-summary.csv`
- `frontpage-10p-1mb-summary.json`
- `frontpage-10p-1mb-raw.json`

That run used 10 generated stable patterns over a 1 MB corpus. Match counts
agreed across `java-native-naive`, `re2j`, and `rmatch`, but the workload is too
small to demonstrate rmatch's intended many-pattern advantage.

The larger 2.0-SNAPSHOT diagnostic run is:

- `diagnostic-1000p-1mb-summary.csv`
- `diagnostic-1000p-1mb-summary.json`
- `diagnostic-1000p-1mb-raw.json`

That run used 1,000 generated stable patterns over a 1 MB corpus. rmatch had a
lower median scanning time than the Java and RE2J loops, but match counts did
not agree. Treat it as a benchmark-harness/workload finding, not as public
performance evidence.
