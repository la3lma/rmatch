# rmatch release process

This document describes the cautious release lane for rmatch.
The detailed living checklist is in
[maven-central-release-checklist.md](maven-central-release-checklist.md).

## Version line

- `1.9.x`: Maven Central release-candidate line. Functionality is useful and
  benchmark-positive, but syntax/API documentation is still being hardened.
- `1.99.x`: optional final pre-2.0 preview line if the syntax/API contract needs
  one last public shakeout.
- `2.0.0`: stable, fully documented release line.

The first candidate is `2.0.0-RC1`. It requires Java 21 or newer; artifacts
compiled with `--release 21` cannot run on an older JVM. The release note is
[release-notes-2.0.0-RC1.md](release-notes-2.0.0-RC1.md), and
the proposed stable language contract is
[regex-syntax-and-semantics.md](regex-syntax-and-semantics.md).

## Public artifact lane

For the 2.0 release-candidate line, publish only:

- `no.rmz:rmatch-parent`
- `no.rmz:rmatch`

Do not publish `rmatch-tester`; it is project-local benchmark and experiment
tooling.

## Compatibility policy

Release candidates expose the proposed 2.0 contract for feedback and may still
change incompatibly before the final release. Starting with `2.0.0`, semantic
versioning covers the exported `no.rmz.rmatch` API and documented syntax and
matching behavior. Incompatible API removal requires the next major release.
The normal path is deprecation in at least one 2.x minor release followed by
removal in the next major release; severe security or correctness failures may
require a faster, explicitly documented exception.

Run `make api-compat-check` before an RC or final release. The checked-in RC1
signature is the review baseline for later candidates; it becomes the stable
2.0 baseline only when `2.0.0` is released.

## Signing identity

Release artifacts are signed with:

```text
9017955845408C9B4422B5DE55D9C01E75B1E582
Bjorn Remseth <rmz@rmz.no>
```

The public key is available from `keys.openpgp.org`.

## Local preflight

Run these from a clean release branch:

```bash
mvn -pl rmatch -am clean verify

mvn -pl rmatch -am \
  -Pcentral-release \
  -DskipTests \
  -Dspotbugs.skip=true \
  -Dgpg.keyname=55D9C01E75B1E582 \
  verify
```

Verify signatures:

```bash
for asc in target/*.asc rmatch/target/*.asc; do
  artifact=${asc%.asc}
  gpg --verify "$asc" "$artifact"
done
```

Check that `rmatch` does not leak test dependencies:

```bash
mvn -q -pl rmatch dependency:tree -Dscope=compile
```

Expected compile/runtime dependencies for `no.rmz:rmatch` are empty.

## Maven Central upload

Only after local preflight succeeds and the release notes have been reviewed:

```bash
mvn -pl rmatch -am \
  -Pcentral-release \
  -DskipTests \
  -Dspotbugs.skip=true \
  -Dgpg.keyname=55D9C01E75B1E582 \
  deploy
```

The Central publishing plugin is configured with `autoPublish=false`, so the
upload should land in the Central Portal for manual inspection before final
publication.

Do not push a tag until the Central Portal validation result is known.
