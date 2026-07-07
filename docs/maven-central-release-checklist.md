# Maven Central release checklist

This is the living checklist for publishing `no.rmz:rmatch` releases. It starts
with Maven Central mechanics because that is the current release-prep focus; add
the rest of the release work here as it becomes explicit.

## Release Scope

- [x] Decide the public version line: `1.9.x` is the pre-2.0 Central release
  candidate line.
- [x] Keep `rmatch-tester` out of the Maven Central release lane.
- [x] Publish only `no.rmz:rmatch-parent` and `no.rmz:rmatch`.
- [ ] Decide whether the release branch should be merged to `main` before
  upload or kept as a dedicated release branch until Central validation passes.
- [ ] Decide whether the next development version after `1.9.0` is
  `1.9.1-SNAPSHOT`, `1.10.0-SNAPSHOT`, or back to `2.0-SNAPSHOT`.

## Identity and Access

- [x] Create a current GPG release-signing key.
- [x] Verify the GPG key locally with a detached-signature smoke test.
- [x] Publish and verify the public key on `keys.openpgp.org`.
- [ ] Verify that the `no.rmz` namespace is available/approved in the Central
  Portal.
- [ ] Create or verify Central Portal user token credentials.
- [x] Confirm `~/.m2/settings.xml` has a `central` server entry matching
  `publishingServerId`.
- [x] Retry Central upload after Maven credentials are configured. Attempt on
  2026-07-05 stopped before upload because `~/.m2/settings.xml` was not present
  and no Central/Sonatype/Maven credential environment variables were set.
  Retry on 2026-07-07 succeeded after fixing the Maven settings wrapper and
  setting server id `central`.

## POM and Artifact Hygiene

- [x] Set release POM versions to `1.9.0` on the release-prep branch.
- [x] Ensure parent and library POMs have name, description, URL, licenses,
  developers, organization, and SCM metadata.
- [x] Remove inherited test dependencies from the public compile/runtime graph.
- [x] Confirm `no.rmz:rmatch` compile/runtime dependencies are only
  Aho-Corasick after the `1.9.1-SNAPSHOT` dependency-surface cleanup.
- [x] Run a dependency freshness pass and try to use current stable versions of
  all direct dependencies where possible. Do not upgrade blindly: each upgrade
  must pass the normal release validation gates. First `1.9.1-SNAPSHOT` pass on
  2026-07-07 bumped Guava and JetBrains annotations first, then removed both
  from the public dependency surface after confirming they were only used for
  simple precondition checks and three `compareTo` annotations. The same pass
  bumped JUnit, Mockito, Byte Buddy, Spotless, SpotBugs,
  compiler/dependency/resources/shade/assembly plugins, and added Maven
  Enforcer while leaving milestone/beta plugin lines alone.
- [ ] Before every major release, run a dependency hygiene check: review direct
  and transitive dependencies for known vulnerabilities, stale or unmaintained
  packages, unnecessary public/transitive exposure, license compatibility and
  reasonable upgrades to current stable versions; record the decisions and
  validation evidence here.
- [x] Remove obsolete Cobertura and FindBugs hooks; SpotBugs is the active
  static-analysis tool.
- [x] Review Guava usage and decide whether it must remain a public transitive
  dependency, can be reduced, or should stay for pragmatic `1.9.x` stability.
  Decision on 2026-07-07: remove it. Usage was limited to `Preconditions`; a
  minimal internal helper now preserves the same exception behavior without
  making Guava transitive for consumers.
- [x] Review JetBrains annotation usage and decide whether annotations should
  remain compile-scoped, become optional/provided, or be removed from public
  dependency surface. Decision on 2026-07-07: remove it. Usage was limited to
  three internal `@NotNull` annotations on `compareTo` parameters.
- [x] Remove application-style `Main-Class` and `Class-Path` manifest entries
  from the library JAR.
- [x] Generate source JAR.
- [x] Generate javadoc JAR.
- [x] Generate GPG signatures for POM, main JAR, source JAR, and javadoc JAR.
- [x] Verify generated `.asc` signatures locally.
- [x] Review whether Java 25 is the intended public baseline for `1.9.x`.
  Decision on 2026-07-07: no, use Java 21 as the public baseline and compile
  with `--release 21` so Maven Central consumers on current LTS Java can use the
  library while newer JDKs remain valid build/test runtimes.
- [x] Review whether Guava should remain a public transitive dependency before
  `2.0.0`. Decision: no, remove before `1.9.1`.
- [x] Review whether JetBrains annotations should remain compile-scoped or move
  to optional/provided. Decision: no, remove before `1.9.1`.

## Documentation

- [x] Add `CHANGELOG.md` for the `1.9.0` release-candidate line.
- [x] Add release process documentation in `docs/release.md`.
- [x] Reshape `README.md` as a Maven-facing landing page.
- [x] Add Maven dependency coordinates to README.
- [x] Document Java version requirement in README.
- [x] Document current supported syntax in README.
- [x] Document deliberate syntax limitations in README.
- [x] Fix README quick-start example to use the real buffer class.
- [x] Compile and run the README quick-start example against the built artifact.
- [x] Compile and run the README scratch-project example against the previous
  Maven Central artifact using a clean temporary Maven repository. Result on
  2026-07-07: the README example as written used the currently published
  `no.rmz:rmatch:1.9.0`; running `mvn -q compile exec:java
  -Dexec.mainClass=Example` printed `user token match: user:alice` and
  `log-level match: WARN` in about five seconds on the local machine.
