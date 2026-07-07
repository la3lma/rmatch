# rmatch

[![Maven Central](https://img.shields.io/maven-central/v/no.rmz/rmatch.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/no.rmz/rmatch)

`rmatch` is a Java library for matching many regular expressions against large
input buffers with a one-pass-oriented matching pipeline. It is aimed at
high-volume multi-pattern workloads: register many patterns once, scan a large
text buffer, and receive callbacks for matches.

`1.9.x` is the pre-2.0 Maven Central line. The core matcher is usable and
actively benchmarked, while the public syntax/API contract is still being
polished toward a stable `2.0.0`.

## Use from Maven Central

Add rmatch to an existing Maven project:

```xml
<dependency>
  <groupId>no.rmz</groupId>
  <artifactId>rmatch</artifactId>
  <version>1.9.0</version>
</dependency>
```

For Gradle:

```kotlin
dependencies {
    implementation("no.rmz:rmatch:1.9.0")
}
```

`1.9.x` is compiled with `--release 21`, so consumers should use Java 21 or
newer. Maven Central also publishes source and Javadoc artifacts for IDEs and
API browsers.

## Copy-Paste Example

```java
import no.rmz.rmatch.impls.MatcherImpl;
import no.rmz.rmatch.utils.StringBuffer;

public class Example {
  public static void main(String[] args) throws Exception {
    MatcherImpl matcher = new MatcherImpl();

    matcher.add("ERROR|WARN", (buffer, start, end) -> {
      String match = buffer.getString(start, end);
      System.out.println("log-level match: " + match);
    });

    matcher.add("user:[a-z]+", (buffer, start, end) -> {
      System.out.println("user token match: " + buffer.getString(start, end));
    });

    matcher.match(new StringBuffer("INFO user:alice WARN disk nearly full"));
    matcher.shutdown();
  }
}
```

Expected output:

```text
user token match: user:alice
log-level match: WARN
```

## Regex Syntax

The current parser supports a deliberately small core language:

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

## Performance Comparisons

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
on a 32-logical-CPU AMD Ryzen 9 9950X3D machine on 2026-07-07 found:

- A 10-pattern / 1 MB sanity run had identical match counts across all three
  engines, but was too small to favor rmatch.
- Larger stable-pattern probes showed rmatch faster than RE2J, but also exposed
  match-count divergence between rmatch and the Java/RE2J loops on that
  generated workload.

For that reason, this README deliberately does not present a public speedup
chart yet. The raw receipts are checked in under
[docs/benchmark-receipts/agogo-2026-07-07](docs/benchmark-receipts/agogo-2026-07-07),
and the next public chart should be generated only from a semantics-aligned
workload where the compared engines agree on match counts.

## Release Notes and Roadmap

- [docs/maven-central-release-checklist.md](docs/maven-central-release-checklist.md)
  tracks the Maven Central release checklist.
- [docs/regex-syntax-roadmap.md](docs/regex-syntax-roadmap.md) tracks syntax
  coverage toward `2.0.0`.

## Repository Layout

- [rmatch/](rmatch/) contains the public library artifact `no.rmz:rmatch`.
- [rmatch-tester/](rmatch-tester/) contains local performance and experiment
  tooling; it is not part of the Maven Central release lane.
