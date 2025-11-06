rmatch
======

## Current Performance Comparison

| Metric | rmatch | Java Regex | Ratio (rmatch/java) |
|--------|--------|------------|---------------------|
| **5000 patterns** | 16.4s | 4.3s | 3.8x slower |
| **Peak Memory** | 108MB | 19MB | 5.7x more memory |
| **Pattern Loading** | 19MB | 0MB | 0.0x less memory |
| **Matching Phase** | 74MB | 4MB | 18.5x more memory |

*Latest benchmark comparison between rmatch and native Java regex (java.util.regex.Pattern) on 5000 regex patterns against Wuthering Heights corpus. Updated: 2025-11-06 11:34 UTC*

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

## 🚨 CRITICAL: Performance Validation Rule

> **"I will not merge anything to main that does not provably improve performance."**

### Development Guidelines for Performance Changes

**ALL performance optimizations MUST:**

1. **✅ Use Production-Scale Testing**: Test with 5000+ regex workloads against real text corpora
2. **✅ Show Measurable Improvement**: Demonstrate clear performance gains in comprehensive benchmarks  
3. **❌ Never Trust Micro-Benchmarks**: Small-scale synthetic tests are insufficient and often misleading
4. **❌ Never Trust Theoretical Improvements**: Code that "should be faster" must prove it IS faster

### Lessons Learned

- **Enum switching** theoretical 13.6% improvement → **Actually slower** in production
- **Pattern matching instanceof** → **1.7% performance regression**  
- **Character classification optimizations** → **2-9% performance regression**

**The Rule Exists Because:** Brilliant optimization ideas are necessary, but they must be proven in our specific use case with realistic workloads before adoption.

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

---

## ⚡ Java 25 JIT Optimization (Production Ready)

rmatch includes proven JIT optimization techniques for Java 25 that provide significant performance improvements in production workloads.

### Recommended Production Configuration

For optimal performance across all workload sizes:

```bash
export JAVA_OPTS="-Drmatch.engine=fastpath -Drmatch.prefilter=aho -XX:+TieredCompilation -XX:CompileThreshold=500"
```

### Performance Results

**Test Environment:** Apple M2 Max (aarch64), macOS 26.0.1, OpenJDK 25 (Temurin-25+36-LTS)

| Configuration | 5K Patterns | 10K Patterns |
|---------------|-------------|--------------|
| **Baseline** | 10,230ms | 21,656ms |
| **Optimized** | **9,895ms** (+3.3%) | **19,297ms** (+10.9%) |

*Performance characteristics may vary across different architectures. See [FASTPATH_PERFORMANCE_ANALYSIS.md](FASTPATH_PERFORMANCE_ANALYSIS.md) for detailed architecture specifications and cross-platform considerations.*

### Key Benefits

- **✅ Scales with workload size**: 3.3% improvement at 5K, 10.9% at 10K patterns
- **✅ Consistent performance**: 8% coefficient of variation across runs  
- **✅ Production validated**: Tested with comprehensive benchmarks using real text corpora
- **✅ Easy to apply**: Simple environment variable configuration

### Advanced Configuration

For specific workload tuning:

```bash
# Small workloads (≤5K patterns): Pure ASCII optimization
export JAVA_OPTS="-Drmatch.engine=fastpath -Drmatch.prefilter.threshold=99999 -XX:+TieredCompilation -XX:CompileThreshold=500"

# Large workloads (≥10K patterns): Maximum prefilter benefits  
export JAVA_OPTS="-Drmatch.engine=fastpath -Drmatch.prefilter.threshold=5000 -XX:+TieredCompilation -XX:CompileThreshold=500"
```

### Documentation

See [FASTPATH_PERFORMANCE_ANALYSIS.md](FASTPATH_PERFORMANCE_ANALYSIS.md) for:
- Complete performance analysis and validation methodology
- JIT technique explanations and benchmark results
- Component-by-component optimization breakdown

---

## Dispatch Optimization Experiments

rmatch includes benchmarks to test modern Java language features for dispatch pattern optimization on Java 25.

### Quick Start

Run dispatch optimization benchmarks:
```bash
make bench-dispatch
```

Or run the script directly:
```bash
scripts/run_dispatch_benchmarks.sh
```

### What's Tested

The benchmarks evaluate three optimization strategies:

1. **Pattern Matching for instanceof** - Java 16+ pattern matching vs traditional cast
2. **Switch Expressions** - Enhanced switch with arrow syntax vs if-else chains  
3. **Enum Dispatch** - Switch expressions vs if-else for enum handling

### Key Findings

Based on empirical testing:
- ✅ **Enum switch expressions**: 13.6% faster than if-else chains - RECOMMENDED
- ❌ **Pattern matching instanceof**: No measurable benefit (0.08%)
- ✅ **If-else for char ranges**: 19.5% faster than switch - keep current approach

### Documentation

See [DISPATCH_OPTIMIZATION_RESULTS.md](DISPATCH_OPTIMIZATION_RESULTS.md) for:
- Detailed benchmark results
- Performance analysis
- Specific recommendations for code changes
- Examples of patterns to refactor

These experiments follow the same methodology as GC experiments to provide data-driven guidance on whether modern language features improve performance.

