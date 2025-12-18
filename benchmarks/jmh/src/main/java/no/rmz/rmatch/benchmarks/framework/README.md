# Extended Testing Framework

This directory contains the modern JMH-based Extended Testing Framework for rmatch performance evaluation.

## Overview

The Extended Testing Framework replaces legacy CSV-based logging with structured JMH benchmarks, providing comprehensive performance measurement with statistical confidence intervals and GitHub Actions compatibility.

## Key Features

- **JMH Integration**: Built on OpenJDK JMH for accurate performance measurement
- **Pattern Library**: 50+ categorized test patterns (Simple, Complex, Pathological)  
- **Structured Logging**: Modern logging format compatible with monitoring systems
- **GitHub Actions Ready**: Automatic environment detection and compatible reporting
- **Statistical Confidence**: Proper warmup, measurement iterations, and result aggregation

## Framework Components

### Core Classes

- `ExtendedTestFramework` - Main JMH benchmark class with `@Benchmark` methods
- `PatternLibrary` & `DefaultPatternLibrary` - Pattern management and categorization
- `BenchmarkConfiguration` - Configuration for benchmark execution
- `TestResults` - Structured results with performance metrics

### Pattern Management

- `PatternCategory` - SIMPLE, COMPLEX, PATHOLOGICAL categories
- `TestPattern` - Individual pattern with metadata
- `PatternMetadata` - Pattern characteristics and complexity information

### Test Organization

- `TestSuite` - Collection of test scenarios
- `TestScenario` - Individual test case with specific parameters

## Usage

### Basic JMH Benchmark Execution

```bash
# Run the extended framework benchmarks
cd /path/to/rmatch
./scripts/run_jmh.sh

# Or target specific benchmarks
JMH_INCLUDE=".*ExtendedTestFramework.*" ./scripts/run_jmh.sh
```

### Programmatic Usage

```java
// Create pattern library
PatternLibrary library = new DefaultPatternLibrary();

// Get patterns by category
List<TestPattern> simplePatterns = library.getPatternsByCategory(PatternCategory.SIMPLE);

// Create configuration
BenchmarkConfiguration config = BenchmarkConfiguration.defaultConfig();

// Results are automatically logged in structured format:
// BENCHMARK_RESULTS: test=pattern_matching_simple, duration_ms=462, memory_mb=21, matches=586000, patterns=22, throughput_ops_sec=1269.9, timestamp=1758226542121
```

### Example Output

The framework produces structured log output compatible with modern monitoring:

```
INFO: BENCHMARK_RESULTS: test=pattern_matching_simple, duration_ms=462, memory_mb=21, matches=586000, patterns=22, throughput_ops_sec=1269.9, timestamp=1758226542121
```

## Integration with Legacy Systems

The framework maintains backward compatibility with existing rmatch baseline management systems while providing modern structured output instead of CSV files.

### Migration from CSV Logging

**Before (Legacy)**:
```java
CSVAppender.append("measurements/results.csv", new long[]{timestamp, duration, memory});
```

**After (Modern)**:
```java
LOG.info(results.toStructuredLog());
// Produces: BENCHMARK_RESULTS: test=example, duration_ms=100, memory_mb=50, matches=1000, patterns=10, throughput_ops_sec=10000.0, timestamp=1234567890
```

## Pattern Categories

### SIMPLE (22 patterns)
- Literal string patterns (`hello`, `world`, `test`)
- Basic character classes (`[a-z]`, `[0-9]`, `[aeiou]`)
- Simple quantifiers (`a+`, `b*`, `c?`, `d{3}`)

### COMPLEX (10 patterns)  
- Email-like patterns
- Phone number formats
- Date patterns (ISO, US format)
- Complex alternations
- Nested groups with quantifiers

### PATHOLOGICAL (5 patterns)
- Exponential backtracking patterns
- Large quantifier ranges
- Deeply nested groups
- Large alternations with quantifiers

## Performance Characteristics

The framework is designed for:
- **Throughput measurement**: Operations per second
- **Memory usage tracking**: Peak memory consumption during matching
- **Statistical confidence**: Proper JMH measurement methodology
- **GitHub Actions compatibility**: CI-friendly execution and reporting

## Configuration Options

```java
BenchmarkConfiguration config = new BenchmarkConfiguration(
    1000,         // maxPatterns
    100000,       // maxInputLength  
    true,         // enablePercentileMetrics
    true,         // githubActionsMode (auto-detected)
    "test_name"   // testName
);
```

## Extending the Framework

### Adding Custom Patterns

```java
PatternLibrary library = new DefaultPatternLibrary();

PatternMetadata metadata = new PatternMetadata(
    "custom_pattern", 
    "Description of custom pattern",
    PatternCategory.SIMPLE,
    3,        // complexity (1-10)
    false     // isPathological
);

TestPattern pattern = new TestPattern("your-regex-here", metadata);
library.addCustomPattern(pattern);
```

### Creating Custom Test Scenarios

```java
TestScenario scenario = new TestScenario(
    "custom_test",
    PatternCategory.COMPLEX,
    50,                    // pattern count
    "test input data",     // input text
    config                 // benchmark configuration
);
```

## Implementation Notes

- All regex patterns are validated to work with rmatch's regex engine
- Memory measurements include compilation and matching phases
- Structured logging format is designed for easy parsing by monitoring tools
- Framework automatically detects GitHub Actions environment for optimized CI execution

## See Also

- `FrameworkUsageExample.java` - Complete usage examples
- `../CompileAndMatchBench.java` - Existing JMH benchmarks
- `../../../scripts/run_jmh.sh` - JMH execution script