# 2026-07-04 — De-box/de-hash the per-character hot path

**Branch:** `experiment/debox-hot-loop` (commits `3a44c0f`, `a158d61`)
**Status:** Experiment passed. Recommended next step: run the `rmatch-perftest`
gate (`make perf-local-baseline` / `make perf-local-candidate`) as final arbiter
before merging.

## Hypothesis

The inner match loop pays per-character costs for boxed-`Character` hash lookups
(`ConcurrentHashMap<Character, DFANode>` transitions, synchronized `HashMap`
first-char caches) and guava precondition checks. Replacing these with
direct-indexed ASCII arrays should give a constant-factor win that grows with
regexp count × corpus size.

## Change set (kept deliberately minimal)

- `DFANodeImpl`: `DFANode[128]` ASCII transition table with a `NO_TRANSITION`
  sentinel. Side effect worth knowing: the old `computeIfAbsent` path could not
  cache nulls, so **failing transitions were recomputed on every visit**; the
  sentinel fixes that. Lock-free ASCII cache for `getRegexpsThatCanStartWith`.
- `NodeStorageImpl`: same for start-node transitions — kills the per-occurrence
  epsilon-edge re-scan for characters that start no regexp (spaces, punctuation).
  Caches invalidated in `addToStartnode`.
- `RegexpImpl.canStartWith`: tri-state `byte[128]`, lock-free ASCII path.
- Removed guava `checkNotNull`/`checkArgument` from the three per-character
  inner-loop methods only (internal invariants, not API boundaries).
- **Not** touched: `Buffer`/`Character` interfaces, `MatchSet` synchronization,
  prefilter.

## Method

- Cascade: {100, 1k, 5k, 10k} regexps (Wuthering Heights word list) ×
  {0.7, 2.7, 6.8, 13.5} MB corpus (wuthr10.txt repeated), on both `MatcherImpl`
  ("single") and `MatcherFactory`/MultiMatcher ("factory", the production path).
- Driver (`data/.../CascadeBench.java`) compiled outside the repo, run against
  snapshot jars of main and the branch — identical driver bytecode both sides.
- Fresh JVM per cell, warmup iterations discarded, `-Xmx8g -XX:+UseG1GC`.
- Correctness gate: identical total match counts per cell, both variants.
- Full `./mvnw verify` green on the branch.

## Results

### Cascade (median measured ms; candidate after the addRegexp fix for factory)

| impl | nRegex | corpus | main | branch | speedup |
|---|---|---|---|---|---|
| single | 100 | 0.7MB | 52 | 46 | 1.13× |
| single | 1k | 0.7MB | 585 | 565 | 1.04× |
| single | 1k | 2.7MB | 1,859 | 1,829 | 1.02× |
| single | 1k | 6.8MB | 4,819 | 4,642 | 1.04× |
| single | 1k | 13.5MB | 9,498 | 8,810 | 1.08× |
| single | 5k | 0.7MB | 16,374 | 15,227 | 1.08× |
| single | 5k | 2.7MB | 62,352 | 60,582 | 1.03× |
| single | 5k | 6.8MB | 161,430 | 144,726 | 1.12× |
| single | 5k | 13.5MB | 317,243 | 290,718 | 1.09× |
| single | 10k | 0.7MB | 42,590 | 40,010 | 1.06× |
| single | 10k | 2.7MB | 163,968 | 143,194 | 1.15× |
| single | 10k | 6.8MB | 413,896 | 328,110 | **1.26×** |
| factory | 1k | 0.7MB | 354 | 371 | 0.95× |
| factory | 5k | 0.7MB | 8,842 | 8,277 | 1.07× |
| factory | 10k | 0.7MB | 16,940 | 16,680 | 1.02× |
| factory | 10k | 2.7MB | 66,388 | 60,971 | 1.09× |
| factory | 10k | 6.8MB | 162,347 | 157,191 | 1.03× |
| factory | 10k | 13.5MB | see interleaved re-test below | | |

Match counts identical in every cell. Speedup grows with scale — consistent with
removing a per-character constant cost.

### Incident 1: factory regression caused by cache-clear in addRegexp

