# Copilot Instructions for rmatch

## Project overview
You are assisting on **rmatch**, a Java library for matching many regular expressions **simultaneously** in a single pass over input. Core goals: throughput, predictable latency, and low allocation. Favor automata-based approaches (e.g., combined NFA/DFA, Aho–Corasick-style constructions) and careful data structures over backtracking engines. The public API should be small, stable, and easy to use from plain Java.

**Always read** `README.md` and `pom.xml` to infer versions and dependencies before answering or generating code. If something is unclear, propose options with trade-offs.

## Coding standards
- **Language & tooling:** Use the Java version declared in `pom.xml`. If unsure, parse it and state assumptions before proceeding. Use Maven conventions for sources, tests, and shading if applicable.
- **Style:** Prefer clear, allocation-light code. Avoid hidden allocations in hot loops (boxing, streams in tight paths, regex backtracking helpers). Prefer `final` for fields and locals when helpful.
- **Data structures:** Use compact arrays/bitsets/IntLists over generic collections on hot paths. Prefer contiguous memory layouts and branch-predictable code.
- **Concurrency:** All public entry points must document thread-safety. If an object isn’t thread-safe, provide a builder/factory for per-thread instances or immutable compiled automata with thread-safe `match()` calls.
- **Errors:** No checked exceptions in hot paths. Validate inputs at construction/compile time; fail fast with clear messages.
- **Logging:** Avoid logging in inner loops. Expose optional counters/metrics instead.

## API design
- Keep the API minimal: “compile patterns” → “run match/scan” → “report matches (pattern id, start, end)”.
- Accept many patterns at once (IDs or tags); allow incremental adds only if it doesn’t inflate runtime or complicate determinization excessively.
- Provide deterministic iteration order for reported matches (e.g., by start offset, then pattern id).
- Do not leak internal automata types in the public API.
- Include small examples in Javadoc for each public type.

## Performance constraints
- Target single-pass scanning: input traversed once per compiled automaton set.
- Optimize for large pattern sets (100–10k). Avoid O(n·m) behavior where n = input length, m = patterns.
- Hot code paths must avoid allocation; reuse buffers; consider primitive arrays.
- Include **JMH** benchmarks for new hot methods; show baseline vs. change.
- If a change worsens throughput or p99 latency by >3% in microbenchmarks, explain why and how it is mitigated.

## Testing
- Unit tests for: overlapping matches, anchored vs. unanchored, multiline, unicode classes, zero-length matches, catastrophic cases, and very large pattern sets.
- Property tests (if used): random pattern/input generation to check invariants (e.g., same results pre/post refactor).
- Regression corpus: include minimal repro inputs for past bugs (especially state explosions or missed matches).
- Ensure tests run fast; large corpora go behind a profile (e.g., `-Pslow`).

## Benchmarks (JMH)
- Add benchmarks under `src/jmh/java` with small/medium/large pattern suites and representative texts.
- Report: throughput (MB/s), matches/sec, allocations/op, and (if available) p95/p99 lat via JMH profilers.
- Keep benchmark data sets deterministic and checked into repo or synthesized reproducibly.

## Documentation
- Keep `README.md` usage examples in sync with public API.
- Each new public method gets Javadoc with complexity notes and examples.
- Add a short “Performance Tips” section (buffering, threading, repeated scans of different inputs).

## Security & licensing
- License is **Apache-2.0**; keep headers and NOTICE current. Avoid GPL/LGPL dependencies unless optional.
- Avoid unbounded memory growth from user-supplied patterns; validate or cap pattern features (e.g., huge counted repeats).

## Commits and PRs
- Commit messages: present-tense imperative; include “Why” and “Impact” sections for perf-sensitive changes.
- PR checklist: tests added, JMH baseline posted (if hot path), docs updated, API compatibility considered.
- Prefer small, reviewable PRs; separate refactors from behavior changes.

## How to reason about algorithm choices
- Prefer combined automata or AC-style multi-pattern search for exact substrings; for full regex sets, consider Thompson NFA with bit-parallelization or determinization with care for state explosion (use sparse transitions, equivalence classes).
- For Unicode: document supported classes and normalization assumptions. Avoid hidden normalization; if needed, make it explicit.

## When generating code or answers
- First, **summarize** how you inferred versions, constraints, and whether code touches hot paths.
- If asked for new features, propose the smallest API surface and show a tiny example plus tests.
- If touching hot paths, generate a matching JMH benchmark and mention expected effects.
- If you must choose between simplicity and speed, offer both versions and explain the trade-offs.

## Examples the team likes (generate similar)
- A `Compiler` that takes `(List<PatternSpec>) -> CompiledMatcher`.
- A `CompiledMatcher.scan(ByteBuffer|CharSequence)` returning an iterator or callback on matches with `(patternId, start, end)`.
- Builder flags: `unicode`, `caseFold`, `anchored`, `maxStates`, `dfaCacheSize`.

## Things to avoid
- Backtracking regex engines on the hot path.
- Hidden global state; static mutability; non-deterministic match ordering.
- Large object graphs per pattern; prefer shared tables.

