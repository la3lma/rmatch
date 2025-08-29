# Product Requirements Document: GitHub Action Performance Test

## Overview

This document outlines the requirements for implementing a GitHub Action that automatically tests whether Pull Requests (PRs) provide meaningful performance improvements to the rmatch regular expression matcher compared to Java's standard regex implementation.

## Background

The rmatch project implements a custom regular expression matcher designed to be more efficient than Java's built-in `java.util.regex` implementation. Currently, the project has performance testing infrastructure, but lacks an automated way to validate that PRs actually improve performance in a measurable and meaningful way.

## Current State Analysis

### Existing Infrastructure
- **JavaRegexpMatcher**: Mock matcher using standard Java `Pattern.compile()` and matching
- **MatcherBenchmarker**: Infrastructure for comparing performance between matchers
- **MatchPairAnalysis**: Analyzes results between rmatch and Java matchers
- **JMH Benchmarks**: Micro-benchmarks using Java Microbenchmark Harness
- **Macro Benchmarks**: Large-scale performance tests using Wuthering Heights corpus
- **GitHub Workflows**: Basic CI (`.github/workflows/ci.yml`) and performance testing (`.github/workflows/perf.yml`)
- **Comparison Scripts**: `scripts/compare_and_comment.sh` for posting performance results

### Current Gaps
- No clear pass/fail criteria for performance improvements
- No standardized baseline for comparison
- Performance tests run but don't block PRs based on results
- No quantitative definition of "meaningful improvement"

## Requirements

### Functional Requirements

#### FR1: Automated Performance Comparison
- **Description**: The GitHub Action must automatically run performance comparisons between rmatch and JavaRegexpMatcher when a PR is created or updated
- **Acceptance Criteria**:
  - Trigger on PR creation, updates, and pushes to PR branches
  - Compare rmatch performance against JavaRegexpMatcher baseline
  - Generate quantitative performance metrics (execution time, memory usage)
  - Store results in a standardized format

#### FR2: Meaningful Improvement Criteria
- **Description**: Define and implement clear criteria for what constitutes "meaningful improvement"
- **Acceptance Criteria**:
  - Execution time improvement threshold: ≥5% faster than baseline
  - Memory usage improvement threshold: ≥3% less memory than baseline
  - Statistical significance: Results must be consistent across multiple runs (minimum 3 runs)
  - Regression detection: Flag if performance degrades by ≥2%

#### FR3: Pass/Fail Determination
- **Description**: The GitHub Action must return success/failure status based on performance criteria
- **Acceptance Criteria**:
  - **PASS**: rmatch shows meaningful improvement OR maintains performance within acceptable bounds
  - **FAIL**: rmatch shows performance regression beyond threshold
  - **WARNING**: Performance change is within noise threshold but concerning
  - Status must be reported to GitHub PR checks interface

#### FR4: Baseline Management
- **Description**: Maintain and update performance baselines for comparison
- **Acceptance Criteria**:
  - Store baseline performance data in `benchmarks/baseline/` directory
  - Support for multiple baseline types (micro, macro benchmarks)
  - Automatic baseline update when merged to main branch
  - Version-controlled baseline history

#### FR5: Comprehensive Reporting
- **Description**: Generate detailed performance reports for PR review
- **Acceptance Criteria**:
  - Markdown summary comment on PR with key metrics
  - Detailed performance comparison tables
  - Visual indicators (✅/❌/⚠️) for different metrics
  - Links to full benchmark results and artifacts

### Non-Functional Requirements

#### NFR1: Performance Test Execution Time
- **Description**: Performance tests should complete within reasonable time limits
- **Acceptance Criteria**:
  - Micro-benchmarks: Complete within 5 minutes
  - Macro benchmarks: Complete within 15 minutes
  - Total GitHub Action runtime: ≤20 minutes

#### NFR2: Resource Utilization
- **Description**: Efficient use of GitHub Actions resources
- **Acceptance Criteria**:
  - Use GitHub-hosted runners (ubuntu-latest)
  - Pin CPU governor to 'performance' mode for consistent results
  - Optimize JVM settings for stable performance measurement

#### NFR3: Reliability
- **Description**: Performance tests must be reliable and repeatable
- **Acceptance Criteria**:
  - Multiple runs to account for variance
  - Warm-up iterations before measurement
  - Statistical analysis to filter noise
  - Retry mechanism for failed runs

## Technical Implementation

### New Components Required

#### 1. Enhanced Performance Test Runner
**File**: `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/GitHubActionPerformanceTest.java`
- Orchestrates comparison between rmatch and JavaRegexpMatcher
- Implements statistical analysis for meaningful improvement detection
- Generates structured output for GitHub Action consumption

#### 2. Performance Criteria Evaluator
**File**: `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/PerformanceCriteriaEvaluator.java`
- Defines improvement thresholds and regression detection
- Implements pass/fail logic based on performance metrics
- Provides detailed analysis of performance changes

