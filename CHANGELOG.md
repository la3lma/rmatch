# Changelog

## 1.9.7-SNAPSHOT - in development

- Continue pre-2.0 stabilization after publishing `1.9.6`.

## 1.9.6 - pre-2.0 Maven Central release candidate

- Continue pre-2.0 cleanup after publishing `1.9.5`.
- Added `RMatch.newMatcher(int parallelism)` so applications can choose the
  number of pattern partitions and concurrent workers. The supported range is
  1 through 1024, leaving room for current high-end servers while retaining a
  guard against accidental unbounded platform-thread creation. Parallelism 1
  uses the single-engine matcher without a worker pool; the automatic matcher
  keeps its hardware heuristic and now observes the same upper bound.
- BREAKING (2.0 contract): matchers now have an explicit build-then-use
  lifecycle. The first `match()` permanently freezes pattern registration;
  later `add()` calls throw `IllegalStateException`. The experimental
  `remove()` methods have been removed from the public and internal matcher
  contracts. Applications with a changed rule set construct a replacement
  matcher instead of mutating compiled automata.
- Pattern/action registration now uses action object identity. Re-registering
  the same action instance for one pattern is idempotent, while distinct action
  instances remain distinct even when `equals()` considers them equal.
  Callback order is explicitly unspecified for both single and partitioned
  matchers.
- Added JaCoCo coverage behind a `-Pcoverage` Maven profile and a CI job that
  uploads library-module coverage to Codacy without blocking the main release
  gate if Codacy-side coverage initialization is not ready.

## 1.9.5 - pre-2.0 Maven Central release candidate

`1.9.5` is a sharper pre-2.0 contract candidate. It keeps the root
`no.rmz.rmatch` facade introduced in `1.9.4`, then pins several behaviors that
should not be left to accident: callback coordinate conventions, matcher
lifecycle, registration/concurrency rules, finite string-backed buffers, and
per-pattern flags.

### Public API and behavior

- BREAKING (2.0 contract): match callbacks now receive half-open
  `[start, end)` offsets, the same convention as `String.substring` and
  `Buffer.getString`. Matched text is `buffer.getString(start, end)`; the old
  inclusive-end convention and its `end + 1` idiom are gone. Migration: drop
  the `+ 1` when recovering text, add `- 1` anywhere an inclusive end offset
  was stored or compared.
- BREAKING (2.0 contract): `Buffer` is now pure content with three methods:
  `hasCharAt(long)`, `charAt(long)`, and `getString(long, long)` (default
  implementation provided). The cursor methods `getNext()`, `hasNext()`,
  `getCurrentPos()`, plus `clone()` and the `Comparable` contract are gone;
  engines keep their own cursors and scan a shared buffer instance from
  several threads, so implementations must tolerate concurrent readers.
  There is deliberately no total-length method, keeping bounded-window
  buffers over streaming input expressible later. This also removes the
  per-character synchronization and `Character` boxing the old cursor
  contract forced onto the string-backed buffer. Migration: custom buffer
  implementations shrink to positional lookups; callers that only used
  `RMatch.stringBuffer(...)` are unaffected.
- BREAKING (2.0 contract): buffer positions are now `long` throughout the
  public API. `Buffer.getString(long, long)` takes `long` offsets, and
  `Action.performMatch(Buffer, long, long)` receives `long` match offsets.
  This keeps custom buffer implementations larger than the `int` range
  expressible without another breaking change later. The built-in
  string-backed buffers remain limited by `String` and reject out-of-range
  arguments. Engine internals keep `int` positions; widening happens at the
  callback boundary. Migration: lambda actions are source-compatible as-is;
  explicit `Action` implementations change the parameter types to `long`,
  and Mockito verifications need `long` matchers (`eq(0L)`, `anyLong()`).
