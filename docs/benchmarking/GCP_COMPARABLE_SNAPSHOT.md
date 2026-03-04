# Comparable GCP Snapshot

- Cohort: `e2-standard-8|x86_64`
- Pattern count: `10000`
- Source matrix: `benchmarking/framework/regex_bench_framework/reports/workload_all_live/cohort_workload_engine_matrix.csv`

| Corpus | Winner | rmatch (ms) | re2j (ms) | java-native-naive (ms) | re2j vs winner | java-native-naive vs winner |
|---|---:|---:|---:|---:|---:|---:|
| 1MB | rmatch | 17,340.1 | 255,653.6 | 137,385.1 | 14.74x | 7.92x |
| 10MB | rmatch | 19,762.0 | - | 1,205,972.5 | - | 61.02x |
| 100MB | rmatch | 63,219.3 | 25,716,382.9 | - | 406.78x | - |

Missing entries indicate no successful completed run for that engine/workload yet.

![Comparable runtime](../../charts/gcp_e2_10k_runtime_seconds.png)

![Relative slowdown vs winner](../../charts/gcp_e2_10k_relative_x_vs_winner.png)

![Throughput trend](../../charts/gcp_e2_10k_throughput_mib_s.png)

