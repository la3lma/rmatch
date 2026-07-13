#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
VERSION=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' "$ROOT/pom.xml" | head -n 1)
MAIN_JAR="$ROOT/rmatch/target/rmatch-$VERSION.jar"
SOURCE_JAR="$ROOT/rmatch/target/rmatch-$VERSION-sources.jar"
JAVADOC_JAR="$ROOT/rmatch/target/rmatch-$VERSION-javadoc.jar"

"$ROOT/mvnw" -q -B -pl rmatch -am -Pcentral-release -DskipTests \
  -Dspotbugs.skip=true -Dgpg.skip=true clean verify

for artifact in "$MAIN_JAR" "$SOURCE_JAR" "$JAVADOC_JAR"; do
  test -s "$artifact" || { echo "Missing artifact: $artifact" >&2; exit 1; }
done

module=$(jar --describe-module --file "$MAIN_JAR")
grep -q '^no.rmz.rmatch@' <<<"$module"
grep -q '^exports no.rmz.rmatch$' <<<"$module"
if grep -q '^exports no.rmz.rmatch\.' <<<"$module"; then
  echo "Unexpected exported implementation package" >&2
  exit 1
fi

packages=$(jar tf "$JAVADOC_JAR" | grep 'package-summary.html$')
expected='no.rmz.rmatch/no/rmz/rmatch/package-summary.html'
[[ "$packages" == "$expected" ]] || {
  echo "Unexpected Javadoc package surface:" >&2
  printf '%s\n' "$packages" >&2
  exit 1
}

jar tf "$MAIN_JAR" | grep -q '^META-INF/MANIFEST.MF$'
jar tf "$MAIN_JAR" | grep -q '^META-INF/maven/no.rmz/rmatch/pom.properties$'
major=$(javap -verbose -classpath "$MAIN_JAR" no.rmz.rmatch.RMatch | sed -n 's/.*major version: //p')
[[ "$major" == 65 ]] || { echo "Expected Java 21 class major 65, got $major" >&2; exit 1; }

echo "Release artifacts PASS for no.rmz:rmatch:$VERSION"
printf '%s\n' "$module"
echo "Classfile major: $major (Java 21)"
