# Contributing to rmatch

Thanks for considering a contribution. rmatch is a performance-focused
library, and its development workflow reflects that: correctness is proven by
an elaborate functional test suite, and performance claims are proven by
receipts, never by plausibility.

## Building and testing

```bash
./mvnw clean verify        # full build: all tests, Spotless, Checkstyle, SpotBugs
./mvnw -Pcoverage verify   # same, with JaCoCo coverage reports
```

Java 21 is the published baseline; CI verifies on 21 and a current JDK.
Code style is Google Java Format, enforced by Spotless — run
`./mvnw spotless:apply` before committing and there is nothing to argue
about.

## What a pull request needs

1. **Green gate.** The Main Gate workflow runs the full suite on every PR.
   Fix the build, not the expectation — in particular, the performance
   canary's expected match count changes only when pattern files, corpus
   files, or documented match semantics change.
2. **Tests for behavior.** Semantics changes need cases in the relevant
   suite under `rmatch/src/test/java/no/rmz/rmatch/semantics/`; bug fixes
   need a regression test that fails before the fix.
3. **Performance receipts for engine changes.** Anything touching the scan
   path goes through the external branch-vs-main benchmark protocol in
   [docs/performance-regression-testing.md](docs/performance-regression-testing.md).
   Almost all plausible matcher improvements are not improvements until
   measured; ratios above 1.10 or match-count mismatches are a no.
4. **Public API discipline.** The supported surface is the root
   `no.rmz.rmatch` package only. Implementation packages are not exported
   and may change without notice; do not add public API without discussing
   it in an issue first.

## Repository layout

- `rmatch/` — the published library (`no.rmz:rmatch`).
- `rmatch-tester/` — local performance and experiment tooling; never
  published.
- Benchmark orchestration lives in the separate
  [rmatch-perftest](https://github.com/la3lma/rmatch-perftest) repository.

## Reporting bugs

A minimal reproduction is gold: the pattern set, the input text, the
expected matches, and the observed matches. The test classes in
`rmatch/src/test/java/no/rmz/rmatch/bugManifestations/` show the preferred
shape — small, self-contained, and named for the behavior they pin.
