# Maven Central release checklist

This is the living checklist for publishing `no.rmz:rmatch` releases. It starts
with Maven Central mechanics because that is the current release-prep focus; add
the rest of the release work here as it becomes explicit.

## Release Scope

- [x] Decide the public version line: `1.9.x` is the pre-2.0 Central release
  candidate line.
- [x] Keep `rmatch-tester` out of the Maven Central release lane.
- [x] Publish only `no.rmz:rmatch-parent` and `no.rmz:rmatch`.
- [x] Decide whether the release branch should be merged to `main` before
  upload or kept as a dedicated release branch until Central validation passes.
  Decision for `1.9.1`: keep the dedicated release branch through Central
  validation, then merge/cherry-pick public docs and receipts back as needed.
- [x] Decide whether the next development version after `1.9.1` is
  `1.9.2-SNAPSHOT`, `1.10.0-SNAPSHOT`, or back to `2.0-SNAPSHOT`. Decision:
  continue mainline development on `2.0-SNAPSHOT`; use the `1.9.x` release
  branch only for release-candidate stabilization.

## 1.9.2 Release Run

- [x] Sync release worktree from current `origin/main`. Result on 2026-07-08:
  release branch `u/la3lma/codex/release-1.9.2` starts at `d9fe0eca`, the
  merge of PR #277 with word-boundary assertions.
- [x] Set release POM versions and README snippets to `1.9.2`.
- [x] Convert `1.9.2` changelog notes from snapshot wording to release-candidate
  wording, with pure zero-width reporting and flag-mode behavior documented as
  future work rather than release blockers.
- [x] Run full release preflight. Result on 2026-07-08:
  `make release-central-preflight` succeeded.
- [x] Run Central profile check without signing/uploading. Result on
  2026-07-08: `make release-central-profile-check` succeeded.
- [x] Run signed Central release-profile verify. Result on 2026-07-08:
  `./mvnw -B -pl rmatch -am -Pcentral-release -DskipTests
  -Dspotbugs.skip=true -Dgpg.keyname=55D9C01E75B1E582 verify` succeeded and
  signed the parent POM plus rmatch POM, main JAR, source JAR, and Javadoc JAR.
- [x] Verify `1.9.2` generated `.asc` signatures locally. Result on
  2026-07-08: `gpg --verify` reported good signatures for all generated
  release artifacts using key `9017955845408C9B4422B5DE55D9C01E75B1E582`.
- [x] Inspect `1.9.2` JAR manifest, embedded POM properties, and Java baseline.
  Result on 2026-07-08: manifest has `Java-Version: 21`, no application
  `Main-Class`, embedded properties report `no.rmz:rmatch:1.9.2`, and
  `javap` reports classfile major version 65.
- [x] Verify `1.9.2` compile dependency tree. Result on 2026-07-08:
  `no.rmz:rmatch` has only `org.ahocorasick:ahocorasick:0.6.3` in compile
  scope.
- [x] Run a downstream consumer smoke test after local install. Result on
  2026-07-08: `/tmp/rmatch-192-consumer-smoke.XBPoXD`, command
  `mvn -q clean verify exec:java -Dexec.mainClass=Example`, printed
  `log-level match: WARN` and `user token match: user:alice`.
- [x] Upload the `1.9.2` release commit to Central Portal. Result on
  2026-07-08: release commit `48151212` deployed as Central deployment
  `ea3d5702-e26e-4376-974c-6e094298aac8`; validation succeeded.
- [x] Publish the validated `1.9.2` Central deployment. Result on 2026-07-08:
  the deployment moved from `VALIDATED` to `PUBLISHING` via the Central
  Publisher API and then reached `PUBLISHED`.
- [x] Confirm `1.9.2` artifact availability from Maven Central. Result on
  2026-07-08: direct checks for
  `https://repo.maven.apache.org/maven2/no/rmz/rmatch/1.9.2/rmatch-1.9.2.pom`
  and the corresponding `-javadoc.jar` returned HTTP 200.
- [x] Run a clean-repository consumer smoke test against Maven Central
  `1.9.2`. Result on 2026-07-08:
  `/tmp/rmatch-192-central-consumer-smoke.Hq0dTH` with empty Maven repository
  `/tmp/rmatch-192-central-m2.XCCLKb`, command
  `mvn -Dmaven.repo.local=/tmp/rmatch-192-central-m2.XCCLKb -q clean verify
  exec:java -Dexec.mainClass=Example`, printed `log-level match: WARN` and
  `user token match: user:alice`.
