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

Still required before merge: run the same external `rmatch-perftest` agogo gate
used for issue #269 and compare against `main`.
