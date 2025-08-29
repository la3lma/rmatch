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

### Local Testing

```bash
# Run basic performance validation
mvn -pl rmatch-tester exec:java \
  -Dexec.mainClass=no.rmz.rmatch.performancetests.BasicPerformanceTest

# Run full performance comparison  
mvn -pl rmatch-tester exec:java \
  -Dexec.mainClass=no.rmz.rmatch.performancetests.GitHubActionPerformanceTestRunner \
  -Dexec.args="1000 5"  # maxRegexps numRuns
```

### Interpreting Results

- ✅ **PASS**: Your changes improve or maintain performance
- ❌ **FAIL**: Your changes cause performance regression
- ⚠️ **WARNING**: Results are inconclusive or within noise threshold

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