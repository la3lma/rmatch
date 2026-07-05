# 2026-07-05 — Lazy Match materialization: the death of the O(l·m) bug

**Branch:** `experiment/lazy-match-materialization`
(commits `76788c9`, `65cf926`, `9fff218` + roadmap/notebook commits)
**Status:** Accepted and merged. All receipts in.

## The ceremony

For well over a decade, `MatchSetImpl` carried this comment:

> *"XXX This lines represents the most egregious bug in the whole regexp
> package, since it incurs a cost in both runtime and used memory directly
> proportional to the number of expressions (m) the matcher matches for.
> For a text that is l characters long, this in turns adds a factor O(l*m)
> to the resource use of the algorithm. Clearly not logarithmic in the
> number of expressions, and thus a showstopper."*

Its author was right on every count. Measured this morning on unmodified
main: 10,000 patterns × 2.7MB took 163 seconds, superlinear in m, exactly as
the comment predicted. Measured tonight, same cell, same machine: **2.9
seconds** — and the engine now beats the fastest realistic JVM alternative
(an RE2J per-pattern loop) on every corpus that doesn't fit in cache.

The comment was removed without ceremony, as instructed. This entry is the
ceremony. It was a good comment: it stated the problem precisely, admitted
fault honestly, and waited fourteen years for the receipts. Rest in peace.

## Hypothesis

Match objects were created eagerly for every candidate regexp at every start
position (~m/26 per word start), nearly all dying by the second character:
~1,600 allocations + registrations + abandons per real match at 10k patterns.
Since DFA node regexp sets shrink monotonically along any path, the alive set
needs no per-regexp state at all — a Match need only exist once a regexp
reaches a terminal state.

## Mechanism

`MatchSetImpl` tracks *(start, current DFA node, candidates)*. Matches
materialize on first terminal contact; per-character cost per match set drops
from O(alive candidates) to O(materialized), usually zero.

Three invariants had to be restored explicitly (see The gremlins):
create-once per (match set, regexp) via a `spent` set; exact death rule
(dead ⟺ nothing materialized ∧ no unconsumed candidate alive); failing-node
bars apply to unmaterialized candidates too.

## The gremlins (and why the battery grew)

The word-list cascade — ~60 cells, bit-identical checksums — hid three
serious defects. All were exposed within minutes by ONE structurally
different workload (the stable-10K gate data: zero-length-capable patterns
like `[^0-9]*`, wildcard chains, heavy duplicates), found by jstack sampling
rather than theorizing:

1. **Re-materialization storm.** A match removed while its regexp stayed
   alive+terminal was recreated every character. Restored the eager
   implementation's create-once invariant (`spent` set).
2. **Immortal match sets.** With no exhaustion signal, match sets rode
   near-immortal DFA paths; the active set grew without bound — quadratic
   matching. Restored eager lifetimes exactly: dead when nothing materialized
   and no unconsumed candidate is alive (early-exit keeps healthy sets O(1)).
3. **Missing failing-node bar** for unmaterialized candidates — the only
   true output bug: 885,406 matches vs baseline's 849,345. Barred failed
   candidates from later materialization; count parity restored exactly.

Doctrine (rmz): *provoke the gremlins so they can be killed with fire* —
the receipts battery must keep growing structurally diverse adversarial
workloads. The gate workload is now a permanent battery member.

## Receipts

### Mac cascade (vs two-char main, median ms, checksums identical)

| impl | nRegex | corpus | main | lazy | speedup |
|---|---|---|---|---|---|
| single | 1k | 0.7MB | 217 | 139 | 1.56× |
| single | 5k | 2.7MB | 7,560 | 1,245 | 6.07× |
| single | 10k | 0.7MB | 6,758 | 1,709 | 3.95× |
| single | 10k | 2.7MB | 20,829 | 2,922 | 7.13× |
| factory | 10k | 0.7MB | 2,010 | 906 | 2.22× |
| factory | 10k | 2.7MB | 8,986 | 1,804 | 4.98× |

### Multi-MB confirmation (vs two-char main)

| impl | nRegex | corpus | main | lazy | speedup |
|---|---|---|---|---|---|
| single | 5k | 13.5MB | 33,616 | 4,064 | 8.27× |
| single | 10k | 6.8MB | 51,876 | 5,066 | **10.24×** |
| factory | 10k | 6.8MB | 19,886 | 4,534 | 4.39× |
| factory | 10k | 13.5MB | 38,106 | 8,598 | 4.43× |

### stable-10K gate (adversarial patterns; vs pre-two-char baseline)

1MB: **0.980** · 10MB: **0.532** — PASS both; correctness validator: pass,
zero issues, deterministic counts. (The workload that exposed the gremlins,
now won outright.)

### RE2J head-to-head (agogo, 32-thread x86, 10k patterns, factory)

| corpus | RE2J loop | rmatch | verdict |
|---|---|---|---|
| 0.7MB | 402 | 451 | RE2J by a nose (cache-resident) |
| 2.7MB | 1,621 | **1,271** | rmatch 1.28× |
| 6.8MB | 3,826 | **2,641** | rmatch 1.45× |

The cache-residency story (key for positioning): per-pattern loops make m
passes; while the corpus is cache-resident those passes are nearly free and
literal-prescan engines shine. Beyond cache, every pass pays memory
bandwidth; rmatch reads the corpus once. Small benchmarks systematically
flatter loop engines — always benchmark beyond LLC size.

### Cumulative, one day of work (10k × 2.7MB, single, Mac)

163s (morning main) → 143s (de-box) → 20.8s (two-char) → **2.9s (lazy)**
≈ **56×**, with bit-identical output at every step.

### Honest costs

- Single engine is ~28% slower than two-char main on the adversarial
  zero-length-heavy workload specifically (factory is 13% faster there).
  Traded knowingly for 4–10× everywhere else.
- The correctness fixes cost a few points vs the broken first version
  (RE2J margin 1.50/1.55× → 1.28/1.45×). Correct beats fast-but-wrong.

## Data

[`data/2026-07-05-lazy-match-materialization/`](data/2026-07-05-lazy-match-materialization/)