- BREAKING (2.0 contract): `Matcher` now extends `AutoCloseable`. The
  `shutdown()` method (which threw `InterruptedException`) is replaced by an
  idempotent `close()` that throws nothing, so matchers work in
  try-with-resources. Partitioned matchers force-terminate their worker pool
  if it does not stop within a grace period, and restore the interrupt flag
  if interrupted while waiting.
- Added the flags-capable registration API that fixes the 2.0 shape for
  per-pattern options: `Matcher.add(String, Set<PatternFlag>, Action)` and the
  matching `remove` overload, with `PatternFlag.CASE_INSENSITIVE` as the first
  flag (equivalent to the `(?i)` prefix). Future matching modes join as new
  enum constants, which is binary compatible.
- Specified and enforced the matcher behavioral contracts for 2.0: action
  exceptions abort the scan and propagate out of `match()` (partitioned
  matchers finish the surviving partitions, then rethrow the first failure
  unwrapped); registration and matching must not run concurrently on one
  instance; and after `close()` every method except `close()` throws
  `IllegalStateException`. The lifecycle rule is now enforced with an
  explicit closed flag in both implementations and pinned by a dedicated
  contract test suite.
- Clarified that the public buffer facade is deliberately finite and
  string-backed today: true streaming inputs must be materialized, while lazy
  file-backed or bounded-lookback stream buffers remain possible future work if
  users need them.
- Added explicit `RMatch.stringBuffer(...)` convenience methods for strings,
  character sequences, paths, readers, and input streams.
- Removed the shorter `RMatch.buffer(String)` alias before 2.0 so the public
  helper name states the materialization behavior directly.

### Correctness, release hygiene, and CI

- Fixed a prefilter correctness bug where optional-suffix patterns such as
  `ab?`, `ab*`, and `ab?c` could lose their short-form matches when the literal
  prefilter was forced on. The prefilter now refuses prefix hints when the last
  hinted character can disappear, and the regression is pinned by a dedicated
  forced-prefilter test suite.
- Made the prefilter activation threshold read dynamically when configuring an
  engine instead of freezing one JVM-global static value at class-load time.
  This removes test-order sensitivity when tests deliberately override
  `rmatch.prefilter.threshold`.
- Release polish: the dormant `EdgeInvisibilityEnsurance` bug-manifestation
  test now runs (renamed to match the test pattern; the historical bug it
  pins is confirmed fixed), published Javadocs build under strict doclint
  with warnings failing the build, and the repository gained
  `CONTRIBUTING.md`, `SECURITY.md`, and README notes on pattern identity in
  callbacks and parse-error behavior.
- Shipped-jar hygiene: removed the experimental Bloom-filter match engine and
  its `SimpleBloomFilter` (reachable only via `rmatch.engine=bloom`, never
  used in production); `FastCounters` diagnostics now go through
  `java.util.logging` instead of stdout; the partition-count heuristic lives
  in one place in `MatcherFactory`; and the `MultiMatcher` partition guard
  message states the actual limit.
- The main CI gate now also runs on pushes to `main`, and the README carries
  its status badge.
- CI: the main gate now runs the full `verify` test suite (both modules,
  Spotless/Checkstyle/SpotBugs) on Java 21 and 25, keeps a fast smoke job,
  and adds a modest performance canary that asserts completion and an exact
  match count over a fixed 10k-pattern Wuthering Heights workload while
  publishing timing as information only. The Codacy workflow now triggers on
  `main` instead of the nonexistent `master`.

## 1.9.4 - pre-2.0 Maven Central release candidate

`1.9.4` removes the last external compile-scope dependency and tightens the
documented public API around the root `no.rmz.rmatch` facade.

- Replaced the external `org.ahocorasick:ahocorasick` literal-prefilter
  dependency with a small internal Aho-Corasick implementation. The original
  library served rmatch well and deserves credit; the replacement is about
  keeping the pre-2.0 JPMS and Maven Central boundary clean as the project
  moves forward.
