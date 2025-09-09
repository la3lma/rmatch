#!/bin/bash
# Quick script to regenerate the macro performance timeline chart

echo "Updating macro performance timeline chart..."
python3 scripts/generate_macro_performance_plot.py

if [ $? -eq 0 ]; then
    echo "✅ Performance chart updated successfully!"
    echo "📊 Chart saved to: performance_timeline.png"
    echo "🔗 View in README: https://github.com/la3lma/rmatch#macro-performance-timeline"
else
    echo "❌ Failed to update performance chart"
    exit 1
fi