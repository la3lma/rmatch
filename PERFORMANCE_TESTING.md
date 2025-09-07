# Performance Testing Documentation

## Overview

This document describes the automated performance comparison system implemented for rmatch PRs.

## How It Works

### Performance Criteria

The system evaluates PRs based on these thresholds:

- **PASS**: 
  - Time improvement ≥ 5% OR Memory improvement ≥ 3%
  - OR performance maintained within acceptable bounds
- **FAIL**: 
  - Performance regression ≥ 2% in time or memory
- **WARNING**: 
  - Changes within noise threshold but with low statistical significance

### Workflow Trigger

The performance check automatically runs when:
- A PR is opened against main/master branch
- A PR is updated with new commits
- A PR is reopened

### Statistical Significance

- Minimum 3 test runs required
- Results must have coefficient of variation < 20%
- Baseline comparison uses historical performance data

## Components

### Core Classes

- `PerformanceCriteriaEvaluator`: Implements pass/fail logic
- `GitHubActionPerformanceTest`: Orchestrates performance comparison
- `BaselineManager`: Manages performance baseline data
- `GitHubActionPerformanceTestRunner`: Main entry point for GitHub Actions
- `ComprehensivePerformanceTest`: Large-scale performance testing with full corpus
- `BasicPerformanceTest`: Unified entry point for both basic and comprehensive testing

### Workflows

- `.github/workflows/performance-check.yml`: PR performance validation
- `.github/workflows/perf.yml`: Existing nightly performance testing

### Scripts

- `scripts/compare_and_comment.sh`: Enhanced with pass/fail logic and GitHub status checks

## Baseline Management

Baselines are stored in `benchmarks/baseline/` directory:
- `rmatch-baseline.json`: rmatch performance baseline
- `java-baseline.json`: Java regexp performance baseline

Format: Simple CSV-like with metadata headers.

## Usage for Developers

## Usage for Developers

### Local Testing

```bash
# Run basic performance validation (small scale)
mvn -pl rmatch-tester exec:java \
  -Dexec.mainClass=no.rmz.rmatch.performancetests.BasicPerformanceTest

# Run comprehensive performance test (large scale with Wuthering Heights corpus)
mvn -pl rmatch-tester exec:java \
  -Dexec.mainClass=no.rmz.rmatch.performancetests.BasicPerformanceTest \
  -Dexec.args="comprehensive"

# Run comprehensive test with specific number of regexps
mvn -pl rmatch-tester exec:java \
  -Dexec.mainClass=no.rmz.rmatch.performancetests.BasicPerformanceTest \
  -Dexec.args="comprehensive 1000"

# Run standalone comprehensive test
mvn -pl rmatch-tester exec:java \
  -Dexec.mainClass=no.rmz.rmatch.performancetests.ComprehensivePerformanceTest \
  -Dexec.args="5000"  # maxRegexps

# Run full performance comparison  
mvn -pl rmatch-tester exec:java \
  -Dexec.mainClass=no.rmz.rmatch.performancetests.GitHubActionPerformanceTestRunner \
  -Dexec.args="1000 5"  # maxRegexps numRuns
```

### Test Types

#### Basic Performance Test
- **Scale**: Small (100 regexps)
- **Purpose**: Quick validation and development feedback
- **Duration**: ~30 seconds
- **Usage**: `BasicPerformanceTest` (default mode)

#### Comprehensive Performance Test
- **Scale**: Large (1000-10000 regexps)
- **Purpose**: Realistic large-scale performance comparison
- **Corpus**: Full Wuthering Heights text (675KB, 13,182 lines)
- **Regexps**: Real words from Wuthering Heights (10,445+ patterns available)
- **Duration**: 2-10 minutes depending on scale
- **Usage**: `BasicPerformanceTest comprehensive` or `ComprehensivePerformanceTest`
- **Output**: Detailed performance metrics including throughput (MB/s) and performance ratios

### Interpreting Results

- ✅ **PASS**: Your changes improve or maintain performance
- ❌ **FAIL**: Your changes cause performance regression
- ⚠️ **WARNING**: Results are inconclusive or within noise threshold

### Performance Metrics

The comprehensive test provides detailed metrics:
- **Throughput (MB/s)**: Processing speed for both rmatch and Java regex
- **Performance Ratio**: Ratio of rmatch/java performance (>1.0 means rmatch is faster)
- **Memory Usage**: Memory consumption comparison
- **Status**: Overall performance assessment based on criteria

### When to Use Each Test

#### Use Basic Test When:
- Developing and need quick feedback
- Running in CI/CD for every commit
- Testing small changes or bug fixes
- Need results in under 1 minute

#### Use Comprehensive Test When:
- Evaluating major performance changes
- Preparing for releases
- Investigating performance characteristics at scale
- Need realistic workload assessment
- Have 5-10 minutes available for testing

### Performance Reports

The system generates:
1. **PR Comment**: High-level summary with status and key metrics
2. **JSON Report**: Detailed performance data and statistics
3. **GitHub Status Check**: Pass/fail status visible in PR checks

## Future Enhancements

The infrastructure is designed to support:
- AI agent feedback loops for performance optimization guidance
- Multiple baseline types (micro/macro benchmarks)  
- Historical performance tracking and trend analysis
- Adaptive threshold tuning based on historical variance