#!/usr/bin/env bash
set -euo pipefail

# Run a minimal JMH test suite for quick testing
# Usage: scripts/run_jmh_mini_suite.sh [SUITE_NAME]

root_dir=$(git rev-parse --show-toplevel)
cd "$root_dir"

# Generate a run identifier for this entire test suite
SUITE_NAME="${1:-mini-test}"
RUN_ID="$(date -u +"%Y%m%dT%H%M%SZ")-${SUITE_NAME}"

echo "Starting minimal JMH test suite: ${SUITE_NAME}"
echo "Run ID: ${RUN_ID}"

# Export run ID so all individual JMH runs use the same identifier
export JMH_RUN_ID="$RUN_ID"

# Define minimal test configurations for quick testing
test_configs=(
    "Corpus Matching - RMATCH|.*corpusBasedBenchmark.*|-p corpusPatternCount=100 -p textCorpus=WUTHERING_HEIGHTS -p matcherType=RMATCH"
    "Corpus Matching - Java Native|.*corpusBasedBenchmark.*|-p corpusPatternCount=100 -p textCorpus=WUTHERING_HEIGHTS -p matcherType=JAVA_NATIVE"
)

# Run each test configuration
for config in "${test_configs[@]}"; do
    IFS='|' read -r description include_pattern extra_params <<< "$config"
    
    echo ""
    echo "=== Running: $description ==="
    echo "Include pattern: $include_pattern"
    echo "Extra params: $extra_params"
    
    # Set the include pattern and run with minimal settings
    JMH_INCLUDE="$include_pattern" \
    JMH_FORKS=1 \
    JMH_WARMUP_IT=1 \
    JMH_IT=1 \
    JMH_WARMUP=1s \
    JMH_MEASURE=1s \
    JMH_THREADS=1 \
    scripts/run_jmh.sh $extra_params
    
    # Small delay between tests to ensure different timestamps
    sleep 1
done

echo ""
echo "=== Mini Test Suite Complete ==="
echo "Suite: ${SUITE_NAME}"
echo "Run ID: ${RUN_ID}"
echo "Results location: benchmarks/results/jmh-${RUN_ID}-*.{json,txt}"

# Count results files for this run
result_count=$(ls benchmarks/results/jmh-${RUN_ID}-*.json 2>/dev/null | wc -l || echo 0)
echo "Generated ${result_count} result files"

# List all result files from this run
echo ""
echo "Result files:"
ls -lt benchmarks/results/jmh-${RUN_ID}-* 2>/dev/null | head -10 || echo "  No files found"

echo ""
echo "✅ Mini JMH test suite '${SUITE_NAME}' completed successfully!"