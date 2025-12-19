# Optimal Configuration Guide

## Current Optimal Configuration (December 2025)

Based on comprehensive testing using `OptimizationSwitchExhaustiveTest.java`, the optimal configuration for the Wuthering Heights corpus with 10,000 real-word regular expressions is:

### **Recommended Default Settings**
```properties
rmatch.engine=fastpath
rmatch.prefilter=aho
rmatch.prefilter.threshold=5000
```

### Performance Results
- **Execution Time**: 75ms (fastest among all tested combinations)
- **Memory Usage**: 2330 MB
- **Test Configuration**: Wuthering Heights corpus (675KB) with 10,000 real-word regexps

### Alternative Configurations
For reference, other well-performing configurations:
1. `engine=default,prefilter=aho,threshold=5000` → 55ms, 1479 MB
2. `engine=default,prefilter=aho,threshold=99999` → 100ms, 653 MB
3. `engine=fastpath,prefilter=aho,threshold=99999` → 167ms, 1507 MB

## Implementation Status

✅ **These optimal settings are now the DEFAULT configuration** (as of December 19, 2025):
- `MatcherImpl.java:38` - Default engine changed from "default" to "fastpath"
- `FastPathMatchEngine.java:57` - Default threshold changed from "7000" to "5000"
- Aho-Corasick prefilter was already enabled by default

This means new matcher instances will automatically use the optimal configuration without requiring explicit system properties.

## Important Notes

⚠️ **Configuration is Corpus-Dependent**: The optimal configuration may vary significantly depending on:
- Corpus size and characteristics
- Regular expression patterns and complexity
- Number of regular expressions
- Memory constraints

### Best Practice
**Always run benchmarks on your specific corpus/expression combination** to determine the optimal configuration for your use case. The settings above serve as a good starting point, but performance optimization should be validated for each unique workload.

### How to Benchmark Your Configuration
1. Use `OptimizationSwitchExhaustiveTest.java` as a template
2. Replace corpus and regexp files with your test data
3. Run the exhaustive test to compare all configuration combinations
4. Select the configuration with the best performance/memory trade-off for your needs

## Configuration Properties

### Engine Types
- `default` - Standard engine implementation
- `bloom` - Bloom filter-based engine (mutually exclusive with fastpath)
- `fastpath` - FastPath engine optimized for performance

### Prefilter Options
- `none` - No prefiltering
- `aho` - Aho-Corasick algorithm prefiltering

### Threshold Settings
- `99999` - Conservative threshold (less aggressive prefiltering)
- `5000` - Aggressive threshold (more aggressive prefiltering)

## Test Evidence
This configuration was determined through systematic testing as documented in:
- Test: `OptimizationSwitchExhaustiveTest.java`
- Date: December 19, 2025
- Commit: Current branch `codex/locate-optimization-toggles-and-create-test`

## Future Updates
This document should be updated whenever:
1. New optimization strategies are implemented
2. Significant performance improvements are discovered
3. New corpus types or usage patterns are identified
4. Hardware or JVM changes affect performance characteristics