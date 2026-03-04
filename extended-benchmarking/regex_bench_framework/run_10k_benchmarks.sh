#!/bin/bash

echo "🔍 Monitoring pattern generation progress..."
echo "Waiting for patterns_1000.txt and patterns_10000.txt..."

# Monitor for pattern completion
while true; do
    if [ -f "benchmark_suites/log_mining/patterns_10000.txt" ]; then
        echo "✅ All patterns ready! Starting 10K benchmarks..."
        break
    elif [ -f "benchmark_suites/log_mining/patterns_1000.txt" ]; then
        echo "✅ 1000 patterns ready, waiting for 10000 patterns..."
    fi
    sleep 10
done

echo "🚀 Launching comprehensive 10K pattern benchmarks..."

# Run comprehensive comparison
echo "📊 Running comprehensive comparison with all engines..."
arch -x86_64 .venv/bin/regex-bench run-phase \
  --config test_matrix/ultimate_10k_comparison.json \
  --output results/ultimate_10k_$(date +%Y%m%d_%H%M%S) \
  --parallel 2

echo "✅ 10K benchmarks completed!"
