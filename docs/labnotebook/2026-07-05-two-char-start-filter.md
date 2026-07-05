# 2026-07-05 — Two-character start-candidate filtering

**Branch:** `experiment/two-char-start-filter` (commit `9877f88`)
**Status:** Accepted and merged (rmz decision). Multi-MB confirmation and
stable-10K gate were still in flight at merge time; results are appended below
as they land — if either contradicts, the merge is to be revisited.

> Historical note: rmz had previously attempted this idea without success.
> This attempt survived; the difference is likely the exact-DFA formulation of
> the filter (see Correctness) rather than any heuristic prefix test.

## Hypothesis

At 10k word-like patterns, ~m/26 speculative `Match` objects are created at
every word-start position and nearly all die on the second character.
Filtering start candidates by the first TWO characters should remove ~an order
of magnitude of speculative match creation, at the cost of one lookahead and a
cached table lookup.

## Mechanism (exact, not heuristic)

A regexp r stays a candidate at position i with characters (c1, c2) iff:

- r ∈ regexps(δ(δ(start, c1), c2)) — alive in the DFA after both chars — **or**
- r is terminal at δ(start, c1) — has a complete length-1 match on c1.

Both are cached DFA properties, so the filter cannot change match semantics.
The second disjunct is essential: dropping it silently loses all length-1
matches (a plausible failure mode for earlier attempts). Cached per (c1, c2)
in `FastPathMatchEngine`; invalidated on pattern-set changes.
`RegexStringBuffer` now implements `LookaheadBuffer.peek()`; buffers that
cannot peek fall back to the one-character filter (also at end-of-buffer and
for non-ASCII).

## Correctness receipts

- Full `verify` green; 5 targeted edge-case tests (length-1 matches,
  last-position matches, wildcard second char, quantified `?`,
  fastpath-vs-legacy differential).
- Match counts bit-identical to main in every benchmark cell (Mac cascade 11
  cells + agogo grid), including 1,714,000 at 10k × 6.8MB.
- RE2J independently reproduces rmatch's match count exactly (171,400 @ 10k ×
  0.7MB) — external confirmation of the counting semantics.
- Discovered along the way (differential vs unmodified main): KB-1 — `ab?`
  misses its length-1 match. Pre-existing, both engines; NOT caused by this
  change. See KNOWN-BUGS.md.

## Performance receipts

### Mac cascade (median ms; main-post-debox vs branch; counts identical)

| impl | nRegex | corpus | main | twochar | speedup |
|---|---|---|---|---|---|
| single | 100 | 0.7MB | 40 | 37 | 1.08× |
| single | 1k | 0.7MB | 588 | 257 | 2.29× |
| single | 1k | 2.7MB | 1,924 | 526 | 3.66× |
| single | 5k | 0.7MB | 16,005 | 2,777 | 5.76× |
| single | 5k | 2.7MB | 57,906 | 7,806 | 7.42× |
| single | 10k | 0.7MB | 35,417 | 6,806 | 5.20× |
| single | 10k | 2.7MB | 142,440 | 20,748 | 6.87× |
| factory | 1k | 0.7MB | 314 | 140 | 2.24× |
| factory | 5k | 0.7MB | 6,959 | 1,115 | 6.24× |
| factory | 10k | 0.7MB | 19,084 | 2,382 | **8.01×** |
| factory | 10k | 2.7MB | 63,602 | 9,314 | 6.83× |

Speedup grows with pattern count — the O(l·m) signature. The candidate ran
second in sequence (the position thermal drift penalizes), and the effect is
10–20× larger than the machine's known drift band.

### agogo grid + cross-engine (32-thread x86, factory path, 10k patterns)

| engine | 0.7MB | 1.35MB | 2.7MB | 6.8MB |
|---|---|---|---|---|
| RE2J per-pattern loop | 403 | 820 | 1,600 | 3,800 |
| **rmatch two-char** | 890 | 1,500 | 3,700 | 6,556 |
| rmatch main | 4,200 | 7,900 | 15,200 | 38,000 |
| java.util.regex loop | 4,900 | 9,600 | 18,600 | 46,700 |

Charts: `data/2026-07-05-two-char-start-filter/crossengine_*.png`.

Honest cross-engine reading:

- Two-char lifts rmatch from naive-loop parity to ~7× better than looping
  `java.util.regex` at 10k patterns.
- A hand-rolled RE2J per-pattern loop still wins ~1.7–2.2× on THIS workload —
  pure literal words on a cache-warm corpus, i.e. RE2J's best case (its
  literal prescan makes it ~10k optimized substring searches). Gap narrows
  slowly with corpus size. Beating it fair and square is the goal of the next
  experiment (lazy Match materialization).

## Appended results

- Multi-MB confirmation (6.8/13.5MB, Mac): PENDING at merge time.
- stable-10K gate: PENDING at merge time.

## Data

[`data/2026-07-05-two-char-start-filter/`](data/2026-07-05-two-char-start-filter/)
