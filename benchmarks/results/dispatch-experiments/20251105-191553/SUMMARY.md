# Dispatch Optimization Benchmark Results

**Date:** Wed Nov  5 19:17:00 UTC 2025
**Java Version:** openjdk version "25.0.1" 2025-10-21 LTS
**Hostname:** runnervmf2e7y

## Configuration

- **JMH Forks:** 1
- **Warmup:** 2 iterations × 1s
- **Measurement:** 3 iterations × 1s
- **Threads:** 1

## Results

See `dispatch-results.json` for detailed results.

### Benchmark Comparisons

This benchmark tests three optimization strategies:

1. **Pattern Matching for instanceof**
   - Traditional: `if (x instanceof Foo) { Foo f = (Foo) x; ... }`
   - Modern: `if (x instanceof Foo f) { ... }`

2. **Switch Expressions**
   - Traditional: if-else chains
   - Modern: Enhanced switch with arrow syntax

3. **Enum Dispatch**
   - Traditional: if-else enum comparison
   - Modern: Switch expression with enums

### Key Findings

Review the detailed results in `dispatch-results.json` to compare:
- `traditionalInstanceofDispatch` vs `patternMatchingInstanceofDispatch`
- `ifElseCharClassification` vs `switchCharClassification` vs `simpleSwitchCharClassification`
- `enumIfElseDispatch` vs `enumSwitchDispatch`

Lower scores (ns/op) are better.

## Analysis

To extract and compare results, use:

```bash
# Extract benchmark scores
jq '.[] | {benchmark: .benchmark, score: .primaryMetric.score, unit: .primaryMetric.scoreUnit}' dispatch-results.json

# Compare instanceof dispatch methods
jq '.[] | select(.benchmark | contains("Instanceof")) | {benchmark: .benchmark, score: .primaryMetric.score}' dispatch-results.json

# Compare char classification methods
jq '.[] | select(.benchmark | contains("CharClassification")) | {benchmark: .benchmark, score: .primaryMetric.score}' dispatch-results.json

# Compare enum dispatch methods
jq '.[] | select(.benchmark | contains("enumDispatch")) | {benchmark: .benchmark, score: .primaryMetric.score}' dispatch-results.json
```

## Recommendations

Based on the results:
1. If pattern matching shows improvement, apply it to hot paths with instanceof checks
2. If switch expressions are faster, convert if-else chains to switch where applicable
3. Document which patterns provide measurable benefit (>3% improvement)

