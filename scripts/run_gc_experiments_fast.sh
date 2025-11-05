#!/usr/bin/env bash
set -euo pipefail

# Fast GC experiments script - optimized for quick testing
# This version uses minimal benchmark parameters for rapid GC comparison
# Based on run_gc_experiments.sh but heavily optimized for speed

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

# Ensure we're using Java 25 - portable approach without -P flag
java_version=$(java -version 2>&1 | head -n1 | grep -o '[0-9][0-9]*' | head -n1)
if [[ "$java_version" != "25" ]]; then
  echo "ERROR: Java 25 is required. Current version: $java_version" >&2
  echo "Please set JAVA_HOME to point to Java 25" >&2
  exit 1
fi

echo "Running FAST GC experiments with Java 25..."
echo "Java version: $(java -version 2>&1 | head -n1)"
echo ""

# Create output directory for GC experiment results
mkdir -p benchmarks/results/gc-experiments
timestamp=$(date -u +"%Y%m%dT%H%M%SZ")
experiment_dir="benchmarks/results/gc-experiments/${timestamp}-fast"
mkdir -p "$experiment_dir"

# Define GC configurations to test (prioritized list)
declare -A gc_configs

# Most important configs for Java 25
gc_configs["g1-default"]=""
gc_configs["g1-compact-headers"]="-XX:+UseCompactObjectHeaders"
gc_configs["zgc-generational"]="-XX:+UseZGC -XX:+ZGenerational"
gc_configs["zgc-compact-headers"]="-XX:+UseZGC -XX:+ZGenerational -XX:+UseCompactObjectHeaders"

# Function to run a single benchmark configuration with timeout
run_benchmark_with_gc() {
  local config_name="$1"
  local gc_flags="$2"
  local benchmark_type="$3"  # "jmh" or "macro"
  
  echo "=========================================="
  echo "Running FAST $benchmark_type benchmark with: $config_name"
  echo "GC Flags: $gc_flags"
  echo "=========================================="
  
  local result_dir="$experiment_dir/${config_name}"
  mkdir -p "$result_dir"
  
  # Set up MAVEN_OPTS with GC flags
  export MAVEN_OPTS="${gc_flags}"
  export JAVA_TOOL_OPTIONS="${gc_flags}"
  
  local status=0
  
  if [[ "$benchmark_type" == "jmh" ]]; then
    # Run JMH microbenchmarks with MINIMAL parameters for speed
    echo "Running FAST JMH microbenchmarks..."
    
    # Ultra-minimal iterations for rapid testing
    timeout 300 bash -c "
      JMH_FORKS=1 \
      JMH_WARMUP_IT=1 \
      JMH_IT=1 \
      JMH_WARMUP=500ms \
      JMH_MEASURE=1s \
      JMH_THREADS=1 \
      JMH_INCLUDE='no\.rmz\.rmatch\.benchmarks\.CompileAndMatchBench\.buildMatcher' \
      bash scripts/run_jmh.sh -p patternCount=10
    " 2>&1 | tee "$result_dir/jmh-output.log" || status=$?
    
    # Copy results to experiment directory
    latest_jmh=$(ls -t benchmarks/results/jmh-*.json 2>/dev/null | head -n1 || true)
    if [[ -n "$latest_jmh" ]]; then
      cp "$latest_jmh" "$result_dir/jmh-result.json"
      cp "${latest_jmh%.json}.txt" "$result_dir/jmh-result.txt" 2>/dev/null || true
    fi
    
  elif [[ "$benchmark_type" == "macro" ]]; then
    # Run macro benchmarks with MINIMAL regex set
    echo "Running FAST macro benchmarks..."
    
    # Much smaller pattern set for very fast testing
    timeout 180 bash -c "
      MAX_REGEXPS=1000 bash scripts/run_macro_with_memory.sh
    " 2>&1 | tee "$result_dir/macro-output.log" || status=$?
    
    # Copy results to experiment directory
    latest_macro=$(ls -t benchmarks/results/macro-*.json 2>/dev/null | head -n1 || true)
    if [[ -n "$latest_macro" ]]; then
      cp "$latest_macro" "$result_dir/macro-result.json"
      cp "${latest_macro%.json}.log" "$result_dir/macro-result.log" 2>/dev/null || true
    fi
  fi
  
  # Unset environment variables
  unset MAVEN_OPTS
  unset JAVA_TOOL_OPTIONS
  
  echo ""
  if [[ $status -eq 0 ]]; then
    echo "✓ $config_name ($benchmark_type) completed successfully"
  elif [[ $status -eq 124 ]]; then
    echo "⚠ $config_name ($benchmark_type) timed out - likely too slow"
  else
    echo "✗ $config_name ($benchmark_type) failed with status $status"
  fi
  echo ""
  
  return $status
}

