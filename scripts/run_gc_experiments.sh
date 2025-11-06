#!/usr/bin/env bash
set -euo pipefail

# Script to run benchmarks with different GC configurations for Java 25
# This explores GC settings to optimize memory usage and performance
# Based on recommendations from: https://inside.java/2025/10/20/jdk-25-performance-improvements/

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

# Ensure we're using Java 25 - portable approach without -P flag
java_version=$(java -version 2>&1 | head -n1 | grep -o '[0-9][0-9]*' | head -n1)
if [[ "$java_version" != "25" ]]; then
  echo "ERROR: Java 25 is required. Current version: $java_version" >&2
  echo "Please set JAVA_HOME to point to Java 25" >&2
  exit 1
fi

echo "Running GC experiments with Java 25..."
echo "Java version: $(java -version 2>&1 | head -n1)"
echo ""

# Create output directory for GC experiment results
mkdir -p benchmarks/results/gc-experiments
timestamp=$(date -u +"%Y%m%dT%H%M%SZ")
experiment_dir="benchmarks/results/gc-experiments/${timestamp}"
mkdir -p "$experiment_dir"

# Define GC configurations to test
# Each configuration has: name, JVM flags
declare -A gc_configs

# Default G1 (baseline)
gc_configs["g1-default"]=""

# Generational ZGC (new in recent Java versions)
gc_configs["zgc-generational"]="-XX:+UseZGC -XX:+ZGenerational"

# Shenandoah GC
gc_configs["shenandoah"]="-XX:+UseShenandoahGC"

# G1 with Compact Object Headers enabled (new in Java 25)
gc_configs["g1-compact-headers"]="-XX:+UseCompactObjectHeaders"

# G1 with Compact Object Headers disabled (explicit)
gc_configs["g1-no-compact-headers"]="-XX:-UseCompactObjectHeaders"

# ZGC Generational with Compact Object Headers
gc_configs["zgc-compact-headers"]="-XX:+UseZGC -XX:+ZGenerational -XX:+UseCompactObjectHeaders"

# Shenandoah with Compact Object Headers
gc_configs["shenandoah-compact-headers"]="-XX:+UseShenandoahGC -XX:+UseCompactObjectHeaders"

# Function to run a single benchmark configuration
run_benchmark_with_gc() {
  local config_name="$1"
  local gc_flags="$2"
  local benchmark_type="$3"  # "jmh" or "macro"
  
  echo "=========================================="
  echo "Running $benchmark_type benchmark with: $config_name"
  echo "GC Flags: $gc_flags"
  echo "=========================================="
  
  local result_dir="$experiment_dir/${config_name}"
  mkdir -p "$result_dir"
  
  # Set up MAVEN_OPTS with GC flags
  # Note: We overwrite MAVEN_OPTS to ensure clean GC configuration for each test.
  # If scripts internally set MAVEN_OPTS, those will be overridden.
  export MAVEN_OPTS="${gc_flags}"
  
  # Also set up flags for direct Java execution
  # JAVA_TOOL_OPTIONS is picked up by all JVM invocations
  export JAVA_TOOL_OPTIONS="${gc_flags}"
  
  local status=0
  
  if [[ "$benchmark_type" == "jmh" ]]; then
    # Run JMH microbenchmarks
    echo "Running JMH microbenchmarks..."
    
    # Use minimal iterations for faster testing
    JMH_FORKS=1 \
    JMH_WARMUP_IT=2 \
    JMH_IT=3 \
    JMH_WARMUP=2s \
    JMH_MEASURE=5s \
    JMH_THREADS=1 \
    JMH_INCLUDE='no\.rmz\.rmatch\.benchmarks\..*' \
    bash scripts/run_jmh.sh -p patternCount=100,500,1000 2>&1 | tee "$result_dir/jmh-output.log" || status=$?
    
    # Copy results to experiment directory
    latest_jmh=$(ls -t benchmarks/results/jmh-*.json 2>/dev/null | head -n1 || true)
    if [[ -n "$latest_jmh" ]]; then
      cp "$latest_jmh" "$result_dir/jmh-result.json"
      cp "${latest_jmh%.json}.txt" "$result_dir/jmh-result.txt" 2>/dev/null || true
    fi
    
  elif [[ "$benchmark_type" == "macro" ]]; then
    # Run macro benchmarks
    echo "Running macro benchmarks..."
    
    # Use smaller pattern set for faster testing
    MAX_REGEXPS=5000 bash scripts/run_macro_with_memory.sh 2>&1 | tee "$result_dir/macro-output.log" || status=$?
    
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
  else
    echo "✗ $config_name ($benchmark_type) failed with status $status"
  fi
  echo ""
  
  return $status
}

