# Lab Notebook

Experiment log for rmatch performance work. One entry per experiment.

Ground rule (hard-earned): **receipts before belief.** Every optimization is a
hypothesis until it has measurably won on a cascade of increasingly larger
regexp-count × corpus-size combinations, with identical match counts to `main`
in every cell. Ideas that "obviously" help usually don't; the current code has
survived many attempted optimizations.

## Entries

| Date | Experiment | Verdict |
|------|-----------|---------|
| 2026-07-04 | [De-box/de-hash the per-character hot path](2026-07-04-debox-ascii-hot-loop.md) | **Win, merged** — Mac + agogo cascades, stable-10K gate 0.915/0.918 |
| 2026-07-05 | [MultiMatcher partition-count sweep](2026-07-05-partition-sweep.md) | Exploratory — U-curve; heuristic should factor regexp count, not just cores |
| 2026-07-05 | [Two-char start filtering](2026-07-05-two-char-start-filter.md) | **Win, merged** — 2.2–8× vs main; ~7× vs java loop; RE2J loop still ~2× ahead on literal/cache-warm |
| 2026-07-05 | [Lazy Match materialization](2026-07-05-lazy-match-materialization.md) | **Win, merged** — O(l·m) bug dead; 4–10×; beats RE2J loop beyond cache; 3 gremlins killed |
| 2026-07-05 | [KB-1: quantifier binding](2026-07-05-kb1-quantifier-binding.md) | **Fixed** — ab? ≡ (ab)? parser bug; star didn't loop; now agrees with java.util.regex |
| 2026-07-05 | [Semantics suite + syntax Tier-1](2026-07-05-semantics-suite-and-syntax-tier1.md) | **Complete, merged** — groups, escapes+classes, {m,n}, (?i); differential suite killed KB-2/4/5 |
| 2026-07-05 | [Match-count determinism](2026-07-05-match-count-determinism.md) | **Resolved** — engine deterministic; racy `int++` in perftest harness (fixed, `587e78a`) |
| 2026-07-07 | [Line anchors `^` and `$`](2026-07-07-line-anchors.md) | **Merged** — tests pass; agogo external perf gate green |
| 2026-07-08 | [Word boundaries `\b` and `\B`](2026-07-08-word-boundaries.md) | **Implemented** — tests pass; agogo gate green at 0.911/1.018 |

## Known bugs

Confirmed correctness bugs are tracked in [KNOWN-BUGS.md](KNOWN-BUGS.md) until
fixed with a regression test. The historical entries remain useful as design
warnings after they are resolved.

## Method notes

- Benchmark driver lives with each entry's data (`data/<entry>/CascadeBench.java`),
  compiled *outside* the repo against snapshot jars, so baseline and candidate run
  identical driver bytecode.
- Correctness gate: total match count must be identical between variants in every
  cell, or the experiment is void.
- **Provoke the gremlins so they can be killed with fire** (rmz, 2026-07-05):
  the standard word-list cascade is necessary but nowhere near sufficient. The
  lazy-materialization experiment passed ~60 word-list cells with bit-identical
  output while hiding three serious defects — a re-materialization storm, a
  quadratic active-set blowup, and an actual output-correctness bug — all
  exposed within minutes by ONE structurally different workload (zero-length-
  capable patterns, wildcard chains, duplicates). The receipts battery must
  keep growing more and more diverse adversarial workloads: zero-length
  matchers, wildcard loops, deeply overlapping/nested patterns, pathological
  duplicates, anchor-heavy sets, single-character floods. Every workload family
  that CAN provoke a distinct failure mode should be in the battery.
- **Beware sequential A/B on a workstation**: this machine showed ±20% swings on
  identical code at long runtimes (thermal drift). For any suspicious cell, re-test
  with interleaved runs (B,C,B,C — fresh JVM each) before believing a regression
  or an improvement.
