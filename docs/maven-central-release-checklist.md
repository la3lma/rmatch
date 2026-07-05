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
- [ ] Confirm `~/.m2/settings.xml` has a `central` server entry matching
  `publishingServerId`.
- [ ] Retry Central upload after Maven credentials are configured. Attempt on
  2026-07-05 stopped before upload because `~/.m2/settings.xml` was not present
  and no Central/Sonatype/Maven credential environment variables were set.

## POM and Artifact Hygiene

- [x] Set release POM versions to `1.9.0` on the release-prep branch.
- [x] Ensure parent and library POMs have name, description, URL, licenses,
  developers, organization, and SCM metadata.
- [x] Remove inherited test dependencies from the public compile/runtime graph.
- [x] Confirm `no.rmz:rmatch` compile/runtime dependencies are only Guava,
  JetBrains annotations, and Aho-Corasick.
- [x] Remove application-style `Main-Class` and `Class-Path` manifest entries
  from the library JAR.
- [x] Generate source JAR.
- [x] Generate javadoc JAR.
- [x] Generate GPG signatures for POM, main JAR, source JAR, and javadoc JAR.
- [x] Verify generated `.asc` signatures locally.
- [ ] Review whether Java 25 is the intended public baseline for `1.9.0`.
- [ ] Review whether Guava should remain a public transitive dependency before
  `2.0.0`.
- [ ] Review whether JetBrains annotations should remain compile-scoped or move
  to optional/provided.

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
- [ ] Add a short public performance note with exact benchmark provenance.
- [ ] Add or link API docs once published javadocs are available.
- [ ] Decide how prominently to describe `1.9.0` as pre-2.0 in README after
  Central publication.

## Local Validation

- [x] Run `mvn -pl rmatch -am clean verify`.
- [x] Run Central profile signing build without deployment.
- [x] Verify signatures locally.
- [x] Inspect generated JAR manifest.
- [x] Inspect generated POM metadata.
- [x] Confirm `rmatch-tester` still builds with tests skipped.
- [ ] Run full reactor tests including `rmatch-tester` if practical.
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
- [ ] Run the chosen release benchmark smoke/gate and record exact result paths.

## Central Portal Upload

- [ ] Confirm there are no uncommitted release-branch changes.
- [ ] Confirm the release commit hash to upload.
- [x] Configure Central upload for manual validation:
  `autoPublish=false`, `waitUntil=VALIDATED`.
- [ ] Run the Central deploy command with `autoPublish=false`.
- [ ] Inspect the uploaded deployment in Central Portal.
- [ ] Confirm Central Portal validation status.
- [ ] Only after validation, publish/release the deployment.

## Git Tagging and Post-Release

- [ ] Create the `rmatch-1.9.0` tag only after Central validation is known.
- [ ] Push the release tag.
- [ ] Confirm artifact availability from Maven Central.
- [ ] Confirm the MvnRepository page updates.
- [ ] Update README if any "after publication" language should become present
  tense.
- [ ] Bump repository back to the agreed next development snapshot.
- [ ] Add a post-release note summarizing exactly what was published.

## Known Follow-Up Release Work

- [ ] Expand public API documentation for the callback coordinate convention.
- [ ] Decide whether to introduce a friendlier public facade before `2.0.0`.
- [ ] Decide final anchor/boundary assertion roadmap for `^`, `$`, `\b`, and
  `\B`.
- [ ] Decide compatibility claims versus `java.util.regex` for `2.0.0`.
- [ ] Decide how benchmark claims should be phrased and reproduced publicly.
