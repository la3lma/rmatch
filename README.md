rmatch
======

## Repository Navigation

- Core library: [rmatch/](rmatch/)
- Tester and harness: [rmatch-tester/](rmatch-tester/)
- Benchmark platform: [benchmarking/framework/regex_bench_framework/](benchmarking/framework/regex_bench_framework/)
- Documentation index: [docs/README.md](docs/README.md)
- Planning backlog: [docs/plans/](docs/plans/)
- Papers: [docs/papers/](docs/papers/)

## Latest Performance Tests Running 10K Regular Expression Patterns on Google Compute Node

Comparable setup used for the snapshot below:

- Same hardware cohort: `e2-standard-8|x86_64` (GCP)
- Same pattern suite: `stable_patterns` (`10,000` patterns)
- Same corpus sizes: `1MB`, `10MB`, `100MB`
- Source: [benchmarking/framework/regex_bench_framework/reports/workload_all_live/cohort_workload_engine_matrix.csv](benchmarking/framework/regex_bench_framework/reports/workload_all_live/cohort_workload_engine_matrix.csv)

| Corpus | Winner | rmatch (ms) | re2j (ms) | java-native-naive (ms) | re2j vs winner | java-native-naive vs winner |
|---|---:|---:|---:|---:|---:|---:|
| 1MB | rmatch | 17,340.1 | 255,653.6 | 137,385.1 | 14.74x | 7.92x |
| 10MB | rmatch | 19,762.0 | - | 1,205,972.5 | - | 61.02x |
| 100MB | rmatch | 63,219.3 | 25,716,382.9 | - | 406.78x | - |

`-` means no successful completed run yet for that engine/workload combination.

### Comparable Plot 1: Runtime Scaling
![Comparable runtime scaling](charts/gcp_e2_10k_runtime_seconds.png)

### Comparable Plot 2: Relative Slowdown vs Winner
![Relative slowdown vs winner](charts/gcp_e2_10k_relative_x_vs_winner.png)

### Comparable Plot 3: Throughput Trend (MiB/s; 1 MiB = 1,048,576 bytes)
![Throughput trend](charts/gcp_e2_10k_throughput_mib_s.png)

More detail:

- Snapshot doc: [docs/benchmarking/LATEST_PERFORMANCE_TESTS_10K_REGEX_PATTERNS_GOOGLE_COMPUTE_NODE.md](docs/benchmarking/LATEST_PERFORMANCE_TESTS_10K_REGEX_PATTERNS_GOOGLE_COMPUTE_NODE.md)
- Interactive all-runs report: [benchmarking/framework/regex_bench_framework/reports/workload_all_live/workload_engine_comparison_all.html](benchmarking/framework/regex_bench_framework/reports/workload_all_live/workload_engine_comparison_all.html)
- Legacy front-page notes moved to:
  - [docs/benchmarking/LEGACY_FRONT_PAGE_NOTES.md](docs/benchmarking/LEGACY_FRONT_PAGE_NOTES.md)
