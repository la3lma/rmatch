#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
BASELINE="$ROOT/docs/api/2.0.0-RC1-public-api.txt"
VERSION=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' "$ROOT/pom.xml" | head -n 1)
JAR="$ROOT/rmatch/target/rmatch-$VERSION.jar"
ACTUAL=$(mktemp "${TMPDIR:-/tmp}/rmatch-public-api.XXXXXX")
trap 'rm -f "$ACTUAL"' EXIT

if [[ ! -f "$JAR" ]]; then
  "$ROOT/mvnw" -q -B -pl rmatch -am -DskipTests -Dspotbugs.skip=true package
fi

classes=(
  no.rmz.rmatch.Action
  no.rmz.rmatch.Buffer
  no.rmz.rmatch.Matcher
  no.rmz.rmatch.PatternFlag
  no.rmz.rmatch.RMatch
  no.rmz.rmatch.RegexpParserException
)

for class in "${classes[@]}"; do
  javap -public -classpath "$JAR" "$class"
done >"$ACTUAL"

if ! diff -u "$BASELINE" "$ACTUAL"; then
  echo "Public API differs from the reviewed 2.0.0-RC1 baseline." >&2
  echo "Review the change; update the baseline only when it is intentional." >&2
  exit 1
fi

echo "Public API matches $BASELINE"
