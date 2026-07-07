# Design note: anchor and boundary-assertion machinery

**Status: ACTIVE for step 2** (2026-07-08). The `^` / `$` line-anchor sub-issue
has merged via [issue #269](https://github.com/la3lma/rmatch/issues/269). Word
boundaries `\b` / `\B` are being implemented on branch
`u/la3lma/codex/issue-270-word-boundaries`; see
[issue #270](https://github.com/la3lma/rmatch/issues/270). Input anchors and
flag-mode work remain future steps under
[issue #267](https://github.com/la3lma/rmatch/issues/267).

Covers: `^`, `$`, MULTILINE, `\b`, `\B`, and the groundwork a non-DOTALL `.`
toggle would share. See also
[issue #267](https://github.com/la3lma/rmatch/issues/267),
`docs/regex-syntax-roadmap.md`, and `docs/labnotebook/KNOWN-BUGS.md`.

## Problem statement

Today a DFA transition is a pure function δ(state, char), and finality is a
pure property of the reached state. Anchors and word boundaries make both
context-dependent: what character preceded, what character follows, and
where the buffer edges are — all without consuming input. The design
question is where that context lives without harming the caches that carry
the engine's performance (per-node `DFANode[128]` transition tables, cached
terminal sets, subset-construction cache, two-char start filter).

## Hard constraint learned from the negated-set bug

Assertions MUST be part of the transition/acceptance function itself —
never a post-hoc "kill the match afterwards" mechanism. The old
FailNode/failing-set approach was unsound precisely because one failing NDFA
path killed matches whose other paths were legal, and its flag was not even
honored at match creation. Its ghost must not return via `foo\b|foobar`.

## Design (adapted from RE2's empty-width-flags technique)

### 1. Assertion edges in the NDFA

`^ $ \b \B` compile to ε-edges labeled with an assertion: BOL, EOL, WB, NWB
(plus BOT/EOT for `\A`-style anchors later). An assertion edge may be
crossed during ε-closure only when its assertion holds at the current
position.

### 2. Context-classified transitions (start-side: `^`, left `\b`)

When consuming char i we know prev = text[i-1] (or BOF) and curr = text[i]
— which is everything a start-side assertion needs:

- BOL(i)  ⟺ prev ∈ {BOF, '\n'}
- WB(i)   ⟺ isWord(prev) ≠ isWord(curr)

Fold these into a context class, K ≤ 4 values, computed branchlessly per
position. Subset construction extends to δ(state, contextClass, char):
ε-closure under a KNOWN context is deterministic, so DFA node caching
still works; the transition table gains a small extra dimension.

### 3. Conditional finality via lookahead (end-side: `$`, right `\b`)

A match ending at i under `$` is final iff text[i+1] ∈ {'\n', EOF}. The
machinery already exists:

- `LookaheadBuffer.peek()` (built for the two-char start filter) supplies
  text[i+1] at step i; peek() == null is the EOT context.
- `MatchImpl.lastFinalEnd` already commits matches at a remembered earlier
  end.

Terminal sets per DFA node become a small array indexed by next-char class
(newline-or-EOF / word / other) instead of a single set — same caching
pattern as `getTerminalRegexpsCached()` today.

### 4. Pay-for-what-you-use (the efficiency keystone)

Only DFA nodes whose ε-frontier contains assertion edges become
context-sensitive. All other nodes keep exactly today's single
`DFANode[128]` table and single terminal set behind one predictable branch
(`isContextSensitive`). Therefore: a pattern set containing no anchors
compiles to bit-identical structures and must produce bit-identical
checksums at unchanged speed. This is a testable claim and will be tested
per lab protocol, not assumed.

## Efficiency assessment

- Non-anchor workloads: one flag check per match-set step + a few ops per
  position to track prev. Predicted noise-level on the word-list cascade;
  the experiment fails if checksums change or timing regresses beyond noise.
- Memory: ×K (≤4) transition tables only on assertion-adjacent nodes
  (~4KB vs ~1KB per such node). Negligible.
- Precedent: RE2's DFA threads empty-width flag bits through byte-at-a-time
  transitions at industrial throughput. Adaptation, not invention.

## Test strategy

- The java.util.regex differential oracle survives intact:
  `Matcher.region()` with `useTransparentBounds(true)` and
  `useAnchoringBounds(false)` gives per-start matching with TRUE context for
  `^ $ \b` — extend the generator with assertions at each campaign step.
- Adversarial battery additions: anchors inside alternations, groups and
  counted quantifiers; `foo\b|foobar`-style patterns aimed specifically at
  the old failing-path bug; assertion-dense pattern sets mixed with plain literals to
  verify pay-for-use isolation.

## Known hard corners (named before implementation)

1. **Subset-construction cache keys**: context must enter the key only for
   context-sensitive closures; contaminating the context-free cache would be
   subtle and wrong. Adversarial tests before code.
2. **Two-char start filter and Aho-Corasick prefilter**: start-context
   affects δ(start, c1); either add start-context to those caches or
   conservatively bypass the filters for context-sensitive patterns
   (measured, per protocol).
3. **Zero-width matches**: `"^$"` on an empty line forces the question; the
   engine cannot express end < start today. DECISION NEEDED (rmz) before
   implementation. Recommendation: descope pure zero-width patterns
   initially with a loud compile error, documented like the other honest
   descopes.

## Campaign shape (when taken off hold)

1. `^` / `$` — merged via [issue #269](https://github.com/la3lma/rmatch/issues/269);
   K=2 warm-up; README anchors become true again after merge and perf gate.
2. `\b` / `\B` — active in [issue #270](https://github.com/la3lma/rmatch/issues/270);
   start- and end-side word-boundary context over the existing assertion edges.
3. MULTILINE — a flag on the anchors.
4. (Shared groundwork then enables a non-DOTALL `.` toggle as a flag.)

Estimated effort: step 1 comparable to the lazy-materialization experiment;
step 3 roughly half that again. Full lab protocol at every step.
