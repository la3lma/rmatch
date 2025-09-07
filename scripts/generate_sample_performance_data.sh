#!/bin/bash

# Generate sample performance data for demonstration purposes
# This script runs multiple performance tests with different configurations
# to create a more comprehensive dataset for chart generation

set -e

echo "Generating sample performance data..."

# Ensure we're in the right directory
cd "$(dirname "$0")/.."

# Create results directory if it doesn't exist
mkdir -p benchmarks/results

# Run performance tests with different regex counts to show scaling behavior
for regex_count in 50 100 150 200; do
    echo "Running performance test with $regex_count regexes..."
    
    # Run the test (ignore exit codes since some may "fail" due to performance thresholds)
    ./mvnw -q -B -pl rmatch-tester exec:java \
        -Dexec.mainClass=no.rmz.rmatch.performancetests.GitHubActionPerformanceTestRunner \
        -Dexec.args="$regex_count 3" || true
    
    # Add a small delay to ensure different timestamps
    sleep 2
done

echo "Sample performance data generation completed!"
echo "Generated files:"
ls -la benchmarks/results/performance-check-*.json | tail -5

echo ""
echo "Now regenerating charts with the new data..."
python3 scripts/generate_performance_charts.py

echo ""
echo "Chart generation completed! Check the charts/ directory for updated visualizations."