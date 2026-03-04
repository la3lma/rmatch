# Monitor Running Benchmarks

Monitor active benchmarks with real-time CPU usage, process counts, and log activity.

**Usage:** `/monitor [results_directory]`

**Command:**
```bash
# Find the most recent results directory if none specified
if [ -n "$1" ]; then
    RESULTS_DIR="$1"
else
    RESULTS_DIR=$(ls -dt results/*/ 2>/dev/null | head -1 | sed 's|/$||')
    if [ -z "$RESULTS_DIR" ]; then
        echo "❌ No results directory found"
        exit 1
    fi
    echo "🎯 Auto-detected: $RESULTS_DIR"
fi

# Run the monitor for a few cycles
timeout 30 ./monitor_benchmark.sh "$RESULTS_DIR" || echo "📊 Monitoring complete"
```
