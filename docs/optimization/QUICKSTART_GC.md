# Quick GC Experiment Example

This is a quick example showing how to run a minimal GC experiment to see the impact of different GC settings.

## Prerequisites

Ensure you have Java 25:
```bash
export JAVA_HOME=/path/to/java-25
export PATH=$JAVA_HOME/bin:$PATH
java -version  # Should show 25.x.x
```

## Step 1: Validate GC Configurations

First, verify that all GC configurations are available:

```bash
make validate-gc
```

Expected output:
```
Testing G1 (default)... ✓ OK
Testing ZGC Generational... ✓ OK
Testing Shenandoah... ✓ OK
Testing G1 + Compact Headers... ✓ OK
Testing G1 - Compact Headers... ✓ OK
Testing ZGC + Compact Headers... ✓ OK
Testing Shenandoah + Compact Headers... ✓ OK
```

## Step 2: Run a Quick Experiment

Run a quick macro benchmark comparison (uses 5000 patterns):

```bash
# Compare G1 vs ZGC Generational (quick test)
scripts/run_gc_experiments.sh macro g1-default
scripts/run_gc_experiments.sh macro zgc-generational
```

Or run with a specific configuration:

```bash
# Test only Shenandoah on JMH microbenchmarks
scripts/run_gc_experiments.sh jmh shenandoah
```

## Step 3: Review Results

Find the results directory:
```bash
latest=$(ls -td benchmarks/results/gc-experiments/*/ | head -n1)
echo "Results in: $latest"
```

View the summary:
```bash
cat "${latest}SUMMARY.md"
```

Compare metrics manually:
```bash
# Compare G1 vs ZGC
echo "=== G1 Default ==="
jq '{duration_ms, peak_memory: .memory.detailed.peak_used_mb}' \
  "${latest}g1-default/macro-result.json"

echo "=== ZGC Generational ==="
jq '{duration_ms, peak_memory: .memory.detailed.peak_used_mb}' \
  "${latest}zgc-generational/macro-result.json"
```

## Step 4: Full Comparison (Optional)

For a comprehensive comparison across all configurations:

```bash
# This takes longer - tests all GC configs with both JMH and macro benchmarks
make bench-gc-experiments
```

This will:
1. Run JMH microbenchmarks with each GC configuration
2. Run macro benchmarks with each GC configuration
3. Generate a summary report at `benchmarks/results/gc-experiments/<timestamp>/SUMMARY.md`

## Expected Outcomes

Based on Java 25 improvements, you might see:

- **ZGC Generational**: Lower memory footprint, possibly faster for large heaps
- **Shenandoah**: More predictable pause times, good for latency-sensitive workloads
- **Compact Object Headers**: 10-20% reduction in memory usage
- **G1 (default)**: Good baseline performance, works well for most cases

Look for:
- **Duration improvement > 3%**: Consider adopting that GC
- **Memory reduction > 5%**: Consider enabling Compact Object Headers
- **Consistent results**: Run multiple iterations to confirm findings

## Example Result Analysis

Let's say you see these results:

```
G1 Default:
  Duration: 19500ms
  Peak Memory: 108MB

ZGC Generational:
  Duration: 18200ms
  Peak Memory: 95MB

ZGC + Compact Headers:
  Duration: 18100ms
  Peak Memory: 82MB
```

Analysis:
- ZGC is ~7% faster and uses ~12% less memory
- Compact Headers saves additional 14% memory
- **Recommendation**: Use `-XX:+UseZGC -XX:+ZGenerational -XX:+UseCompactObjectHeaders`

## Next Steps

If you find a winning configuration:

1. **Update default scripts** (optional):
   - Edit `scripts/run_jmh.sh` and `scripts/run_macro_with_memory.sh`
   - Add `export MAVEN_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:+UseCompactObjectHeaders"`

2. **Document the finding** in README.md

3. **Share results** with the team via issue/PR comments

## Troubleshooting

**Issue**: "GC not available"
- Solution: Check your JDK build supports that GC. Some GCs may be platform-specific.

**Issue**: "Out of memory"
- Solution: Increase heap size in the script: `-Xmx8g`

**Issue**: "Tests taking too long"
- Solution: Run individual configs or reduce pattern count in scripts

## Quick Reference

```bash
# Validate configurations
make validate-gc

# Quick test - single config
scripts/run_gc_experiments.sh macro g1-default

# Compare two configs
scripts/run_gc_experiments.sh macro g1-default
scripts/run_gc_experiments.sh macro zgc-generational

# Full comprehensive test
make bench-gc-experiments

# View latest results
cat $(ls -td benchmarks/results/gc-experiments/*/ | head -n1)SUMMARY.md
```