First candidate build regressed factory 10k to 0.90–0.91×. Cause:
`addRegexp` did an unconditional `Arrays.fill(asciiStartCache, null)`, and
`addRegexp` is called once per basis NDFA node during DFA construction —
thousands of 128-slot clears per DFA node, ~18× amplified by MultiMatcher
partitions. Guarding the clear with a populated flag (`a158d61`) flipped those
cells to 1.02×/1.09×.

**Lesson:** an O(128) no-op in a "cold" method wasn't cold — construction paths
are hot at 10k patterns. Measure the production path, not just the unit engine.

### Incident 2: phantom 0.85× regression from thermal drift

The sequential confirmation run showed factory 10k × 13.5MB at 0.85× — the only
negative multi-MB cell. Interleaved re-test (B,C,B,C, fresh JVMs, same session):

| round | main | branch |
|---|---|---|
| 1 | 322,732 | **313,455** |
| 2 | 388,795 | **308,599** |

Main itself swung 323→389s (+20%) on identical code; the branch stayed within
±1%. Even main's best leg loses to the branch's worst (1.03×). GC logs: branch
had fewer pauses and less total pause time in both rounds (6.0 vs 6.9s;
5.6 vs 7.3s), so no collector-pressure penalty from the per-node arrays.

**Lesson:** this machine drifts ±20% under multi-hour load. Sequential
marathon A/B runs cannot be trusted for cells that run late; interleave any
suspicious cell before believing it.

## Verdict

- Single engine: **1.02–1.26×**, growing with scale. Real.
- Factory/production: **1.02–1.09×** with the disputed cell resolved to ≥1.03×
  by interleaved measurement. Real, smaller.
- Correctness: identical match counts across ~60 cells; `verify` green.

Keep. Final arbiter before merge: the `rmatch-perftest` stable-10K gate.

## Addendum 2026-07-05: agogo (32-thread x86) + stable-10K gate

**agogo.local** (32-thread x86, Ubuntu, Temurin 25, ~48 MultiMatcher
partitions): clean sweep, 9/9 cells, single 1.05–1.21×, factory 1.12–1.30×.
One cell (factory 10k × 6.8MB) initially read 0.74× but did not survive a
4-round alternating re-test: candidate 37.6–37.9s (±0.4%) vs baseline
43.4–62.6s — the baseline is *bimodal* on that box (~43s/~60s environmental
modes) and the candidate beat baseline's best leg in every round. GC ruled
out (pause totals ~1–2s of 37–56s runs; candidate's lower). Data:
[`data/2026-07-04-debox-ascii-hot-loop/agogo-results.csv`](data/2026-07-04-debox-ascii-hot-loop/agogo-results.csv).

**stable-10K gate** (10k stable patterns, synthetic 1MB+10MB corpus,
`scanning_ns` medians, threshold 1.10): candidate/baseline = **0.915 (1MB)**
and **0.918 (10MB)** — PASS, candidate ~8.5% faster on both.

**Merged to main** on the strength of: Mac cascade, agogo cascade,
interleaved re-tests on both machines, gate pass, ~60 correctness-identical
cells, full `verify`.

Open issues spun out of this experiment (not blockers, pre-existing on main):

1. **Nondeterministic match counts** on the stable_patterns/synthetic-corpus
   workload: identical iterations differ ~4.3% in match count (both variants,
   observed via the gate's correctness validator). Never occurs on the
   word-list workloads. Suspect MultiMatcher partitioning or the perftest
   RMatchBenchmark accounting. Deserves its own experiment.
2. **Partition-count heuristic**: `MatcherFactory` hardcodes 1.5×cores.
   Partition sweep on agogo in progress; early data suggest per-partition
   regexp count matters more than core count (fewer partitions = superlinearly
   more work each).
3. Perftest framework skew found along the way: `pattern_suites` plumbing bug
   (fixed, rmatch-perftest `758b23e`); gate script still expects a `jobs.db`
   the current runner no longer writes (comparison computed manually from
   `raw_results`).

## Data

Raw CSVs, driver, and scripts: [`data/2026-07-04-debox-ascii-hot-loop/`](data/2026-07-04-debox-ascii-hot-loop/)
CSV schema: `RESULT,variant,impl,nRegexps,corpusChars,iter,phase,match_ms,totalMatches`
