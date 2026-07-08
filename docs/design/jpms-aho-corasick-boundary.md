# JPMS and Aho-Corasick Dependency Boundary

Issue: <https://github.com/la3lma/rmatch/issues/282>

## Summary

`rmatch` currently has an explicit JPMS descriptor:

```java
module no.rmz.rmatch {
  requires java.logging;
  requires java.management;
  requires ahocorasick;
}
```

The `ahocorasick` module name is derived from
`org.ahocorasick:ahocorasick:0.6.3`, which has no `module-info.class` and no
`Automatic-Module-Name` manifest entry. `javac` therefore treats it as a
filename-derived automatic module and emits this release warning:

```text
Required filename-based automodules detected: [ahocorasick-0.6.3.jar].
Please don't publish this project to a public artifact repository!
```

This is not a runtime failure and did not block `1.9.3`, but it means we should
not call the JPMS story fully clean before `2.0.0`.

## Evidence

Baseline command on 2026-07-08:

```bash
./mvnw -B -pl rmatch -am -Pcentral-release \
  -DskipTests -Dspotbugs.skip=true -Dgpg.skip=true clean verify
```

Result:

- Build succeeds.
- `javac` emits the filename-based automatic-module warning.
- `javac` also reports `module-info.java:[4,12] requires directive for an
  automatic module`.

Dependency inspection:

```bash
jar --describe-module \
  --file ~/.m2/repository/org/ahocorasick/ahocorasick/0.6.3/ahocorasick-0.6.3.jar
```

Result:

```text
No module descriptor found. Derived automatic module.

ahocorasick@0.6.3 automatic
```

Maven Central reports `0.6.3` as the latest published version, so a simple
version bump does not solve this.

## Current Usage

Production code imports Aho-Corasick directly only in
`no.rmz.rmatch.engine.prefilter.AhoCorasickPrefilter`:

- `org.ahocorasick.trie.Trie`
- `org.ahocorasick.trie.Emit`

The public matcher API does not expose Aho-Corasick types. This is an internal
implementation dependency used by the literal prefilter.

## Options

### Keep The Current State Until 2.0

This is acceptable for a short-lived `1.9.x` release-candidate line. The Maven
artifact works, Central accepts it, and consumer smoke tests pass.

The cost is that the JPMS descriptor is useful but not pristine. We should keep
the issue open and avoid overstating JPMS quality in release notes.

### Remove `module-info.java` And Publish `Automatic-Module-Name`

This would avoid the `javac` warning because `rmatch` would again be compiled as
a classpath library while still advertising a stable module name in the JAR
manifest.

This is the cleanest low-risk option if we decide that `1.9.x` should not carry
any JPMS warning. It is also a step back from explicit JPMS, so it should be a
conscious compatibility decision rather than a quiet fix.

### Shade The Existing Dependency

Plain Maven shading after compilation is not enough. The warning is produced
while compiling `module-info.java`; at that point `javac` still sees
`ahocorasick-0.6.3.jar` as an automatic module.

Shading can still help the public dependency surface, but it does not by itself
make the explicit JPMS build clean. To solve the warning with shading, the
classes would need to be internalized before module compilation or the module
descriptor would need a more complex build path.

### Replace Or Reimplement The Prefilter

This is the most complete 2.0-quality solution. Since the dependency is used
behind one internal class, we could replace it with:

- a small internal Aho-Corasick implementation,
- another library with explicit JPMS metadata,
- or a different literal-prefilter design.

This must go through functional tests and the agogo performance gate. The
literal prefilter is performance-sensitive, so this should not be treated as a
cosmetic dependency cleanup.

### Vendor The Upstream Classes

The upstream JAR is small, but vendoring third-party source creates licensing,
maintenance, and audit obligations. It should be considered only if replacing or
rewriting the prefilter is clearly worse.

## Recommendation

Initial recommendation before implementation:

1. Build a replacement/internal prefilter behind the existing
   `AhoCorasickPrefilter` behavior.
2. Run the full functional suite.
3. Run the agogo performance gate against the current Aho-backed implementation.
4. If performance is acceptable, remove the Aho-Corasick dependency and the
   `requires ahocorasick` directive.
5. If performance is not acceptable, either keep the dependency and document the
   JPMS caveat, or fall back to `Automatic-Module-Name` until a better
   dependency-boundary solution exists.

## Implementation Result

Branch `u/la3lma/codex/issue-282-internal-prefilter` follows that path. It
keeps the `AhoCorasickPrefilter` API but replaces the external library with a
small internal trie plus failure-link implementation tailored to rmatch's
literal-hint model.

The original `org.ahocorasick:ahocorasick` library did the job well for rmatch
for a long time, and deserves explicit credit for that. The reason to move on is
not that it was bad code; it is that time does not stand still. rmatch now has
an explicit JPMS story, Maven Central publication, and a tighter pre-2.0 public
surface. A filename-derived automatic module is the wrong long-term boundary for
an implementation-only prefilter.

Local validation on 2026-07-08:

```bash
./mvnw -B -pl rmatch -am verify -Dspotbugs.skip=true
```

Result:

- 335 tests passed; 0 failures; 0 errors; 3 skipped.
- The bounded micro-guard in `AhoCorasickPrefilterPerformanceTest` reported
  `Naive=120.41 ms, Internal Aho-Corasick=15.54 ms, Improvement=87.09%`.

Central-profile validation:

```bash
./mvnw -B -pl rmatch -am -Pcentral-release \
  -DskipTests -Dspotbugs.skip=true -Dgpg.skip=true clean verify
```

Result:

- Build succeeded.
- The filename-based automatic-module warning disappeared.

Compile dependency tree:

```text
no.rmz:rmatch:jar:1.9.4-SNAPSHOT
```

No compile-scope third-party dependency remains in `no.rmz:rmatch`.

External agogo performance gate on 2026-07-08:

- Host hardware: 32 logical CPUs, AMD Ryzen 9 9950X3D.
- Docker image: `maven:3.9-eclipse-temurin-26`.
- Harness: `rmatch-perftest`,
  `test_matrix/stable_10k_moderate_rmatch.json`.
- Baseline: `origin/main` after PR #283, using the existing Aho-backed
  implementation.
- Candidate: branch `u/la3lma/codex/issue-282-internal-prefilter`, using the
  internal implementation.
- Both runs used identical generated pattern files and pinned 1MB/10MB
  synthetic corpora.

Result directories on agogo:

```text
/home/rmz/git/rmatch-agogo-issue-282-internal-prefilter-20260708_172435/rmatch-perftest/benchmarking/framework/regex_bench_framework/results/agogo_issue_282_baseline_aho_fixed_20260708_153744
/home/rmz/git/rmatch-agogo-issue-282-internal-prefilter-20260708_172435/rmatch-perftest/benchmarking/framework/regex_bench_framework/results/agogo_issue_282_candidate_internal_fixed_20260708_154048
```

Gate result:

```text
1MB:  baseline_ms=2437.950  candidate_ms=2474.760  ratio=1.015
      match_count=19,234,521 for both runs
10MB: baseline_ms=19233.018 candidate_ms=19315.342 ratio=1.004
      match_count=192,371,384 for both runs
PERF_GATE=PASS
```

The replacement reaches parity with the existing prefilter under the current
agogo gate and removes the JPMS automatic-module warning. That is enough to
move the implementation to PR review and close #282 once merged.
