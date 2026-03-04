# Generate Test Corpora

Generate test corpora using existing infrastructure. Creates realistic text data for benchmarking.

**What this generates:**
- Three corpus types: logs, natural_language, synthetic
- Three sizes each: 1MB, 10MB, 100MB
- Stored in benchmark_suites/corpora/ directory
- Ready for immediate use in benchmarks

**Usage:** `/corpora`

**Command:**
```bash
echo "📚 Generating test corpora..."
make generate-corpora
echo "✅ Test corpora generated! Check benchmark_suites/corpora/ directory."
```