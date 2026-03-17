# RMatch Performance Analysis and Recommendations

I took a close look at the public repo structure, README, build files, and the main hot-path classes in the matcher core: `FastPathMatchEngine`, `MatchSetImpl`, `DFANodeImpl`, `NodeStorageImpl`, `StringBuffer`, `AhoCorasickPrefilter`, `LiteralPrefilter`, `CompressedDFAState`, and `StateSetBuffers`. One important caveat: the repo's README says the heavier benchmark orchestration and reporting now live outside the core repo in rmatch-perftest and rmatch-meta, so the ranking below is based on source inspection rather than running the full benchmark campaign.

## Executive Summary

My short version is: the library already shows strong performance-oriented thinking, but the code still pays a lot of overhead in object creation, synchronization, and general-purpose collection use. If I were prioritizing work, I would start with the first two items below.

## Priority Recommendations

### 1. Replace the "one Match per regexp per start position" model

This looks like the biggest likely win. `MatchSetImpl` explicitly calls part of its current construction "the most egregious bug," and when a start position is activated it builds a `HashSet` of matches and adds one match object per candidate regexp. That is exactly the sort of multiplicative fan-out that will hurt allocation rate, cache locality, and GC when pattern counts grow.

I would reframe the active work unit as something more compact, like:
- start index
- current DFA/NFA state
- compact set of candidate regexp IDs, ideally a bitset or primitive ID vector

Then only materialize per-regexp match objects when a regexp actually reaches a terminal/commit-worthy state. That change is harder than a local micro-optimization, but it attacks the most structural source of overhead I saw.

### 2. Remove synchronization and boxing from the inner loop

The custom `StringBuffer` synchronizes simple methods like `hasNext`, `getNext`, `getString`, and clone-related operations, and `getNext()` returns boxed `Character` rather than primitive `char`. `MatchSetImpl` also repeatedly synchronizes on matches and copies into `matchSnapshot`, while `FastPathMatchEngine.match()` itself is structured around local per-invocation data structures such as `HashSet<MatchSet>`. That pattern suggests the hot path is paying lock and object costs in code that otherwise looks mostly thread-confined per match run.

Concretely, I would:
- add a non-synchronized input path for the engine internals
- return `char` instead of `Character`
- make the hot `MatchSet` implementation explicitly single-threaded
- keep thread-safety only at the public API boundary if needed

This is the kind of work that often produces very visible wins before deeper algorithmic changes do.

### 3. Finish the fast path, or simplify it

`FastPathMatchEngine` grabs a thread-local `StateSetBuffers`, but the visible matching loop still uses ordinary `HashSet` structures, including a fresh `toRemove` set inside the loop. On the prefilter side, the code builds a full candidate list, then a `HashSet<Integer>`, then a `HashMap<Integer, Set<Regexp>>`, and only afterward converts to arrays and sorts them. `AhoCorasickPrefilter.scan()` also converts the whole input to `String` and accumulates results into an `ArrayList`. Meanwhile, `StateSetBuffers` already contains thread-local `BitSet` and `int[]` scratch storage.

That says to me: there is probably real headroom here, but only if the primitive buffers become the actual primary representation. Otherwise, the code is carrying optimization scaffolding without getting the full benefit.

So I would do one of two things:
- fully commit to primitive-state fast paths, or
- remove partial machinery that is not yet paying for its complexity

The worst place to be is halfway.

### 4. Specialize DFA caches for the actual workload

`DFANodeImpl` stores outgoing edges in a `ConcurrentHashMap<Character, DFANode>`, keeps first-character candidate sets in a `HashMap<Character, Set<Regexp>>`, computes those candidate sets in a synchronized method, and uses `parallelStream().anyMatch(...)` for one finality check.

Given the repo's intentionally reduced regex language and likely text/log scanning use cases, I would try a two-tier approach:
- a fixed ASCII array fast path for outgoing edges and start-character filtering
- a sparse fallback map for non-ASCII or uncommon cases
- regexp IDs/bitsets instead of `Set<Regexp>` in hot caches
- a plain loop instead of `parallelStream()` unless profiling proves otherwise

That would reduce hashing, boxing, and object chasing on exactly the hottest repeated operations.

