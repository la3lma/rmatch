# Performance Regression Testing

When contributing to rmatch development, performance regression testing is part
of the work. Almost all plausible matcher improvements are not improvements
until they have been measured against the same inputs, on the same machine, with
the same correctness expectations.

This is especially true for this codebase because many changes that look
locally faster can move work elsewhere: from scanning to compilation, from one
pattern shape to another, from small corpora to large corpora, or from correct
matching to subtly different matching.

## Developer A/B Performance Protocol

Use this workflow for branch-vs-`main` performance checks without keeping the
full benchmark orchestration inside this repository.

1. In `rmatch` on `main`, run correctness checks and publish locally:

   ```bash
   ./mvnw -q test
   ./mvnw -q -DskipTests install
   ```

2. In the benchmark framework, run the baseline benchmark config and keep the
   result directory.

3. Switch to the candidate branch in `rmatch`, run the same correctness checks,
   and install again:

   ```bash
   ./mvnw -q test
   ./mvnw -q -DskipTests install
   ```

4. In the benchmark framework, rerun the exact same benchmark config.

5. Compare baseline vs candidate from the generated reports, raw JSON, or
   database artifacts.

Treat the comparison as valid only when:

- The pattern files are byte-identical.
- The corpus files are byte-identical.
- The compared engines report matching counts that are identical or otherwise
  explicitly explained.
- The same metric is compared on both sides, usually median `scanning_ns`.
- The run records enough provenance to repeat the result later.

For README-facing performance evidence, first run the benchmark framework's
fast guardrail tests:

```bash
cd ../rmatch-perftest/benchmarking/framework/regex_bench_framework
make test-readme-efficiency
```

Those tests do not prove a speedup. They prove that the public-evidence path is
using deterministic literal-token inputs, refuses cross-engine match-count
mismatches, and does not silently substitute an unrelated corpus.

## Separation of Concerns

The benchmark campaign machinery lives outside the Maven library artifact.
This repository contributes the artifact under test; the benchmark framework
does the multi-engine orchestration, input generation, result capture, and
comparison.

That separation keeps the public Maven artifact small while still making
performance work reproducible enough to trust.