#### 3. Baseline Management System
**File**: `rmatch-tester/src/main/java/no/rmz/rmatch/performancetests/BaselineManager.java`
- Loads and compares against baseline performance data
- Updates baselines when changes are merged to main
- Maintains baseline history and metadata

#### 4. Enhanced GitHub Action Workflow
**File**: `.github/workflows/performance-check.yml`
- Dedicated workflow for PR performance testing
- Integrates with existing benchmark infrastructure
- Reports results to GitHub PR interface

#### 5. Performance Test Configuration
**File**: `rmatch-tester/src/main/resources/performance-test-config.properties`
- Configurable thresholds for improvement criteria
- Test parameters (corpus size, regex count, iterations)
- Environment-specific settings

### Modified Components

#### 1. MatcherBenchmarker Enhancement
- Add methods for statistical analysis across multiple runs
- Implement confidence intervals and significance testing
- Enhanced JSON output format with metadata

#### 2. MatchPairAnalysis Enhancement  
- Add pass/fail determination logic
- Implement improvement percentage calculations
- Enhanced reporting capabilities

#### 3. Compare and Comment Script Enhancement
**File**: `scripts/compare_and_comment.sh`
- Add pass/fail exit codes
- Enhanced markdown formatting for PR comments
- Integration with GitHub PR status checks

### Data Structures

#### Performance Baseline Format
```json
{
  "version": "1.0",
  "timestamp": "2024-01-15T10:30:00Z",
  "environment": {
    "java_version": "17.0.16",
    "os": "ubuntu-latest",
    "cpu_governor": "performance"
  },
  "micro_benchmarks": {
    "compilation_time_ms": 123.45,
    "match_time_ns": 98765,
    "memory_usage_mb": 45.67
  },
  "macro_benchmarks": {
    "total_time_ms": 5432.10,
    "matches_found": 12345,
    "memory_peak_mb": 256.78
  }
}
```

#### Performance Result Format
```json
{
  "test_run_id": "pr-123-run-1",
  "rmatch_results": {...},
  "java_results": {...},
  "comparison": {
    "time_improvement_percent": 8.5,
    "memory_improvement_percent": 4.2,
    "passes_criteria": true,
    "significance_level": 0.95
  }
}
```

## Success Metrics

1. **Accuracy**: 95% of performance improvements correctly identified
2. **False Positive Rate**: <5% of non-improving PRs flagged as improvements
3. **Execution Time**: Average GitHub Action runtime <15 minutes
4. **Developer Adoption**: PR authors use performance feedback to optimize changes
5. **Regression Prevention**: Zero performance regressions merged to main branch

## Implementation Phases

### Phase 1: Core Infrastructure (Week 1-2)
- Implement PerformanceCriteriaEvaluator
- Enhance MatcherBenchmarker with statistical analysis
- Create baseline data structure and storage

### Phase 2: GitHub Action Integration (Week 3)
- Create enhanced performance-check.yml workflow
- Modify compare_and_comment.sh for pass/fail logic
- Implement PR status check integration

### Phase 3: Testing and Validation (Week 4)
- Test with historical PR data
- Validate improvement criteria thresholds
- Performance tune test execution time

### Phase 4: Documentation and Rollout (Week 5)
- Update README with performance testing guidelines
- Create developer documentation for interpreting results
- Enable workflow for all PRs

## Risks and Mitigation

1. **Risk**: Performance test results are noisy/inconsistent
   - **Mitigation**: Multiple runs, statistical analysis, CPU governor pinning

2. **Risk**: Tests take too long and slow down development
   - **Mitigation**: Optimized test parameters, parallel execution, time limits

3. **Risk**: False positives discourage legitimate PRs
   - **Mitigation**: Careful threshold tuning, warning vs. failure modes

4. **Risk**: Infrastructure costs for frequent performance testing
   - **Mitigation**: Efficient resource usage, test caching where possible

## Dependencies

- Java 17 runtime environment
- Maven build system
- GitHub Actions infrastructure
- JMH (Java Microbenchmark Harness)
- Wuthering Heights test corpus
- Existing rmatch and JavaRegexpMatcher implementations

## Acceptance Criteria

The GitHub Action performance test is considered complete when:

1. It automatically runs on all PRs and provides pass/fail status
2. It correctly identifies meaningful performance improvements (≥5% time, ≥3% memory)
3. It prevents performance regressions from being merged
4. It completes within 20 minutes on GitHub Actions
5. It provides clear, actionable feedback to developers
6. It maintains baseline performance data automatically
7. It has been validated against historical performance data

## Future Enhancements

- Integration with performance trend analysis
- Support for custom performance test scenarios
- A/B testing framework for performance optimizations
- Performance profiling integration (async-profiler)
- Multi-platform performance comparison (Linux, macOS, Windows)