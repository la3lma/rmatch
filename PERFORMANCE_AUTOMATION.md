# Performance Chart Automation

This document describes the automated performance chart generation and updating system integrated into the CI/CD pipeline.

## Overview

The system automatically generates and updates performance charts whenever benchmarks are run, ensuring the performance timeline displayed on the README is always current with the latest data.

## Components

### 1. Macro Performance Timeline Chart

- **File**: `performance_timeline.png`
- **Location**: Project root (displayed at top of README.md)
- **Script**: `scripts/generate_macro_performance_plot.py`
- **Data Source**: All `macro-*.json` files in `benchmarks/results/`

#### Color Coding
- 🟢 **Green (Excellent)**: < 2 seconds
- 🟡 **Gold/Yellow (Good)**: 2-15 seconds  
- 🔴 **Red (Needs Work)**: > 15 seconds

### 2. Quick Update Script

- **File**: `scripts/update_performance_chart.sh`
- **Usage**: `./scripts/update_performance_chart.sh`
- **Purpose**: Manual chart regeneration

## Automated Workflows

### Pull Request Performance Check (`.github/workflows/performance-check.yml`)

**Triggers**: On pull requests to main/master branches

**Process**:
1. Runs comprehensive performance tests (5000 regexps, 5 runs)
2. Generates performance evolution charts
3. **NEW**: Generates macro performance timeline chart
4. **NEW**: Auto-commits updated chart back to the PR branch
5. Compares results and posts PR comment
6. Uploads performance artifacts

**Key Addition**:
```yaml
- name: Commit updated performance timeline
  if: github.event_name == 'pull_request'
  run: |
    git config --local user.email "action@github.com"
    git config --local user.name "GitHub Action"
    
    if [ -f "performance_timeline.png" ]; then
      git add performance_timeline.png
      if ! git diff --staged --quiet; then
        git commit -m "chore: update macro performance timeline chart"
        git push origin HEAD:${{ github.head_ref }}
      fi
    fi
```

### Nightly Performance Run (`.github/workflows/perf.yml`)

**Triggers**: Nightly schedule (2 AM UTC) + manual dispatch

**Process**:
1. Runs JMH microbenchmarks
2. Runs macro benchmarks
3. **NEW**: Generates macro performance timeline
4. Profiles performance hotspots
5. Compares vs baseline
6. Uploads artifacts including timeline chart

### Daily Chart Updates (`.github/workflows/update-performance-charts.yml`)

**Triggers**: Daily schedule (6 AM UTC) + push to main + manual dispatch

**Process**:
1. Generates performance evolution charts
2. **NEW**: Generates macro performance timeline chart
3. **NEW**: Commits both chart types if changes detected
4. Pushes updates to main branch

## Benefits

### 1. Always Current Performance Data
- Charts automatically update after every benchmark run
- No manual intervention required
- PR reviewers see latest performance trends

### 2. Immediate Feedback in PRs
- Performance impact visible immediately in PR
- Chart updates committed directly to PR branch
- Reviewers can see performance progression

### 3. Historical Tracking
- Long-term performance trends preserved
- Visual identification of regressions/improvements
- Data-driven performance decisions

### 4. Multiple Update Paths
- **PR Workflow**: Updates on every performance test
- **Nightly Run**: Scheduled comprehensive testing
- **Daily Charts**: Ensures charts stay current
- **Manual**: On-demand updates via script

## Technical Details

### Chart Generation
- **Python Dependencies**: matplotlib, pandas, numpy (from `requirements.txt`)
- **Data Parsing**: Handles malformed JSON in benchmark results
- **Performance Statistics**: Shows recent average, best, worst times
- **Visual Features**: Trend lines, reference lines, color-coded performance levels

### Git Integration
- **Auto-commit**: Charts committed with `[skip ci]` to prevent loops
- **Branch Targeting**: PR commits go to feature branch, scheduled runs to main
- **Conflict Handling**: Only commits if actual changes detected

### Error Handling
- **Graceful Degradation**: Chart generation failures don't break workflows
- **Validation**: Checks for chart file existence before operations
- **Logging**: Clear success/warning/error messages

## Usage Examples

### Manual Chart Update
```bash
# Regenerate chart with latest data
./scripts/update_performance_chart.sh
```

### Workflow Verification
```bash
# Check if workflow will trigger chart update
git log --oneline --grep="performance"

# View recent benchmark results  
ls -la benchmarks/results/macro-*.json | tail -5
```

### Troubleshooting
```bash
# Test chart generation locally
python3 scripts/generate_macro_performance_plot.py

# Check for malformed JSON files
python3 -c "
import json, glob
for f in glob.glob('benchmarks/results/macro-*.json'):
    try:
        json.load(open(f))
        print(f'✓ {f}')
    except Exception as e:
        print(f'✗ {f}: {e}')
"
```

## Future Enhancements

- **Performance Regression Detection**: Automatic alerts for significant slowdowns
- **Benchmark Comparison**: Side-by-side PR vs baseline performance
- **Interactive Charts**: Web-based performance dashboard
- **Performance Goals**: Automated pass/fail criteria based on targets