### 5. Make the compressed DFA state representation canonical

`CompressedDFAState` is a strong idea: it stores sorted integer IDs, precomputes the hash, and its own class comment claims very large memory reductions versus the old `SortedSet<NDFANode>` representation. But `NodeStorageImpl` still constructs a compressed key and also keeps the original `SortedSet<NDFANode> -> DFANode` map alive, updating both representations together.

That means part of the older, heavier representation is still in the lookup and storage story. I would make the compressed form the canonical internal state representation and treat any reconstruction of `SortedSet` as compatibility/debug behavior only. That will likely help both memory footprint and lookup cost.

---

## What I think is good about the engineering

There is a lot to like here.

The repo is modular, the README is honest about the deliberately reduced regex surface, and the project clearly separates core library concerns from tester/harness concerns and from external performance/reporting repos. The build also already includes a solid toolchain: Maven wrapper, Checkstyle, Spotless, SpotBugs, Surefire, JUnit, and JMH dependencies. That is better engineering hygiene than many performance-driven projects have.

Also, the code shows clear performance awareness. `CompressedDFAState` is not accidental engineering; it is a deliberate attempt to attack memory layout and state representation, which is exactly the right instinct for this kind of engine.

## Where I would improve hygiene and style

My main critique is not "bad engineering." It is "too many optimization layers partially overlap."

The codebase looks like it is in transition from an older, object-heavy design toward a more compact and performance-oriented one. That is normal, but it also means complexity accumulates fast. `FastPathMatchEngine` reaching for `StateSetBuffers` without visibly using them is the clearest symptom.

The hygiene/style improvements I would make are:

1. **Separate the public contract from the hot implementation.**
   Decide explicitly which parts are thread-safe API surface and which parts are thread-confined engine internals. Right now that boundary looks blurry from the amount of synchronization in otherwise hot code.

2. **Tighten the static-analysis gate.** `FIXED (2026-03-17)`
   Parent POM now runs SpotBugs with `failOnError=true`, and the surfaced high-priority issue in `BloomFilterMatchEngine` was fixed.

3. **Put microbenchmarks next to hot classes.**
   Since the heavy benchmark campaign is intentionally external, I would still keep a small, always-runnable JMH suite near the core engine classes for things like buffer traversal, state transition, candidate filtering, and commit logic. The repo already has JMH in the build, and the README already frames the split between core library and external perf orchestration.

4. **Treat heuristic prefilter logic as a correctness risk zone.** `PARTIALLY FIXED (2026-03-17)`
   `LiteralPrefilter` contains heuristic scoring, including a `.contains(lowerLiteral)` common-word penalty. That may be fine, but it deserves aggressive tests and property-based checks because heuristic shortcuts in prefiltering can quietly become either false negatives or wasted work.
   Status update for the prefilter hardening plan:
   - `DONE (1)` Added prefilter ON/OFF invariance tests that assert identical match outputs for the same workload.
   - `DONE (2)` Added differential fuzzing tests (constrained to supported syntax) comparing prefilter ON vs OFF.
   - `DONE (5)` Added heuristic kill switches via system properties and conservative safety gating for hint activation.
   - `NEXT (3)` Expand property-based/fuzz coverage to broader generated corpora and larger scenario matrices.
   - `NEXT (4)` Add stronger correctness telemetry/reporting for prefilter decisions in benchmark runs.
   - `NEXT (6)` Add staged rollout defaults and documented guardrails for heuristic changes.

5. **Rename the custom StringBuffer.** `FIXED (2026-03-17)`
   `no.rmz.rmatch.utils.StringBuffer` has been renamed to `RegexStringBuffer` and usages were updated.

## My priority order

If this were my queue, I would do the work in this order:

1. eliminate per-regexp match object fan-out
2. remove synchronization/boxing from the hot path
3. fully integrate or simplify the fast-path/prefilter machinery
4. specialize DFA caches
5. make compressed state canonical

That ordering is my judgment call, but it is the order I would bet on before spending time on smaller tweaks.

If you want, I can turn this into a more concrete engineering plan with suggested refactor steps, benchmark hypotheses, and a "do this first / measure this / rollback criteria" checklist.
