#!/bin/bash
RESULTS_DIR="$1"
if [ -z "$RESULTS_DIR" ]; then
    echo "Usage: $0 <results_directory>"
    exit 1
fi

echo "📊 Real-time Benchmark Monitor"
echo "🎯 Watching: $RESULTS_DIR"
echo "⏰ Started: $(date)"
echo ""

# Function to show current activity
show_activity() {
    echo "🔍 $(date '+%H:%M:%S') - Current Activity:"
    
    # Check benchmark processes
    BENCH_PROCS=$(ps aux | grep -E "regex-bench|java.*Benchmark" | grep -v grep | wc -l | tr -d ' ')
    echo "  📈 Active benchmark processes: $BENCH_PROCS"
    
    # Check CPU usage of benchmark processes  
    if [ "$BENCH_PROCS" -gt 0 ]; then
        echo "  💻 CPU usage:"
        ps aux | grep -E "regex-bench|java.*Benchmark" | grep -v grep | awk '{print "    " $3"% - " $11 " " $12 " " $13}' | head -3
    fi
    
    # Check results directory activity
    if [ -d "$RESULTS_DIR" ]; then
        echo "  📁 Results directory:"
        echo "    Files: $(find "$RESULTS_DIR" -type f | wc -l | tr -d ' ')"
        echo "    Latest: $(find "$RESULTS_DIR" -type f -exec stat -f '%m %N' {} \; 2>/dev/null | sort -nr | head -1 | cut -d' ' -f2- | xargs basename 2>/dev/null || echo 'none')"
        
        # Check for log files with recent activity
        for log in "$RESULTS_DIR"/*.log "$RESULTS_DIR"/logs/*.log; do
            if [ -f "$log" ]; then
                echo "  📄 $(basename "$log"): $(tail -1 "$log" 2>/dev/null | head -c 60)..."
            fi
        done
    fi
    
    echo ""
}

# Monitor loop
while true; do
    show_activity
    sleep 5
done
