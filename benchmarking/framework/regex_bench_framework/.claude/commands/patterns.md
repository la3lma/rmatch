# Generate Pattern Suites

Generate regex pattern suites using existing infrastructure. Creates patterns for log mining and security signatures.

**What this generates:**
- Log mining patterns: 10, 100, 1000, 10000 patterns
- Security signature patterns: 10, 100, 1000 patterns
- Stored in benchmark_suites/ directory
- Reusable for multiple benchmark runs

**Usage:** `/patterns`

**Command:**
```bash
echo "🎲 Generating pattern suites..."
make generate-patterns
echo "✅ Pattern suites generated! Check benchmark_suites/ directory."
```