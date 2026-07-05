# Regex syntax roadmap: rmatch vs java.util.regex

Written 2026-07-05, at rmz's request, as the engine reached parity/dominance
with the fastest JVM alternatives (see labnotebook 2026-07-05 entries). The
surface language was deliberately kept small while the engine matured; this
document maps what is missing relative to `java.util.regex` and classifies
each gap by whether it fits the existing regexp → NDFA (Thompson-style)
compilation, i.e. whether it is "front-end syntax at relatively low cost".

## Currently supported

Literals, concatenation, alternation `|`, quantifiers `?` `*` `+` (on the
previous atom), any-char `.`, line anchors `^` `$`, character classes
`[abc]`, ranges `[a-z]`, negation `[^abc]`.

## Tier 1 — pure front-end, NDFA machinery unchanged (low cost, high value)

| feature | java.util.regex | notes |
|---|---|---|
| Grouping `( … )` | yes | Parser/precedence only; the NDFA composition (concat/alt/quantify) already exists. Unlocks `(foo|bar)+baz`. The single highest-leverage gap. |
| Non-capturing `(?: … )` | yes | Same as grouping for us — we have no capture semantics to distinguish from. |
| Shorthand classes `\d \w \s \D \W \S` | yes | Sugar for existing char-class nodes. Ubiquitous in real rule sets (log mining, secrets scanning). |
| Escapes `\n \t \r \f \\ \. \xNN \uNNNN` | yes | Lexer-level only. |
| Counted quantifiers `{m}` `{m,n}` `{m,}` | yes | Expand by unrolling: `X{2,4}` → `XX(X(X)?)?`. NDFA size linear in the bound; cap the bound (e.g. 1000) as Java effectively does. Very common (`[0-9]{4}`). |
| POSIX/ASCII classes `\p{Alpha}` etc. (ASCII subset) | yes | Char-class sugar. Full Unicode properties later (bigger class sets, same mechanism). |

## Tier 2 — compilable, small semantic machinery (medium cost)

| feature | java.util.regex | notes |
|---|---|---|
| Case-insensitive matching (API flag first, `(?i)` later) | yes | Compile char/class nodes to case-folded pairs. Per-pattern API flag is trivial; scoped inline flags are parser work. |
| Word boundary `\b` (and `\B`) | yes | Zero-width context assertion — same family as the existing `^`/`$` anchor handling (the historically tricky ε/terminal zone; do AFTER the KB-1 campaign hardens it). Huge value for word-list workloads: kills substring false positives. |
| Input anchors `\A \z \Z` | yes | Variants of existing anchor machinery. |
| `DOTALL` / `MULTILINE` modes | yes | Semantics switches on existing `.`/anchor nodes. |
| Lazy quantifiers `*?` `+?` `??` | yes | Not an NDFA change at all for us: they alter WHICH match is reported (shortest vs longest per start). Could map to "commit at first terminal hit instead of last". Needs a semantics decision more than code. |

## Tier 3 — possible but expensive (defer; revisit deliberately)

| feature | java.util.regex | notes |
|---|---|---|
| Capturing groups / submatch extraction | yes | Needs tagged transitions (TNFA, Laurikari) — real machinery, known technique, meaningful engine work. The Action API would grow a group-aware variant. |
| Lookahead `(?=) (?!)` | yes | Regular in theory (product construction) but costly and invasive. |
| Lookbehind `(?<=) (?<!)` | yes | Same, worse. |

## Never (non-regular or meaningless here)

| feature | why not |
|---|---|
| Backreferences `\1` | Non-regular; incompatible with the one-pass DFA model. This is the same line RE2/RE2J draws. |
| Atomic groups, possessive quantifiers | Backtracking-control concepts; no backtracking exists to control. |

## Suggested order of attack

1. `( … )` + `(?: … )` — parser only, unlocks composition.
2. `\d \w \s` + escapes — biggest real-world pattern coverage per line of code.
3. `{m,n}` — with an explicit expansion bound.
4. Case-insensitivity as an `add(pattern, flags, action)` API flag.
5. `\b` — after the ε/terminal zone is hardened by the KB-1 campaign.

## Measuring progress

Adopt an external compatibility yardstick: e.g. the fraction of a well-known
public rule set (gitleaks default rules, or the grok pattern library) that
compiles and matches correctly after each tier lands. That number is also
the honest "syntax coverage" line for any future positioning material,
alongside the cache-residency performance story.

Perf guard: every syntax addition goes through the standard lab protocol —
the compile path may change freely, but scan-path receipts (cascade,
identical checksums where semantics are unchanged) must stay clean.
