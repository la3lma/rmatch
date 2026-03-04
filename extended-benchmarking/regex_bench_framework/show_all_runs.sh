#!/bin/bash

echo "📊 All Benchmark Runs Status"
echo "=============================="

# Find all database files and show their status
find results -name "jobs.db" -type f | sort | while read db_file; do
    run_dir=$(dirname "$db_file")
    run_name=$(basename "$run_dir")
    echo ""
    echo "🔍 $run_name:"
    echo "   Database: $db_file"

    # Get status from database
    if command -v regex-bench &> /dev/null; then
        regex-bench job-status --output "$run_dir" | grep -A5 "Recent Benchmark Runs" | tail -4
    else
        echo "   ❌ regex-bench command not found"
    fi
done

echo ""
echo "💡 Use 'make bench-status' to see the latest run"
echo "💡 Use 'make bench-continue' to resume any interrupted run"