- Removed the `requires ahocorasick` JPMS dependency and the compile-scope
  Maven dependency. Local Central-profile verification no longer emits the
  filename-derived automatic-module warning.
- Verified the replacement on agogo against the Aho-backed baseline using the
  stable 10K-pattern Docker gate. Median scan-time ratios were 1.015 on 1MB and
  1.004 on 10MB with identical match counts, so the replacement reaches parity
  under the current release gate.
- Added `RMatch` as the recommended public facade and moved `Action`, `Buffer`,
  `Matcher`, and `RegexpParserException` into the root package.
- Changed the JPMS descriptor to export only `no.rmz.rmatch`.
- Restricted the published Javadocs to the supported facade package so
  implementation types such as compiler internals, node-management interfaces,
  and utility classes are no longer presented as public documentation.

## 1.9.3 - pre-2.0 Maven Central release candidate

`1.9.3` tightens the public API surface before the eventual `2.0.0` line. It
keeps the same matcher behavior as the post-`1.9.2` mainline, but removes a
deprecated buffer method, narrows several implementation-only classes, and adds
the first JPMS module descriptor for `no.rmz.rmatch`.

Published to Maven Central on 2026-07-08 as `no.rmz:rmatch:1.9.3`, with the
Git tag `rmatch-1.9.3` pointing at release commit `e65a540a`.

### Release hygiene

- Removed the deprecated `Buffer.getCurrentRestString()` method from the public
  buffer contract. Internal users now either request explicit substrings or
  collect remaining text by advancing a cloned cursor.
- Added an explicit JPMS module descriptor for `no.rmz.rmatch` and began
  tightening the module boundary before 2.0.
- Stopped presenting `MatcherImpl` and `MultiMatcher` as public construction
  APIs. `MatcherFactory` is now the stable entry point, with
  `newSingleMatcher()` available for deterministic single-engine use.
- Reduced accidental public surface in compiler and implementation packages:
  compiler helper nodes/builders and most concrete engine implementation
  classes are now package-private.
- Removed the internal domination-heap accessor from the `Regexp` interface so
  that match-domination bookkeeping no longer leaks through the public contract.

### Validation

- Full tester-inclusive verification passed after the API-closure changes:
  `./mvnw -B -pl rmatch-tester -am verify -Dspotbugs.skip=true`.
- Agogo Docker performance gate passed against the published `1.9.2` baseline
  on a 32-logical-CPU AMD Ryzen 9 9950X3D machine. Median `scanning_ns` ratios
  were 0.867 on the 1MB corpus and 1.018 on the 10MB corpus, both within the
  1.10 release gate.

### Deliberate limitations and future work

- The JPMS descriptor is useful for consumers that want an explicit module
  name, but the compile path still emits an automatic-module warning for the
  Aho-Corasick dependency. That dependency-boundary decision is accepted for
  this pre-2.0 release candidate and remains tracked before `2.0.0`.

## 1.9.2 - pre-2.0 Maven Central release candidate

`1.9.2` adds the first context-sensitive zero-width assertions to the
published `1.9.x` line. It keeps the same pre-2.0 positioning as `1.9.1`, but
now supports line anchors and ASCII word boundaries.

Published to Maven Central on 2026-07-08 as `no.rmz:rmatch:1.9.2`, with the
Git tag `rmatch-1.9.2` pointing at release commit `48151212`.

### Highlights

- Added the first zero-width assertion machinery for line anchors `^` and `$`.
  They now compile as assertion edges in the automaton rather than throwing at
  compile time.
- Added word-boundary assertions `\b` and `\B`, aligned with rmatch's ASCII
  `\w` shorthand semantics: letters, digits, and underscore are word
  characters.
- Added a dedicated line-anchor semantics suite covering buffer boundaries,
  newline boundaries, whole-line matching, alternation, grouping, and mixed
  anchored/unanchored pattern sets.
- Added a dedicated word-boundary semantics suite covering whole-word,
  prefix/suffix boundary, non-boundary, newline, underscore, and digit cases.
