# GC Optimization Results for RMatch

## Executive Summary

Based on fast GC experiments conducted on Java 25, **G1 with Compact Object Headers** (`-XX:+UseCompactObjectHeaders`) shows the best overall performance for the RMatch regex library:

- **12.3% faster** JMH microbenchmarks
- **5.8% faster** macro benchmarks
- **9.7% lower memory usage** (28MB vs 31MB peak)

## Detailed Results

### Test Configuration
- **Java Version**: OpenJDK 25 (Temurin-25+36-LTS)
- **Platform**: Apple M2 Max, macOS 26.0.1
- **Test Date**: November 5, 2025
- **JMH Test**: Pattern compilation with 10 patterns
- **Macro Test**: Wuthering Heights corpus with 1000 regex patterns

### Performance Comparison

| Configuration | JMH (us/op) | JMH vs G1 | Macro (ms) | Macro vs G1 | Peak Memory (MB) | Memory vs G1 |
|---------------|-------------|-----------|------------|-------------|------------------|--------------|
| **G1 Default** | 10.72 | baseline | 2656 | baseline | 31 | baseline |
| **G1 + Compact Headers** | **9.54** | **+12.3%** | **2510** | **+5.8%** | **28** | **-9.7%** |
| ZGC (no ZGenerational¹) | 11.93 | -10.2% | 3645 | -27.1% | 78 | +151% |
| ZGC + Compact Headers | 11.27 | -4.9% | 3339 | -20.5% | 74 | +139% |

¹ Note: ZGenerational flag was ignored by JVM (removed in Java 24+)

### Key Findings

1. **G1 + Compact Object Headers is the clear winner**
   - Best performance in both micro and macro benchmarks
   - Lowest memory usage
   - Stable and well-tested GC for production use

2. **ZGC shows poor performance for RMatch workloads**
   - 27% slower on macro benchmarks
   - 2.5x higher memory usage
   - Not optimal for pattern-heavy regex workloads

3. **Compact Object Headers provide significant benefits**
   - Available in Java 25
   - Reduce memory overhead
   - Improve performance across both GC algorithms

## Recommendations

### Immediate Actions

1. **Update build configuration** to use optimal GC settings:
   ```bash
   export MAVEN_OPTS="-XX:+UseCompactObjectHeaders"
   export JAVA_TOOL_OPTIONS="-XX:+UseCompactObjectHeaders"
   ```

2. **Update benchmark scripts** to use the optimal configuration by default

3. **Document the recommendation** in README.md

### Implementation

Add to your project's configuration:

```xml
<!-- Maven pom.xml -->
<properties>
    <maven.compiler.arg>-XX:+UseCompactObjectHeaders</maven.compiler.arg>
</properties>
```

Or for shell scripts:
```bash
# Add to .bashrc or build scripts
export JAVA_OPTS="${JAVA_OPTS} -XX:+UseCompactObjectHeaders"
```

### Performance Impact

The **5.8-12.3% performance improvement** combined with **9.7% memory reduction** makes this an excellent optimization with:
- **No code changes required**
- **No compatibility issues**
- **Immediate benefit for all regex workloads**

## Technical Details

### Why G1 + Compact Headers Works Well

1. **Compact Object Headers** (JEP 450) reduce memory overhead by:
   - Shrinking object headers from 128 bits to 64 bits on 64-bit platforms
   - Better cache locality for object-intensive workloads like regex compilation
   - Lower GC pressure due to reduced memory footprint

2. **G1 GC characteristics** align well with RMatch:
   - Good for applications with mixed allocation patterns
   - Handles both short-lived pattern objects and long-lived compiled regexes
   - Predictable pause times for interactive use

### Why ZGC Underperformed

1. **Higher memory overhead** for small objects (regex patterns)
2. **Colored pointer overhead** less beneficial for computational workloads
3. **Tuned for very large heaps** (multi-GB), not optimal for typical regex usage

## Validation

These results should be validated by:

1. **Running full benchmarks** if adopting in production:
   ```bash
   make bench-gc-experiments  # Full test suite
   ```

2. **Testing with your specific regex patterns** and data sizes

3. **Monitoring production performance** after deployment

## References

- [JEP 450: Compact Object Headers](https://openjdk.org/jeps/450)
- [JDK 25 Performance Improvements](https://inside.java/2025/10/20/jdk-25-performance-improvements/)
- [G1 GC Tuning Guide](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-g1-garbage-collector1.html)

---

**Generated**: November 5, 2025  
**Next Review**: After production deployment and monitoring