"""
Statistical analysis of benchmark results.
"""

import numpy as np
from typing import List, Dict, Any
from collections import defaultdict
import statistics


class StatisticalAnalyzer:
    """Analyze benchmark results with statistical rigor."""

    def analyze_results(self, results: List[Any], config: Dict[str, Any]) -> Dict[str, Any]:
        """Perform comprehensive statistical analysis on results."""

        # Group results by engine and test parameters
        grouped_results = self._group_results(results)

        # Compute statistics for each group
        statistics_by_group = {}
        for group_key, group_results in grouped_results.items():
            statistics_by_group[group_key] = self._compute_group_statistics(group_results)

        # Compute cross-engine comparisons
        comparisons = self._compute_comparisons(statistics_by_group, config)

        return {
            'grouped_statistics': statistics_by_group,
            'comparisons': comparisons,
            'summary': self._generate_summary(statistics_by_group, comparisons),
            'validation': self._validate_results(statistics_by_group, config)
        }

    def _group_results(self, results: List[Any]) -> Dict[str, List[Any]]:
        """Group results by engine and test parameters."""
        groups = defaultdict(list)

        for result in results:
            if result.status == 'ok':
                # Create group key from relevant parameters (string for JSON compatibility)
                group_key = f"{result.engine_name}_{result.patterns_compiled or 0}_{result.corpus_size_bytes or 0}"
                groups[group_key].append(result)

        return dict(groups)

    def _compute_group_statistics(self, results: List[Any]) -> Dict[str, Any]:
        """Compute statistics for a group of results."""
        if not results:
            return {}

        # Extract timing data
        scanning_times = [r.scanning_ns for r in results if r.scanning_ns is not None]
        compilation_times = [r.compilation_ns for r in results if r.compilation_ns is not None]
        memory_usage = [r.memory_peak_bytes for r in results if r.memory_peak_bytes is not None]
        match_counts = [r.match_count for r in results if r.match_count is not None]

        stats = {
            'sample_size': len(results),
            'engine_name': results[0].engine_name,
            'patterns_compiled': results[0].patterns_compiled,
            'corpus_size_bytes': results[0].corpus_size_bytes
        }

        # Scanning time statistics
        if scanning_times:
            stats['scanning'] = self._compute_timing_stats(scanning_times, 'nanoseconds')

        # Compilation time statistics
        if compilation_times:
            stats['compilation'] = self._compute_timing_stats(compilation_times, 'nanoseconds')

        # Memory statistics
        if memory_usage:
            stats['memory'] = self._compute_memory_stats(memory_usage)

        # Match count validation
        if match_counts:
            stats['matches'] = self._compute_match_stats(match_counts)

        # Throughput calculations
        if scanning_times and results[0].corpus_size_bytes:
            corpus_mb = results[0].corpus_size_bytes / (1024 * 1024)
            # Filter out None and zero values to avoid division by None/zero errors
            valid_times = [t for t in scanning_times if t is not None and t > 0]
            if valid_times:
                throughputs = [corpus_mb / (t / 1e9) for t in valid_times]  # MB/s
                stats['throughput'] = self._compute_throughput_stats(throughputs)

        return stats

    def _compute_timing_stats(self, times: List[float], unit: str) -> Dict[str, Any]:
        """Compute timing statistics."""
        if not times:
            return {}

        times_array = np.array(times)

        return {
            'unit': unit,
            'count': len(times),
            'mean': float(np.mean(times_array)),
            'median': float(np.median(times_array)),
            'std_dev': float(np.std(times_array)),
            'min': float(np.min(times_array)),
            'max': float(np.max(times_array)),
            'percentiles': {
                'p50': float(np.percentile(times_array, 50)),
                'p90': float(np.percentile(times_array, 90)),
                'p95': float(np.percentile(times_array, 95)),
                'p99': float(np.percentile(times_array, 99))
            },
            'coefficient_of_variation': float(np.std(times_array) / np.mean(times_array)) if np.mean(times_array) > 0 else 0
        }

    def _compute_memory_stats(self, memory_values: List[int]) -> Dict[str, Any]:
        """Compute memory usage statistics."""
        if not memory_values:
            return {}

        memory_array = np.array(memory_values)

        return {
            'unit': 'bytes',
            'count': len(memory_values),
            'mean_bytes': float(np.mean(memory_array)),
            'median_bytes': float(np.median(memory_array)),
            'max_bytes': float(np.max(memory_array)),
            'mean_mb': float(np.mean(memory_array) / (1024 * 1024)),
            'median_mb': float(np.median(memory_array) / (1024 * 1024)),
            'max_mb': float(np.max(memory_array) / (1024 * 1024))
        }

    def _compute_match_stats(self, match_counts: List[int]) -> Dict[str, Any]:
        """Compute match count statistics for validation."""
        if not match_counts:
            return {}

        unique_counts = set(match_counts)

        return {
            'count': len(match_counts),
            'unique_values': len(unique_counts),
            'consistent': len(unique_counts) == 1,
            'values': list(unique_counts),
            'mean': statistics.mean(match_counts),
            'mode': statistics.mode(match_counts) if len(match_counts) > 1 else match_counts[0]
        }

    def _compute_throughput_stats(self, throughputs: List[float]) -> Dict[str, Any]:
        """Compute throughput statistics."""
        if not throughputs:
            return {}

        throughput_array = np.array(throughputs)

        return {
            'unit': 'MB/s',
            'count': len(throughputs),
            'mean': float(np.mean(throughput_array)),
            'median': float(np.median(throughput_array)),
            'min': float(np.min(throughput_array)),
            'max': float(np.max(throughput_array)),
            'std_dev': float(np.std(throughput_array))
        }

    def _compute_comparisons(self, statistics_by_group: Dict[str, Dict[str, Any]], config: Dict[str, Any]) -> Dict[str, Any]:
        """Compute cross-engine performance comparisons."""
        comparisons = {}

        # Group by test parameters, compare engines
        test_groups = defaultdict(list)
        for group_key, stats in statistics_by_group.items():
            # Parse the string group key back to components
            parts = group_key.split('_')
            engine_name = parts[0]
            pattern_count = int(parts[1])
            corpus_size = int(parts[2])
            test_key = (pattern_count, corpus_size)
            test_groups[test_key].append((engine_name, stats))

        for test_key, engine_stats_list in test_groups.items():
            pattern_count, corpus_size = test_key
            test_name = f"patterns_{pattern_count}_corpus_{corpus_size}"

            # Find baseline engine (typically java-native)
            baseline_engine = None
            baseline_stats = None

            for engine_name, stats in engine_stats_list:
                if engine_name == 'java-native':
                    baseline_engine = engine_name
                    baseline_stats = stats
                    break

            if baseline_stats is None and engine_stats_list:
                # Use first engine as baseline if java-native not found
                baseline_engine, baseline_stats = engine_stats_list[0]

            if baseline_stats is None:
                continue

            comparison_data = {
                'baseline_engine': baseline_engine,
                'engines': {}
            }

            for engine_name, stats in engine_stats_list:
                engine_comparison = self._compare_engines(stats, baseline_stats, engine_name, baseline_engine)
                comparison_data['engines'][engine_name] = engine_comparison

            comparisons[test_name] = comparison_data

        return comparisons

    def _compare_engines(self, engine_stats: Dict[str, Any], baseline_stats: Dict[str, Any],
                        engine_name: str, baseline_name: str) -> Dict[str, Any]:
        """Compare an engine against baseline."""
        comparison = {
            'engine': engine_name,
            'baseline': baseline_name
        }

        # Compare throughput
        if 'throughput' in engine_stats and 'throughput' in baseline_stats:
            engine_throughput = engine_stats['throughput']['mean']
            baseline_throughput = baseline_stats['throughput']['mean']

            if baseline_throughput > 0:
                comparison['throughput_speedup'] = engine_throughput / baseline_throughput
                comparison['throughput_improvement'] = ((engine_throughput - baseline_throughput) / baseline_throughput) * 100

        # Compare memory usage
        if 'memory' in engine_stats and 'memory' in baseline_stats:
            engine_memory = engine_stats['memory']['mean_mb']
            baseline_memory = baseline_stats['memory']['mean_mb']

            if baseline_memory > 0:
                comparison['memory_ratio'] = engine_memory / baseline_memory
                comparison['memory_improvement'] = ((baseline_memory - engine_memory) / baseline_memory) * 100

        # Compare compilation time
        if 'compilation' in engine_stats and 'compilation' in baseline_stats:
            engine_compilation = engine_stats['compilation']['mean']
            baseline_compilation = baseline_stats['compilation']['mean']

            if baseline_compilation > 0:
                comparison['compilation_speedup'] = baseline_compilation / engine_compilation

        return comparison

    def _generate_summary(self, statistics_by_group: Dict[str, Dict[str, Any]],
                         comparisons: Dict[str, Any]) -> Dict[str, Any]:
        """Generate high-level summary of analysis."""
        engines = set()
        total_measurements = 0

        for stats in statistics_by_group.values():
            engines.add(stats['engine_name'])
            total_measurements += stats['sample_size']

        return {
            'engines_analyzed': len(engines),
            'engine_names': list(engines),
            'total_measurements': total_measurements,
            'test_combinations': len(statistics_by_group),
            'comparisons_computed': len(comparisons)
        }

    def _validate_results(self, statistics_by_group: Dict[str, Dict[str, Any]],
                         config: Dict[str, Any]) -> Dict[str, Any]:
        """Validate results against success criteria."""
        validation = {
            'correctness': {},
            'performance': {},
            'statistical_validity': {}
        }

        success_criteria = config.get('success_criteria', {})

        # Validate correctness (match count consistency)
        correctness_criteria = success_criteria.get('correctness', {})
        validation['correctness'] = self._validate_correctness(statistics_by_group, correctness_criteria)

        # Validate performance claims
        performance_criteria = success_criteria.get('performance', {})
        validation['performance'] = self._validate_performance(statistics_by_group, performance_criteria)

        # Validate statistical validity
        statistical_criteria = success_criteria.get('statistical_validity', {})
        validation['statistical_validity'] = self._validate_statistics(statistics_by_group, statistical_criteria)

        return validation

    def _validate_correctness(self, statistics_by_group: Dict[str, Dict[str, Any]],
                             criteria: Dict[str, Any]) -> Dict[str, Any]:
        """Validate correctness criteria."""
        # Simple validation - check if match counts are consistent
        validation = {'status': 'pass', 'issues': []}

        for group_key, stats in statistics_by_group.items():
            if 'matches' in stats:
                if not stats['matches']['consistent']:
                    validation['status'] = 'fail'
                    validation['issues'].append(f"Inconsistent match counts for {group_key}")

        return validation

    def _validate_performance(self, statistics_by_group: Dict[str, Dict[str, Any]],
                             criteria: Dict[str, Any]) -> Dict[str, Any]:
        """Validate performance criteria."""
        # Simplified validation
        return {'status': 'pass', 'notes': 'Performance validation not yet implemented'}

    def _validate_statistics(self, statistics_by_group: Dict[str, Dict[str, Any]],
                           criteria: Dict[str, Any]) -> Dict[str, Any]:
        """Validate statistical validity."""
        validation = {'status': 'pass', 'issues': []}

        min_sample_size = criteria.get('min_sample_size', 5)
        max_cv = criteria.get('max_coefficient_variation', 0.2)

        for group_key, stats in statistics_by_group.items():
            if stats['sample_size'] < min_sample_size:
                validation['status'] = 'warning'
                validation['issues'].append(f"Low sample size for {group_key}: {stats['sample_size']}")

            if 'scanning' in stats:
                cv = stats['scanning']['coefficient_of_variation']
                if cv > max_cv:
                    validation['status'] = 'warning'
                    validation['issues'].append(f"High variance for {group_key}: CV={cv:.3f}")

        return validation