rmatch
======

## Current Performance Comparison

| Metric | rmatch | Java Regex | Ratio (rmatch/java) |
|--------|--------|------------|---------------------|
| **1000 patterns** | 2.2s | 1.6s | 1.4x slower |
| **Peak Memory** | 30MB | 15MB | 2.0x more memory |
| **Pattern Loading** | 4MB | 1MB | 4.0x more memory |
| **Matching Phase** | 12MB | 0MB | 0.0x less memory |

*Latest benchmark comparison between rmatch and native Java regex (java.util.regex.Pattern) on 1000 regex patterns against Wuthering Heights corpus. Updated: 2025-09-09 16:40 UTC*

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

## Performance Analytics

### Benchmark Overview

![Performance Overview](charts/performance_overview.png)

*Comprehensive performance tracking based on automated benchmark results from JMH microbenchmarks and macro-scale testing.*

### Detailed JMH Performance Evolution

![JMH Performance Evolution](charts/jmh_performance_evolution.png)

*Detailed analysis of JMH benchmark results showing build performance trends over time and performance distribution statistics.*

### Key Performance Metrics

- **Benchmark Data Sources**: All performance data is sourced from `benchmarks/results/`
- **JMH Microbenchmarks**: Precise timing measurements with statistical confidence intervals  
- **Macro Benchmarks**: End-to-end performance testing with real workloads
- **Automated Tracking**: Performance evolution tracked continuously via GitHub Actions

> 📊 **Performance Data**: Charts are automatically generated from benchmark results stored in `benchmarks/results/`. 
> See [PERFORMANCE_CHARTS.md](PERFORMANCE_CHARTS.md) for detailed documentation on benchmark data formats and chart generation.

You need to install mvnw by doing:

      mvn -q -B wrapper:wrapper -Dmaven=3.9.10
      git add mvnw mvnw.cmd .mvn/wrapper/*
      mvn -q -B verify
      git commit -m "build: add Maven Wrapper (3.9.9)"


Also install async profiler

      brew tap qwwdfsad/tap
      brew install async-profiler
      asprof --version


[![codebeat badge](https://codebeat.co/badges/0a25fe03-4371-4c5f-a125-ab524f477898)](https://codebeat.co/projects/github-com-la3lma-rmatch-master)

[![Maintainability](https://api.codeclimate.com/v1/badges/ecfba15253e7095438fb/maintainability)](https://codeclimate.com/repos/64a2ba4d1c8c821c92003b52/maintainability)

[![Test Coverage](https://api.codeclimate.com/v1/badges/ecfba15253e7095438fb/test_coverage)](https://codeclimate.com/repos/64a2ba4d1c8c821c92003b52/test_coverage)

