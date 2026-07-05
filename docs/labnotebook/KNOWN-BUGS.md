# Known Bugs

Confirmed correctness bugs, kept here so they are not forgotten between
campaigns. Each entry stays until a fix lands with a regression test.

## KB-1: `ab?` does not match a lone `a` (length-1 match via optional suffix)

**Status:** OPEN. Discovered 2026-07-05 while writing edge-case tests for the
two-character start-filter experiment.

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

**Plan:** Run a dedicated correctness campaign after the current optimization
campaign (rmz decision, 2026-07-05): differential fuzzing against
`java.util.regex` over the supported syntax subset (literals, `?`, `*`, `+`,
`.`, `|`, character classes, anchors) on random small inputs; triage every
mismatch. Consistency is table stakes for matchers.
