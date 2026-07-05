# 2026-07-05 — Nondeterministic match counts: root cause found (harness, not engine)

**Status:** Resolved. rmatch is deterministic; the bug was in the benchmark harness.

## Symptom

The stable-10K gate's correctness validator flagged **inconsistent match counts
between identical iterations** (~4.3% apart: 8,379,153 vs 8,753,203 on 1MB;
similar on 10MB). Observed on main and on the de-boxed branch alike. Word-list
workloads (Wuthering Heights driver) never showed it.

## Known failure mode checked first: "skidding"

An earlier version of this problem was caused by reading counts while
MultiMatcher worker threads were still running (nondeterministic thread
termination). Code review of `MultiMatcher.match()`: the `CountDownLatch` is
sound — each worker counts down only after its partition's `match()` returns,
`await()` establishes happens-before, so all match work completes and is
visible before `match()` returns to the caller. That half stays fixed.

(Robustness note, separate issue: a partition that *throws* never counts down,
so `match()` would hang forever rather than fail. Worth hardening someday.)

## Actual root cause: the other half of skidding

`RMatchBenchmark.CountingAction` in rmatch-perftest counted with a plain
`int count++`. Actions are invoked **concurrently from every MultiMatcher
partition thread**, so the non-atomic read-modify-write lost ~4% of
increments, differently each run. The safe counters elsewhere (AtomicLong in
the lab driver, `synchronized` in MatcherBenchmarker) are why only the
perftest harness ever showed the symptom.

Fix: `LongAdder` (rmatch-perftest `587e78a`).

## Verification

Rerun of the identical stable-10K probe after the fix:

| corpus | iter 0 | iter 1 |
|---|---|---|
| 1MB | 8,900,532 | 8,900,532 |
| 10MB | 89,006,757 | 89,006,757 |

Bit-identical. Corroboration: the correct count is *higher* than every racy
reading (lost updates undercount), and the 10MB count is ~10× the 1MB count
as expected for a repeated synthetic corpus.

## Lessons

- Consistency is table stakes for matchers — and for their harnesses. A racy
  counter in the measurement path masquerades as an engine correctness bug.
- Callbacks fire on engine worker threads; every Action that aggregates must
  be thread-safe. Consider documenting this contract on `Action.performMatch`.
- When a known failure mode exists (skidding), check it first — but check
  both halves: "still running" *and* "racing on the shared state".