- [x] Add a short public performance note with exact benchmark provenance.
  README now explains the comparison model against naive `java.util.regex` and
  RE2J loops, the rmatch one-pass setup, the measured fields, and the
  byte-identical-input/match-count discipline used for release gates.
- [x] Update end-user-facing Javadocs before the `1.9.1` release. Result on
  2026-07-07: `Matcher`, `Action`, `Buffer`, `MatcherImpl`, `MatcherFactory`,
  `MultiMatcher`, `RegexStringBuffer`, `LookaheadBuffer`, `CounterAction`, and
  `RegexpParserException` now document user-facing lifecycle, callback offset
  semantics, concurrency expectations, buffer substring behavior, factory
  behavior, and parse-error meaning. Historical public engine types such as
  `Match`, `MatchSet`, `Regexp`, `RegexpFactory`, and `NodeStorage` now explain
  that they are diagnostic/internal rather than the normal application API.
- [x] Build generated Javadocs after the public-doc pass. Result on 2026-07-07:
  `mvn -q -pl rmatch -am -DskipTests -Dspotbugs.skip=true javadoc:javadoc`
  succeeded.
- [x] Decide where hosted API docs should live and wire the release path. First
  publication target is javadoc.io via the Maven Central `-javadoc.jar`.
  `make javadocs` builds browsable local docs, and
  `make release-central-javadoc-check` verifies that the `central-release`
  profile creates the Javadoc jar that javadoc.io will consume after Central
  publication.
- [ ] Post-release: after `1.9.1` is visible on javadoc.io, add a README badge
  and API-doc link:
  `[![Javadocs](https://javadoc.io/badge2/no.rmz/rmatch/javadoc.svg)](https://javadoc.io/doc/no.rmz/rmatch)`.
- [ ] Decide how prominently to describe `1.9.x` as pre-2.0 in README after
  Central publication.

## Local Validation

- [x] Run `mvn -pl rmatch -am clean verify`.
- [x] Run Central profile signing build without deployment.
- [x] Verify signatures locally.
- [x] Inspect generated JAR manifest.
- [x] Inspect generated POM metadata.
- [x] Confirm `rmatch-tester` still builds with tests skipped.
- [x] Run full reactor tests including `rmatch-tester` if practical.
- [x] Run full bumped `1.9.1-SNAPSHOT` reactor test including `rmatch-tester`.
  Result on 2026-07-07: `mvn -pl rmatch-tester -am verify` succeeded;
  `rmatch` tests reported 312 tests, 0 failures, 0 errors, 3 skipped;
  `rmatch-tester` tests reported 16 tests, 0 failures, 0 errors, 2 skipped;
  SpotBugs reported 0 findings.
- [x] Re-run full `1.9.1-SNAPSHOT` reactor verification after removing Guava
  and JetBrains annotations. Result on 2026-07-07:
  `mvn -pl rmatch-tester -am verify` succeeded; `rmatch` tests reported 312
  tests, 0 failures, 0 errors, 3 skipped; `rmatch-tester` tests reported 16
  tests, 0 failures, 0 errors, 2 skipped; Spotless and SpotBugs passed in both
  modules.
- [x] Verify Java 21 public baseline. Result on 2026-07-07:
  `mvn -pl rmatch-tester -am clean verify` succeeded after changing the
  compiler configuration to `--release 21`; `rmatch` tests reported 312 tests,
  0 failures, 0 errors, 3 skipped; `rmatch-tester` tests reported 16 tests,
  0 failures, 0 errors, 2 skipped; Spotless and SpotBugs passed in both
  modules. `javap` on `no.rmz.rmatch.impls.MatcherImpl` reported classfile
  major version 65.
- [x] Verify post-cleanup compile dependency tree. Result on 2026-07-07:
  `no.rmz:rmatch` has only `org.ahocorasick:ahocorasick:0.6.3` in compile
  scope; `rmatch-tester` has `rmatch` and Aho-Corasick in compile scope when
  resolved through the reactor.
- [x] Run bumped `1.9.1-SNAPSHOT` Central release-profile verify without
  deployment. Result on 2026-07-07:
  `mvn -pl rmatch -am -Pcentral-release -DskipTests -Dspotbugs.skip=true
  -Dgpg.keyname=55D9C01E75B1E582 verify` succeeded and generated signed
  artifacts.
- [x] Re-run bumped `1.9.1-SNAPSHOT` Central release-profile verify without
  deployment after removing Guava and JetBrains annotations. Result on
  2026-07-07: the same command succeeded and generated signed artifacts.
