rmatch
======

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

