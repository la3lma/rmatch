# Large-Scale Benchmark

Run large-scale benchmarks using pre-generated patterns and large corpora, avoiding pattern generation bottlenecks.

**What this runs:**
- Uses pre-generated pattern suites (10-100 patterns to avoid 1000+ pattern generation bottleneck)
- Large corpora (100MB) for realistic testing
- 4-core parallel execution
- All available engines
- Full statistical analysis

**Usage:** `/bench-large [pattern_count]`
- Default: uses 100 patterns
- Options: 10, 100 (avoids slow 1000+ pattern generation)

**Command:**
```bash
PATTERN_COUNT=${1:-100}
echo "🚀 Running large-scale benchmark with ${PATTERN_COUNT} patterns..."

# Create optimized config that uses pre-generated patterns and large corpora
cat > test_matrix/large_scale_demo.json << 'EOF'
{
  "phase": 1,
  "description": "Large-scale benchmark using pre-generated patterns",
  "test_matrix": {
    "pattern_counts": [PATTERN_COUNT_PLACEHOLDER],
    "input_sizes": ["100MB"],
    "pattern_suites": ["log_mining"],
    "corpora": ["synthetic_controllable"],
    "engines": ["java-native-optimized", "java-native-unfair", "re2j", "rmatch"],
    "iterations_per_combination": 5,
    "warmup_iterations": 2
  },
  "execution_plan": {
    "parallel_execution": {
      "max_concurrent_engines": 4,
      "resource_isolation": true
    }
  }
}
EOF

# Replace placeholder with actual pattern count
sed -i.bak "s/PATTERN_COUNT_PLACEHOLDER/${PATTERN_COUNT}/g" test_matrix/large_scale_demo.json && rm test_matrix/large_scale_demo.json.bak

# Run the benchmark
arch -x86_64 .venv/bin/regex-bench run-phase \
  --config test_matrix/large_scale_demo.json \
  --output results/large_scale_$(date +%Y%m%d_%H%M%S) \
  --parallel 4

echo "✅ Large-scale benchmark complete! Use /report to generate analysis."
```