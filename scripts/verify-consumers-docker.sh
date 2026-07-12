#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
VERSION=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' "$ROOT/pom.xml" | head -n 1)
WORK=$(mktemp -d "${TMPDIR:-/tmp}/rmatch-consumers.XXXXXX")
M2="$WORK/m2"
MAVEN_IMAGE=${MAVEN_IMAGE:-maven:3.9-eclipse-temurin-21}
GRADLE_IMAGE=${GRADLE_IMAGE:-gradle:8.14-jdk21}
cleanup() {
  docker run --rm -v "$WORK:/work" "$MAVEN_IMAGE" \
    sh -c 'rm -rf /work/* /work/.[!.]* /work/..?*' >/dev/null 2>&1 || true
  rmdir "$WORK" 2>/dev/null || true
}
trap cleanup EXIT

mkdir -p "$M2" "$WORK/src/example" "$WORK/maven/src/main/java/example"
mkdir -p "$WORK/gradle/src/main/java/example" "$WORK/jpms/src/example.consumer/example"

cat > "$WORK/src/example/Example.java" <<'JAVA'
package example;

import java.util.concurrent.atomic.AtomicInteger;
import no.rmz.rmatch.RMatch;

public final class Example {
  public static void main(String[] args) throws Exception {
    AtomicInteger matches = new AtomicInteger();
    try (var matcher = RMatch.newMatcher(2)) {
      matcher.add("WARN|ERROR", (buffer, start, end) -> matches.incrementAndGet());
      matcher.match(RMatch.stringBuffer("INFO WARN ERROR"));
    }
    if (matches.get() != 2) {
      throw new AssertionError("expected 2 matches, got " + matches.get());
    }
    System.out.println("consumer PASS: " + matches.get() + " matches");
  }
}
JAVA

cp "$WORK/src/example/Example.java" "$WORK/maven/src/main/java/example/Example.java"
cp "$WORK/src/example/Example.java" "$WORK/gradle/src/main/java/example/Example.java"
cp "$WORK/src/example/Example.java" "$WORK/jpms/src/example.consumer/example/Example.java"

cat > "$WORK/maven/pom.xml" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId><artifactId>consumer</artifactId><version>1</version>
  <properties><maven.compiler.release>21</maven.compiler.release></properties>
  <dependencies><dependency><groupId>no.rmz</groupId><artifactId>rmatch</artifactId><version>$VERSION</version></dependency></dependencies>
</project>
XML

cat > "$WORK/gradle/settings.gradle.kts" <<'GRADLE'
rootProject.name = "rmatch-consumer"
GRADLE
cat > "$WORK/gradle/build.gradle.kts" <<GRADLE
plugins { application }
repositories { maven { url = uri("$M2") }; mavenCentral() }
dependencies { implementation("no.rmz:rmatch:$VERSION") }
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
application { mainClass = "example.Example" }
GRADLE

cat > "$WORK/jpms/src/example.consumer/module-info.java" <<'JAVA'
module example.consumer {
  requires no.rmz.rmatch;
}
JAVA

docker run --rm -v "$ROOT:/src" -v "$M2:/m2" -w /src "$MAVEN_IMAGE" \
  mvn -q -B -pl rmatch -am -Dmaven.repo.local=/m2 -DskipTests -Dspotbugs.skip=true install

docker run --rm -v "$WORK/maven:/work" -v "$M2:/m2" -w /work "$MAVEN_IMAGE" \
  mvn -q -B -Dmaven.repo.local=/m2 package
docker run --rm -v "$WORK/maven:/work" -v "$M2:/m2" -w /work "$MAVEN_IMAGE" \
  java -cp "target/classes:/m2/no/rmz/rmatch/$VERSION/rmatch-$VERSION.jar" example.Example

docker run --rm -v "$WORK/gradle:/work" -v "$M2:$M2" -w /work "$GRADLE_IMAGE" \
  gradle --no-daemon --console=plain run

mkdir -p "$WORK/classpath-classes" "$WORK/jpms-mods"
docker run --rm -v "$WORK:/work" -w /work "$MAVEN_IMAGE" sh -c \
  "javac --release 21 -cp m2/no/rmz/rmatch/$VERSION/rmatch-$VERSION.jar -d classpath-classes src/example/Example.java && java -cp classpath-classes:m2/no/rmz/rmatch/$VERSION/rmatch-$VERSION.jar example.Example"

docker run --rm -v "$WORK:/work" -w /work "$MAVEN_IMAGE" sh -c \
  "javac --release 21 --module-path m2/no/rmz/rmatch/$VERSION/rmatch-$VERSION.jar -d jpms-mods --module-source-path jpms/src -m example.consumer && java --module-path jpms-mods:m2/no/rmz/rmatch/$VERSION/rmatch-$VERSION.jar -m example.consumer/example.Example"

echo "All consumer modes PASS for no.rmz:rmatch:$VERSION"
