rmatch
======

## What This Library Does

- `rmatch` is a Java library for matching many regular expressions against large input buffers in one pass-oriented matching pipeline.
- It is built for high-volume multi-pattern workloads where you register many patterns once, then scan large text data efficiently and trigger callbacks on matches.
- The implementation intentionally uses a reduced regex surface syntax compared to modern Java/PCRE-style regex engines.
- This was a deliberate trade-off: prioritize core matching efficiency first, then extend syntax breadth as the engine matures.

## Example Usage

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

## Regex Syntax (Current)

The current parser supports a deliberately small core language:

- Literal text: `abc`
- Concatenation: `ab` (implicit)
- Alternation: `a|b`
- Quantifiers on previous atom: `?`, `*`, `+`
- Any single character: `.`
- Line anchors: `^`, `$`
- Character classes: `[abc]`, ranges `[a-z]`, negated classes `[^abc]`

### Important Limitations

This is **not** full Java/PCRE regex syntax today. In particular, treat the following as unsupported/not guaranteed:

- Grouping and precedence control with parentheses: `( ... )`
- Counted quantifiers: `{m}`, `{m,n}`
- Lookaround: `(?=...)`, `(?!...)`, `(?<=...)`, `(?<!...)`
- Backreferences and capture-group features
- Shorthand classes and many escapes such as `\\d`, `\\w`, `\\s`, `\\b`
- Inline flags such as `(?i)`

This reduced syntax was a conscious engineering choice to prioritize matching-engine performance work first. As the core algorithms stabilize, extending syntax coverage is a natural next step.

## Repository Navigation

- Core library: [rmatch/](rmatch/)
- Tester and harness: [rmatch-tester/](rmatch-tester/)
- Benchmark platform repository: [rmatch-perftest](https://github.com/la3lma/rmatch-perftest)
- Documentation, plans, and papers repository: [rmatch-meta](https://github.com/la3lma/rmatch-meta)
- Historical archive repository: [rmatch-archive](https://github.com/la3lma/rmatch-archive)

## Developer A/B Performance Protocol (Using `rmatch-perftest`)

Use this workflow for branch-vs-`main` performance checks without keeping full perf orchestration inside this repo:

1. In `rmatch` on `main`, run correctness checks and publish locally:
   - `./mvnw -q test`
   - `./mvnw -q -DskipTests install`
2. In `rmatch-perftest`, run the baseline benchmark config (for example stable 10K/1MB+10MB gate config).
3. Switch to candidate branch in `rmatch`, run the same correctness checks, and install again:
   - `./mvnw -q test`
   - `./mvnw -q -DskipTests install`
4. In `rmatch-perftest`, rerun the exact same benchmark config.
5. Compare baseline vs candidate from `rmatch-perftest` reports/databases.

Why this works:

- `rmatch-perftest` contains benchmark orchestration (Docker/GCP/local), including multi-engine comparisons.
- `rmatch` contributes only the Maven artifact under test (resolved from local `~/.m2` during A/B).
- This keeps concerns separated and avoids carrying heavy campaign infrastructure in the core library repo.

## Benchmarking and Reports

Performance benchmarking, workload comparisons, and campaign reports now live outside this repository:

- Benchmark execution framework and run control: [rmatch-perftest](https://github.com/la3lma/rmatch-perftest)
- Benchmark writeups, snapshots, and analysis papers: [rmatch-meta](https://github.com/la3lma/rmatch-meta)

[![MvnRepository](https://badges.mvnrepository.com/badge/no.rmz/rmatch/badge.svg?label=MvnRepository&color=green)](https://mvnrepository.com/artifact/no.rmz/rmatch)
