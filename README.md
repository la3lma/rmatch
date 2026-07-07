# rmatch

[![MvnRepository](https://badges.mvnrepository.com/badge/no.rmz/rmatch/badge.svg?label=MvnRepository&color=green)](https://mvnrepository.com/artifact/no.rmz/rmatch)

`rmatch` is a Java library for matching many regular expressions against large
text buffers with one pass-oriented matching pipeline. It is aimed at workloads
where many patterns are registered once and then reused against large corpora,
for example log scanning, rule matching, and other high-volume multi-pattern
search tasks.

`1.9.0` is a pre-2.0 Maven Central release-candidate line. The engine is useful
and benchmark-positive, while the public syntax/API contract is still being
polished toward a stable `2.0.0`.

## Installation

Maven, after the `1.9.0` Central publication:

```xml
<dependency>
  <groupId>no.rmz</groupId>
  <artifactId>rmatch</artifactId>
  <version>1.9.0</version>
</dependency>
```

`1.9.x` is compiled with `--release 21`, so consumers should use Java 21 or
newer. Release smoke tests have also been run with newer JDKs.

## Quick Start

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

`MatcherImpl` uses the fast-path engine by default. The callback receives the
matched buffer and inclusive start/end offsets. rmatch reports the longest match
for each start position; overlapping matches from different start positions may
therefore be reported.

## Supported Syntax in 1.9.0

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

These constructs are not part of the supported `1.9.0` surface:

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

- [CHANGELOG.md](CHANGELOG.md) describes the `1.9.0` pre-release line.
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
2. In `rmatch-perftest`, run the baseline benchmark config.
3. Switch to the candidate branch in `rmatch`, rerun the same checks, and
   install locally again.
4. In `rmatch-perftest`, rerun the exact same benchmark config.
5. Compare baseline vs candidate from the generated reports/databases.

Keeping the benchmark campaign machinery in `rmatch-perftest` keeps the Maven
library artifact small and focused.
