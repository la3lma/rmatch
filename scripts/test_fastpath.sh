#!/bin/bash
# Quick test script to verify fast-path optimizations work

set -e

echo "=== Fast-Path Optimization Quick Test ==="
echo ""

# Change to project root
cd "$(dirname "$0")"

echo "1. Building project..."
mvn clean compile -DskipTests -q -Dspotbugs.skip=true

echo "2. Running unit tests for fast-path components..."
mvn test -q -Dtest="AsciiOptimizerTest,StateSetBuffersTest" -Dspotbugs.skip=true

echo ""
echo "=== All Tests Passed! ==="
echo ""
echo "Next steps:"
echo "  1. Run micro-benchmarks: mvn package && java -jar benchmarks/jmh/target/*-benchmarks.jar FastPathBench"
echo "  2. Run production benchmarks: java -jar benchmarks/jmh/target/*-benchmarks.jar ProductionWorkloadBench"
echo "  3. See FASTPATH_OPTIMIZATION.md for detailed testing guide"