- [x] Create and push the `rmatch-1.9.2` tag. Result on 2026-07-08:
  annotated tag `rmatch-1.9.2` points at uploaded release commit `48151212`
  and was pushed to `origin`.
- [x] Bump repository back to the next development snapshot. Result on
  2026-07-08: POMs moved from final `1.9.2` release versions to
  `1.9.3-SNAPSHOT`; README examples remain on the published `1.9.2` version.

## Identity and Access

- [x] Create a current GPG release-signing key.
- [x] Verify the GPG key locally with a detached-signature smoke test.
- [x] Publish and verify the public key on `keys.openpgp.org`.
- [x] Verify that the `no.rmz` namespace is available/approved in the Central
  Portal.
- [x] Create or verify Central Portal user token credentials.
- [x] Confirm `~/.m2/settings.xml` has a `central` server entry matching
  `publishingServerId`.
- [x] Retry Central upload after Maven credentials are configured. Attempt on
  2026-07-05 stopped before upload because `~/.m2/settings.xml` was not present
  and no Central/Sonatype/Maven credential environment variables were set.
  Retry on 2026-07-07 succeeded after fixing the Maven settings wrapper and
  setting server id `central`. Do not publish the old validated `1.9.0`
  deployment for the `1.9.1` release; create a fresh `1.9.1` deployment after
  the final version bump and validation pass.

## POM and Artifact Hygiene

- [x] Set release POM versions to `1.9.1` on the release-prep branch.
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
- [x] Before every major release, run a dependency hygiene check: review direct
  and transitive dependencies for known vulnerabilities, stale or unmaintained
  packages, unnecessary public/transitive exposure, license compatibility and
  reasonable upgrades to current stable versions; record the decisions and
  validation evidence here. Result for `1.9.1`: dependency freshness and OSV
  checks were run on 2026-07-07; Guava and JetBrains annotations were removed
  from the public dependency surface; the remaining compile/runtime dependency
  is Aho-Corasick.
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
- [x] Confirm the README leads with the performance/scaling reason to use
  rmatch; API convenience supports the pitch but does not replace it.
