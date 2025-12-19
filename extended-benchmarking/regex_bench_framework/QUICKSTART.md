# Regex Benchmarking Framework - Quick Start Guide

## 🚀 Getting Started

This framework provides comprehensive, scientific benchmarking for regex engines with full support for your target engines: **Java Native**, **rmatch**, **RE2 C++**, **RE2J**, **Go RE2**, and **Hyperscan**.

### 1. Setup (One Command)

```bash
make setup
```

This creates the virtual environment, installs dependencies, and builds all available engines.

### 2. Quick Validation Test

```bash
make test-quick
```

Runs a 2-minute validation test to ensure everything works.

### 3. Run Phase 1 Benchmarks

```bash
make bench-phase1
```

Runs the complete Phase 1 benchmark suite (30-60 minutes) comparing all engines.

### 4. Generate Report

```bash
make report
```

Creates HTML report with performance charts and statistical analysis.

## 📋 Framework Features

### ✅ **Complete Engine Support**
- **Java Native** (`java.util.regex`) - Industry standard baseline
- **RE2J** - Java port with linear-time guarantees
- **RE2 C++** - Google's original high-performance engine
- **Go RE2** - Go wrapper for RE2
- **Hyperscan** - Intel's multi-pattern SIMD engine
- **rmatch** - Your high-performance engine (integration ready)

### ✅ **Scientific Rigor**
- Statistical significance testing
- Confidence intervals and outlier detection
- Warm-up phase handling
- Cross-platform normalization
- Reproducible results with fixed seeds

### ✅ **Realistic Test Data**
- **Log Mining Patterns**: Timestamps, IPs, URLs, error codes
- **Large-Scale Corpora**: 1MB to 100MB+ test data
- **Configurable Match Density**: Sparse to dense matching scenarios
- **Pattern Categories**: Simple to pathological complexity

### ✅ **Rich Output Format**
- **JSON-only data storage** (no CSV dependencies)
- Detailed timing breakdowns (compilation vs scanning)
- Memory usage profiling
- Throughput calculations (MB/s, patterns/s)
- Comprehensive metadata for reproducibility

## 🎯 Understanding Results

### Key Metrics
- **Throughput**: MB/s scanning rate
- **Speedup**: Performance ratio vs baseline
- **Memory Efficiency**: Peak memory usage
- **Compilation Cost**: Pattern compilation time
- **Scaling**: Performance vs pattern count

### Expected Performance Characteristics
```
Pattern Count:     10        100       1000
java-native:     ~50 MB/s   ~45 MB/s   ~35 MB/s
re2j:           ~80 MB/s   ~75 MB/s   ~70 MB/s
re2-cpp:        ~120 MB/s  ~110 MB/s  ~100 MB/s
hyperscan:      ~100 MB/s  ~200 MB/s  ~400 MB/s
rmatch:         ???        ???       ??? (This is what we're measuring!)
```

## 🛠 Advanced Usage

### Custom Configurations

```bash
# Run specific engines only
make bench-custom CONFIG=test_matrix/custom.json

# Single engine test
make bench-single ENGINE=rmatch

# Compare specific runs
make compare BASELINE=results/phase1_20241219_120000 COMPARISON=results/phase1_20241219_140000
```

### Engine Integration

To add rmatch integration:

1. **Create engine directory**:
   ```bash
   mkdir engines/rmatch
   ```

2. **Add configuration** (`engines/rmatch/engine.json`):
   ```json
   {
     "name": "rmatch",
     "version": "2.1.0",
     "description": "High-performance regex engine",
     "type": "external",
     "command": ["bash", "runner.sh", "{patterns}", "{corpus}"],
     "requires": ["rmatch"],
     "characteristics": {
       "backtracking": false,
       "linear_time_guarantee": true,
       "multi_pattern_optimized": true
     }
   }
   ```

3. **Add runner script** (`engines/rmatch/runner.sh`):
   ```bash
   #!/bin/bash
   exec rmatch benchmark "$1" "$2"
   ```

4. **Test integration**:
   ```bash
   make build-rmatch
   make bench-single ENGINE=rmatch
   ```

## 📊 Expected Results for Your Use Case

Based on the structured benchmarking plan, you should see:

### **Primary Claim Validation**
- **rmatch vs Java**: Target >2x throughput improvement
- **Statistical significance**: p < 0.05 required
- **Consistency**: CV < 15% across runs

### **Scaling Characteristics**
- **Small pattern sets (10-100)**: Compilation overhead matters
- **Large pattern sets (1000+)**: Scanning efficiency dominates
- **Hyperscan advantage**: Increases with pattern count
- **RE2 family**: Consistent linear performance

### **Architecture Insights**
- **Memory vs Speed**: Trade-off curves per engine
- **Compilation vs Runtime**: Different optimization strategies
- **Feature Support**: Which engines handle which pattern types

## 🔧 Troubleshooting

### Common Issues

**Hyperscan on ARM Mac**:
```bash
# Use Docker x86 emulation
docker run --platform linux/amd64 -v $(pwd):/app ubuntu:latest
# Or use cloud x86 instance
```

**Java Out of Memory**:
```bash
export JAVA_OPTS="-Xmx8g"
make bench-phase1
```

**Build Failures**:
```bash
# Check system status
make info
make status

# Rebuild specific engine
make build-re2j
```

## 📈 Next Steps

1. **Phase 1**: Establish baseline performance comparison
2. **Phase 2**: Add more pattern suites (security signatures, pathological cases)
3. **Phase 3**: Large-scale testing (10GB corpora, 50K patterns)
4. **Publication**: Generate paper-ready methodology and results

## 🎯 Success Criteria

The framework validates these claims:
- ✅ **Throughput**: rmatch >2x faster than java.util.regex
- ✅ **Scalability**: Sub-linear degradation with pattern count
- ✅ **Memory**: Comparable or better memory usage
- ✅ **Correctness**: 100% match agreement with reference engines

Ready to benchmark rmatch against the best regex engines available! 🚀