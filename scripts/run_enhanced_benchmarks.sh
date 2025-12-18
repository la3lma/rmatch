#!/usr/bin/env bash
set -euo pipefail

# Enhanced benchmark runner that combines JMH Extended Testing Framework
# with architecture-aware performance analysis and improved visualization
#
# Usage: scripts/run_enhanced_benchmarks.sh [test_suite]
# 
# Test suites:
#   quick    - Fast validation run with minimal iterations  
#   standard - Standard comprehensive benchmarks (default)
#   full     - Full benchmark suite with all matcher types
#   architecture - Architecture-aware benchmarking for cross-platform comparison

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

TEST_SUITE=${1:-standard}
TIMESTAMP=$(date -u +"%Y%m%dT%H%M%SZ")
RESULTS_DIR="benchmarks/results/enhanced-${TIMESTAMP}"

echo "=== Enhanced rmatch Benchmark Suite ==="
echo "Suite: $TEST_SUITE"
echo "Timestamp: $TIMESTAMP"
echo "Results: $RESULTS_DIR"
echo

mkdir -p "$RESULTS_DIR"

# Function to run JMH with specific configuration
run_jmh_enhanced() {
    local config_name="$1"
    local jmh_args="$2"
    local description="$3"
    
    echo "--- Running: $config_name ---"
    echo "Description: $description"
    
    local json_out="$RESULTS_DIR/jmh-${config_name}-${TIMESTAMP}.json"
    local txt_out="$RESULTS_DIR/jmh-${config_name}-${TIMESTAMP}.txt"
    
    # Run JMH with enhanced framework
    JMH_INCLUDE='.*ExtendedTestFramework.*|.*ArchitectureAwareBench.*' \
    ./scripts/run_jmh.sh \
        -rf json -rff "$json_out" \
        -o "$txt_out" \
        $jmh_args || true
    
    if [[ -f "$json_out" ]]; then
        echo "✅ $config_name completed: $json_out"
    else
        echo "❌ $config_name failed"
    fi
    
    echo
}

case "$TEST_SUITE" in
    "quick")
        echo "Running quick validation benchmarks..."
        run_jmh_enhanced "quick-rmatch" \
            "-p patternCount=50 -p patternCategory=SIMPLE -p matcherType=RMATCH -wi 1 -i 1 -f 1" \
            "Quick RMATCH validation with simple patterns"
        
        run_jmh_enhanced "quick-fastpath" \
            "-p patternCount=50 -p patternCategory=SIMPLE -p matcherType=FASTPATH -wi 1 -i 1 -f 1" \
            "Quick FastPath validation with simple patterns"
        ;;
        
    "standard") 
        echo "Running standard comprehensive benchmarks..."
        
        run_jmh_enhanced "standard-rmatch-simple" \
            "-p patternCount=100,200 -p patternCategory=SIMPLE -p matcherType=RMATCH" \
            "Standard RMATCH benchmarks with simple patterns"
            
        run_jmh_enhanced "standard-fastpath-simple" \
            "-p patternCount=100,200 -p patternCategory=SIMPLE -p matcherType=FASTPATH" \
            "Standard FastPath benchmarks with simple patterns"
            
        run_jmh_enhanced "standard-fastpath-complex" \
            "-p patternCount=50,100 -p patternCategory=COMPLEX -p matcherType=FASTPATH" \
            "Standard FastPath benchmarks with complex patterns"
        ;;
        
    "full")
        echo "Running full benchmark suite with all matcher types..."
        
        for matcher in "RMATCH" "FASTPATH" "FASTPATH_JIT_OPTIMIZED" "JAVA_NATIVE"; do
            for category in "SIMPLE" "COMPLEX"; do
                run_jmh_enhanced "full-${matcher,,}-${category,,}" \
                    "-p patternCount=50,100,200 -p patternCategory=$category -p matcherType=$matcher" \
                    "Full $matcher benchmarks with $category patterns"
            done
        done
        ;;
        
    "architecture")
        echo "Running architecture-aware benchmarks..."
        
        # First run architecture characterization
        run_jmh_enhanced "arch-normalization" \
            ".*ArchitectureAwareBench.*" \
            "Architecture normalization and system characterization"
        
        # Then run matcher comparisons
        run_jmh_enhanced "arch-comparison" \
            "-p patternCount=100 -p patternCategory=SIMPLE -p matcherType=RMATCH,FASTPATH,JAVA_NATIVE" \
            "Cross-architecture matcher comparison"
        ;;
        
    *)
        echo "ERROR: Unknown test suite: $TEST_SUITE"
        echo "Available suites: quick, standard, full, architecture"
        exit 1
        ;;
esac

echo "=== Generating Enhanced Performance Report ==="

# Create summary report
REPORT_FILE="$RESULTS_DIR/performance-report.md"

cat > "$REPORT_FILE" <<EOF
# Enhanced rmatch Benchmark Report

**Suite**: $TEST_SUITE  
**Timestamp**: $TIMESTAMP  
**Architecture**: $(uname -m) $(uname -s) $(uname -r)  
**Java Version**: $(java -version 2>&1 | head -n1)  

## Results Location

All benchmark results are available in: \`$RESULTS_DIR\`

## JMH Results

EOF

# List all generated JSON files
if ls "$RESULTS_DIR"/*.json 1> /dev/null 2>&1; then
    echo "### Generated Benchmark Files" >> "$REPORT_FILE"
    echo >> "$REPORT_FILE"
    for json_file in "$RESULTS_DIR"/*.json; do
        filename=$(basename "$json_file")
        echo "- \`$filename\`" >> "$REPORT_FILE"
    done
    echo >> "$REPORT_FILE"
else
    echo "⚠️  No JSON result files were generated" >> "$REPORT_FILE"
fi

# Integration with existing system
echo "## Integration with Existing Performance System" >> "$REPORT_FILE"
echo >> "$REPORT_FILE"
echo "These results complement the existing CSV-based performance tracking and can be" >> "$REPORT_FILE"
echo "integrated with the current baseline management system for comprehensive analysis." >> "$REPORT_FILE"
echo >> "$REPORT_FILE"

# Add usage instructions
cat >> "$REPORT_FILE" <<EOF
## Next Steps

1. **Analyze Results**: Use JMH's built-in analysis tools or integrate with existing visualization
2. **Compare Architectures**: Results include architecture normalization for cross-platform comparison  
3. **Update Baselines**: Integrate with existing baseline management if results show improvements
4. **Visualize Trends**: Use results with existing performance chart generation tools

## Enhanced Framework Features

- **Multiple Matcher Types**: RMATCH, FASTPATH (with/without JIT optimization), Java native
- **Pattern Categorization**: Simple and complex pattern libraries with 50+ test patterns
- **Architecture Awareness**: Automatic system characterization and normalization
- **Modern Statistical Analysis**: JMH provides confidence intervals and significance testing
- **GitHub Actions Ready**: JSON output compatible with existing CI visualization pipeline

---
*Generated by Enhanced rmatch Benchmark Suite*
EOF

echo "✅ Enhanced benchmark suite completed!"
echo "📊 Results: $RESULTS_DIR"  
echo "📋 Report: $REPORT_FILE"
echo
echo "To view the report:"
echo "  cat '$REPORT_FILE'"