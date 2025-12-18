#!/bin/bash

# Script to run dispatch optimization benchmarks
# This tests modern Java language features for performance improvements

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RESULTS_DIR="${PROJECT_ROOT}/benchmarks/results/dispatch-experiments"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
OUTPUT_DIR="${RESULTS_DIR}/${TIMESTAMP}"

# JMH configuration
JMH_FORKS="${JMH_FORKS:-3}"
JMH_WARMUP_IT="${JMH_WARMUP_IT:-5}"
JMH_IT="${JMH_IT:-10}"
JMH_WARMUP="${JMH_WARMUP:-1s}"
JMH_MEASURE="${JMH_MEASURE:-2s}"
JMH_THREADS="${JMH_THREADS:-1}"

# Ensure Java 25 is available
if ! command -v java &> /dev/null; then
    echo "ERROR: java command not found"
    exit 1
fi

JAVA_VERSION_STRING=$(java -version 2>&1 | grep version | cut -d'"' -f2)
JAVA_MAJOR_VERSION=$(echo "$JAVA_VERSION_STRING" | cut -d'.' -f1)
if [ "$JAVA_MAJOR_VERSION" -lt 25 ] 2>/dev/null; then
    echo "WARNING: Java 25 required for all modern features. Current version: $JAVA_VERSION_STRING"
fi

# Create output directory
mkdir -p "${OUTPUT_DIR}"

echo "=========================================="
echo "Running Dispatch Optimization Benchmarks"
echo "=========================================="
echo "Output directory: ${OUTPUT_DIR}"
echo "JMH Configuration:"
echo "  - Forks: ${JMH_FORKS}"
echo "  - Warmup iterations: ${JMH_WARMUP_IT} x ${JMH_WARMUP}"
echo "  - Measurement iterations: ${JMH_IT} x ${JMH_MEASURE}"
echo "  - Threads: ${JMH_THREADS}"
echo ""

# Build the benchmarks
echo "Building benchmarks..."
cd "${PROJECT_ROOT}"
mvn clean package -pl benchmarks/jmh -am -DskipTests -q

# Run the benchmarks
echo ""
echo "Running dispatch optimization benchmarks..."
echo "Note: Command-line JMH parameters override benchmark annotations for reproducibility"
echo ""

JMH_JAR="${PROJECT_ROOT}/benchmarks/jmh/target/rmatch-benchmarks-jmh-1.1-SNAPSHOT-benchmarks.jar"

if [ ! -f "${JMH_JAR}" ]; then
    echo "ERROR: JMH jar not found at ${JMH_JAR}"
    exit 1
fi

# Run all dispatch benchmarks
# Note: These parameters override @Fork, @Warmup, and @Measurement annotations
# to ensure consistent, reproducible results across runs
java -jar "${JMH_JAR}" \
    -f "${JMH_FORKS}" \
    -wi "${JMH_WARMUP_IT}" \
    -w "${JMH_WARMUP}" \
    -i "${JMH_IT}" \
    -r "${JMH_MEASURE}" \
    -t "${JMH_THREADS}" \
    -rf json \
    -rff "${OUTPUT_DIR}/dispatch-results.json" \
    "DispatchOptimizationBench" \
    2>&1 | tee "${OUTPUT_DIR}/dispatch-output.log"

echo ""
echo "=========================================="
echo "Benchmark complete!"
echo "Results saved to: ${OUTPUT_DIR}"
echo "=========================================="
echo ""

# Generate summary
echo "Generating summary..."
cat > "${OUTPUT_DIR}/SUMMARY.md" << EOF
# Dispatch Optimization Benchmark Results

**Date:** $(date)
**Java Version:** $(java -version 2>&1 | head -1)
**Hostname:** $(hostname)

## Configuration

- **JMH Forks:** ${JMH_FORKS}
- **Warmup:** ${JMH_WARMUP_IT} iterations × ${JMH_WARMUP}
- **Measurement:** ${JMH_IT} iterations × ${JMH_MEASURE}
- **Threads:** ${JMH_THREADS}

## Results

See \`dispatch-results.json\` for detailed results.

### Benchmark Comparisons

This benchmark tests three optimization strategies:

1. **Pattern Matching for instanceof**
   - Traditional: \`if (x instanceof Foo) { Foo f = (Foo) x; ... }\`
   - Modern: \`if (x instanceof Foo f) { ... }\`

2. **Switch Expressions**
   - Traditional: if-else chains
   - Modern: Enhanced switch with arrow syntax

3. **Enum Dispatch**
   - Traditional: if-else enum comparison
   - Modern: Switch expression with enums

### Key Findings

Review the detailed results in \`dispatch-results.json\` to compare:
- \`traditionalInstanceofDispatch\` vs \`patternMatchingInstanceofDispatch\`
- \`ifElseCharClassification\` vs \`switchCharClassification\` vs \`simpleSwitchCharClassification\`
- \`enumIfElseDispatch\` vs \`enumSwitchDispatch\`

Lower scores (ns/op) are better.

## Analysis

To extract and compare results, use:

\`\`\`bash
# Extract benchmark scores
jq '.[] | {benchmark: .benchmark, score: .primaryMetric.score, unit: .primaryMetric.scoreUnit}' dispatch-results.json

# Compare instanceof dispatch methods
jq '.[] | select(.benchmark | contains("Instanceof")) | {benchmark: .benchmark, score: .primaryMetric.score}' dispatch-results.json

# Compare char classification methods
jq '.[] | select(.benchmark | contains("CharClassification")) | {benchmark: .benchmark, score: .primaryMetric.score}' dispatch-results.json

# Compare enum dispatch methods
jq '.[] | select(.benchmark | contains("enumDispatch")) | {benchmark: .benchmark, score: .primaryMetric.score}' dispatch-results.json
\`\`\`

## Recommendations

Based on the results:
1. If pattern matching shows improvement, apply it to hot paths with instanceof checks
2. If switch expressions are faster, convert if-else chains to switch where applicable
3. Document which patterns provide measurable benefit (>3% improvement)

EOF

echo "Summary written to: ${OUTPUT_DIR}/SUMMARY.md"
echo ""
echo "To view results:"
echo "  cat ${OUTPUT_DIR}/SUMMARY.md"
echo "  jq . ${OUTPUT_DIR}/dispatch-results.json"
echo ""
