rmatch
======

## Current Performance Comparison

| Metric | rmatch | Java Regex | Ratio (rmatch/java) |
|--------|--------|------------|---------------------|
| **5000 patterns** | 15.1s | 4.1s | 3.7x slower |
| **Peak Memory** | 108MB | 19MB | 5.7x more memory |
| **Pattern Loading** | 19MB | 0MB | 0.0x less memory |
| **Matching Phase** | 74MB | 4MB | 18.5x more memory |

*Latest benchmark comparison between rmatch and native Java regex (java.util.regex.Pattern) on 5000 regex patterns against Wuthering Heights corpus. Updated: 2025-11-05 19:03 UTC*

---

## Performance Timeline Charts

### rmatch Performance History
![rmatch Benchmark Performance](performance_timeline.png)

### Java Regex Performance History  
![Java Regex Benchmark Performance](java_performance_timeline.png)

### Performance Comparison (rmatch vs Java Regex)
![Performance Comparison](performance_comparison.png)

*Live performance tracking from macro benchmarks. Individual charts show execution time and memory usage patterns over time, while the comparison chart shows rmatch performance ratios relative to Java regex (values > 1.0 mean rmatch is slower/uses more memory).*

---

rmatch

The project is getting closer to a state where it may be useful for others
than myself, but it's not quite there yet.  Be patient ;)


### Key Performance Metrics

- **Benchmark Data Sources**: All performance data is sourced from `benchmarks/results/`
- **JMH Microbenchmarks**: Precise timing measurements with statistical confidence intervals  
- **Macro Benchmarks**: End-to-end performance testing with real workloads
- **Automated Tracking**: Performance evolution tracked continuously via GitHub Actions



[![codebeat badge](https://codebeat.co/badges/0a25fe03-4371-4c5f-a125-ab524f477898)](https://codebeat.co/projects/github-com-la3lma-rmatch-master)

[![Maintainability](https://api.codeclimate.com/v1/badges/ecfba15253e7095438fb/maintainability)](https://codeclimate.com/repos/64a2ba4d1c8c821c92003b52/maintainability)

[![Test Coverage](https://api.codeclimate.com/v1/badges/ecfba15253e7095438fb/test_coverage)](https://codeclimate.com/repos/64a2ba4d1c8c821c92003b52/test_coverage)

---

## GC Optimization for Java 25

rmatch includes tools to experiment with different Garbage Collector (GC) configurations on Java 25 to optimize memory usage and performance.

### Quick Start

Validate GC configurations:
```bash
scripts/validate_gc_configs.sh
```

Run benchmarks with all GC variants:
```bash
make bench-gc-experiments
```

### Available GC Configurations

- **G1 (default)**: General-purpose GC with good throughput
- **ZGC Generational**: Low-latency GC for large heaps
- **Shenandoah**: Concurrent GC with predictable pause times
- **Compact Object Headers**: Reduces memory footprint (Java 25 feature)

### Documentation

See [GC_EXPERIMENTS.md](GC_EXPERIMENTS.md) for:
- Detailed usage instructions
- How to analyze results
- Applying findings to improve performance
- References to Java 25 GC improvements

The experiments help identify optimal GC settings for regex engines with high object churn, following recommendations from the [JDK 25 Performance Improvements](https://inside.java/2025/10/20/jdk-25-performance-improvements/) article.

