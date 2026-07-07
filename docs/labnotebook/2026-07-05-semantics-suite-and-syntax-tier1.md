# 2026-07-05 — Semantics suite + syntax Tier-1 program

**Branch:** `feature/semantics-suite-and-syntax-tier1`
**Status:** Complete, merged. TDD throughout; perf-gated per feature.

## Phase 0: the semantics suite (and what it immediately caught)

Two new suites now pin the engine's ground truth:

- `BasicMatchingSemanticsTest` — table-driven, hand-computed expectations;
  codifies THE SEMANTIC RULE: for every pattern and every start position,
  report the longest match starting there (all starts, overlapping included).
- `JavaRegexDifferentialTest` — seeded-random patterns (now including groups
  and counted quantifiers), 1,440 comparisons per run against a
  java.util.regex per-start-longest oracle (full-match, longest-first —
  immune to java's ordered-alternation quirks). Failures print minimal repros.

The differential found two ancient engine bugs on its FIRST run:

- **Negated sets were unsound by construction.** `[^a]` compiled as
  "match anything, also route 'a' to a FailNode and let the engine kill the
  match later". The failing flag was never honored on the first node (so
  `[^a]` matched `'a'`), and one failing path killed matches whose other NDFA
  paths were legal (`.+[^a]?` lost valid matches). Fix: negated sets are now
  real character classes (complement intervals). Bonus: the gate workload got
  FASTER (3.25s vs 3.80s) — correct is also cheaper here.
- **KB-5 — matches forgetting they were final.** A match that reached a final
  state, then extended while alive-but-non-final (e.g. `[^a]+[cb]` whose loop
  eats past the ender), was silently discarded at death. `MatchImpl` now
  remembers `lastFinalEnd` and commits with it. The first fix attempt updated
  the memory from a stale flag (off-by-one ends) — the differential caught
  that too, within seconds.

Also registered [issue #267](https://github.com/la3lma/rmatch/issues/267):
`^`/`$` throw `UnsupportedOperationException` despite years of README claims.
README corrected.

## The features (each: red tests → green → verify → checksums)

| feature | notes |
|---|---|
| `( )` and `(?: )` | Stack of AlternativesBuilders; groups are quantifiable atoms; nesting; loud errors for unbalanced/unsupported constructs. 12 tests. |
| Escapes + `\d \D \w \W \s \S` | The inverted escape-condition bug was fixed en route; classes are charset sugar, and uppercase forms use the same complement machinery as negated sets; escapes work inside sets. 17 tests. |
| `{m}` `{m,n}` `{m,}` | Replay expansion: parser tracks each atom's source span and re-parses it; `X{2,4}` ≡ `XXX?X?`; cap 1000. Works on chars, sets, classes, groups. 13 tests. |
| `(?i)` prefix (+ `(?s)` no-op) | Compile-time case folding of literals/sets/ranges; replay inherits the flag; prefix-only because pattern identity is the raw string. 11 tests. |

Descoped, documented in the roadmap: `\b`/`\B`, MULTILINE, non-DOTALL toggle —
all wait on the anchor machinery tracked in
[issue #267](https://github.com/la3lma/rmatch/issues/267).

## Receipts

- Full `verify` green after every feature; 1,440-comparison differential
  green with the generator covering all new syntax.
- Word-list cascade: checksums bit-identical in all cells at every step
  (word patterns use none of the new syntax — no change allowed, none seen).
- Timing: within noise at every step; the one suspicious final-cascade cell
  (0.81×) interleaved ×3 and resolved to drift (~1% median) — the third
  drift phantom today caught by the interleave protocol.
- Gate workload note: its 600 `(?i)`-prefixed patterns became MEANINGFUL with
  F4 (previously they could only match the literal text `"(?i)…"`, i.e.
  never). Match counts jumped 8.7M → 19.2M and scan time rose accordingly —
  an effective-workload change, not a regression. All gate baselines from
  before 2026-07-05 are semantically obsolete; re-baseline everything.

## Fallout / next

- KNOWN-BUGS: escape handling, negated sets, and final-state retention closed;
  the anchor issue remains open with a design note — it gates `\b`, MULTILINE,
  and honest README anchors.
- The adversarial battery grew again (gremlin doctrine): the differential
  generator IS an adversarial workload factory now.
- Syntax coverage yardstick (gitleaks/grok %) still to be wired up.
