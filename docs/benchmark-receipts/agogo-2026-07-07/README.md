# agogo benchmark receipts, 2026-07-07

These receipts were produced on a 32-logical-CPU AMD Ryzen 9 9950X3D machine
using Docker and the current `no.rmz:rmatch:2.0-SNAPSHOT` source artifact.

The completed correctness-aligned sanity run is:

- `frontpage-10p-1mb-summary.csv`
- `frontpage-10p-1mb-summary.json`
- `frontpage-10p-1mb-raw.json`

That run used 10 generated stable patterns over a 1 MB corpus. Match counts
agreed across `java-native-naive`, `re2j`, and `rmatch`, but the workload is too
small to demonstrate rmatch's intended many-pattern advantage.

The larger diagnostic run is:

- `diagnostic-1000p-1mb-summary.csv`
- `diagnostic-1000p-1mb-summary.json`
- `diagnostic-1000p-1mb-raw.json`

That run used 1,000 generated stable patterns over a 1 MB corpus. rmatch had a
lower median scanning time than the Java and RE2J loops, but match counts did
not agree. Treat it as a benchmark-harness/workload finding, not as public
performance evidence.
