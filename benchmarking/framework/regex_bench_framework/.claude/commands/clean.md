# Clean Up Benchmark Environment

Clean up benchmark results, temporary files, and reset the environment.

**What this removes:**
- Old benchmark results (keeps latest 5 runs)
- Temporary pattern and corpus files
- Debug and log files
- Background processes

**Usage:** `/clean`

**Command:**
```bash
echo "🧹 Cleaning up benchmark environment..."

# Kill any lingering background processes
pkill -f "RMatchBenchmark\|debug_subprocess\|minimal_subprocess\|runner.sh" 2>/dev/null || true

# Clean up old results (keep latest 5)
if [ -d results ]; then
    echo "Cleaning old benchmark results..."
    ls -dt results/*/ 2>/dev/null | tail -n +6 | xargs rm -rf 2>/dev/null || true
fi

# Clean up temporary files
rm -rf patterns/ large_corpora/ /tmp/test_*.txt /tmp/fair_*.txt debug_subprocess.py minimal_subprocess_test.py 2>/dev/null || true

# Clean Python cache
find . -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true
find . -name "*.pyc" -delete 2>/dev/null || true

echo "✅ Environment cleaned! Use /setup to reinitialize if needed."
```