#!/usr/bin/env bash
set -euo pipefail

# Comprehensive Java 25 JIT optimization testing for rmatch
# This script validates the optimal JIT configuration identified for production use
# 
# Optimal configuration discovered:
# JAVA_OPTS="-Drmatch.engine=fastpath -Drmatch.prefilter=aho -XX:+TieredCompilation -XX:CompileThreshold=500"

echo "=== Java 25 JIT Optimization Validation ==="

# Function to run test and extract duration
run_jit_test() {
    local config_name="$1"
    local rmatch_config="$2" 
    local jit_options="$3"
    
    echo "=== Testing: $config_name ==="
    
    # Run the test
    JAVA_OPTS="$rmatch_config $jit_options" \
    MAX_REGEXPS=5000 \
    ./scripts/run_macro_with_memory.sh > /tmp/jit_${config_name}.log 2>&1
    
    # Extract results
    local json_file=$(grep "Macro JSON:" /tmp/jit_${config_name}.log | awk '{print $3}')
    if [[ -f "$json_file" ]]; then
        local duration=$(grep '"duration_ms":' "$json_file" | sed 's/.*"duration_ms": *\([0-9]*\).*/\1/')
        local memory_used=$(grep '"used_mb":' "$json_file" | sed 's/.*"used_mb": *\([0-9]*\).*/\1/')
        echo "  Duration: ${duration}ms"
        echo "  Memory: ${memory_used}MB"
        echo "  JSON: $json_file"
        
        # Copy results for analysis
        cp "$json_file" "jit_results_${config_name}.json"
    else
        echo "  ERROR: No JSON results found"
    fi
    
    echo
    return 0
}

echo "Java version:"
java -version 2>&1 | head -1

echo

# Test configurations - simplified to avoid shell escaping issues
run_jit_test "baseline_default" "" "-XX:+TieredCompilation -XX:TieredStopAtLevel=4"

run_jit_test "fastpath_default" "-Drmatch.engine=fastpath -Drmatch.prefilter=aho" "-XX:+TieredCompilation -XX:TieredStopAtLevel=4"

run_jit_test "fastpath_aggressive" "-Drmatch.engine=fastpath -Drmatch.prefilter=aho" "-XX:+TieredCompilation -XX:TieredStopAtLevel=4 -XX:+UseStringDeduplication -XX:+OptimizeStringConcat"

run_jit_test "fastpath_fast_warmup" "-Drmatch.engine=fastpath -Drmatch.prefilter=aho" "-XX:+TieredCompilation -XX:CompileThreshold=1000 -XX:Tier3CompileThreshold=500 -XX:Tier4CompileThreshold=2000"

# Test with method-specific compilation hints
run_jit_test "fastpath_with_hints" "-Drmatch.engine=fastpath -Drmatch.prefilter=aho" "-XX:+TieredCompilation -XX:CompileThreshold=500"

echo "=== JIT Comparison Summary ==="
echo "Results saved as jit_results_*.json"
echo

# Show summary
for file in jit_results_*.json; do
    if [[ -f "$file" ]]; then
        config=$(echo "$file" | sed 's/jit_results_\(.*\)\.json/\1/')
        duration=$(grep '"duration_ms":' "$file" | sed 's/.*"duration_ms": *\([0-9]*\).*/\1/')
        memory=$(grep '"used_mb":' "$file" | sed 's/.*"used_mb": *\([0-9]*\).*/\1/')
        printf "%-20s: %6sms, %4sMB\n" "$config" "$duration" "$memory"
    fi
done