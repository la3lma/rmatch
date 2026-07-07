# rmatch release process

This document describes the cautious release lane for the pre-2.0 series.
The detailed living checklist is in
[maven-central-release-checklist.md](maven-central-release-checklist.md).

## Version line

- `1.9.x`: Maven Central release-candidate line. Functionality is useful and
  benchmark-positive, but syntax/API documentation is still being hardened.
- `1.99.x`: optional final pre-2.0 preview line if the syntax/API contract needs
  one last public shakeout.
- `2.0.0`: stable, fully documented release line.

## Public artifact lane

For the `1.9.x` line, publish only:

- `no.rmz:rmatch-parent`
- `no.rmz:rmatch`

Do not publish `rmatch-tester`; it is project-local benchmark and experiment
tooling.

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

Expected compile/runtime dependencies for `no.rmz:rmatch` are limited to
Aho-Corasick.

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
