#!/usr/bin/env bash
set -euo pipefail

# Validate the optimal JIT configuration with multiple runs

echo "=== Validating Optimal JIT Configuration ==="

OPTIMAL_CONFIG="-Drmatch.engine=fastpath -Drmatch.prefilter=aho"
OPTIMAL_JIT="-XX:+TieredCompilation -XX:CompileThreshold=500"

run_validation() {
    local patterns="$1"
    local num_runs="$2"
    
    echo
    echo "=== Validating with $patterns patterns ($num_runs runs) ==="
    
    local times=()
    local total=0
    
    for ((i=1; i<=num_runs; i++)); do
        echo "Run $i/$num_runs..."
        
        JAVA_OPTS="$OPTIMAL_CONFIG $OPTIMAL_JIT" \
        MAX_REGEXPS="$patterns" \
        ./scripts/run_macro_with_memory.sh > /tmp/validation_run_${i}.log 2>&1
        
        local json_file=$(grep "Macro JSON:" /tmp/validation_run_${i}.log | awk '{print $3}')
        if [[ -f "$json_file" ]]; then
            local duration=$(grep '"duration_ms":' "$json_file" | sed 's/.*"duration_ms": *\([0-9]*\).*/\1/')
            times+=("$duration")
            total=$((total + duration))
            echo "  Run $i: ${duration}ms"
        else
            echo "  Run $i: ERROR"
        fi
        
        # Brief pause between runs
        sleep 1
    done
    
    if [[ ${#times[@]} -gt 0 ]]; then
        local average=$((total / ${#times[@]}))
        echo
        echo "Results for $patterns patterns:"
        echo "  Individual times: ${times[*]}ms"
        echo "  Average: ${average}ms"
        echo "  Valid runs: ${#times[@]}/$num_runs"
        
        # Calculate coefficient of variation (CV = std_dev / mean)
        local variance=0
        for time in "${times[@]}"; do
            local diff=$((time - average))
            variance=$((variance + diff * diff))
        done
        variance=$((variance / ${#times[@]}))
        
        local min=${times[0]}
        local max=${times[0]}
        for time in "${times[@]}"; do
            if [[ $time -lt $min ]]; then min=$time; fi
            if [[ $time -gt $max ]]; then max=$time; fi
        done
        
        local range=$((max - min))
        local cv_percent=$(( (range * 100) / average ))
        
        echo "  Range: ${min}ms - ${max}ms (${range}ms spread)"
        echo "  Coefficient of Variation: ${cv_percent}%"
        
        return 0
    else
        echo "No valid results"
        return 1
    fi
}

echo "Configuration under test:"
echo "  rmatch: $OPTIMAL_CONFIG"
echo "  JIT: $OPTIMAL_JIT"

# Validate at both scales
run_validation 5000 3
run_validation 10000 3

echo
echo "=== Validation Complete ==="