# Architecture-Aware Performance Benchmarking

This document explains the architecture-aware benchmarking system in rmatch, which enables fair performance comparisons across different CPU architectures and machine configurations.

## Problem Statement

Performance benchmarks are inherently tied to the hardware they run on. A PR that improves performance overall might appear to regress when run on slower hardware. This creates a problem:

- **The performance ratchet can block upgrades** that are inherently faster but run on different architecture
- **Cross-architecture comparisons are inaccurate** without normalization
- **CI/CD environments may vary** in their available hardware over time

## Solution Overview

The rmatch benchmarking system now includes:

1. **Detailed System Information Collection**: CPU model, cores, cache, architecture
2. **Normalization Benchmark**: Simple, reproducible CPU benchmark for cross-platform comparison
3. **Architecture-Aware Baselines**: Baselines tagged with architecture information
4. **Normalized Comparison**: Performance comparisons account for relative CPU performance

## How It Works

### System Information Collection

The `SystemInfo` utility class collects comprehensive system details:

- **CPU Information**: Model, physical/logical cores, frequency, cache sizes
- **Architecture**: x86_64, ARM, etc.
- **OS Details**: Name, version, kernel
- **CI Environment**: Detection of GitHub Actions, Docker, etc.

This information is collected automatically when benchmarks run and stored with results.

### Normalization Benchmark

The `NormalizationBenchmark` utility provides a standard workload for measuring relative CPU performance:

```java
// Run normalization benchmark (median of 3 runs)
double score = NormalizationBenchmark.runBenchmarkMedian(3);
// Score is in operations per millisecond
```

The benchmark:
- Is CPU-bound and deterministic
- Takes less than 1 second to run
- Stresses different CPU execution units (ALU, branch predictor, etc.)
- Returns a score in operations per millisecond

Higher scores indicate faster hardware. This score is stored with benchmarks and used as a normalization factor.

### Architecture ID

Each system is assigned an architecture ID combining:
- CPU architecture (amd64, arm64, etc.)
- CPU model (sanitized)
- Number of logical cores

Example: `amd64_AMD_EPYC_7763_64_Core_Processor_4`

This ID is used to:
- Match baselines from the same architecture
- Detect cross-architecture comparisons
- Group performance data by architecture

### Baseline Storage

Baseline files now include architecture metadata:

```
# Baseline performance data
# Generated: 2025-11-04T23:15:00Z
# Java: 17.0.17
# OS: Linux 6.11.0-1018-azure
# Git: 4dadac9 (copilot/update-benchmark-ratchet-architecture)
# Architecture: amd64_AMD_EPYC_7763_64_Core_Processor_4
# Normalization Score: 330430.72
# Format: matcherTypeName,usedMemoryInMb,durationInMillis

rmatch,214,20400
```

### Cross-Architecture Comparison

When comparing performance across different architectures, the system:

1. **Detects architecture mismatch** by comparing architecture IDs
2. **Applies normalization factor** to time measurements:
   ```
   normalized_time = measured_time × (baseline_norm_score / current_norm_score)
   ```
3. **Provides warnings** in PR comments about cross-architecture comparison
4. **Does not normalize memory** as it's less architecture-dependent

### Example Scenario

**Scenario**: Baseline run on GitHub Actions (AMD EPYC), PR run on developer's laptop (Intel i7)

1. Baseline:
   - Architecture: `amd64_AMD_EPYC_7763_4`
   - Normalization: 330,000 ops/ms
   - Time: 20,000 ms

2. Current PR:
   - Architecture: `amd64_Intel_Core_i7_8`  
   - Normalization: 450,000 ops/ms (faster CPU)
   - Raw time: 18,000 ms

3. Normalized comparison:
   - Normalization factor: 330,000 / 450,000 = 0.733
   - Normalized time: 18,000 × 0.733 = 13,194 ms
   - **Result**: Improvement (faster than baseline even accounting for faster CPU)

## Usage

### Running Benchmarks

Benchmarks automatically collect architecture information:

```bash
# Macro benchmark
./scripts/run_macro_with_memory.sh

# JMH benchmark  
./scripts/run_jmh.sh

# Java baseline
./scripts/run_java_benchmark_with_memory.sh
```

The resulting JSON files include an `architecture` field with system info and normalization score.

### Viewing Architecture Information

Check current system information:

```bash
./scripts/collect_system_info.sh
```

Output example:
```json
{
  "system_info": {
    "cpu_model": "AMD EPYC 7763 64-Core Processor",
    "cpu_logical_cores": 4,
    "cpu_architecture": "x86_64",
    ...
  },
  "normalization": {
    "score": 330430.72,
    "unit": "ops_per_ms"
  },
  "architecture_id": "amd64_AMD_EPYC_7763_64_Core_Processor_4"
}
```

### PR Performance Reports

PR comments now include architecture information:

```markdown
## ✅ PASS Performance Comparison

**Result**: Performance maintained within acceptable bounds: time 1.2%, memory 0.5%

### 💻 Test Environment

| Attribute | Value |
|-----------|-------|
| **Architecture** | `amd64_AMD_EPYC_7763_64_Core_Processor_4` |
| **Normalization Score** | 330431 ops/ms |
| **Java Version** | 17.0.17 |
| **OS** | Linux 6.11.0-1018-azure |
| **Test Runs** | 5 iterations |

> ⚠️ **Architecture Mismatch**: Baseline was run on `amd64_Intel_Core_i7_8`. 
> Performance normalization applied for fair comparison.
```

## Implementation Details

### Key Classes

- **`SystemInfo`**: Collects detailed CPU and system information
- **`NormalizationBenchmark`**: Runs standardized CPU benchmark
- **`BaselineManager.EnvironmentInfo`**: Stores architecture metadata with baselines
- **`PerformanceCriteriaEvaluator.evaluateWithArchitecture()`**: Performs architecture-aware comparison

### Normalization Algorithm

The normalization factor is calculated as:

```
normalization_factor = baseline_norm_score / current_norm_score
normalized_current_time = current_time × normalization_factor
```

This adjusts current measurements to what they would be on baseline hardware. A higher normalization score means faster hardware, so the factor scales down measurements from faster machines.

### Backwards Compatibility

The system maintains backwards compatibility:

- If no architecture information exists in baseline, falls back to non-normalized comparison
- Warnings clearly indicate when cross-architecture comparison is happening
- Old baseline files without architecture info continue to work

## Best Practices

1. **Same Architecture Preferred**: For most accurate comparison, run benchmarks on the same architecture as the baseline
2. **Establish Baselines Per Architecture**: Consider maintaining separate baselines for common architectures (GitHub Actions, local dev machines)
3. **Monitor Normalization Scores**: Large variations in normalization scores between runs on same hardware may indicate system load or thermal throttling
4. **Review Cross-Architecture Results Carefully**: While normalization helps, same-architecture comparisons are always more reliable

## Limitations

1. **Memory Not Normalized**: Memory usage is assumed to be architecture-independent
2. **Simplified CPU Model**: Normalization benchmark is a proxy, not perfect representation of workload
3. **Environmental Factors**: System load, thermal throttling, and other factors can affect results
4. **Cache Effects**: Different cache sizes between architectures not fully captured

## Future Enhancements

Potential improvements to consider:

- Multiple normalization benchmarks for different workload types
- Automatic detection and handling of thermal throttling
- Machine learning to predict performance across architectures
- Support for GPU/accelerator architectures
- Historical performance tracking per architecture
