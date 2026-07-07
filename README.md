# rmatch

[![MvnRepository](https://badges.mvnrepository.com/badge/no.rmz/rmatch/badge.svg?label=MvnRepository&color=green)](https://mvnrepository.com/artifact/no.rmz/rmatch)

`rmatch` is a Java library for matching many regular expressions against large
text buffers with one pass-oriented matching pipeline. It is aimed at workloads
where many patterns are registered once and then reused against large corpora,
for example log scanning, rule matching, and other high-volume multi-pattern
search tasks.

`1.9.x` is a pre-2.0 Maven Central release-candidate line. The engine is useful
and benchmark-positive, while the public syntax/API contract is still being
polished toward a stable `2.0.0`.

## Installation

Maven, after the `1.9.x` Central publication:

```xml
<dependency>
  <groupId>no.rmz</groupId>
  <artifactId>rmatch</artifactId>
  <version>1.9.1</version>
</dependency>
```

`1.9.x` is compiled with `--release 21`, so consumers should use Java 21 or
newer. Release smoke tests have also been run with newer JDKs.

## Copy-Paste Example

Put this dependency in your Maven project, then copy the class below and run it.
It registers two patterns once, scans one buffer, and prints both matches.

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

Use rmatch when you have many patterns and you want to scan the same large text
stream or corpus without running a separate regex search for every pattern.
For one small pattern against one small string, `java.util.regex` is usually the
simpler tool.

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

## Performance Comparisons

The benchmark question we care about is not "can rmatch beat one regex on one
short string?" It is: when many patterns must be applied to the same corpus, how
does a one-pass multi-pattern engine compare with engines that normally run one
compiled pattern at a time?

We therefore compare rmatch against:

- `java.util.regex` naive loop: compile the same pattern set, then run each
  compiled Java regex over the corpus.
- RE2J loop: compile the same pattern set with RE2J, then run each compiled RE2J
  regex over the corpus.
- rmatch: register the same pattern set in one matcher and scan the corpus once.

The comparisons use the supported rmatch syntax subset only. Patterns and corpus
files are kept byte-identical across engines, and benchmark runs check that
match counts remain consistent before performance numbers are treated as useful.
This matters: a faster run with different inputs or different match semantics is
not evidence.

The numbers and tables we use come from the separate
[rmatch-perftest](https://github.com/la3lma/rmatch-perftest) harness. That
harness records raw per-job measurements such as:

- `compilation_ns`: time to compile/register the pattern set.
- `scanning_ns`: time spent scanning the corpus after compilation.
- `total_ns`: end-to-end time for the benchmark job.
- `match_count`: number of matches reported for correctness/provenance checks.

For release gating, we use median `scanning_ns` over repeated runs, compare
candidate and baseline runs on the same machine, and require byte-identical
input files. The current `1.9.1` release-prep gate used the stable 10K-pattern
moderate workload in `rmatch-perftest`, with 1 MB and 10 MB corpora, and passed
without performance regression after the Java 21 baseline change.

Longer benchmark campaigns, charts, and reports belong in `rmatch-perftest` and
`rmatch-meta`; this repository keeps the Maven library and the short public
explanation close to the code.

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
