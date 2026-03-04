"""
Statistical analysis of benchmark results.
"""

import numpy as np
from typing import List, Dict, Any, Tuple
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
            'validation': self._validate_results(grouped_results, statistics_by_group, config)
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

    def _parse_group_key(self, group_key: str) -> Tuple[str, int, int]:
        """Parse group key into (engine_name, pattern_count, corpus_size_bytes)."""
        parts = group_key.rsplit('_', 2)
        if len(parts) != 3:
            return group_key, 0, 0

        engine_name = parts[0]
        try:
            pattern_count = int(parts[1])
        except (TypeError, ValueError):
            pattern_count = 0
        try:
            corpus_size_bytes = int(parts[2])
        except (TypeError, ValueError):
            corpus_size_bytes = 0
        return engine_name, pattern_count, corpus_size_bytes

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
        throughput_stats = {}

        # Collect engine throughput statistics
        for group_key, stats in statistics_by_group.items():
            engines.add(stats['engine_name'])
            total_measurements += stats['sample_size']

            # Aggregate throughput statistics by engine
            engine_name = stats['engine_name']
            if 'throughput' in stats and stats['throughput']:
                if engine_name not in throughput_stats:
                    throughput_stats[engine_name] = []

                throughput_stats[engine_name].append({
                    'mean': stats['throughput']['mean'],
                    'median': stats['throughput']['median'],
                    'std_dev': stats['throughput']['std_dev'],
                    'min': stats['throughput']['min'],
                    'max': stats['throughput']['max'],
                    'patterns_compiled': stats.get('patterns_compiled', 0),
                    'corpus_size_mb': stats.get('corpus_size_bytes', 0) / (1024 * 1024) if stats.get('corpus_size_bytes') else 0
                })

        # Calculate overall engine throughput summaries
        engine_throughput_summary = {}
        for engine_name, throughputs in throughput_stats.items():
            if throughputs:
                all_means = [t['mean'] for t in throughputs]
                all_medians = [t['median'] for t in throughputs]
                all_std_devs = [t['std_dev'] for t in throughputs]

                engine_throughput_summary[engine_name] = {
                    'configurations_tested': len(throughputs),
                    'average_mean_mbps': statistics.mean(all_means),
                    'average_median_mbps': statistics.mean(all_medians),
                    'average_std_dev_mbps': statistics.mean(all_std_devs),
                    'best_mean_mbps': max(all_means),
                    'worst_mean_mbps': min(all_means)
                }

        return {
            'engines_analyzed': len(engines),
            'engine_names': list(engines),
            'total_measurements': total_measurements,
            'test_combinations': len(statistics_by_group),
            'comparisons_computed': len(comparisons),
            'throughput_summary': engine_throughput_summary
        }

    def _validate_results(self, grouped_results: Dict[str, List[Any]],
                         statistics_by_group: Dict[str, Dict[str, Any]],
                         config: Dict[str, Any]) -> Dict[str, Any]:
        """Validate results against success criteria."""
        validation = {
            'correctness': {},
            'performance': {},
            'statistical_validity': {}
        }

        success_criteria = config.get('success_criteria', {})
        validation_config = config.get('validation', {})

        # Validate correctness (match count consistency)
        correctness_criteria = {}
        if isinstance(validation_config.get('correctness'), dict):
            correctness_criteria.update(validation_config.get('correctness', {}))
        if 'cross_engine_correctness' in validation_config:
            correctness_criteria['cross_engine_correctness'] = bool(validation_config.get('cross_engine_correctness'))
        correctness_criteria.update(success_criteria.get('correctness', {}))
        validation['correctness'] = self._validate_correctness(
            grouped_results,
            statistics_by_group,
            correctness_criteria
        )

        # Validate performance claims
        performance_criteria = success_criteria.get('performance', {})
        validation['performance'] = self._validate_performance(statistics_by_group, performance_criteria)

        # Validate statistical validity
        statistical_criteria = success_criteria.get('statistical_validity', {})
        validation['statistical_validity'] = self._validate_statistics(statistics_by_group, statistical_criteria)

        return validation

    def _validate_correctness(self, grouped_results: Dict[str, List[Any]],
                             statistics_by_group: Dict[str, Dict[str, Any]],
                             criteria: Dict[str, Any]) -> Dict[str, Any]:
        """Validate correctness criteria with engine-semantics-aware policy."""
        validation = {
            'status': 'pass',
            'issues': [],
            'minor_issue_count': 0,
            'major_issue_count': 0,
            'intra_engine_groups_checked': 0,
            'cross_engine_workloads_checked': 0,
            'cross_engine_pairs_checked': 0,
            'policy': {}
        }

        # Explicit default policy:
        # - rmatch and java-native family must agree exactly on identical workloads.
        # - re2j is allowed to differ due to matcher semantics.
        default_strict_engines = [
            'rmatch',
            'java-native-naive',
            'java-native-unfair',
            'java-native-optimized',
            'java-native',
        ]
        default_semantics_different = ['re2j']

        strict_reference_engine = str(criteria.get('strict_reference_engine', 'rmatch'))
        strict_equivalence_engines = list(criteria.get('strict_equivalence_engines', default_strict_engines))
        semantics_different_engines = set(criteria.get('semantics_different_engines', default_semantics_different))
        cross_engine_correctness = bool(criteria.get('cross_engine_correctness', False))
        minor_discrepancy_max_absolute = int(criteria.get('minor_discrepancy_max_absolute', 10))
        minor_discrepancy_max_relative = float(criteria.get('minor_discrepancy_max_relative', 0.001))

        # Never require strict equality for engines explicitly marked with different semantics.
        strict_equivalence_engines = [e for e in strict_equivalence_engines if e not in semantics_different_engines]
        strict_equivalence_set = set(strict_equivalence_engines)

        validation['policy'] = {
            'cross_engine_correctness': cross_engine_correctness,
            'strict_reference_engine': strict_reference_engine,
            'strict_equivalence_engines': strict_equivalence_engines,
            'semantics_different_engines': sorted(semantics_different_engines),
            'minor_discrepancy_max_absolute': minor_discrepancy_max_absolute,
            'minor_discrepancy_max_relative': minor_discrepancy_max_relative,
        }

        def _add_issue(message: str, severity: str = 'major') -> None:
            sev = 'minor' if severity == 'minor' else 'major'
            validation['issues'].append(f"[{sev}] {message}")
            if sev == 'major':
                validation['major_issue_count'] += 1
                validation['status'] = 'fail'
            else:
                validation['minor_issue_count'] += 1
                if validation['status'] == 'pass':
                    validation['status'] = 'warning'

        def _is_minor_delta(a: int, b: int) -> Tuple[bool, int, float]:
            delta = abs(int(a) - int(b))
            denom = max(abs(int(a)), abs(int(b)), 1)
            relative = float(delta) / float(denom)
            is_minor = (delta <= minor_discrepancy_max_absolute) or (relative <= minor_discrepancy_max_relative)
            return is_minor, delta, relative

        # Intra-engine consistency: identical workload + engine should yield same match count/checksum.
        for group_key, stats in statistics_by_group.items():
            engine_name, pattern_count, corpus_size = self._parse_group_key(group_key)
            group_label = f"{engine_name} @ patterns={pattern_count}, corpus_bytes={corpus_size}"
            group_results = grouped_results.get(group_key, [])

            if 'matches' in stats:
                validation['intra_engine_groups_checked'] += 1
                match_stats = stats['matches']
                if not match_stats.get('consistent', True):
                    values = sorted(int(v) for v in match_stats.get('values', []))
                    if values:
                        low = values[0]
                        high = values[-1]
                        is_minor, delta, relative = _is_minor_delta(low, high)
                        sev = 'minor' if is_minor else 'major'
                        _add_issue(
                            f"Inconsistent match counts within engine workload ({group_label}): "
                            f"values={values}, delta={delta}, rel={relative:.6f}",
                            severity=sev,
                        )
                    else:
                        _add_issue(
                            f"Inconsistent match counts within engine workload ({group_label})",
                            severity='major',
                        )

            checksums = {str(r.match_checksum) for r in group_results if r.match_checksum}
            if len(checksums) > 1:
                _add_issue(
                    f"Inconsistent match checksums within engine workload ({group_label})",
                    severity='major',
                )

        if not cross_engine_correctness:
            return validation

        # Cross-engine strict equivalence for selected engines on identical workloads.
        by_workload: Dict[Tuple[int, int], Dict[str, Dict[str, Any]]] = defaultdict(dict)
        for group_key, group_results in grouped_results.items():
            engine_name, pattern_count, corpus_size = self._parse_group_key(group_key)
            if pattern_count <= 0 or corpus_size <= 0:
                continue

            counts = [int(r.match_count) for r in group_results if r.match_count is not None]
            checksums = [str(r.match_checksum) for r in group_results if r.match_checksum]
            by_workload[(pattern_count, corpus_size)][engine_name] = {
                'counts': counts,
                'checksums': checksums,
            }

        for (pattern_count, corpus_size), engines_data in by_workload.items():
            strict_present = sorted([e for e in engines_data.keys() if e in strict_equivalence_set])
            if len(strict_present) < 2:
                continue

            validation['cross_engine_workloads_checked'] += 1

            if strict_reference_engine in strict_present:
                reference_engine = strict_reference_engine
            else:
                reference_engine = strict_present[0]

            reference_counts = set(engines_data[reference_engine].get('counts', []))
            if len(reference_counts) != 1:
                _add_issue(
                    f"Reference engine {reference_engine} is not internally consistent at "
                    f"patterns={pattern_count}, corpus_bytes={corpus_size}: counts={sorted(reference_counts)}"
                    ,
                    severity='major',
                )
                continue
            reference_count = next(iter(reference_counts))

            reference_checksums = set(engines_data[reference_engine].get('checksums', []))
            reference_checksum = next(iter(reference_checksums)) if len(reference_checksums) == 1 else None

            for engine_name in strict_present:
                if engine_name == reference_engine:
                    continue
                validation['cross_engine_pairs_checked'] += 1

                engine_counts = set(engines_data[engine_name].get('counts', []))
                if len(engine_counts) != 1:
                    _add_issue(
                        f"Engine {engine_name} is not internally consistent at "
                        f"patterns={pattern_count}, corpus_bytes={corpus_size}: counts={sorted(engine_counts)}"
                        ,
                        severity='major',
                    )
                    continue

                engine_count = next(iter(engine_counts))
                if engine_count != reference_count:
                    is_minor, delta, relative = _is_minor_delta(reference_count, engine_count)
                    sev = 'minor' if is_minor else 'major'
                    _add_issue(
                        f"Cross-engine match-count mismatch at patterns={pattern_count}, corpus_bytes={corpus_size}: "
                        f"{reference_engine}={reference_count}, {engine_name}={engine_count}, "
                        f"delta={delta}, rel={relative:.6f}",
                        severity=sev,
                    )

                engine_checksums = set(engines_data[engine_name].get('checksums', []))
                engine_checksum = next(iter(engine_checksums)) if len(engine_checksums) == 1 else None
                if reference_checksum and engine_checksum and engine_checksum != reference_checksum:
                    _add_issue(
                        f"Cross-engine checksum mismatch at patterns={pattern_count}, corpus_bytes={corpus_size}: "
                        f"{reference_engine}!={engine_name}",
                        severity='major',
                    )

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