# Function to generate summary report
generate_summary() {
  local summary_file="$experiment_dir/SUMMARY.md"
  
  cat > "$summary_file" <<EOF
# GC Experimentation Results

**Timestamp:** $timestamp
**Java Version:** $(java -version 2>&1 | head -n1)

## Configurations Tested

EOF

  for config_name in "${!gc_configs[@]}"; do
    echo "### $config_name" >> "$summary_file"
    echo "" >> "$summary_file"
    echo "**GC Flags:** \`${gc_configs[$config_name]:-default}\`" >> "$summary_file"
    echo "" >> "$summary_file"
    
    # Add JMH results if available
    local jmh_result="$experiment_dir/${config_name}/jmh-result.json"
    if [[ -f "$jmh_result" ]]; then
      echo "**JMH Results:** Available at \`${config_name}/jmh-result.json\`" >> "$summary_file"
      echo "" >> "$summary_file"
    fi
    
    # Add macro results if available
    local macro_result="$experiment_dir/${config_name}/macro-result.json"
    if [[ -f "$macro_result" ]]; then
      echo "**Macro Results:** Available at \`${config_name}/macro-result.json\`" >> "$summary_file"
      
      # Extract key metrics from macro results
      if command -v jq >/dev/null 2>&1; then
        local duration_ms=$(jq -r '.duration_ms' "$macro_result" 2>/dev/null || echo "N/A")
        local memory_peak=$(jq -r '.memory.detailed.peak_used_mb' "$macro_result" 2>/dev/null || echo "N/A")
        local memory_pattern=$(jq -r '.memory.detailed.pattern_loading_mb' "$macro_result" 2>/dev/null || echo "N/A")
        local memory_matching=$(jq -r '.memory.detailed.matching_mb' "$macro_result" 2>/dev/null || echo "N/A")
        
        echo "" >> "$summary_file"
        echo "- Duration: ${duration_ms}ms" >> "$summary_file"
        echo "- Peak Memory: ${memory_peak}MB" >> "$summary_file"
        echo "- Pattern Loading Memory: ${memory_pattern}MB" >> "$summary_file"
        echo "- Matching Memory: ${memory_matching}MB" >> "$summary_file"
      fi
      echo "" >> "$summary_file"
    fi
    
    echo "---" >> "$summary_file"
    echo "" >> "$summary_file"
  done
  
  cat >> "$summary_file" <<EOF

## How to Compare Results

1. **JMH Microbenchmarks:** Compare the \`Score\` values in \`jmh-result.txt\` files. Higher scores are better for throughput mode.

2. **Macro Benchmarks:** Compare:
   - \`duration_ms\`: Lower is better
   - \`memory.detailed.peak_used_mb\`: Lower is better
   - \`memory.detailed.pattern_loading_mb\`: Lower is better
   - \`memory.detailed.matching_mb\`: Lower is better

3. Use the JMH JSON results with visualization tools or JMH's built-in comparison utilities.

## Recommendations

After reviewing the results:
- If a GC configuration shows significant improvement (>3% in throughput or memory), consider making it the default.
- Update benchmark scripts to use optimal flags.
- Document the chosen configuration in README.md.

## References

- [JDK 25 Performance Improvements](https://inside.java/2025/10/20/jdk-25-performance-improvements/)
- [Java 25 Top Features](https://dev.to/yanev/java-25-top-3-features-that-redefine-performance-startup-and-efficiency-2il9)
EOF

  echo "Summary report generated at: $summary_file"
}

# Parse command line arguments
benchmark_type="${1:-both}"  # "jmh", "macro", or "both"
config_filter="${2:-all}"    # specific config name or "all"

# Validate benchmark type
if [[ "$benchmark_type" != "jmh" && "$benchmark_type" != "macro" && "$benchmark_type" != "both" ]]; then
  echo "ERROR: Invalid benchmark type: $benchmark_type" >&2
  echo "Usage: $0 [jmh|macro|both] [config_name|all]" >&2
  exit 1
fi

# Filter configurations if specified
configs_to_run=()
if [[ "$config_filter" == "all" ]]; then
  configs_to_run=("${!gc_configs[@]}")
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

echo "GC Experiment Plan:"
echo "  Benchmark types: $benchmark_type"
echo "  Configurations: ${configs_to_run[*]}"
echo "  Output directory: $experiment_dir"
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
echo "GC Experiments Complete!"
echo "=========================================="
echo ""
echo "Results directory: $experiment_dir"
echo "Summary report: $experiment_dir/SUMMARY.md"
echo ""

if [[ $overall_status -eq 0 ]]; then
  echo "✓ All benchmarks completed successfully"
else
  echo "⚠ Some benchmarks failed. Check individual logs for details."
fi

echo ""
echo "Next steps:"
echo "1. Review the summary report: cat $experiment_dir/SUMMARY.md"
echo "2. Compare results across configurations"
echo "3. Choose the best performing GC configuration"
echo "4. Update benchmark scripts with optimal flags if improvement > 3%"

exit $overall_status
