# 2.0 pre-RC regression gate

This gate compared the published Maven Central release `no.rmz:rmatch:1.9.6`
with commit `19d4d30adce740344a89939cda726e6dc4d3b7e5`, built as
`1.9.7-SNAPSHOT`. Both artifacts ran in separate Java 21 Docker images on a
16-core, 32-thread AMD Ryzen 9 9950X3D host.

Each cell used the same deterministic scenario, worker-thread count, three
warm-up scans, and nine measured scans. Throughput is whole-task Mbit/s.

| Scenario | Threads | 1.9.6 | Candidate | Change |
|---|---:|---:|---:|---:|
| Diverse literals, 5,000, 8 MiB | 4 | 422.1 | 417.7 | -1.06% |
| Diverse literals, 10,000, 8 MiB | 4 | 265.9 | 267.1 | +0.45% |
| Mixed regex, 5,000, 8 MiB | 4 | 494.9 | 504.8 | +2.00% |
| Mixed regex, 10,000, 8 MiB | 4 | 438.2 | 425.4 | -2.92% |
| Diverse literals, 5,000, 50 MiB | 2 | 692.4 | 758.7 | +9.58% |
| Diverse literals, 10,000, 50 MiB | 1 | 569.3 | 561.9 | -1.29% |
| Mixed regex, 5,000, 50 MiB | 1 | 858.6 | 871.1 | +1.46% |
| Mixed regex, 10,000, 50 MiB | 1 | 688.6 | 706.0 | +2.53% |

All 16 receipts pass exact match-count validation. The largest retained
slowdown is 2.92%, inside the agreed 3% pre-release limit. The largest speedup
is 9.58%. This gate therefore passes without hiding the near-threshold 10,000
mixed-regex, 8 MiB result.

The adjacent JSON files are the unmodified benchmark receipts. They record
engine version, container image, host metadata, input hashes, timing samples,
thread count, and expected and observed matches.

## Consumer compatibility on the same host

The candidate also passed `make release-consumer-smoke` on the same host. The
Docker harness installed the candidate into an isolated Maven repository and
then compiled and ran four independent consumers under Java 21:

- Maven dependency on the class path
- Gradle dependency on the class path
- direct `javac` and `java -cp`
- direct `javac` and `java` as a named JPMS module requiring `no.rmz.rmatch`

Every consumer reported exactly two expected matches. The repeatable harness
is `scripts/verify-consumers-docker.sh`.

