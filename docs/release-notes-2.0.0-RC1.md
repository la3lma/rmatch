# rmatch 2.0.0-RC1 release notes (draft)

`2.0.0-RC1` is the first release intended for serious evaluation by potential
users. rmatch is a Java library for applying many regular expressions to the
same input through one matching pipeline. It is most relevant when pattern sets
number in the hundreds or thousands and are reused over substantial buffers.

## Why 2.0 now

The `1.x` line began as an experiment. The implementation demonstrated that a
shared automaton could offer useful many-pattern matching, but much of the
early series delivered theoretical promise rather than a dependable library.
The `1.9.x` releases were a sequence of engineering steps toward changing that:
correctness defects were fixed, matching semantics were pinned by tests, the
public API was reduced to a small facade, packaging was prepared for Maven
Central, and performance was measured against realistic alternatives.

The project has no known users who need a detailed `1.9.x` migration manual.
Treat that series as release preparation rather than as a compatibility
baseline. The purpose of 2.0 is to give potential users something they can try
with a fair chance of finding it correct, understandable, and useful.

## Runtime requirement

rmatch 2.0 requires Java 21 or newer. Artifacts are compiled with
`--release 21`; they cannot be loaded on older Java runtimes. Java 21 is both
the language/toolchain baseline and the lower compatibility boundary for the
2.x line. The project also verifies the build on a newer Java release in CI.

## Public API

The supported JPMS module is `no.rmz.rmatch`, exporting only the package of the
same name. Its application-facing API consists of:

- `RMatch`, the factory and input-adapter facade
- `Matcher`, the build-then-use matcher lifecycle
- `Action`, the match callback
- `Buffer`, the positional input contract
- `PatternFlag`, per-pattern options
- `RegexpParserException`, malformed or unsupported pattern reporting

Matchers are `AutoCloseable`. Register all patterns before the first scan,
reuse the matcher for subsequent inputs, and close it when finished. Match
callbacks receive half-open `[start, end)` offsets. A partitioned matcher may
invoke callbacks concurrently and does not promise callback order.

## Dependencies and packaging

The published `no.rmz:rmatch` artifact has no third-party compile-time or
runtime dependencies. Test and benchmark libraries remain project-local, and
`rmatch-tester` is not part of the Maven Central release lane.

## Syntax and matching behavior

rmatch deliberately implements a regular-language subset rather than claiming
drop-in Java or PCRE compatibility. The normative supported surface, matching
rules, anchors, character classes, flags, and exclusions are documented in
[Regex syntax and semantics](regex-syntax-and-semantics.md).

Notable exclusions include captures, backreferences, lookaround, scoped flags,
input-only anchors, and pure zero-width match reporting. Unsupported or
malformed syntax fails during registration with `RegexpParserException`.

## Performance evidence

The current public campaign compares rmatch 1.9.6 with RE2/J, Java regex, and
Hyperscan over 8 MiB and 50 MiB generated fixtures containing 1,000 through
10,000 expressions. Every retained timing first passes exact match-count
validation. The campaign shows where RE2/J leads and where rmatch's
many-pattern design crosses over as pattern sets and inputs grow.

The benchmark remains exploratory rather than a universal performance claim.
Harness code, calibrated thread counts, engine versions, scenarios, and raw
receipts are available in the
[rmatch performance measurements project](https://github.com/la3lma/rmatch-performance-measurements).

The final pre-RC regression gate compares the current candidate directly with
published `1.9.6` over eight 8 MiB and 50 MiB cells. All match counts agree;
the largest slowdown is 2.92% and the largest speedup is 9.58%. Raw receipts
and the four-mode consumer verification are recorded in
[the pre-RC evidence bundle](benchmark-receipts/2.0-pre-rc-2026-07-12/README.md).

## RC purpose

RC1 freezes the proposed 2.0 API and documented semantics for external review.
Feedback should focus on correctness, API friction, documentation gaps,
packaging, and workloads where the performance profile differs from the
published evidence. A final `2.0.0` follows only after the release candidate has
been exercised as a clean Maven, Gradle, class-path, and JPMS dependency.
