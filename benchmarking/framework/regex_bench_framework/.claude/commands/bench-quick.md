# Quick Benchmark Test

Run a quick validation benchmark to test that engines are working properly using existing infrastructure.

**What this runs:**
- Small pattern counts (10 patterns)
- Small corpus (100KB)
- 3 iterations for statistical validity
- Multi-core parallel execution
- Fast completion (~10 seconds)

**Usage:** `/bench-quick`

**Command:**
```bash
echo "🏃‍♂️ Running quick benchmark test..."
make test-quick
echo "✅ Quick test complete! Use /bench-large for comprehensive testing."
```