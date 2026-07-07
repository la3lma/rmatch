# rmatch

[![Maven Central](https://img.shields.io/maven-central/v/no.rmz/rmatch.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/no.rmz/rmatch)

`rmatch` is a Java library for matching many regular expressions against large
text buffers with one pass-oriented matching pipeline. It is aimed at workloads
where many patterns are registered once and then reused against large corpora,
for example log scanning, rule matching, and other high-volume multi-pattern
search tasks.

`1.9.x` is a pre-2.0 Maven Central release-candidate line. The engine is useful
and benchmark-positive, while the public syntax/API contract is still being
polished toward a stable `2.0.0`.

## Why Use rmatch?

The benchmark question for rmatch is not "can it beat one regex on one short
string?" It is: when many patterns must be applied to the same corpus, how does
a one-pass multi-pattern engine compare with engines that normally run one
compiled pattern at a time?

The public comparison model is:

- `java.util.regex` naive loop: compile the same pattern set, then run each
  compiled Java regex over the corpus.
- RE2J loop: compile the same pattern set with RE2J, then run each compiled
  RE2J regex over the corpus.
- rmatch: register the same pattern set in one matcher and scan the corpus once.

Current release gates use byte-identical inputs and require match counts to
agree before timing numbers are treated as public evidence. A fresh Docker run
on a 32-logical-CPU AMD Ryzen 9 9950X3D machine on 2026-07-07 used deterministic
literal-token patterns over an 8 MiB corpus. The 1k-pattern case still favors
RE2J, which is expected for smaller literal workloads. At 5k and 10k patterns,
the one-pass rmatch scan pulls ahead while the per-pattern loops continue to
scale with the number of patterns.

![rmatch README efficiency comparison](docs/benchmark-receipts/agogo-2026-07-07/readme-efficiency-large/readme-efficiency-scanning.svg)

| Patterns | Match count | rmatch (s) | RE2J (s) | Java regex loop (s) |
|---:|---:|---:|---:|---:|
| 1,000 | 11,000 | 1.885 | 1.143 | 3.091 |
| 5,000 | 52,721 | 2.075 | 4.073 | 15.314 |
| 10,000 | 102,721 | 2.316 | 7.914 | 30.740 |

The raw receipts, chart inputs, and earlier diagnostic runs are checked in
under
[docs/benchmark-receipts/agogo-2026-07-07](docs/benchmark-receipts/agogo-2026-07-07).

Use rmatch when the workload looks like this:

- You have hundreds, thousands, or tens of thousands of patterns.
- You apply the same pattern set to large buffers or many large documents.
- You care more about aggregate scanning throughput than PCRE-compatible syntax
  breadth.
- You can work within a regular-language subset and do not need captures,
  backreferences, or lookaround.

Do not use rmatch just because the API is pleasant. A convenient API is there
to remove adoption friction; the reason to reach for this library is the
many-pattern scaling profile.

When contributing to rmatch development, performance regression testing is part
of the work: almost all plausible matcher improvements are not improvements
until measured against the same inputs with the same correctness expectations.
The branch-vs-`main` protocol lives in
[docs/performance-regression-testing.md](docs/performance-regression-testing.md).

## Use from Maven Central

Add rmatch to an existing Maven project:

```xml
<dependency>
  <groupId>no.rmz</groupId>
  <artifactId>rmatch</artifactId>
  <version>1.9.1</version>
</dependency>
```

`1.9.x` is compiled with `--release 21`, so consumers should use Java 21 or
newer. Maven Central also publishes source and Javadoc artifacts for IDEs and
API browsers.

For Gradle:

```kotlin
dependencies {
    implementation("no.rmz:rmatch:1.9.1")
}
```

## Copy-Paste Example

For a scratch project, create this structure:

```text
rmatch-demo/
  pom.xml
  src/main/java/Example.java
```

For a complete scratch project, use this full `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>rmatch-demo</artifactId>
  <version>1.0.0</version>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>no.rmz</groupId>
      <artifactId>rmatch</artifactId>
      <version>1.9.1</version>
    </dependency>
  </dependencies>
</project>
```

Put this in `src/main/java/Example.java`. It registers two patterns once, scans
one buffer, and prints both matches.

```java
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.interfaces.Buffer;
import no.rmz.rmatch.utils.RegexStringBuffer;

public class Example {
  public static void main(String[] args) throws Exception {
    MatcherImpl matcher = new MatcherImpl();

    matcher.add("ERROR|WARN", (buffer, start, end) -> {
      System.out.println("log-level match: " + matchedText(buffer, start, end));
    });

    matcher.add("user:[a-z]+", (buffer, start, end) -> {
      System.out.println("user token match: " + matchedText(buffer, start, end));
    });

    matcher.match(new RegexStringBuffer("INFO user:alice WARN disk nearly full"));
    matcher.shutdown();
  }

  private static String matchedText(Buffer buffer, int start, int end) {
    // rmatch callbacks use inclusive start/end offsets.
    return buffer.getString(start, end + 1);
  }
}
```

Run it:

```bash
mvn -q compile exec:java -Dexec.mainClass=Example
```

Expected output:

```text
user token match: user:alice
log-level match: WARN
```

`MatcherImpl` uses the fast-path engine by default. The callback receives the
matched buffer and inclusive start/end offsets. rmatch reports the longest match
for each start position; overlapping matches from different start positions may
therefore be reported.

For one small pattern against one small string, `java.util.regex` is usually the
simpler tool. rmatch is for many-pattern workloads where avoiding a separate
regex search for every pattern matters.

## Supported Syntax in 1.9.x

The parser intentionally supports a reduced regular-expression language. This
is not a drop-in replacement for `java.util.regex` or PCRE.

- Literal text: `abc`
- Concatenation: `ab` (implicit)
- Alternation: `a|b`
- Quantifiers on the previous atom: `?`, `*`, `+`
- Counted quantifiers: `{m}`, `{m,n}`, `{m,}` (expansion capped at 1000)
- Grouping: `( ... )` and `(?: ... )` (equivalent; there are no captures)
- Any single character: `.` (matches every character, including newline)
- Character classes: `[abc]`, ranges `[a-z]`, negated classes `[^abc]`
- Shorthand classes: `\d`, `\D`, `\w`, `\W`, `\s`, `\S`
- Escapes: `\\`, `\.`, `\*`, `\+`, `\?`, `\[`, `\(`, `\n`, `\t`, `\r`, `\f`
- Pattern-prefix flags: `(?i)` for case-insensitive matching; `(?s)` is
  accepted because `.` is already DOTALL

## Important Limitations

These constructs are not part of the supported `1.9.x` surface:

- Line anchors `^` and `$`
- Word boundaries `\b` and `\B`
- Lookaround: `(?=...)`, `(?!...)`, `(?<=...)`, `(?<!...)`
- Backreferences and capture-group features
- Scoped inline flags such as `a(?i)b`
- `MULTILINE` mode and a non-DOTALL `.` toggle

Backreferences are intentionally out of scope because they are non-regular.
Other limitations are candidates for the 2.0 work, especially the anchor and
boundary-assertion machinery.

## Release Notes and Roadmap

- [CHANGELOG.md](CHANGELOG.md) describes the `1.9.x` pre-release line.
- [docs/release.md](docs/release.md) documents the Maven Central release lane.
- [docs/maven-central-release-checklist.md](docs/maven-central-release-checklist.md)
  tracks the current release checklist.
- [docs/regex-syntax-roadmap.md](docs/regex-syntax-roadmap.md) tracks syntax
  coverage toward `2.0.0`.

## Repository Layout

- [rmatch/](rmatch/) contains the public library artifact `no.rmz:rmatch`.
- [rmatch-tester/](rmatch-tester/) contains local performance and experiment
  tooling; it is not part of the Maven Central release lane.
- [rmatch-perftest](https://github.com/la3lma/rmatch-perftest) contains the
  benchmark platform and multi-engine comparison harness.
- [rmatch-meta](https://github.com/la3lma/rmatch-meta) contains longer-form
  benchmark writeups, plans, and analysis papers.

## Developer Performance Workflow

Use `rmatch-perftest` for branch-vs-`main` performance checks:

1. In `rmatch` on `main`, run correctness checks and install locally:
   `./mvnw -q test && ./mvnw -q -DskipTests install`
2. In `rmatch-perftest`, run the baseline benchmark config and keep the result
   directory.
3. Switch to the candidate branch in `rmatch`, rerun the same checks, and
   install locally again.
4. In `rmatch-perftest`, rerun the exact same benchmark config, reusing the
   same pattern and corpus files.
5. Compare baseline vs candidate from the generated reports, raw JSON, or
   database artifacts. Treat the comparison as valid only when inputs and match
   counts agree.

Keeping the benchmark campaign machinery in `rmatch-perftest` keeps the Maven
library artifact small and focused.
