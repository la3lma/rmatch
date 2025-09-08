#!/bin/bash
#
# Generate performance charts from benchmarks/results data
#
# This script creates comprehensive performance charts based solely on 
# benchmark data found in benchmarks/results/. It replaces the previous
# complex multi-source charting system with a clean, focused approach.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "🚀 Generating rmatch performance charts..."
echo "📂 Project root: ${PROJECT_ROOT}"

# Change to project root
cd "${PROJECT_ROOT}"

# Check if benchmark data exists
if [ ! -d "benchmarks/results" ]; then
    echo "❌ Error: benchmarks/results directory not found"
    echo "   Run benchmarks first to generate performance data"
    exit 1
fi

# Count available benchmark files
JMH_COUNT=$(find benchmarks/results -name "jmh-*.json" 2>/dev/null | wc -l)
MACRO_COUNT=$(find benchmarks/results -name "macro-*.json" 2>/dev/null | wc -l)

echo "📊 Found benchmark data:"
echo "   - JMH benchmark files: ${JMH_COUNT}"
echo "   - Macro benchmark files: ${MACRO_COUNT}"

if [ "$JMH_COUNT" -eq 0 ] && [ "$MACRO_COUNT" -eq 0 ]; then
    echo "❌ Error: No benchmark data found in benchmarks/results/"
    echo "   Run './scripts/run_jmh.sh' or other benchmark scripts first"
    exit 1
fi

# Check Python dependencies
echo "🔍 Checking Python dependencies..."
python3 -c "import pandas, matplotlib, seaborn, numpy" 2>/dev/null || {
    echo "❌ Error: Missing Python dependencies"
    echo "   Install with: pip3 install pandas matplotlib seaborn numpy"
    echo "   Or using requirements.txt: pip3 install -r requirements.txt"
    exit 1
}

# Generate charts
echo "📈 Generating performance charts..."
python3 scripts/generate_benchmarks_charts.py

# Verify charts were created
if [ -f "charts/performance_overview.png" ]; then
    echo "✅ Performance charts generated successfully!"
    echo "📁 Charts saved to: charts/"
    ls -la charts/*.png | sed 's/^/   /'
else
    echo "❌ Error: Chart generation failed"
    exit 1
fi

echo ""
echo "🎉 Performance chart generation complete!"
echo "   View charts in the charts/ directory"
echo "   Updated charts are used in README.md"