# Changelog

## 1.9.0 - pre-2.0 Maven Central release candidate

`1.9.0` is the first release-candidate line aimed at Maven Central. The engine
functionality is now strong enough to publish for external use, while the
documentation, syntax surface, release process, and compatibility promises are
still being tightened toward a spotless `2.0.0`.

### Highlights

- Added the fast-path matching work that makes `rmatch` competitive with RE2J
  on cache-friendly workloads and faster on larger corpora in current benchmark
  probes.
- Added syntax Tier 1 coverage: grouping, non-capturing grouping, escapes,
  shorthand character classes, counted quantifiers, and prefix `(?i)`.
- Fixed several long-standing correctness bugs found by the new semantics
  suite, including quantifier binding, negated character classes, and matches
  that extended past their last final state.
- Added release signing with the public key
  `9017955845408C9B4422B5DE55D9C01E75B1E582`.

### Deliberate limitations

- `1.9.0` is not a claim of full Java/PCRE regex compatibility.
- Line anchors `^` and `$`, word boundaries `\b`/`\B`, lookaround,
  backreferences, scoped flags, and capture-group semantics are not part of the
  supported 1.9.0 surface.
- The `rmatch-tester` module remains local project tooling and is not part of
  the Maven Central release lane.

### Release intent

Use `1.9.x` to shake out packaging, docs, examples, and syntax-contract issues.
Use `1.99.x` as the final API/syntax preview if needed. Reserve `2.0.0` for
the stable, fully documented release line.