# Function to generate summary report with performance comparison
generate_summary() {
  local summary_file="$experiment_dir/SUMMARY.md"
  
  cat > "$summary_file" <<EOF
# FAST GC Experimentation Results

**Timestamp:** $timestamp
**Java Version:** $(java -version 2>&1 | head -n1)
**Test Type:** Fast/Minimal (optimized for speed)

## Configurations Tested

EOF

  # Collect results for comparison
  declare -A jmh_scores
  declare -A macro_durations
  declare -A macro_memories
  
  for config_name in "${!gc_configs[@]}"; do
    echo "### $config_name" >> "$summary_file"
    echo "" >> "$summary_file"
    echo "**GC Flags:** \`${gc_configs[$config_name]:-default}\`" >> "$summary_file"
    echo "" >> "$summary_file"
    
    # Extract JMH performance data
    local jmh_result="$experiment_dir/${config_name}/jmh-result.json"
    if [[ -f "$jmh_result" ]] && command -v jq >/dev/null 2>&1; then
      # Extract score from JMH results
      local score=$(jq -r '.benchmarks[]? | select(.benchmark | contains("buildMatcher")) | .primaryMetric.score' "$jmh_result" 2>/dev/null | head -1 || echo "N/A")
      jmh_scores["$config_name"]="$score"
      echo "**JMH Score:** ${score} ops/s" >> "$summary_file"
      echo "" >> "$summary_file"
    fi
    
    # Extract macro performance data
    local macro_result="$experiment_dir/${config_name}/macro-result.json"
    if [[ -f "$macro_result" ]] && command -v jq >/dev/null 2>&1; then
      local duration_ms=$(jq -r '.duration_ms // "N/A"' "$macro_result" 2>/dev/null || echo "N/A")
      local memory_peak=$(jq -r '.memory.detailed.peak_used_mb // "N/A"' "$macro_result" 2>/dev/null || echo "N/A")
      
      macro_durations["$config_name"]="$duration_ms"
      macro_memories["$config_name"]="$memory_peak"
      
      echo "**Macro Results:**" >> "$summary_file"
      echo "- Duration: ${duration_ms}ms" >> "$summary_file"
      echo "- Peak Memory: ${memory_peak}MB" >> "$summary_file"
      echo "" >> "$summary_file"
    fi
    
    echo "---" >> "$summary_file"
    echo "" >> "$summary_file"
  done
  
  # Generate performance comparison
  cat >> "$summary_file" <<EOF

## Performance Comparison

### JMH Throughput (ops/s - Higher is Better)

| Configuration | Score | Relative Performance |
|---------------|-------|---------------------|
EOF

  # Find baseline (g1-default) for comparison
  local baseline_jmh="${jmh_scores["g1-default"]:-0}"
  for config_name in g1-default g1-compact-headers zgc-generational zgc-compact-headers; do
    if [[ -v "jmh_scores[$config_name]" ]]; then
      local score="${jmh_scores[$config_name]}"
      local relative=""
      if [[ "$score" != "N/A" && "$baseline_jmh" != "0" && "$baseline_jmh" != "N/A" ]]; then
        relative=$(awk -v s="$score" -v b="$baseline_jmh" 'BEGIN{printf "%.1f%%", (s/b-1)*100}')
        if [[ "$relative" == "0.0%" ]]; then
          relative="baseline"
        fi
      else
        relative="N/A"
      fi
      echo "| $config_name | $score | $relative |" >> "$summary_file"
    fi
  done

  cat >> "$summary_file" <<EOF

### Macro Duration (ms - Lower is Better)

| Configuration | Duration | Relative Performance |
|---------------|----------|---------------------|
EOF

  local baseline_macro="${macro_durations["g1-default"]:-0}"
  for config_name in g1-default g1-compact-headers zgc-generational zgc-compact-headers; do
    if [[ -v "macro_durations[$config_name]" ]]; then
      local duration="${macro_durations[$config_name]}"
      local relative=""
      if [[ "$duration" != "N/A" && "$baseline_macro" != "0" && "$baseline_macro" != "N/A" ]]; then
        relative=$(awk -v d="$duration" -v b="$baseline_macro" 'BEGIN{printf "%.1f%%", (d/b-1)*100}')
        if [[ "$relative" == "0.0%" ]]; then
          relative="baseline"
        fi
      else
        relative="N/A"
      fi
      echo "| $config_name | $duration | $relative |" >> "$summary_file"
    fi
  done

  cat >> "$summary_file" <<EOF

## Key Findings

Based on these FAST benchmark results:

EOF

  # Auto-generate recommendations based on results
  local best_jmh_config=""
  local best_jmh_score=0
  local best_macro_config=""
  local best_macro_duration=999999999

  for config_name in "${!jmh_scores[@]}"; do
    local score="${jmh_scores[$config_name]}"
    if [[ "$score" != "N/A" ]] && awk -v s="$score" -v b="$best_jmh_score" 'BEGIN{exit(s>b?0:1)}'; then
      best_jmh_score="$score"
      best_jmh_config="$config_name"
    fi
  done

  for config_name in "${!macro_durations[@]}"; do
    local duration="${macro_durations[$config_name]}"
    if [[ "$duration" != "N/A" ]] && awk -v d="$duration" -v b="$best_macro_duration" 'BEGIN{exit(d<b?0:1)}'; then
      best_macro_duration="$duration"
      best_macro_config="$config_name"
    fi
  done

  if [[ -n "$best_jmh_config" ]]; then
    echo "- **Best JMH Performance**: $best_jmh_config" >> "$summary_file"
  fi
  if [[ -n "$best_macro_config" ]]; then
    echo "- **Best Macro Performance**: $best_macro_config" >> "$summary_file"
  fi

  cat >> "$summary_file" <<EOF

## Next Steps

1. If results show >3% improvement, run full benchmarks: \`scripts/run_gc_experiments.sh both all\`
2. Update MAVEN_OPTS in build scripts with optimal GC flags
3. Add optimal GC settings to README.md

## References

- [JDK 25 Performance Improvements](https://inside.java/2025/10/20/jdk-25-performance-improvements/)
- [Java 25 Compact Object Headers](https://openjdk.org/jeps/450)
EOF

  echo "Summary report generated at: $summary_file"
}

# Parse command line arguments
benchmark_type="${1:-both}"  # "jmh", "macro", or "both"
config_filter="${2:-important}"    # specific config name, "important", or "all"

# Validate benchmark type
if [[ "$benchmark_type" != "jmh" && "$benchmark_type" != "macro" && "$benchmark_type" != "both" ]]; then
  echo "ERROR: Invalid benchmark type: $benchmark_type" >&2
  echo "Usage: $0 [jmh|macro|both] [config_name|important|all]" >&2
  exit 1
fi

# Filter configurations
configs_to_run=()
if [[ "$config_filter" == "all" ]]; then
  configs_to_run=("${!gc_configs[@]}")
elif [[ "$config_filter" == "important" ]]; then
  # Only test the most promising configurations for Java 25
  configs_to_run=("g1-default" "g1-compact-headers" "zgc-generational" "zgc-compact-headers")
else
  if [[ -v "gc_configs[$config_filter]" ]]; then
    configs_to_run=("$config_filter")
  else
    echo "ERROR: Unknown configuration: $config_filter" >&2
    echo "Available configurations:" >&2
    for name in "${!gc_configs[@]}"; do
      echo "  - $name" >&2
    done
    exit 1
  fi
fi

# Sort configurations for consistent ordering
IFS=$'\n' configs_to_run=($(sort <<<"${configs_to_run[*]}"))
unset IFS

echo "FAST GC Experiment Plan:"
echo "  Benchmark types: $benchmark_type"
echo "  Configurations: ${configs_to_run[*]}"
echo "  Output directory: $experiment_dir"
echo "  Timeouts: JMH=300s, Macro=180s"
echo ""

# Track overall success
overall_status=0

# Run benchmarks for each configuration
for config_name in "${configs_to_run[@]}"; do
  gc_flags="${gc_configs[$config_name]}"
  
  if [[ "$benchmark_type" == "both" ]]; then
    # Run both JMH and macro
    run_benchmark_with_gc "$config_name" "$gc_flags" "jmh" || overall_status=$?
    run_benchmark_with_gc "$config_name" "$gc_flags" "macro" || overall_status=$?
  else
    # Run specified benchmark type
    run_benchmark_with_gc "$config_name" "$gc_flags" "$benchmark_type" || overall_status=$?
  fi
done

# Generate summary report
generate_summary

echo "=========================================="
echo "FAST GC Experiments Complete!"
echo "=========================================="
echo ""
echo "Results directory: $experiment_dir"
echo "Summary report: $experiment_dir/SUMMARY.md"
echo ""

if [[ $overall_status -eq 0 ]]; then
  echo "✓ All benchmarks completed successfully"
else
  echo "⚠ Some benchmarks failed or timed out. Check individual logs for details."
fi

echo ""
echo "Quick results preview:"
echo "======================"
if [[ -f "$experiment_dir/SUMMARY.md" ]]; then
  echo ""
  # Show key findings section
  sed -n '/## Key Findings/,/## Next Steps/p' "$experiment_dir/SUMMARY.md" | sed '$d' | sed '$d'
fi

echo ""
echo "To run full experiments (if fast results are promising):"
echo "  make bench-gc-experiments"
echo ""
echo "To see detailed results:"
echo "  cat $experiment_dir/SUMMARY.md"

exit $overall_status