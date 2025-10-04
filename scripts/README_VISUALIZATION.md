# Benchmark Visualization System

This system provides comprehensive visualization of JMH benchmark results comparing RMATCH vs Java native regex performance.

## Quick Start

```bash
# Generate all visualizations from latest benchmark results
make visualize-benchmarks
```

## Architecture Independence

The system is designed to work across different architectures (x86_64, ARM64, etc.) by:

- **Automatic compatibility checking**: Detects and recreates incompatible virtual environments
- **Platform-specific binaries**: Uses `--only-binary=all` to ensure architecture-appropriate packages
- **Path resolution**: Uses dynamic path resolution to work with symlinks and different filesystem layouts
- **Clean environment**: Clears Python environment variables to avoid conflicts

## Generated Visualizations

- `runtime_scatter_rmatch_vs_java.png` - Scatter plot comparing performance
- `relative_performance_bars.png` - Bar chart showing relative performance
- `method_comparison.png` - Detailed method comparison
- `performance_summary_table.png` - Statistical summary table
- `benchmark_comparison_data.csv` - Raw comparison data
- `performance_summary.csv` - Statistical summary data

## Cross-Platform Compatibility

The Makefile automatically:
1. Checks virtual environment compatibility on each run
2. Recreates environment if packages are incompatible with current architecture
3. Installs platform-specific binary packages
4. Uses dynamic path resolution for different filesystem layouts

This ensures the system works on both Intel and Apple Silicon Macs, as well as different filesystem configurations.