# 2026-07-05 — MultiMatcher partition-count sweep (agogo, 32-thread x86)

**Status:** Exploratory. No code change yet; informs a future partition heuristic.
**Hypothesis under test (rmz):** the optimal partition count is harder than
"1.5× cores"; cache-line saturation might explain large-corpus contention.

## Method

Coarse grid over partition counts {4, 8, 12, 16, 24, 32, 48, 64} on the
contention-sensitive cell (10k regexps × 6.8MB Wuthering Heights ×10), both
the pre-merge baseline and the de-boxed candidate jars, interleaved per
partition count. `MultiMatcher(p, …)` constructed directly via the
`multi:N` driver mode. One warmup + one measured iteration per point
(single samples — this box has known bimodal ~43s/~60s environmental modes,
so individual points carry ±20–45% uncertainty).

## Results (measured ms)

| partitions | baseline | candidate | regexps/partition |
|---|---|---|---|
| 4 | 108,743 | 88,569 | 2,500 |
| 8 | 67,692 | 63,090 | 1,250 |
| 12 | 55,861 | 58,679 | 833 |
| 16 | 52,090 | 48,437 | 625 |
| 24 | 60,991† | 40,272 | 417 |
| 32 | 40,937 | 51,041† | 313 |
| 48 | 42,523 | **37,586** | 208 |
| 64 | 68,208 | 53,269 | 156 |

† Probably a slow-mode-contaminated single sample.

## Findings

1. **Left side is algorithmic, not cache:** p4 is 2.4–2.9× slower than p48
   with zero oversubscription. Per-partition cost grows superlinearly in
   regexps-per-partition, so finer splitting wins even past core count.
2. **Right side shows real oversubscription cost:** p64 (2× cores) degrades
   both variants.
3. **Broad flat valley at 24–48**; the hardcoded 1.5×cores (=48 here) lands
   inside it. Defensible on this box, but by luck rather than design.
4. **Heuristic implication:** partition count should factor in regexp count
   (target roughly 200–600 regexps/partition, capped near core count), not
   cores alone. A 10k-pattern workload on 4 cores currently gets 6 partitions
   = 1,667 regexps each — deep in the bad zone.

## Next steps (open)

- Repetitions at p ∈ {24, 32, 48} to resolve the valley statistically.
- `perf stat -e LLC-load-misses,cache-misses` per point on agogo to test the
  cache-saturation story directly.
- Sweep a second axis: regexp count (1k/5k) to fit a regexps-per-partition
  model rather than a single-cell optimum.
- Related open issue: nondeterministic match counts on the synthetic suite
  (see 2026-07-04 entry addendum) — MultiMatcher is a shared suspect.

## Data

[`data/2026-07-05-partition-sweep/sweep-agogo.csv`](data/2026-07-05-partition-sweep/sweep-agogo.csv)