- [x] Re-run bumped `1.9.1-SNAPSHOT` Central release-profile verify without
  deployment after lowering the public Java baseline to 21. Result on
  2026-07-07: `mvn -pl rmatch -am -Pcentral-release -DskipTests
  -Dspotbugs.skip=true -Dgpg.keyname=55D9C01E75B1E582 verify` succeeded and
  generated signed artifacts plus javadocs.
- [x] Run OSV vulnerability check for the bumped dependency/plugin set. Result
  on 2026-07-07: no vulnerabilities returned for 39 queried Maven coordinates.
- [x] Run an external consumer smoke test using a clean temporary Maven
  project.
- [x] Install the release candidate locally before the consumer smoke test:
  `mvn -pl rmatch -am clean install`. Result on 2026-07-05: build success;
  312 tests, 0 failures, 0 errors, 3 skipped; `no.rmz:rmatch:1.9.0`
  installed in the local Maven repository.
- [x] Compile and run a temporary downstream project outside this repository:
  `/tmp/rmatch-consumer-smoke.8sEIzq`, command
  `mvn -q clean verify exec:java`. Result on 2026-07-05: build success;
  output included `user token match: user:alice` and
  `log-level match: WARN`.
- [ ] Repeat the external consumer smoke test after Central publication, using
  the artifact resolved from Maven Central rather than the local Maven
  repository.
- [x] Run an external consumer smoke test for `1.9.1-SNAPSHOT` using a clean
  temporary Maven project outside the repository. Result on 2026-07-07:
  `/tmp/rmatch-191-consumer-smoke.xqqeOm`, command
  `mvn -q clean verify exec:java`, output included
  `user token match: user:alice` and `log-level match: WARN`.
- [x] Re-run the external consumer smoke test after installing the
  post-cleanup `1.9.1-SNAPSHOT` locally. Result on 2026-07-07: the same
  temporary project resolved the updated artifact and printed
  `user token match: user:alice` and `log-level match: WARN`.
- [x] Run the chosen release benchmark smoke/gate and record exact result paths.
  Result on 2026-07-07: perftest stable 10K moderate gate, baseline
  `results/local_gate_dep_baseline_clean_20260707_124944`, candidate
  `results/local_gate_dep_candidate_clean_20260707_125341`.
- [x] Run a speed regression test against the previous dependency-surface
  baseline. Result on 2026-07-07: compared commit `1fd41ae8` against the
  Guava/JB-pruned candidate using `rmatch-perftest` stable 10K moderate config.
  Inputs were byte-identical (`patterns_10000.txt`, `corpus_1MB.txt`,
  `corpus_10MB.txt`) and match counts were identical. Median `scanning_ns`
  ratios were 0.980x for 1MB and 1.005x for 10MB, both within the 1.10 slowdown
  gate; no performance regression detected.
- [x] Run a speed regression test for the Java 21 baseline change. Result on
  2026-07-07: compared previous clean dependency-surface candidate
  `results/local_gate_dep_candidate_clean_20260707_125341` against Java 21
  candidate `results/local_gate_java21_candidate_same_inputs_20260707_111243`
  using byte-identical inputs (`patterns_10000.txt`, `corpus_1MB.txt`,
  `corpus_10MB.txt`) and identical match counts. Median `scanning_ns` ratios
  were 0.986x for 1MB and 0.968x for 10MB, both within the 1.10 slowdown gate;
  no performance regression detected.

## Central Portal Upload

- [x] Confirm there are no uncommitted release-branch changes.
- [x] Confirm the release commit hash to upload: `81435dda`.
- [x] Configure Central upload for manual validation:
  `autoPublish=false`, `waitUntil=VALIDATED`.
- [x] Run the Central deploy command with `autoPublish=false`.
- [ ] Inspect the uploaded deployment in Central Portal. Deployment id:
  `27700f0f-46da-40a1-a9b1-ba192cdc02e3`.
- [x] Confirm Central Portal validation status. Deployment
  `27700f0f-46da-40a1-a9b1-ba192cdc02e3` validated successfully on
  2026-07-07; it still requires manual publishing.
- [ ] Only after validation, publish/release the deployment.

## Git Tagging and Post-Release

- [ ] Create the `rmatch-1.9.0` tag only after Central validation is known.
- [ ] Push the release tag.
- [ ] Confirm artifact availability from Maven Central.
- [ ] Confirm the MvnRepository page updates.
- [ ] Update README if any "after publication" language should become present
  tense.
- [ ] After `1.9.1` is published and resolvable from Maven Central, update the
  README dependency and scratch-project examples from `1.9.0` to `1.9.1`, then
  rerun the clean-repository scratch-project smoke test against Central.
- [ ] Bump repository back to the agreed next development snapshot.
- [ ] Add a post-release note summarizing exactly what was published.

## Known Follow-Up Release Work

- [ ] Expand public API documentation for the callback coordinate convention.
- [ ] Decide whether to introduce a friendlier public facade before `2.0.0`.
- [ ] Decide final anchor/boundary assertion roadmap for `^`, `$`, `\b`, and
  `\B`.
- [ ] Decide compatibility claims versus `java.util.regex` for `2.0.0`.
- [ ] Decide how benchmark claims should be phrased and reproduced publicly.
