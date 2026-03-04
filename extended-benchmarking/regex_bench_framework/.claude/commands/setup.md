# Setup Regex Benchmarking Environment

Set up the complete regex benchmarking environment using existing infrastructure.

**What this does:**
1. Sets up Python virtual environment
2. Builds all available regex engines
3. Generates pattern suites (10, 100, 1000, 10000 patterns)
4. Generates test corpora (1MB, 10MB, 100MB)
5. Runs engine availability check

**Usage:** `/setup`

**Command:**
```bash
echo "🚀 Setting up regex benchmarking environment..."
make setup && make build-engines && make generate-patterns && make generate-corpora && make check-engines
echo "✅ Setup complete! Use /bench-quick to run a quick test."
```