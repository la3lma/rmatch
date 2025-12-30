#!/usr/bin/env python3
"""
Performance Analysis Module for Regex Benchmark Framework
Provides comprehensive analysis of benchmark results across multiple dimensions.
"""

import sqlite3
import pandas as pd
import numpy as np
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Union
from dataclasses import dataclass
import json


@dataclass
class PerformanceMetrics:
    """Container for performance metrics."""
    engine: str
    pattern_count: int
    corpus_size: str
    compilation_time_ns: float
    scanning_time_ns: float
    total_time_ns: float
    throughput_mb_per_sec: float
    patterns_per_sec: float
    match_count: int
    iteration: int
    memory_compilation_bytes: Optional[int] = None
    memory_peak_bytes: Optional[int] = None

    @property
    def corpus_size_bytes(self) -> int:
        """Convert corpus size string to bytes."""
        size_map = {'1MB': 1024**2, '10MB': 10*1024**2, '100MB': 100*1024**2, '1GB': 1024**3}
        return size_map.get(self.corpus_size, 0)


class PerformanceAnalyzer:
    """Analyzes benchmark performance data and generates insights."""

    def __init__(self, db_path: Path):
        self.db_path = db_path
        self.conn = sqlite3.connect(str(db_path))
        self.metrics_cache = {}

    def __del__(self):
        if hasattr(self, 'conn'):
            self.conn.close()

    def get_completed_results(self) -> List[PerformanceMetrics]:
        """Retrieve all completed benchmark results."""
        query = """
        SELECT
            engine_name,
            pattern_count,
            input_size,
            compilation_ns,
            scanning_ns,
            match_count,
            iteration,
            memory_compilation_bytes,
            memory_peak_bytes
        FROM benchmark_jobs
        WHERE status = 'COMPLETED'
        AND compilation_ns IS NOT NULL
        AND scanning_ns IS NOT NULL
        ORDER BY engine_name, pattern_count, input_size, iteration
        """

        cursor = self.conn.execute(query)
        results = []

        for row in cursor.fetchall():
            engine, pattern_count, corpus_size, comp_ns, scan_ns, matches, iteration, mem_comp, mem_peak = row

            total_ns = comp_ns + scan_ns
            corpus_bytes = self._parse_corpus_size(corpus_size)

            # Calculate throughput (MB/sec)
            throughput = (corpus_bytes / (1024 * 1024)) / (scan_ns / 1e9) if scan_ns > 0 else 0

            # Calculate patterns per second
            patterns_per_sec = pattern_count / (scan_ns / 1e9) if scan_ns > 0 else 0

            metrics = PerformanceMetrics(
                engine=engine,
                pattern_count=pattern_count,
                corpus_size=corpus_size,
                compilation_time_ns=comp_ns,
                scanning_time_ns=scan_ns,
                total_time_ns=total_ns,
                throughput_mb_per_sec=throughput,
                patterns_per_sec=patterns_per_sec,
                match_count=matches,
                iteration=iteration,
                memory_compilation_bytes=mem_comp,
                memory_peak_bytes=mem_peak
            )
            results.append(metrics)

        return results

    def _parse_corpus_size(self, size_str: str) -> int:
        """Parse corpus size string to bytes."""
        size_map = {
            '1MB': 1024**2,
            '10MB': 10 * 1024**2,
            '100MB': 100 * 1024**2,
            '1GB': 1024**3
        }
        return size_map.get(size_str, 0)

    def aggregate_by_configuration(self, metrics: List[PerformanceMetrics]) -> Dict:
        """Aggregate metrics by engine, pattern_count, corpus_size configuration."""
        aggregated = {}

        for metric in metrics:
            key = (metric.engine, metric.pattern_count, metric.corpus_size)
            if key not in aggregated:
                aggregated[key] = {
                    'engine': metric.engine,
                    'pattern_count': metric.pattern_count,
                    'corpus_size': metric.corpus_size,
                    'corpus_size_bytes': metric.corpus_size_bytes,
                    'iterations': [],
                    'compilation_times_ns': [],
                    'scanning_times_ns': [],
                    'total_times_ns': [],
                    'throughputs_mb_per_sec': [],
                    'patterns_per_sec': [],
                    'match_counts': [],
                    'memory_compilation_bytes': [],
                    'memory_peak_bytes': []
                }

            agg = aggregated[key]
            agg['iterations'].append(metric.iteration)
            agg['compilation_times_ns'].append(metric.compilation_time_ns)
            agg['scanning_times_ns'].append(metric.scanning_time_ns)
            agg['total_times_ns'].append(metric.total_time_ns)
            agg['throughputs_mb_per_sec'].append(metric.throughput_mb_per_sec)
            agg['patterns_per_sec'].append(metric.patterns_per_sec)
            agg['match_counts'].append(metric.match_count)
            if metric.memory_compilation_bytes:
                agg['memory_compilation_bytes'].append(metric.memory_compilation_bytes)
            if metric.memory_peak_bytes:
                agg['memory_peak_bytes'].append(metric.memory_peak_bytes)

        # Calculate statistics
        for key, agg in aggregated.items():
            agg['num_iterations'] = len(agg['iterations'])
            agg['avg_compilation_ns'] = np.mean(agg['compilation_times_ns'])
            agg['avg_scanning_ns'] = np.mean(agg['scanning_times_ns'])
            agg['avg_total_ns'] = np.mean(agg['total_times_ns'])
            agg['avg_throughput_mb_per_sec'] = np.mean(agg['throughputs_mb_per_sec'])
            agg['avg_patterns_per_sec'] = np.mean(agg['patterns_per_sec'])
            agg['std_throughput'] = np.std(agg['throughputs_mb_per_sec'])
            agg['min_throughput'] = np.min(agg['throughputs_mb_per_sec'])
            agg['max_throughput'] = np.max(agg['throughputs_mb_per_sec'])

            # Memory statistics
            if agg['memory_compilation_bytes']:
                agg['avg_memory_compilation_mb'] = np.mean(agg['memory_compilation_bytes']) / (1024 * 1024)
                agg['max_memory_compilation_mb'] = np.max(agg['memory_compilation_bytes']) / (1024 * 1024)
                agg['std_memory_compilation_mb'] = np.std(agg['memory_compilation_bytes']) / (1024 * 1024)
            else:
                agg['avg_memory_compilation_mb'] = None
                agg['max_memory_compilation_mb'] = None
                agg['std_memory_compilation_mb'] = None

            if agg['memory_peak_bytes']:
                agg['avg_memory_peak_mb'] = np.mean(agg['memory_peak_bytes']) / (1024 * 1024)
                agg['max_memory_peak_mb'] = np.max(agg['memory_peak_bytes']) / (1024 * 1024)
                agg['std_memory_peak_mb'] = np.std(agg['memory_peak_bytes']) / (1024 * 1024)
            else:
                agg['avg_memory_peak_mb'] = None
                agg['max_memory_peak_mb'] = None
                agg['std_memory_peak_mb'] = None

        return aggregated

    def get_engine_comparison_data(self) -> Dict:
        """Get data structured for engine comparison analysis."""
        metrics = self.get_completed_results()
        aggregated = self.aggregate_by_configuration(metrics)

        comparison_data = {
            'engines': set(),
            'pattern_counts': set(),
            'corpus_sizes': set(),
            'configurations': {}
        }

        for (engine, pattern_count, corpus_size), agg in aggregated.items():
            comparison_data['engines'].add(engine)
            comparison_data['pattern_counts'].add(pattern_count)
            comparison_data['corpus_sizes'].add(corpus_size)

            config_key = f"{pattern_count}_{corpus_size}"
            if config_key not in comparison_data['configurations']:
                comparison_data['configurations'][config_key] = {}

            comparison_data['configurations'][config_key][engine] = {
                'throughput': agg['avg_throughput_mb_per_sec'],
                'patterns_per_sec': agg['avg_patterns_per_sec'],
                'compilation_ns': agg['avg_compilation_ns'],
                'scanning_ns': agg['avg_scanning_ns'],
                'std_throughput': agg['std_throughput'],
                'iterations': agg['num_iterations']
            }

        # Convert sets to sorted lists
        comparison_data['engines'] = sorted(list(comparison_data['engines']))
        comparison_data['pattern_counts'] = sorted(list(comparison_data['pattern_counts']))
        comparison_data['corpus_sizes'] = sorted(list(comparison_data['corpus_sizes']),
                                                key=lambda x: self._parse_corpus_size(x))

        return comparison_data

    def analyze_scaling_behavior(self) -> Dict:
        """Analyze how engines scale with pattern complexity and corpus size."""
        metrics = self.get_completed_results()
        aggregated = self.aggregate_by_configuration(metrics)

        scaling_data = {}

        for engine in set(m.engine for m in metrics):
            scaling_data[engine] = {
                'pattern_scaling': {},  # throughput vs pattern_count for each corpus_size
                'corpus_scaling': {},   # throughput vs corpus_size for each pattern_count
                'efficiency': {}        # patterns_per_sec vs various dimensions
            }

        # Analyze pattern scaling (pattern complexity impact)
        for (engine, pattern_count, corpus_size), agg in aggregated.items():
            if corpus_size not in scaling_data[engine]['pattern_scaling']:
                scaling_data[engine]['pattern_scaling'][corpus_size] = []

            scaling_data[engine]['pattern_scaling'][corpus_size].append({
                'pattern_count': pattern_count,
                'throughput': agg['avg_throughput_mb_per_sec'],
                'patterns_per_sec': agg['avg_patterns_per_sec'],
                'scanning_ns': agg['avg_scanning_ns']
            })

        # Analyze corpus scaling (data size impact)
        for (engine, pattern_count, corpus_size), agg in aggregated.items():
            if pattern_count not in scaling_data[engine]['corpus_scaling']:
                scaling_data[engine]['corpus_scaling'][pattern_count] = []

            scaling_data[engine]['corpus_scaling'][pattern_count].append({
                'corpus_size': corpus_size,
                'corpus_bytes': agg['corpus_size_bytes'],
                'throughput': agg['avg_throughput_mb_per_sec'],
                'patterns_per_sec': agg['avg_patterns_per_sec'],
                'scanning_ns': agg['avg_scanning_ns']
            })

        # Sort the scaling data
        for engine in scaling_data:
            for corpus_size in scaling_data[engine]['pattern_scaling']:
                scaling_data[engine]['pattern_scaling'][corpus_size].sort(
                    key=lambda x: x['pattern_count'])

            for pattern_count in scaling_data[engine]['corpus_scaling']:
                scaling_data[engine]['corpus_scaling'][pattern_count].sort(
                    key=lambda x: x['corpus_bytes'])

        return scaling_data

    def get_performance_summary(self) -> Dict:
        """Generate comprehensive performance summary."""
        metrics = self.get_completed_results()
        if not metrics:
            return {"error": "No completed results found"}

        aggregated = self.aggregate_by_configuration(metrics)
        comparison_data = self.get_engine_comparison_data()
        scaling_data = self.analyze_scaling_behavior()

        # Calculate overall stats
        summary = {
            'total_completed_jobs': len(metrics),
            'engines_tested': comparison_data['engines'],
            'pattern_counts_tested': comparison_data['pattern_counts'],
            'corpus_sizes_tested': comparison_data['corpus_sizes'],
            'configurations_completed': len(aggregated),
            'engine_performance': {},
            'performance_highlights': {},
            'scaling_insights': {}
        }

        # Engine-level performance summary
        for engine in comparison_data['engines']:
            engine_metrics = [m for m in metrics if m.engine == engine]
            if engine_metrics:
                throughputs = [m.throughput_mb_per_sec for m in engine_metrics]
                patterns_per_sec = [m.patterns_per_sec for m in engine_metrics]

                summary['engine_performance'][engine] = {
                    'completed_jobs': len(engine_metrics),
                    'avg_throughput_mb_per_sec': np.mean(throughputs),
                    'max_throughput_mb_per_sec': np.max(throughputs),
                    'avg_patterns_per_sec': np.mean(patterns_per_sec),
                    'max_patterns_per_sec': np.max(patterns_per_sec)
                }

        return summary

    def analyze_memory_usage(self) -> Dict:
        """Analyze memory usage patterns across engines and configurations."""
        metrics = self.get_completed_results()
        aggregated = self.aggregate_by_configuration(metrics)

        memory_analysis = {
            'compilation_memory': {},  # Memory usage for pattern compilation
            'memory_scaling': {},      # How memory scales with pattern count and corpus size
            'engine_comparison': {},   # Memory usage comparison between engines
            'efficiency_ratios': {}    # Memory efficiency metrics
        }

        # Analyze compilation memory patterns
        for (engine, pattern_count, corpus_size), agg in aggregated.items():
            if agg['avg_memory_compilation_mb'] is not None:
                if engine not in memory_analysis['compilation_memory']:
                    memory_analysis['compilation_memory'][engine] = {}

                config_key = f"{pattern_count}_{corpus_size}"
                memory_analysis['compilation_memory'][engine][config_key] = {
                    'avg_mb': agg['avg_memory_compilation_mb'],
                    'max_mb': agg['max_memory_compilation_mb'],
                    'std_mb': agg['std_memory_compilation_mb'],
                    'pattern_count': pattern_count,
                    'corpus_size': corpus_size,
                    'iterations': agg['num_iterations']
                }

        # Analyze memory scaling with pattern complexity
        for engine in memory_analysis['compilation_memory']:
            memory_analysis['memory_scaling'][engine] = {
                'pattern_scaling': {},  # memory vs pattern_count for each corpus_size
                'corpus_scaling': {}    # memory vs corpus_size for each pattern_count
            }

            engine_data = memory_analysis['compilation_memory'][engine]

            # Group by corpus size for pattern scaling analysis
            corpus_groups = {}
            for config_key, data in engine_data.items():
                corpus_size = data['corpus_size']
                if corpus_size not in corpus_groups:
                    corpus_groups[corpus_size] = []
                corpus_groups[corpus_size].append({
                    'pattern_count': data['pattern_count'],
                    'memory_mb': data['avg_mb']
                })

            for corpus_size, points in corpus_groups.items():
                memory_analysis['memory_scaling'][engine]['pattern_scaling'][corpus_size] = sorted(
                    points, key=lambda x: x['pattern_count'])

            # Group by pattern count for corpus scaling analysis
            pattern_groups = {}
            for config_key, data in engine_data.items():
                pattern_count = data['pattern_count']
                if pattern_count not in pattern_groups:
                    pattern_groups[pattern_count] = []
                pattern_groups[pattern_count].append({
                    'corpus_size': data['corpus_size'],
                    'memory_mb': data['avg_mb']
                })

            for pattern_count, points in pattern_groups.items():
                # Sort by corpus size (convert to bytes for sorting)
                memory_analysis['memory_scaling'][engine]['corpus_scaling'][pattern_count] = sorted(
                    points, key=lambda x: self._parse_corpus_size(x['corpus_size']))

        # Calculate memory efficiency ratios
        for (engine, pattern_count, corpus_size), agg in aggregated.items():
            if agg['avg_memory_compilation_mb'] is not None:
                config_key = f"{pattern_count}_{corpus_size}"

                if config_key not in memory_analysis['efficiency_ratios']:
                    memory_analysis['efficiency_ratios'][config_key] = {}

                # Memory per pattern (MB/pattern)
                memory_per_pattern = agg['avg_memory_compilation_mb'] / pattern_count

                # Memory per corpus MB (compilation memory / corpus size)
                corpus_mb = self._parse_corpus_size(corpus_size) / (1024 * 1024)
                memory_per_corpus_mb = agg['avg_memory_compilation_mb'] / corpus_mb

                memory_analysis['efficiency_ratios'][config_key][engine] = {
                    'memory_per_pattern_mb': memory_per_pattern,
                    'memory_per_corpus_mb': memory_per_corpus_mb,
                    'total_memory_mb': agg['avg_memory_compilation_mb'],
                    'throughput_mb_per_sec': agg['avg_throughput_mb_per_sec']
                }

        return memory_analysis

    def get_memory_comparison_summary(self) -> Dict:
        """Generate a summary of memory usage comparison between engines."""
        memory_analysis = self.analyze_memory_usage()

        summary = {
            'engine_memory_overview': {},
            'memory_winners': {},  # Which engine uses least memory for each configuration
            'memory_scaling_insights': {}
        }

        # Calculate overall memory usage per engine
        for engine, configs in memory_analysis['compilation_memory'].items():
            memory_values = [data['avg_mb'] for data in configs.values()]
            if memory_values:
                summary['engine_memory_overview'][engine] = {
                    'avg_memory_mb': np.mean(memory_values),
                    'max_memory_mb': np.max(memory_values),
                    'min_memory_mb': np.min(memory_values),
                    'std_memory_mb': np.std(memory_values),
                    'configurations_tested': len(memory_values)
                }

        # Find memory winners for each configuration
        for config_key, engines in memory_analysis['efficiency_ratios'].items():
            if engines:
                # Find engine with lowest memory usage
                min_engine = min(engines.keys(), key=lambda e: engines[e]['total_memory_mb'])
                max_engine = max(engines.keys(), key=lambda e: engines[e]['total_memory_mb'])

                summary['memory_winners'][config_key] = {
                    'lowest_memory': {
                        'engine': min_engine,
                        'memory_mb': engines[min_engine]['total_memory_mb']
                    },
                    'highest_memory': {
                        'engine': max_engine,
                        'memory_mb': engines[max_engine]['total_memory_mb']
                    },
                    'memory_ratio': engines[max_engine]['total_memory_mb'] / engines[min_engine]['total_memory_mb']
                }

        return summary

    def export_to_dataframe(self) -> pd.DataFrame:
        """Export performance data to pandas DataFrame for analysis."""
        metrics = self.get_completed_results()

        data = []
        for metric in metrics:
            data.append({
                'engine': metric.engine,
                'pattern_count': metric.pattern_count,
                'corpus_size': metric.corpus_size,
                'corpus_size_bytes': metric.corpus_size_bytes,
                'compilation_time_ns': metric.compilation_time_ns,
                'scanning_time_ns': metric.scanning_time_ns,
                'total_time_ns': metric.total_time_ns,
                'throughput_mb_per_sec': metric.throughput_mb_per_sec,
                'patterns_per_sec': metric.patterns_per_sec,
                'match_count': metric.match_count,
                'iteration': metric.iteration
            })

        return pd.DataFrame(data)


if __name__ == "__main__":
    # Example usage
    db_path = Path("results/production_20251221_115840/jobs.db")
    analyzer = PerformanceAnalyzer(db_path)

    print("=== Performance Analysis ===")
    summary = analyzer.get_performance_summary()
    print(json.dumps(summary, indent=2))