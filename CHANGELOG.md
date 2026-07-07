# Changelog

## 1.9.2-SNAPSHOT - in development

### Highlights

- Added the first zero-width assertion machinery for line anchors `^` and `$`.
  They now compile as assertion edges in the automaton rather than throwing at
  compile time.
- Added a dedicated line-anchor semantics suite covering buffer boundaries,
  newline boundaries, whole-line matching, alternation, grouping, and mixed
  anchored/unanchored pattern sets.
- Kept assertion-aware matching on a separate path so ordinary pattern sets can
  continue using the existing no-context hot path.

### Still required before merge/release

- Run the full external `rmatch-perftest` branch-vs-main gate. Local smoke data
  is recorded under `docs/benchmark-receipts/issue-269-line-anchors`, but is not
  sufficient release evidence.
- Decide and document pure zero-width match semantics, for example `^$`.
- Continue the broader #267 work for word boundaries and flag-mode behavior.

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
