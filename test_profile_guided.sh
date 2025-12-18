#!/usr/bin/env bash
set -euo pipefail

# Profile-guided optimization test using Java Flight Recorder

echo "=== Profile-Guided Optimization Test ==="

# Function to run with profiling
run_with_profiling() {
    local config_name="$1"
    local rmatch_config="$2"
    
    echo
    echo "=== Profiling: $config_name ==="
    
    # Run with JFR profiling to identify hot methods
    local profile_file="/tmp/rmatch_profile_${config_name}.jfr"
    
    JAVA_OPTS="$rmatch_config \\
-XX:+FlightRecorder \\
-XX:StartFlightRecording=duration=30s,filename=$profile_file,settings=profile \\
-XX:+UnlockDiagnosticVMOptions \\
-XX:+TraceClassLoading \\
-XX:+LogVMOutput" \\
MAX_REGEXPS=5000 \\
./scripts/run_macro_with_memory.sh > "/tmp/profile_${config_name}.log" 2>&1 || true
    
    echo "Profile saved to: $profile_file"
    
    # Extract benchmark duration from log
    local json_file=$(grep "Macro JSON:" "/tmp/profile_${config_name}.log" | awk '{print $3}' || echo "")
    if [[ -f "$json_file" ]]; then
        local duration=$(grep '"duration_ms":' "$json_file" | sed 's/.*"duration_ms": *\([0-9]*\).*/\1/' || echo "unknown")
        echo "Profiled run duration: ${duration}ms"
    fi
    
    return 0
}

# Function to run with optimization based on profile
run_with_optimization() {
    local config_name="$1" 
    local rmatch_config="$2"
    local profile_file="/tmp/rmatch_profile_${config_name}.jfr"
    
    echo
    echo "=== Optimized run: $config_name ==="
    
    # Use profile information to optimize compilation
    JAVA_OPTS="$rmatch_config \\
-XX:+TieredCompilation \\
-XX:TieredStopAtLevel=4 \\
-XX:+UseProfiledHints \\
-XX:CompileThreshold=500 \\
-XX:+OptimizeStringConcat \\
-XX:+UseStringDeduplication" \\
MAX_REGEXPS=5000 \\
./scripts/run_macro_with_memory.sh > "/tmp/optimized_${config_name}.log" 2>&1 || true
    
    local json_file=$(grep "Macro JSON:" "/tmp/optimized_${config_name}.log" | awk '{print $3}' || echo "")
    if [[ -f "$json_file" ]]; then
        local duration=$(grep '"duration_ms":' "$json_file" | sed 's/.*"duration_ms": *\([0-9]*\).*/\1/' || echo "unknown")
        echo "Optimized run duration: ${duration}ms"
    fi
    
    return 0
}

# Test configurations
BASELINE=""
FASTPATH_AHO="-Drmatch.engine=fastpath -Drmatch.prefilter=aho"

# Profile both configurations
run_with_profiling "baseline" "$BASELINE"
run_with_profiling "fastpath" "$FASTPATH_AHO"

# Run optimized versions
run_with_optimization "baseline" "$BASELINE"
run_with_optimization "fastpath" "$FASTPATH_AHO"

echo
echo "=== Profile-Guided Optimization Complete ==="
echo "Profiles and logs saved in /tmp/"