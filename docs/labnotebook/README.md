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
| 2026-07-05 | [Match-count determinism](2026-07-05-match-count-determinism.md) | **Resolved** — engine deterministic; racy `int++` in perftest harness (fixed, `587e78a`) |

## Known bugs

Confirmed correctness bugs are tracked in [KNOWN-BUGS.md](KNOWN-BUGS.md) until
fixed with a regression test. Current: KB-1 — `ab?` misses its length-1 match.

## Method notes

- Benchmark driver lives with each entry's data (`data/<entry>/CascadeBench.java`),
  compiled *outside* the repo against snapshot jars, so baseline and candidate run
  identical driver bytecode.
- Correctness gate: total match count must be identical between variants in every
  cell, or the experiment is void.
- **Beware sequential A/B on a workstation**: this machine showed ±20% swings on
  identical code at long runtimes (thermal drift). For any suspicious cell, re-test
  with interleaved runs (B,C,B,C — fresh JVM each) before believing a regression
  or an improvement.