- [x] Update README dependency snippets to `1.9.1` for the release candidate.
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
- [x] Before 2.0, perform a rendered Javadoc quality pass for all public API
  documentation: inspect the generated pages, fix spelling mistakes and awkward
  wording, and verify that every description remains technically correct. Track
  this under [issue #271](https://github.com/la3lma/rmatch/issues/271). Result
  on 2026-07-08: public interfaces, compiler helpers, matcher implementations,
  and diagnostic utility docs were rewritten for clarity; generated Javadocs
  were built and swept for the known typo/placeholder language.
- [x] Decide where hosted API docs should live and wire the release path. First
  publication target is javadoc.io via the Maven Central `-javadoc.jar`.
  `make javadocs` builds browsable local docs, and
  `make release-central-javadoc-check` verifies that the `central-release`
  profile creates the Javadoc jar that javadoc.io will consume after Central
  publication.
- [x] Post-release: after `1.9.1` is visible on javadoc.io, add a README badge
  and API-doc link:
  `[![Javadocs](https://javadoc.io/badge2/no.rmz/rmatch/javadoc.svg)](https://javadoc.io/doc/no.rmz/rmatch)`.
- [x] Decide how prominently to describe `1.9.x` as pre-2.0 in README after
  Central publication. Decision on 2026-07-07: keep the short paragraph near
  the top of README, after the badges and before the performance rationale.

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
- [x] Run full final-version release-lane verification after bumping POMs to
  `1.9.1`. Result on 2026-07-07:
  `mvn -pl rmatch -am clean verify` succeeded; `rmatch` tests reported
  312 tests, 0 failures, 0 errors, 3 skipped; Spotless and SpotBugs passed.
- [x] Run tester-inclusive verification as a non-release-lane smoke check.
  Result on 2026-07-07: `mvn -pl rmatch-tester -am clean verify` succeeded;
  `rmatch-tester` tests reported 16 tests, 0 failures, 0 errors, 2 skipped.
  Note: `rmatch-tester` remains outside the Central release lane and keeps its
  own snapshot wiring.
- [x] Verify Java 21 public baseline. Result on 2026-07-07:
  `mvn -pl rmatch-tester -am clean verify` succeeded after changing the
  compiler configuration to `--release 21`; `rmatch` tests reported 312 tests,
  0 failures, 0 errors, 3 skipped; `rmatch-tester` tests reported 16 tests,
  0 failures, 0 errors, 2 skipped; Spotless and SpotBugs passed in both
  modules. `javap` on `no.rmz.rmatch.impls.MatcherImpl` reported classfile
  major version 65.
- [x] Before release, audit source, tests, benchmark harnesses, and build/release
  scripts for deprecated method/API usage. Remove deprecated calls where
  practical; document and track any unavoidable remaining usage. Track this
  under [issue #272](https://github.com/la3lma/rmatch/issues/272). Result for
  `1.9.2` on 2026-07-08: source/test/build grep found the public deprecated
  `Buffer.getCurrentRestString()` method and its test implementation delegate.
  This is deliberate compatibility surface and should not be removed during the
  `1.9.2` release cut; keep the cleanup tracked under
  [issue #272](https://github.com/la3lma/rmatch/issues/272).
- [ ] Before 2.0, audit for unused methods, classes, and interfaces that are not
  part of the intended external API. Remove unused accidental/internal surface
  where safe; document any unused public surface that is intentionally retained.
  Track this under [issue #273](https://github.com/la3lma/rmatch/issues/273).
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
- [x] Run final `1.9.1` Central release-profile verify without deployment.
  Result on 2026-07-07:
  `mvn -pl rmatch -am -Pcentral-release -DskipTests -Dspotbugs.skip=true
  -Dgpg.keyname=55D9C01E75B1E582 verify` succeeded and generated signed
  `1.9.1` artifacts plus javadocs.
- [x] Verify final `1.9.1` signatures locally. Result on 2026-07-07:
  `gpg --verify` reported good signatures for parent POM, rmatch POM, main
  JAR, source JAR, and Javadoc JAR using key
  `9017955845408C9B4422B5DE55D9C01E75B1E582`.
- [x] Inspect final `1.9.1` JAR manifest and POM properties. Result on
  2026-07-07: `rmatch-1.9.1.jar` manifest has `Java-Version: 21`, no
  application-style `Main-Class`, and embedded pom properties report
  `groupId=no.rmz`, `artifactId=rmatch`, `version=1.9.1`.
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
- [x] Repeat the external consumer smoke test after Central publication, using
  the artifact resolved from Maven Central rather than the local Maven
  repository. Result on 2026-07-07:
  `/tmp/rmatch-191-central-consumer-smoke.DlvpZt` with empty Maven repository
  `/tmp/rmatch-191-central-m2.KUwue0`, command
  `mvn -Dmaven.repo.local=/tmp/rmatch-191-central-m2.KUwue0 -q clean verify
  exec:java -Dexec.mainClass=Example`, printed
  `user token match: user:alice` and `log-level match: WARN`.
- [x] Run an external consumer smoke test for `1.9.1-SNAPSHOT` using a clean
  temporary Maven project outside the repository. Result on 2026-07-07:
  `/tmp/rmatch-191-consumer-smoke.xqqeOm`, command
  `mvn -q clean verify exec:java`, output included
  `user token match: user:alice` and `log-level match: WARN`.
- [x] Re-run the external consumer smoke test after installing the
  post-cleanup `1.9.1-SNAPSHOT` locally. Result on 2026-07-07: the same
  temporary project resolved the updated artifact and printed
  `user token match: user:alice` and `log-level match: WARN`.
- [x] Run an external consumer smoke test for final `1.9.1` after local
  install. Result on 2026-07-07:
  `/tmp/rmatch-191-final-consumer-smoke.RLTC9k`, command
  `mvn -q clean verify exec:java -Dexec.mainClass=Example`, output included
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
- [x] Run the benchmark-framework README evidence guardrails. Result on
  2026-07-07: `make test-readme-efficiency` in `rmatch-perftest` passed
  6 tests, covering deterministic literal-token inputs, large README config
  scale, corpus fallback safety, cross-engine match-count mismatch rejection,
  and median scan/total summaries.
- [x] Generate public README performance chart only from a semantics-aligned
  benchmark receipt. Result on 2026-07-07: agogo Docker run
  `readme_efficiency_large_20260707_191000`, deterministic literal-token
  patterns over an 8 MiB corpus, match counts agreed across `rmatch`, RE2J, and
  `java-native-naive` in every charted cell.

## Central Portal Upload

- [x] Confirm there are no uncommitted release-branch changes before upload.
- [x] Confirm the `1.9.1` release commit hash uploaded:
  `5ee41857`.
- [x] Configure Central upload for manual validation:
  `autoPublish=false`, `waitUntil=VALIDATED`.
- [x] Run the Central deploy command with `autoPublish=false` for `1.9.1`.
  Result on 2026-07-07:
  `mvn -pl rmatch -am -Pcentral-release -DskipTests -Dspotbugs.skip=true
  -Dgpg.keyname=55D9C01E75B1E582 deploy` succeeded.
- [x] Inspect the uploaded `1.9.1` deployment in Central Portal. Deployment id:
  `88ce19c0-3326-4152-a540-97194f903326`. User approved/published this
  deployment on 2026-07-07.
- [x] Historical note: previous deployment
  `27700f0f-46da-40a1-a9b1-ba192cdc02e3` validated successfully on
  2026-07-07, but it belongs to the earlier `1.9.0` release-prep state and
  should not be published as the `1.9.1` release.
- [x] Confirm Central Portal validation status for the fresh `1.9.1`
  deployment. Deployment `88ce19c0-3326-4152-a540-97194f903326` validated
  successfully on 2026-07-07 and requires manual publishing.
- [x] Only after validation, publish/release the `1.9.1` deployment. User
  approved/published deployment `88ce19c0-3326-4152-a540-97194f903326` on
  2026-07-07.

## Git Tagging and Post-Release

- [x] Create the `rmatch-1.9.1` tag only after Central validation is known.
  Result on 2026-07-07: annotated tag `rmatch-1.9.1` points at uploaded
  release commit `5ee41857`.
- [x] Push the release tag. Result on 2026-07-07:
  `git push origin rmatch-1.9.1` succeeded.
- [x] Confirm artifact availability from Maven Central. Result on 2026-07-07:
  `https://repo.maven.apache.org/maven2/no/rmz/rmatch/1.9.1/rmatch-1.9.1.pom`
  and the corresponding `-javadoc.jar` returned HTTP 200 after propagation.
- [ ] Confirm the MvnRepository page updates. Status on 2026-07-07: still
  pending external indexing; direct `mvnrepository.com` checks are also gated
  by Cloudflare challenge from this environment.
- [x] Update the MvnRepository banner at the bottom of any README or project
  page where it is still used, and make sure it points to the latest version on
  Maven Central. Result on 2026-07-07: no MvnRepository banner remains in
  README; the active version badge points to Central directly.
- [x] Update README if any "after publication" language should become present
  tense. Result on 2026-07-07: README already describes `1.9.1` as the
  current Maven Central line and now includes the javadoc.io badge.
- [x] After `1.9.1` is published and resolvable from Maven Central, rerun the
  clean-repository scratch-project smoke test against Central using the README
  `1.9.1` snippets. Result on 2026-07-07:
  `/tmp/rmatch-191-central-consumer-smoke.DlvpZt` with empty Maven repository
  `/tmp/rmatch-191-central-m2.KUwue0`, command
  `mvn -Dmaven.repo.local=/tmp/rmatch-191-central-m2.KUwue0 -q clean verify
  exec:java -Dexec.mainClass=Example`, printed
  `user token match: user:alice` and `log-level match: WARN`.
- [x] Bump repository back to the agreed next development snapshot. Result on
  2026-07-07: no change needed in this dedicated `1.9.x` release branch; the
  agreed next development line remains `2.0-SNAPSHOT` on mainline.
- [x] Add a post-release note summarizing exactly what was published. Result on
  2026-07-07: `CHANGELOG.md` records Maven Central publication date, coordinate
  `no.rmz:rmatch:1.9.1`, tag `rmatch-1.9.1`, and release commit `5ee41857`.

## Known Follow-Up Release Work

- [ ] Expand public API documentation for the callback coordinate convention.
- [ ] Decide whether to introduce a friendlier public facade before `2.0.0`.
- [x] Decide final anchor/boundary assertion roadmap for `^`, `$`, `\b`, and
  `\B`. Result on 2026-07-08: line anchors and word-boundary assertions are
  implemented, tested, documented, and performance-gated for the `1.9.2`
  release lane. Remaining assertion/mode work is tracked separately: input
  anchors, MULTILINE, non-DOTALL behavior, and pure zero-width match reporting.
- [ ] Decide compatibility claims versus `java.util.regex` for `2.0.0`.
- [ ] Decide how benchmark claims should be phrased and reproduced publicly.
