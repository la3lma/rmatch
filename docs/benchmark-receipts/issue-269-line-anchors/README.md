# Issue #269 line-anchor receipts

Date: 2026-07-07.

Branch: `u/la3lma/codex/issue-267-line-anchors`.

Parent issue: <https://github.com/la3lma/rmatch/issues/267>.

Sub-issue: <https://github.com/la3lma/rmatch/issues/269>.

## Functional receipt

Command:

```bash
mvn -q -B -pl rmatch -am test
```

Result: pass.

The dedicated functional suite is
`rmatch/src/test/java/no/rmz/rmatch/semantics/LineAnchorSemanticsTest.java`.
It covers:

- `^` at buffer start and after newline
- `$` at EOF and before newline
- `^...$` whole-line matching
- anchors mixed with unanchored patterns
- anchors inside alternation and groups
- `$` inside alternation, so the assertion is part of transition semantics and
  not a post-hoc match filter

## Performance receipt status

The normal full benchmark gate could not be run from this checkout because the
local `../rmatch-perftest` repository contains only its README; the framework
path referenced by this repository's Makefile,
`benchmarking/framework/regex_bench_framework`, is not present.

As a stopgap, a temporary non-committed driver was compiled in `/tmp` and run
against:

- baseline: detached `origin/main` worktree at `47490672`
- candidate: this branch after the hot-loop split that keeps assertion context
  out of the ordinary no-anchor loop

This is only a sanity receipt. It is not a replacement for the external
`rmatch-perftest` gate before merge.

## Local no-anchor sanity data

Driver parameters:

```text
patterns=5000 repeats=40 warmups=3 iterations=9 anchored=false
```

The deterministic corpus contained 1,806,240 characters and every measured
iteration produced 200,000 matches on both baseline and candidate.

Observed medians:

- Baseline first run: `398.009 ms`
- Candidate after hot-loop split: `421.735 ms`
- Baseline rerun in the same session: `605.819 ms`

Interpretation: local workstation variance is too high for this microdriver to
prove a win or a regression. It does, however, show identical match counts and
no obvious catastrophic penalty in assertion-free workloads. The branch should
not be merged on this evidence alone.

## Anchor sanity data

Driver parameters:

```text
patterns=1000 repeats=20 warmups=2 iterations=5 anchored=true
```

Candidate result:

- corpus length: 180,000 characters
- expected matches: 20,000
- median: `1746.791 ms`
- min/max: `1714.846 ms` / `1870.128 ms`

This anchor-heavy synthetic workload intentionally bypasses the ordinary
literal prefilter and two-character start cache. It is useful for correctness
and smoke testing, not for public performance positioning.

## Merge gate still required

Before merging #269, run the real branch-vs-main gate from the full
`rmatch-perftest` framework using byte-identical inputs and match-count checks.
The key claim to verify is still pay-for-what-you-use: pattern sets without
context assertions must stay within noise of `main`, and anchor-heavy sets must
remain functionally correct while their expected overhead is understood.
