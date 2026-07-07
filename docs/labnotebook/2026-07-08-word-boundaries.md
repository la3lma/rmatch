# Word boundaries `\b` and `\B`

Date: 2026-07-08

Branch: `u/la3lma/codex/issue-270-word-boundaries`

Issue: [#270](https://github.com/la3lma/rmatch/issues/270)

## Hypothesis

The line-anchor assertion machinery can support word-boundary assertions
without changing the context-free hot path. The only extra semantics needed are
the word/non-word classification immediately before and immediately after the
current character.

## Implementation note

`MatchContext` now carries both start-side and end-side word-boundary facts.
Assertion evaluation is phase-aware:

- before consuming the current character, `\b` compares previous vs current
  character classes
- after consuming the current character, `\b` compares current vs next
  character classes

The word class deliberately matches rmatch's ASCII `\w` shorthand:
`[A-Za-z0-9_]`.

## Functional receipt

Focused local test:

```sh
./mvnw -q -B -pl rmatch -Dtest=SurfaceRegexpParserTest,LineAnchorSemanticsTest,WordBoundarySemanticsTest test
```

Result: PASS.

The new suite covers:

- whole-word `\bcat\b`
- prefix-side `\bcat`
- suffix-side `cat\b`
- non-boundary `\Bcat\B`
- underscore and digit behavior
- newline behavior
- cloneable non-lookahead buffer behavior

## Performance gate

External `rmatch-perftest` agogo gate: PASS.

Run id: `word-boundary-gate-20260708-002756`

Baseline: `origin/main` at `a6afc125`.

Candidate: `255e066f` on
`u/la3lma/codex/issue-270-word-boundaries`.

The candidate reused the exact baseline-generated patterns and corpora. SHA-256
hashes matched for:

- `patterns_10000.txt`
- `corpus_1MB.txt`
- `corpus_10MB.txt`

Scanning ratios, candidate vs baseline:

| Corpus | Baseline median | Candidate median | Ratio | Gate |
|--------|-----------------|------------------|-------|------|
| 1MB | 2689.924 ms | 2451.092 ms | 0.911 | PASS |
| 10MB | 18231.954 ms | 18562.257 ms | 1.018 | PASS |

Both runs reported `correctness_status=pass`, `failed_runs=0`,
`patterns_failed=0`, and identical match counts.
