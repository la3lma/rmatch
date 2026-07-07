# Regex syntax roadmap: rmatch vs java.util.regex

Written 2026-07-05, at rmz's request, as the engine reached parity/dominance
with the fastest JVM alternatives (see labnotebook 2026-07-05 entries). The
surface language was deliberately kept small while the engine matured. This is
a candidate/feasibility study of every notable `java.util.regex` feature that
rmatch lacks, assessed against the existing regexp → NDFA (Thompson-style)
compilation pipeline.

**Currently supported:** literals, concatenation, alternation `|`, quantifiers
`?` `*` `+`, counted quantifiers `{m}`/`{m,n}`/`{m,}`, grouping
`( )`/`(?: )`, any-char `.`, character classes `[abc]`, ranges `[a-z]`,
negation `[^abc]`, common escapes and shorthand classes, and prefix-level
`(?i)`. Line anchors `^`/`$` were not supported in the published 1.9.0/1.9.1
line; the first implementation branch is tracked as
[issue #269](https://github.com/la3lma/rmatch/issues/269).

## Candidate features

| Candidate | Tier | Feasibility study |
|---|---|---|
| **Grouping `( … )` and non-capturing `(?: … )`** | 1 — do first | Pure parser/precedence work; the NDFA composition operators (concatenate, alternate, quantify) already exist and simply need to accept a parenthesized subexpression as their operand. Since rmatch has no capture semantics, `(…)` and `(?:…)` are identical for us. Zero engine risk, zero scan-path cost, and it is the single highest-leverage gap: it unlocks composition like `(foo\|bar)+baz` which today cannot be expressed at all. |
| **Shorthand classes `\d \w \s \D \W \S` + escapes `\n \t \\ \. \xNN \uNNNN`** | 1 | Lexer-level sugar that expands to the existing char-class NDFA nodes; negated forms map to the existing negated-class support. No new engine states or semantics. Highest real-world coverage per line of code: log-mining, grok, and secrets-scanning rule sets use these in nearly every pattern. |
| **Counted quantifiers `{m}` `{m,n}` `{m,}`** | 1 | Compile by unrolling into existing operators: `X{2,4}` → `XX(X(X)?)?`, `X{2,}` → `XXX*`. NDFA size grows linearly with the bound, so impose an explicit expansion cap (java effectively caps too). Requires grouping to land first for sane parsing. Very common in real rules (`[0-9]{4}`, `\w{8,64}`), so this plus the two rows above probably covers the majority of wild patterns. |
| **POSIX/Unicode classes `\p{Alpha}` etc.** | 1 (ASCII subset) | ASCII-range versions are plain char-class sugar — trivial. Full Unicode property classes are the same mechanism with much larger class sets; feasible but interacts with the ASCII-oriented fast paths (128-wide tables), so ship the ASCII subset first and benchmark the Unicode variant separately. |
| **Case-insensitive matching (API flag, later `(?i)`)** | 2 | Compilable by case-folding every char/class node at compile time — a pattern-level transform, no engine change. An `add(pattern, flags, action)` API overload is trivial and covers the dominant use case; scoped inline `(?i)…(?-i)` is parser bookkeeping on top. Scan-path cost: slightly larger char classes, nothing structural. |
| **Word boundary `\b` / `\B`** | 2 | Zero-width context assertion, same family as the existing `^`/`$` anchor machinery — which lives in the historically bug-prone ε/terminal zone (KB-1's neighborhood). Technically well-understood: condition transitions on the character-class of the previous/next input character. Deliberately sequenced AFTER the correctness campaign hardens that zone with a strong test suite. Very high value for word-list workloads: eliminates substring false positives ("cat" in "category") without any performance trick. |
| **Input anchors `\A \z \Z`** | 2 | Straightforward variants of the existing anchor handling (buffer-start/buffer-end instead of line-start/line-end). Small, but same ε-zone caveat as `\b`: land with tests from the correctness campaign. |
| **`DOTALL` / `MULTILINE` modes** | 2 | Mode switches that alter which char set `.` expands to and whether `^`/`$` bind to line or buffer boundaries — both are compile-time choices over existing node types. Needs the flags-capable `add()` API from the case-insensitivity row. |
| **Lazy quantifiers `*?` `+?` `??`** | 2 | Curiously, for an automata engine this is not an NDFA change at all: laziness only changes WHICH match is reported (shortest-per-start instead of longest-per-start). Maps to "commit at first terminal hit instead of last" in the match-selection layer. The work is a semantics decision and its interaction with domination rules, not compilation. Prototype behind a per-pattern flag once the semantics spec from the correctness campaign exists. |
| **Capturing groups / submatch extraction** | 3 — deliberate future | Requires tagged transitions (TNFA, Laurikari) so the automaton records sub-span positions during the single pass — a known, published technique used by production engines, but real engine machinery: new state representation, tag conflict resolution, and a group-aware Action API variant. Feasible without abandoning one-pass matching; schedule as its own experiment series with the full lab protocol, since it touches the hot path. |
| **Lookahead `(?=) (?!)`** | 3 | Regular in the formal sense — implementable by product construction (running the assertion automaton in parallel) — but state-space cost is multiplicative and the engine impact invasive. Defer until a concrete use case justifies it; many practical lookaheads can be rewritten as alternations or handled by the Action layer. |
| **Lookbehind `(?<=) (?<!)`** | 3 | Same story as lookahead but worse, because the assertion runs against already-consumed input; would need either reverse automata or bounded buffering. Defer indefinitely; revisit only with a compelling workload. |
| **Backreferences `\1`** | Never | Non-regular — provably outside what any finite automaton can match, and thus incompatible with one-pass DFA matching by construction. RE2/RE2J draw exactly the same line, in good company. Document as a permanent exclusion. |
| **Atomic groups `(?>…)`, possessive `*+` `++`** | Never | These exist to control backtracking; rmatch has no backtracking to control. Accept-and-ignore (parse and treat as their plain equivalents) could be offered for compatibility, but implementing their Java semantics is meaningless here. |

## Status update 2026-07-05 (evening)

Implemented, test-first, perf-gated (see labnotebook entry): grouping
`( )`/`(?: )`, escapes + shorthand classes, counted
quantifiers `{m}`/`{m,n}`/`{m,}` (replay expansion, cap 1000), and `(?i)`
prefix case-insensitivity (`(?s)` accepted as no-op — `.` is DOTALL-always).
Deliberately descoped pending anchor machinery after 1.9.1:
`\b`/`\B`, MULTILINE, non-DOTALL toggle, and pure zero-width match reporting.
The first active anchor sub-issue is
[`^`/`$` line anchors](https://github.com/la3lma/rmatch/issues/269), split out
from [issue #267](https://github.com/la3lma/rmatch/issues/267). Design is
pinned in [docs/design/anchor-machinery.md](design/anchor-machinery.md).
Along the way the new semantics suite exposed and fixed two ancient engine
bugs: negated sets were unsound, and matches could forget earlier final states.

## Suggested order of attack

1. Grouping `( … )` / `(?: … )` — unlocks composition, parser-only.
2. `\d \w \s` + escapes — biggest wild-pattern coverage per line of code.
3. `{m,n}` with an explicit expansion bound.
4. Case-insensitivity as an `add(pattern, flags, action)` API flag.
5. `\b` — after the KB-1 correctness campaign hardens the ε/terminal zone.

## Measuring progress

Adopt an external compatibility yardstick: the fraction of a well-known public
rule set (gitleaks default rules, or the grok pattern library) that compiles
and matches correctly after each tier lands. That number doubles as the honest
"syntax coverage" line for future positioning material, alongside the
cache-residency performance story.

Perf guard: every syntax addition goes through the standard lab protocol — the
compile path may change freely, but scan-path receipts (cascade, identical
checksums where semantics are unchanged) must stay clean.
