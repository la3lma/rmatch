# GC Configuration Experiments

This document describes how to run and analyze GC (Garbage Collector) configuration experiments for rmatch on Java 25.

> **New to GC experiments?** See [QUICKSTART_GC.md](QUICKSTART_GC.md) for a step-by-step tutorial with examples.

## Background

Java 25 introduces several GC improvements that can benefit applications with high object churn like regex engines:

- **Generational ZGC**: A low-latency garbage collector optimized for large heaps
- **Shenandoah GC**: A concurrent GC focused on predictable pause times
- **Compact Object Headers**: Reduces memory footprint of objects (mature in Java 25)

## Running GC Experiments

### Quick Start

Run all GC configurations for both JMH and macro benchmarks:

```bash
make bench-gc-experiments
```

Or run the script directly:

```bash
scripts/run_gc_experiments.sh both all
```

### Custom Runs

Run specific benchmark types:

```bash
# JMH microbenchmarks only
scripts/run_gc_experiments.sh jmh all

# Macro benchmarks only
scripts/run_gc_experiments.sh macro all
```

Run specific GC configurations:

```bash
# Test only ZGC Generational
scripts/run_gc_experiments.sh both zgc-generational

# Test only Shenandoah
scripts/run_gc_experiments.sh macro shenandoah
```

Available configurations:
- `g1-default` - Default G1 GC (baseline)
- `zgc-generational` - Generational ZGC
- `shenandoah` - Shenandoah GC
- `g1-compact-headers` - G1 with Compact Object Headers enabled
- `g1-no-compact-headers` - G1 with Compact Object Headers explicitly disabled
- `zgc-compact-headers` - ZGC Generational with Compact Object Headers
- `shenandoah-compact-headers` - Shenandoah with Compact Object Headers

## Results Location

Results are stored in `benchmarks/results/gc-experiments/<timestamp>/` with:

```
<timestamp>/
├── SUMMARY.md                           # Overview and key metrics
├── g1-default/
│   ├── jmh-result.json                 # JMH results
│   ├── jmh-result.txt                  # Human-readable JMH output
│   ├── jmh-output.log                  # Full JMH execution log
│   ├── macro-result.json               # Macro benchmark results
│   ├── macro-result.log                # Macro execution log
│   └── macro-output.log                # Full macro output
├── zgc-generational/
│   └── ...
└── ... (other configurations)
```

## Analyzing Results

### 1. Review the Summary

```bash
# Find the latest experiment
latest=$(ls -td benchmarks/results/gc-experiments/*/ | head -n1)
cat "${latest}SUMMARY.md"
```

The summary includes key metrics:
- Duration (ms) - lower is better
- Peak Memory (MB) - lower is better
- Pattern Loading Memory (MB) - lower is better
- Matching Memory (MB) - lower is better

### 2. Compare JMH Results

JMH results show throughput (ops/s) where higher is better:

```bash
# View specific configuration
cat "${latest}g1-default/jmh-result.txt"

# Compare scores across configurations
for config in g1-default zgc-generational shenandoah; do
  echo "=== $config ==="
  grep "Score" "${latest}${config}/jmh-result.txt" 2>/dev/null || echo "No results"
done
```

### 3. Compare Macro Results

Extract and compare key metrics:

```bash
for config in g1-default zgc-generational shenandoah; do
  result="${latest}${config}/macro-result.json"
  if [[ -f "$result" ]]; then
    echo "=== $config ==="
    jq '{duration_ms, peak_memory: .memory.detailed.peak_used_mb, pattern_loading: .memory.detailed.pattern_loading_mb, matching: .memory.detailed.matching_mb}' "$result"
  fi
done
```

### 4. Statistical Significance

For meaningful comparisons:
- Look for improvements > 3% in throughput or memory
- JMH provides confidence intervals - check that intervals don't overlap
- Run multiple iterations for production decisions

## Applying Findings

If a GC configuration shows consistent improvement:

1. **Update benchmark scripts** to use the optimal flags by default

2. **Document in README.md** which GC configuration is recommended

3. **Consider application-specific needs**:
   - Use ZGC for large heaps (> 4GB) and low latency requirements
   - Use Shenandoah for predictable pause times
   - Use G1 (default) for general purpose with good throughput
   - Enable Compact Object Headers if memory footprint is critical

4. **Update Maven configuration** (optional):
   Add to `pom.xml` under `maven-surefire-plugin`:
   ```xml
   <argLine>-XX:+UseZGC -XX:+ZGenerational -XX:+UseCompactObjectHeaders</argLine>
   ```

## Example Workflow

```bash
# 1. Run experiments
make bench-gc-experiments

# 2. Find latest results
latest=$(ls -td benchmarks/results/gc-experiments/*/ | head -n1)

# 3. View summary
cat "${latest}SUMMARY.md"

# 4. If ZGC shows 5% improvement, update default scripts
# Edit scripts/run_jmh.sh and scripts/run_macro_with_memory.sh
# to set MAVEN_OPTS="-XX:+UseZGC -XX:+ZGenerational"

# 5. Re-run benchmarks to verify
make bench-micro
make bench-macro

# 6. Update README.md with findings
```

## References

- [JDK 25 Performance Improvements](https://inside.java/2025/10/20/jdk-25-performance-improvements/)
- [Java 25 Top 3 Features](https://dev.to/yanev/java-25-top-3-features-that-redefine-performance-startup-and-efficiency-2il9)
- [ZGC Documentation](https://docs.oracle.com/en/java/javase/25/gctuning/z-garbage-collector.html)
- [Shenandoah GC Documentation](https://wiki.openjdk.org/display/shenandoah/Main)
- [Compact Object Headers](https://openjdk.org/jeps/450)

## Troubleshooting

### Java Version Issues

Ensure Java 25 is active:
```bash
java -version  # Should show "25.0.x"
export JAVA_HOME=/path/to/java-25
export PATH=$JAVA_HOME/bin:$PATH
```

### GC Not Available

Some GC options may not be available on all platforms:
- Shenandoah: May need to be explicitly enabled at JDK build time
- ZGC: Available on Linux x64, macOS, Windows (check your platform)

Check available GCs:
```bash
java -XX:+PrintFlagsFinal -version | grep UseG1GC
java -XX:+PrintFlagsFinal -version | grep UseZGC
java -XX:+PrintFlagsFinal -version | grep UseShenandoahGC
```

### Out of Memory Errors

If benchmarks fail with OOM:
- Increase heap size: Add `-Xmx8g` to MAVEN_OPTS in the script
- Reduce pattern count: Edit MAX_REGEXPS in script
- Use smaller test datasets
