# agogo benchmark receipts, 2026-07-07

These receipts were produced on a 32-logical-CPU AMD Ryzen 9 9950X3D machine
using Docker. The README efficiency runs used
`no.rmz:rmatch:1.9.1-SNAPSHOT`.

The README-facing large-corpus run is:

- `readme-efficiency-large/README_EFFICIENCY_BENCHMARK.md`
- `readme-efficiency-large/readme-efficiency-summary.csv`
- `readme-efficiency-large/readme-efficiency-scanning.svg`
- `readme-efficiency-large/benchmark_results.json`

That run used deterministic literal-token patterns over an 8 MiB corpus. Match
counts agreed across `java-native-naive`, `re2j`, and `rmatch` in every cell, so
it is suitable as public performance evidence for the README. It shows RE2J
ahead at 1k patterns, and rmatch ahead at 5k and 10k patterns.

The earlier README-shape 1 MiB run is:

- `readme-efficiency/README_EFFICIENCY_BENCHMARK.md`
- `readme-efficiency/readme-efficiency-summary.csv`
- `readme-efficiency/readme-efficiency-scanning.svg`
- `readme-efficiency/benchmark_results.json`

That run also had matching counts across engines, but the workload was too
small to show the intended scaling advantage clearly. Treat it as a useful
sanity receipt rather than the front-page chart.
