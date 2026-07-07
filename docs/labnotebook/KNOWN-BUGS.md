# Known Bugs

Confirmed correctness bugs, kept here so they are not forgotten between
campaigns. Each entry stays until a fix lands with a regression test.

## KB-1: quantifiers bound to the whole preceding literal (was: `ab?` does not match a lone `a`)

**Status:** FIXED 2026-07-05 (`SurfaceRegexpParser.commitForQuantifier`).
Discovered 2026-07-05 while writing edge-case tests for the two-character
start-filter experiment; root-caused during the dedicated chase.

**Actual root cause — worse than the symptom:** the parser committed the whole
accumulated literal as ONE fragment before applying a quantifier, so the
quantifier bound to the entire literal: `ab?` compiled as `(ab)?` (matches ""
and "ab", never "a"), `error+` as `(error)+` (matches "errorerror", never
"errorr"), and `ab?c` as `(ab)?c` — making a lone "c" spuriously matchable.
Not an ε/terminal bug in the NDFA machinery after all: the ε-wiring per
fragment was correct; the fragments were wrong. Fix: split the last atom into
its own fragment before quantifying (`a(b?)` per standard semantics).

**Verification:** KB1OptionalSuffixLengthOneTest (5 cases, red→green);
`error+` now agrees exactly with java.util.regex; word-list benchmark
checksums bit-identical (no quantifiers → no change allowed, none observed);
gate workload counts changed as correct semantics require (spurious matches
gone, legitimate short matches added), timing unchanged.

**Symptom:** For the pattern `ab?` on input `"a ab"`, the engine reports only
the match at 2–3 (`ab`). The length-1 match at position 0 (`a`, with `b?`
matching empty) is silently missing. Correct regex semantics require both.

**Scope:** Pre-existing and engine-independent within rmatch — reproduced
identically on unmodified `main` with both the fastpath and legacy engines,
so it is NOT related to the start-filter work; likely in how quantifier
terminality is compiled or how a match that is final-but-continuable at
length 1 is committed. Plain single-char patterns (`I`) do fire correctly at
length 1, so the gap is specific to the quantified-suffix case.

**Pinned by:** `TwoCharStartFilterTest.quantifiedPatternsSurvive` asserts the
*current* (buggy) behaviour with a comment; when this bug is fixed that
expectation must gain `ab?@0-0`.

## KB-2: escape parsing throws on every `\x` (inverted condition)

**Status:** FIXED 2026-07-05 as part of syntax program F2 (escapes +
shorthand classes), test-first in EscapeSyntaxTest. Originally: Discovered 2026-07-05 during the KB-1 chase.
`SurfaceRegexpParser.parseQuotedChar` has `if (src.hasNext()) throw` — inverted:
it throws "Expected char after escape char" precisely when a next char EXISTS.
Any pattern containing an escape fails at add() time (loud, not silent).
Scheduled for the Tier-1 escapes work in the syntax program, test-first.

## Negated character sets were unsound by construction

**Status:** FIXED 2026-07-05 (CharSetBuilder complement ranges). Found by the
new differential suite within its first run. `[^a]` compiled as "match any
char, also route 'a' to a FailNode and let the engine kill the match later" —
but (a) the failing flag was never honored on the match's first node, so
`[^a]` matched `'a'`; and (b) one failing NDFA path killed the whole match
even when other legal paths survived, so `.+[^a]?` lost valid matches.
Negated sets now compile to real character classes (complement intervals over
the char domain). The FailNode mechanism is no longer used by charsets.

## KB-5: matches extending past their last final state were discarded

**Status:** FIXED 2026-07-05 (MatchImpl.lastFinalEnd). Found by the
differential suite. A match that reached a final state and then kept
extending while alive-but-non-final (e.g. `[^a]+[cb]` where the loop consumes
past the ender) was silently dropped when it died non-final. Matches now
remember their largest final end and commit with it. Ancient — affected the
eager engine identically.

## Anchors `^` and `$` throw UnsupportedOperationException

**Status:** OPEN. Tracked in
[issue #267](https://github.com/la3lma/rmatch/issues/267). The README used to
claim anchor support; the compiler throws "Not supported yet" for both.
Descoped from the current syntax program (needs real context-assertion
machinery); the README must stop claiming it, and \b/MULTILINE wait on the
same machinery.

**Plan (campaign):** Run a dedicated correctness campaign after the current optimization
campaign (rmz decision, 2026-07-05): differential fuzzing against
`java.util.regex` over the supported syntax subset (literals, `?`, `*`, `+`,
`.`, `|`, character classes, anchors) on random small inputs; triage every
mismatch. Consistency is table stakes for matchers.
