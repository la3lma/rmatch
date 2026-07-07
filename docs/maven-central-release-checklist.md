# Maven Central release checklist

This is the living checklist for publishing `no.rmz:rmatch` releases.

## Release Scope

- [ ] Confirm the release version and the next development snapshot version.
- [ ] Confirm that `rmatch-tester` is not part of the Maven Central release
  lane.
- [ ] Publish only `no.rmz:rmatch-parent` and `no.rmz:rmatch`.

## Artifact Hygiene

- [ ] Run a dependency hygiene check before every major release: review direct
  and transitive dependencies for known vulnerabilities, stale packages,
  unnecessary public/transitive exposure, license compatibility, and reasonable
  upgrades to current stable versions.
- [ ] Generate source JAR.
- [ ] Generate Javadoc JAR.
- [ ] Generate GPG signatures for POM, main JAR, source JAR, and Javadoc JAR.
- [ ] Verify generated `.asc` signatures locally.
- [ ] Inspect generated POM metadata and JAR manifest.

## Documentation

- [ ] Update README dependency snippets to the version being released.
- [ ] Compile and run the README quick-start example against the built artifact.
- [ ] Compile and run the README scratch-project example from a clean temporary
  Maven repository.
- [ ] Update public Javadocs for end-user-facing methods before release.
- [ ] Build generated Javadocs locally.
- [ ] Post-release: after the release is visible on javadoc.io, add or update a
  README badge and API-doc link:
  `[![Javadocs](https://javadoc.io/badge2/no.rmz/rmatch/javadoc.svg)](https://javadoc.io/doc/no.rmz/rmatch)`.

## Performance Evidence

- [ ] Run the chosen release benchmark smoke/gate and record exact result paths.
- [ ] Confirm benchmark inputs are byte-identical across compared engines.
- [ ] Confirm compared engines agree on match counts before using timing numbers
  as public performance evidence.
- [ ] Generate any public README performance chart only from a
  semantics-aligned benchmark receipt.

## Local Validation

- [ ] Run full reactor verification.
- [ ] Run the Central release profile without deployment.
- [ ] Run an external consumer smoke test using a clean temporary Maven project.
- [ ] Run an external consumer smoke test after Central publication using the
  artifact resolved from Maven Central rather than the local Maven repository.

## Central Portal Upload

- [ ] Confirm there are no uncommitted release-branch changes.
- [ ] Confirm the release commit hash to upload.
- [ ] Configure Central upload for manual validation unless auto-publishing is
  explicitly intended.
- [ ] Run the Central deploy command.
- [ ] Inspect the uploaded deployment in Central Portal.
- [ ] Only after validation, publish/release the deployment.

## Git Tagging and Post-Release

- [ ] Create and push the release tag only after Central validation is known.
- [ ] Confirm artifact availability from Maven Central.
- [ ] Confirm the MvnRepository page updates.
- [ ] Update the MvnRepository banner at the bottom of any README or project
  page where it is still used, and make sure it points to the latest version on
  Maven Central.
- [ ] Update README dependency and scratch-project examples to the newly
  published version, then rerun the clean-repository scratch-project smoke test
  against Central.
- [ ] Bump repository back to the agreed next development snapshot.
- [ ] Add a post-release note summarizing exactly what was published.
