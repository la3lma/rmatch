# 2026-07-07 — Line anchors `^` and `$`

**Status:** Functional implementation on feature branch. Do not merge until the
full external performance gate has been run.

Branch: `u/la3lma/codex/issue-267-line-anchors`.

Sub-issue: [#269](https://github.com/la3lma/rmatch/issues/269), split out from
[#267](https://github.com/la3lma/rmatch/issues/267).

## What changed

`^` and `$` now compile to zero-width assertion edges in the NDFA instead of
throwing at compilation time. The closure machinery follows assertion edges
only when the current match context satisfies the assertion:

- `^` is true at buffer start and just after `\n`.
- `$` is true at EOF and just before `\n`.

The assertion is part of the transition/closure semantics. This is deliberate:
patterns such as `foo$|foobar` must not be implemented by accepting `foo` and
then trying to kill it afterward. That road leads back to the old failing-path
bug family.

## Pay-for-what-you-use guard

`NodeStorage` tracks whether any registered pattern uses context assertions.
When no such pattern exists, the engines stay on the ordinary no-context loop
and use the existing start-node cache, DFA ASCII transition cache, prefilter,
and two-character start filter.

When an assertion is present, matching switches to a context-aware path:

- the current line-start/line-end context is computed per consumed character
- assertion-sensitive DFA transitions use context-keyed caches
- Aho-Corasick prefiltering and the two-character start filter are
  conservatively bypassed for the whole matcher storage

That bypass is intentionally conservative for the first sub-issue. It is safe
and measurable; it is not the final performance story.

## Tests

Command:

```bash
mvn -q -B -pl rmatch -am test
```

Result: pass.

Dedicated suite:
`rmatch/src/test/java/no/rmz/rmatch/semantics/LineAnchorSemanticsTest.java`.

Coverage includes anchors at buffer boundaries, anchors around newlines,
whole-line matches, alternation, grouping, and mixed anchored/unanchored
pattern sets.

## Performance notes

The full `rmatch-perftest` framework was not available in the local checkout:
`../rmatch-perftest` only contained its README, not
`benchmarking/framework/regex_bench_framework`.

Temporary local sanity data is checked in under
[`docs/benchmark-receipts/issue-269-line-anchors`](../benchmark-receipts/issue-269-line-anchors).
It shows identical match counts for a no-anchor workload, but also very high
workstation variance. Treat it as a smoke receipt only.

Before merge, run the real branch-vs-main performance gate with byte-identical
inputs and explicit match-count equality.

## Known descopes

- Pure zero-width patterns such as `^$` are still not claimed as supported. The
  engine reports consumed spans today, so zero-width match reporting needs its
  own explicit design decision.
- Word boundaries `\b`/`\B` remain in #267, not this sub-issue.
- `MULTILINE`/DOTALL flag semantics are still future work. The current `^`/`$`
  behavior is line-oriented by default.
