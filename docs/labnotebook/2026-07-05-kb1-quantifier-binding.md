# 2026-07-05 — KB-1 chase: quantifiers bound to the whole literal

**Branch:** `bugfix/kb1-optional-suffix-length1` (commit `fd6d1d4`)
**Status:** Fixed, test-first, perf-neutral. Merged.

## The chase

Registered symptom (KB-1): `ab?` does not match a lone `a`. Pre-registered
hypothesis: ε/terminal wiring for trailing quantifiers — historically the
bug-prone zone ("epsilon chasing was surprisingly hard to get right", rmz).

The red tests (written first, five cases) immediately showed the disease was
bigger than the symptom:

- `ab?` on `"a"` → no match; `ab*` on `"a"` → no match
- `ab*` on `"abb"` → only `@0-1`: **the star didn't loop**
- `ab?c` on `"abc ac"` → matched a **lone `c`**, twice, and missed `"ac"`

Root cause — in the *parser*, not the ε-machinery: `SurfaceRegexpParser`
committed the whole accumulated literal as one fragment before applying a
quantifier, so quantifiers bound to the entire literal: `ab?` ≡ `(ab)?`,
`error+` ≡ `(error)+`, `ab?c` ≡ `(ab)?c` (which legitimately matches a lone
`c` — explaining the "spurious" matches). The per-fragment NDFA ε-wiring was
correct all along; it was being handed the wrong fragments. The hypothesis
was reasonable and wrong — the tests didn't care, which is the point of
writing them first.

Fix: `commitForQuantifier()` splits the trailing atom into its own fragment
before the quantifier attaches — `ab?` ≡ `a(b?)`. One small function.

## Receipts

- `KB1OptionalSuffixLengthOneTest`: 5 red → 5 green. Full `verify` green
  (the only other change: `TwoCharStartFilterTest` gained the `ab?@0-0`
  expectation its own comment had prophesied).
- **External oracle**: `error+` on `"errorr xerror"` now produces exactly
  java.util.regex's answer (`@0-5`, `@8-12`); before: truncated `@0-4`.
- **Word-list cascade**: checksums bit-identical in all 11 cells (word
  patterns contain no quantifiers — no change allowed, none observed).
  Timing within noise; the one suspicious cell (0.80×) was interleaved
  4 rounds and resolved to drift (+3.5% median, one outlier, no mechanism).
- **Gate workload** (quantifier-rich): timing unchanged (3.8s vs 4.1s);
  match counts changed as correct semantics require — spurious
  following-atom matches gone, legitimate short/long matches added. The
  count change is the fix working, and resets the gate's reference counts.

## Fallout

- KB-1 closed in KNOWN-BUGS.md.
- KB-2 registered: `parseQuotedChar` has an inverted condition
  (`if (src.hasNext()) throw`) — every escape sequence fails at add() time.
  Loud, not silent. Scheduled for the Tier-1 escapes work, test-first.
- The pre-KB-1-fix match counts embedded in old benchmark data (e.g. gate
  reference counts 8,900,532) are counts of the *buggy* semantics; future
  A/Bs must re-baseline.

## Data

Cascade CSVs: [`data/2026-07-05-kb1/`](data/2026-07-05-kb1/)
