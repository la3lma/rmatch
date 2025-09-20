#!/usr/bin/env bash
set -euo pipefail

# Run multiple JMH benchmarks as part of a single test suite with a shared run ID
# Usage: scripts/run_jmh_suite.sh [SUITE_NAME]
# Env vars (same as run_jmh.sh):
#   JMH_INCLUDE   - regex for benchmark names to include (default: no\.rmz\.rmatch\.benchmarks\..*)
#   JMH_WARMUP    - e.g. '5s'
#   JMH_WARMUP_IT - warmup iterations, e.g. '5'
#   JMH_MEASURE   - e.g. '10s'
#   JMH_IT        - measurement iterations, e.g. '10'
#   JMH_FORKS     - e.g. '2'
#   JMH_THREADS   - e.g. '1'

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

# Generate a run identifier for this entire test suite
SUITE_NAME="${1:-comprehensive}"
RUN_ID="$(date -u +"%Y%m%dT%H%M%SZ")-${SUITE_NAME}"

echo "Starting JMH test suite: ${SUITE_NAME}"
echo "Run ID: ${RUN_ID}"

# Export run ID so all individual JMH runs use the same identifier
export JMH_RUN_ID="$RUN_ID"

# Define test configurations for a comprehensive suite
# Each array element is: "description|include_pattern|extra_params"
test_configs=(
    "Pattern Compilation - Simple|.*patternCompilationBenchmark.*|-p patternCount=100 -p patternCategory=SIMPLE"
    "Pattern Compilation - Complex|.*patternCompilationBenchmark.*|-p patternCount=100 -p patternCategory=COMPLEX"
    "Corpus Matching - Small Dataset|.*corpusBasedBenchmark.*|-p corpusPatternCount=100 -p textCorpus=WUTHERING_HEIGHTS -p matcherType=RMATCH"
    "Corpus Matching - Java Native|.*corpusBasedBenchmark.*|-p corpusPatternCount=100 -p textCorpus=WUTHERING_HEIGHTS -p matcherType=JAVA_NATIVE"
    "Functional Verification|.*functionalVerification.*|-p patternCount=50 -p patternCategory=SIMPLE"
)

# Run each test configuration
for config in "${test_configs[@]}"; do
    IFS='|' read -r description include_pattern extra_params <<< "$config"
    
    echo ""
    echo "=== Running: $description ==="
    echo "Include pattern: $include_pattern"
    echo "Extra params: $extra_params"
    
    # Set the include pattern and run
    JMH_INCLUDE="$include_pattern" scripts/run_jmh.sh $extra_params
    
    # Small delay between tests to ensure different timestamps
    sleep 2
done

echo ""
echo "=== Test Suite Complete ==="
echo "Suite: ${SUITE_NAME}"
echo "Run ID: ${RUN_ID}"
echo "Results location: benchmarks/results/jmh-${RUN_ID}-*.{json,txt}"

# Count results files for this run
result_count=$(ls benchmarks/results/jmh-${RUN_ID}-*.json 2>/dev/null | wc -l)
echo "Generated ${result_count} result files"

# List all result files from this run
echo ""
echo "Result files:"
ls -lt benchmarks/results/jmh-${RUN_ID}-* 2>/dev/null | head -10 || echo "  No files found"

echo ""
echo "✅ JMH test suite '${SUITE_NAME}' completed successfully!"
echo "Use 'make visualize-benchmarks' to generate plots for this test run."