- Kept assertion-aware matching on a separate path so ordinary pattern sets can
  continue using the existing no-context hot path.

### Validation

- External `rmatch-perftest` branch-vs-main gate passed on agogo for
  `\b`/`\B`: same generated data, correctness pass, identical match counts,
  scanning ratios 0.911 on 1MB and 1.018 on 10MB.

### Deliberate limitations and future work

- Pure zero-width match reporting, for example `^$`, is still outside the
  public support contract because callbacks currently report consumed spans.
- The broader #267 work continues for input anchors and flag-mode behavior.

## 1.9.1 - pre-2.0 Maven Central release candidate

`1.9.1` tightens the Maven Central release package after the first `1.9.x`
publication pass. It keeps the same pre-2.0 positioning, but improves the
public dependency surface, Java baseline, documentation, and performance
evidence.

Published to Maven Central on 2026-07-07 as `no.rmz:rmatch:1.9.1`, with the
Git tag `rmatch-1.9.1` pointing at release commit `5ee41857`.

### Highlights

- Lowered the public Java baseline to Java 21 by compiling with
  `--release 21`, while keeping newer JDKs usable for development and testing.
- Removed Guava and JetBrains annotations from the public dependency surface.
  The `no.rmz:rmatch` compile/runtime graph now contains only Aho-Corasick.
- Expanded end-user-facing Javadocs for matcher lifecycle, callback offset
  semantics, buffer behavior, factory behavior, and parse-error meaning.
- Reworked the README so the main reason to try rmatch is explicit:
  many-pattern scanning performance, not merely API convenience.
- Added README performance receipts from a Docker run on a 32-logical-CPU AMD
  Ryzen 9 9950X3D machine. The public chart uses deterministic literal-token
  patterns over an 8 MiB corpus and only reports cells where rmatch, RE2J, and
  `java.util.regex` agree on match counts.
- Added release checklist items for dependency hygiene, README performance
  evidence guardrails, Javadoc publication through javadoc.io, and
  post-release Maven Central verification.

### Deliberate limitations

- `1.9.1` is still not a full Java/PCRE regex compatibility claim.
- Line anchors `^` and `$`, word boundaries `\b`/`\B`, lookaround,
  backreferences, scoped flags, and capture-group semantics remain outside the
  supported `1.9.x` surface.
- `rmatch-tester` remains local project tooling and is not part of the Maven
  Central release lane.

## 1.9.0 - pre-2.0 Maven Central release candidate

`1.9.0` is the first release-candidate line aimed at Maven Central. The engine
functionality is now strong enough to publish for external use, while the
documentation, syntax surface, release process, and compatibility promises are
still being tightened toward a spotless `2.0.0`.

### Highlights

- Added the fast-path matching work that makes `rmatch` competitive with RE2J
  on cache-friendly workloads and faster on larger corpora in current benchmark
  probes.
- Added syntax Tier 1 coverage: grouping, non-capturing grouping, escapes,
  shorthand character classes, counted quantifiers, and prefix `(?i)`.
- Fixed several long-standing correctness bugs found by the new semantics
  suite, including quantifier binding, negated character classes, and matches
  that extended past their last final state.
- Added release signing with the public key
  `9017955845408C9B4422B5DE55D9C01E75B1E582`.

### Deliberate limitations

- `1.9.0` is not a claim of full Java/PCRE regex compatibility.
- Line anchors `^` and `$`, word boundaries `\b`/`\B`, lookaround,
  backreferences, scoped flags, and capture-group semantics are not part of the
  supported 1.9.0 surface.
- The `rmatch-tester` module remains local project tooling and is not part of
  the Maven Central release lane.

### Release intent

Use `1.9.x` to shake out packaging, docs, examples, and syntax-contract issues.
Use `1.99.x` as the final API/syntax preview if needed. Reserve `2.0.0` for
the stable, fully documented release line.
