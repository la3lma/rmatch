"""
Advanced Analytics Engine for Regex Benchmark Analysis
World-class analytics system for deep regex engine performance insights.
"""

import sqlite3
import numpy as np
import pandas as pd
from typing import Dict, List, Any, Tuple, Optional
from dataclasses import dataclass
from pathlib import Path
import json
from scipy import stats
from sklearn.linear_model import LinearRegression
from sklearn.preprocessing import PolynomialFeatures
from sklearn.metrics import r2_score
import warnings
warnings.filterwarnings('ignore')

@dataclass
class PerformanceInsight:
    """Container for performance insights and recommendations."""
    title: str
    description: str
    severity: str  # 'info', 'warning', 'critical'
    data: Dict[str, Any]
    chart_type: str
    chart_data: Dict[str, Any]

@dataclass
class EngineProfile:
    """Comprehensive engine performance profile."""
    engine: str
    throughput_characteristics: Dict[str, float]
    memory_characteristics: Dict[str, float]
    scaling_behavior: Dict[str, float]
    consistency_metrics: Dict[str, float]
    strengths: List[str]
    weaknesses: List[str]
    use_cases: List[str]

class AdvancedAnalytics:
    """World-class analytics engine for regex benchmark data."""

    def __init__(self, db_path: Path):
        self.db_path = db_path
        self.conn = sqlite3.connect(str(db_path))
        self._load_data()

    def _load_data(self):
        """Load and preprocess all benchmark data."""
        # First, try to load from benchmark_jobs table (full benchmark data)
        try:
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
                memory_peak_bytes,
                status
            FROM benchmark_jobs
            WHERE status = 'COMPLETED'
            AND compilation_ns IS NOT NULL
            AND scanning_ns IS NOT NULL
            """
            self.df = pd.read_sql_query(query, self.conn)
            self._data_source = 'benchmark_jobs'
        except Exception:
            # Fallback to benchmark_statistics table (aggregated data)
            query = """
            SELECT
                engine_name,
                pattern_count,
                input_size,
                metric_name,
                mean_value,
                std_dev,
                sample_count
            FROM benchmark_statistics
            WHERE mean_value IS NOT NULL
            """
            stats_df = pd.read_sql_query(query, self.conn)

            # Pivot the statistics to create columns for each metric
            self.df = stats_df.pivot_table(
                index=['engine_name', 'pattern_count', 'input_size'],
                columns='metric_name',
                values='mean_value',
                aggfunc='first'
            ).reset_index()

            # Flatten column names
            self.df.columns.name = None

            # Convert to expected format (nanoseconds for compatibility)
            if 'compilation_ms' in self.df.columns:
                self.df['compilation_ns'] = self.df['compilation_ms'] * 1e6
            if 'scanning_ms' in self.df.columns:
                self.df['scanning_ns'] = self.df['scanning_ms'] * 1e6
            if 'throughput_mb_s' in self.df.columns:
                self.df['throughput_mb_s_original'] = self.df['throughput_mb_s']

            # Add pseudo-iteration column for compatibility
            self.df['iteration'] = 0
            self.df['status'] = 'COMPLETED'

            self._data_source = 'benchmark_statistics'
        self._enrich_data()

    def _enrich_data(self):
        """Add derived metrics and features."""
        # Parse corpus size to bytes
        size_map = {'1MB': 1024**2, '10MB': 10*1024**2, '100MB': 100*1024**2, '1GB': 1024**3}
        self.df['corpus_bytes'] = self.df['input_size'].map(size_map)

        # Calculate performance metrics
        self.df['total_ns'] = self.df['compilation_ns'] + self.df['scanning_ns']
        self.df['throughput_mb_s'] = (self.df['corpus_bytes'] / (1024**2)) / (self.df['scanning_ns'] / 1e9)
        self.df['compilation_ms'] = self.df['compilation_ns'] / 1e6
        self.df['scanning_ms'] = self.df['scanning_ns'] / 1e6
        self.df['patterns_per_sec'] = self.df['pattern_count'] / (self.df['scanning_ns'] / 1e9)

        # Memory efficiency metrics (conditional on memory data availability)
        if 'memory_compilation_bytes' in self.df.columns:
            self.df['memory_mb'] = self.df['memory_compilation_bytes'] / (1024**2)
            self.df['memory_per_pattern_kb'] = self.df['memory_compilation_bytes'] / (1024 * self.df['pattern_count'])
            if 'throughput_mb_s' in self.df.columns:
                self.df['throughput_per_mb_memory'] = self.df['throughput_mb_s'] / self.df['memory_mb']
        elif 'memory_per_pattern_kb' in self.df.columns:
            # Use pre-calculated memory efficiency from benchmark_statistics
            self.df['memory_mb'] = self.df['memory_per_pattern_kb'] * self.df['pattern_count'] / 1024
            if 'throughput_mb_s' in self.df.columns:
                self.df['throughput_per_mb_memory'] = self.df['throughput_mb_s'] / self.df['memory_mb']
        else:
            # No memory data available - create dummy columns for compatibility
            self.df['memory_mb'] = 10.0  # Default 10MB assumption
            self.df['memory_per_pattern_kb'] = 10.0 * 1024 / self.df['pattern_count']
            if 'throughput_mb_s' in self.df.columns:
                self.df['throughput_per_mb_memory'] = self.df['throughput_mb_s'] / self.df['memory_mb']

        # Scaling factors
        self.df['log_pattern_count'] = np.log10(self.df['pattern_count'])
        self.df['log_corpus_bytes'] = np.log10(self.df['corpus_bytes'])

        # Performance categories
        self.df['pattern_complexity'] = pd.cut(self.df['pattern_count'],
                                             bins=[0, 10, 100, 1000, 10000],
                                             labels=['Minimal', 'Light', 'Moderate', 'Heavy'])

        self.df['corpus_category'] = pd.cut(self.df['corpus_bytes'],
                                          bins=[0, 1024**2, 10*1024**2, 100*1024**2, 1024**3],
                                          labels=['Small', 'Medium', 'Large', 'XLarge'])

    def generate_scaling_analysis(self) -> Dict[str, Any]:
        """Analyze how engines scale with pattern count and corpus size."""
        scaling_data = {}

        for engine in self.df['engine_name'].unique():
            engine_data = self.df[self.df['engine_name'] == engine]

            # Pattern count scaling
            pattern_groups = engine_data.groupby('pattern_count').agg({
                'throughput_mb_s': ['mean', 'std'],
                'memory_per_pattern_kb': ['mean', 'std'],
                'compilation_ms': ['mean', 'std']
            }).round(3)

            # Corpus size scaling
            corpus_groups = engine_data.groupby('corpus_bytes').agg({
                'throughput_mb_s': ['mean', 'std'],
                'scanning_ms': ['mean', 'std']
            }).round(3)

            # Fit scaling models
            pattern_scaling = self._fit_scaling_model(engine_data, 'pattern_count', 'throughput_mb_s')
            corpus_scaling = self._fit_scaling_model(engine_data, 'corpus_bytes', 'scanning_ms')

            scaling_data[engine] = {
                'pattern_scaling': {
                    'data': pattern_groups.to_dict(),
                    'model': pattern_scaling,
                    'interpretation': self._interpret_scaling(pattern_scaling)
                },
                'corpus_scaling': {
                    'data': corpus_groups.to_dict(),
                    'model': corpus_scaling,
                    'interpretation': self._interpret_scaling(corpus_scaling)
                }
            }

        return scaling_data

    def _fit_scaling_model(self, data: pd.DataFrame, x_col: str, y_col: str) -> Dict[str, Any]:
        """Fit polynomial models to understand scaling behavior."""
        if len(data) < 3:
            return {'type': 'insufficient_data', 'r2': 0}

        X = data[x_col].values.reshape(-1, 1)
        y = data[y_col].values

        # Try linear and quadratic fits
        models = {}

        # Linear model
        linear_reg = LinearRegression()
        linear_reg.fit(X, y)
        linear_pred = linear_reg.predict(X)
        linear_r2 = r2_score(y, linear_pred)

        models['linear'] = {
            'coefficients': [linear_reg.intercept_, linear_reg.coef_[0]],
            'r2': linear_r2,
            'equation': f"y = {linear_reg.coef_[0]:.3e}x + {linear_reg.intercept_:.3e}"
        }

        # Quadratic model
        if len(data) >= 5:  # Need enough points for quadratic
            poly_features = PolynomialFeatures(degree=2)
            X_poly = poly_features.fit_transform(X)
            poly_reg = LinearRegression()
            poly_reg.fit(X_poly, y)
            poly_pred = poly_reg.predict(X_poly)
            poly_r2 = r2_score(y, poly_pred)

            models['quadratic'] = {
                'coefficients': poly_reg.coef_,
                'r2': poly_r2,
                'equation': f"y = {poly_reg.coef_[2]:.3e}x² + {poly_reg.coef_[1]:.3e}x + {poly_reg.coef_[0]:.3e}"
            }

        # Return best model
        best_model = max(models.items(), key=lambda x: x[1]['r2'])
        return {
            'type': best_model[0],
            'model_data': best_model[1],
            'all_models': models
        }

    def _interpret_scaling(self, model_data: Dict[str, Any]) -> str:
        """Interpret scaling behavior in human terms."""
        if model_data['type'] == 'insufficient_data':
            return "Insufficient data for scaling analysis"

        model = model_data['model_data']
        r2 = model['r2']

        if r2 < 0.5:
            return f"Poor fit (R² = {r2:.3f}) - inconsistent scaling behavior"
        elif model_data['type'] == 'linear':
            coef = model['coefficients'][1]
            if abs(coef) < 1e-6:
                return f"Constant performance (R² = {r2:.3f}) - excellent scaling"
            elif coef > 0:
                return f"Performance degrades linearly (R² = {r2:.3f}) - slope: {coef:.3e}"
            else:
                return f"Performance improves linearly (R² = {r2:.3f}) - slope: {coef:.3e}"
        else:  # quadratic
            coef = model['coefficients'][2]
            if coef > 0:
                return f"Performance degrades quadratically (R² = {r2:.3f}) - poor scaling"
            else:
                return f"Performance has quadratic behavior (R² = {r2:.3f})"

    def generate_comparative_analysis(self) -> Dict[str, Any]:
        """Generate comprehensive engine comparisons."""
        comparative_data = {}

        # Performance ratios
        engines = list(self.df['engine_name'].unique())
        baseline = engines[0]  # Use first engine as baseline

        for condition in ['pattern_count', 'corpus_bytes']:
            condition_data = {}

            for value in self.df[condition].unique():
                subset = self.df[self.df[condition] == value]
                baseline_perf = subset[subset['engine_name'] == baseline]['throughput_mb_s'].mean()

                ratios = {}
                for engine in engines:
                    engine_perf = subset[subset['engine_name'] == engine]['throughput_mb_s'].mean()
                    if baseline_perf > 0:
                        ratios[engine] = engine_perf / baseline_perf
                    else:
                        ratios[engine] = 0

                condition_data[str(value)] = ratios

            comparative_data[f'{condition}_performance_ratios'] = condition_data

        # Statistical significance tests
        comparative_data['significance_tests'] = self._statistical_significance_tests()

        # Efficiency frontiers
        comparative_data['efficiency_frontiers'] = self._calculate_efficiency_frontiers()

        return comparative_data

    def _statistical_significance_tests(self) -> Dict[str, Any]:
        """Perform statistical tests between engines."""
        significance_results = {}
        engines = list(self.df['engine_name'].unique())

        for i, engine1 in enumerate(engines):
            for engine2 in engines[i+1:]:
                data1 = self.df[self.df['engine_name'] == engine1]['throughput_mb_s']
                data2 = self.df[self.df['engine_name'] == engine2]['throughput_mb_s']

                # T-test
                t_stat, p_value = stats.ttest_ind(data1, data2)

                # Effect size (Cohen's d)
                pooled_std = np.sqrt(((len(data1)-1)*data1.std()**2 + (len(data2)-1)*data2.std()**2) /
                                   (len(data1) + len(data2) - 2))
                cohens_d = (data1.mean() - data2.mean()) / pooled_std if pooled_std > 0 else 0

                significance_results[f"{engine1}_vs_{engine2}"] = {
                    't_statistic': float(t_stat),
                    'p_value': float(p_value),
                    'cohens_d': float(cohens_d),
                    'significant': p_value < 0.05,
                    'effect_size': 'large' if abs(cohens_d) > 0.8 else 'medium' if abs(cohens_d) > 0.5 else 'small',
                    'mean_diff': float(data1.mean() - data2.mean())
                }

        return significance_results

    def _calculate_efficiency_frontiers(self) -> Dict[str, Any]:
        """Calculate Pareto efficiency frontiers for performance vs memory."""
        frontiers = {}

        for pattern_count in self.df['pattern_count'].unique():
            subset = self.df[self.df['pattern_count'] == pattern_count]

            # Group by engine and calculate means
            engine_stats = subset.groupby('engine_name').agg({
                'throughput_mb_s': 'mean',
                'memory_per_pattern_kb': 'mean',
                'throughput_per_mb_memory': 'mean'
            }).reset_index()

            # Find Pareto frontier (high throughput, low memory)
            pareto_engines = []
            for _, row in engine_stats.iterrows():
                is_dominated = False
                for _, other in engine_stats.iterrows():
                    if (other['throughput_mb_s'] >= row['throughput_mb_s'] and
                        other['memory_per_pattern_kb'] <= row['memory_per_pattern_kb'] and
                        (other['throughput_mb_s'] > row['throughput_mb_s'] or
                         other['memory_per_pattern_kb'] < row['memory_per_pattern_kb'])):
                        is_dominated = True
                        break

                if not is_dominated:
                    pareto_engines.append(row.to_dict())

            frontiers[str(pattern_count)] = pareto_engines

        return frontiers

    def generate_engine_profiles(self) -> Dict[str, EngineProfile]:
        """Generate comprehensive profiles for each engine."""
        profiles = {}

        for engine in self.df['engine_name'].unique():
            engine_data = self.df[self.df['engine_name'] == engine]

            # Throughput characteristics
            throughput_chars = {
                'mean_throughput': float(engine_data['throughput_mb_s'].mean()),
                'max_throughput': float(engine_data['throughput_mb_s'].max()),
                'min_throughput': float(engine_data['throughput_mb_s'].min()),
                'throughput_cv': float(engine_data['throughput_mb_s'].std() / engine_data['throughput_mb_s'].mean()),
                'throughput_percentile_95': float(engine_data['throughput_mb_s'].quantile(0.95)),
                'throughput_percentile_5': float(engine_data['throughput_mb_s'].quantile(0.05))
            }

            # Memory characteristics
            memory_chars = {
                'mean_memory_per_pattern': float(engine_data['memory_per_pattern_kb'].mean()),
                'max_memory_per_pattern': float(engine_data['memory_per_pattern_kb'].max()),
                'min_memory_per_pattern': float(engine_data['memory_per_pattern_kb'].min()),
                'memory_efficiency': float(engine_data['throughput_per_mb_memory'].mean()),
                'memory_cv': float(engine_data['memory_per_pattern_kb'].std() / engine_data['memory_per_pattern_kb'].mean())
            }

            # Scaling behavior
            scaling_behavior = self._analyze_engine_scaling(engine_data)

            # Consistency metrics
            consistency_metrics = {
                'iteration_consistency': float(1.0 / (1.0 + engine_data.groupby(['pattern_count', 'corpus_bytes'])['throughput_mb_s'].std().mean())),
                'compilation_consistency': float(1.0 / (1.0 + engine_data['compilation_ms'].std() / engine_data['compilation_ms'].mean())),
                'scanning_consistency': float(1.0 / (1.0 + engine_data['scanning_ms'].std() / engine_data['scanning_ms'].mean()))
            }

            # Generate insights
            strengths, weaknesses, use_cases = self._generate_engine_insights(
                engine, throughput_chars, memory_chars, scaling_behavior, consistency_metrics
            )

            profiles[engine] = EngineProfile(
                engine=engine,
                throughput_characteristics=throughput_chars,
                memory_characteristics=memory_chars,
                scaling_behavior=scaling_behavior,
                consistency_metrics=consistency_metrics,
                strengths=strengths,
                weaknesses=weaknesses,
                use_cases=use_cases
            )

        return profiles

    def _analyze_engine_scaling(self, engine_data: pd.DataFrame) -> Dict[str, float]:
        """Analyze how well an engine scales."""
        scaling_metrics = {}

        # Pattern count scaling
        if len(engine_data['pattern_count'].unique()) > 2:
            pattern_corr = engine_data['pattern_count'].corr(engine_data['throughput_mb_s'])
            scaling_metrics['pattern_scaling_correlation'] = float(pattern_corr)

        # Corpus size scaling
        if len(engine_data['corpus_bytes'].unique()) > 2:
            corpus_corr = engine_data['corpus_bytes'].corr(engine_data['scanning_ms'])
            scaling_metrics['corpus_scaling_linearity'] = float(corpus_corr)

        # Memory scaling
        if len(engine_data['pattern_count'].unique()) > 2:
            memory_pattern_corr = engine_data['pattern_count'].corr(engine_data['memory_per_pattern_kb'])
            scaling_metrics['memory_pattern_efficiency'] = float(-memory_pattern_corr)  # Negative is better

        return scaling_metrics

    def _generate_engine_insights(self, engine: str, throughput: Dict, memory: Dict,
                                 scaling: Dict, consistency: Dict) -> Tuple[List[str], List[str], List[str]]:
        """Generate human-readable insights about engine characteristics."""
        strengths = []
        weaknesses = []
        use_cases = []

        # Throughput analysis
        if throughput['mean_throughput'] > 50:
            strengths.append("High throughput performance")
            use_cases.append("High-volume data processing")
        elif throughput['mean_throughput'] < 10:
            weaknesses.append("Low throughput performance")

        if throughput['throughput_cv'] < 0.2:
            strengths.append("Consistent throughput performance")
        elif throughput['throughput_cv'] > 0.5:
            weaknesses.append("Variable throughput performance")

        # Memory analysis
        if memory['mean_memory_per_pattern'] < 1.0:
            strengths.append("Excellent memory efficiency")
            use_cases.append("Memory-constrained environments")
        elif memory['mean_memory_per_pattern'] > 10.0:
            weaknesses.append("High memory usage per pattern")

        if memory['memory_efficiency'] > 50:
            strengths.append("High throughput per MB of memory")

        # Scaling analysis
        pattern_scaling = scaling.get('pattern_scaling_correlation', 0)
        if pattern_scaling > -0.3:  # Less negative correlation is better
            strengths.append("Good scaling with pattern count")
            use_cases.append("Large pattern sets")
        elif pattern_scaling < -0.7:
            weaknesses.append("Poor scaling with pattern count")

        # Consistency analysis
        if consistency['iteration_consistency'] > 0.8:
            strengths.append("Highly consistent performance")
            use_cases.append("Predictable performance requirements")
        elif consistency['iteration_consistency'] < 0.5:
            weaknesses.append("Inconsistent performance across runs")

        return strengths, weaknesses, use_cases

    def generate_performance_insights(self) -> List[PerformanceInsight]:
        """Generate automated insights and recommendations."""
        insights = []

        # Scaling insight
        scaling_data = self.generate_scaling_analysis()
        best_scaling_engine = min(scaling_data.keys(),
                                key=lambda e: scaling_data[e]['pattern_scaling']['model'].get('model_data', {}).get('r2', 0))

        insights.append(PerformanceInsight(
            title="Pattern Count Scaling Analysis",
            description=f"{best_scaling_engine} shows the best scaling behavior with increasing pattern count",
            severity="info",
            data=scaling_data,
            chart_type="scaling_curves",
            chart_data=self._prepare_scaling_chart_data()
        ))

        # Memory efficiency insight
        efficiency_data = self.df.groupby('engine_name')['throughput_per_mb_memory'].mean().to_dict()
        most_efficient = max(efficiency_data.keys(), key=lambda e: efficiency_data[e])

        insights.append(PerformanceInsight(
            title="Memory Efficiency Leader",
            description=f"{most_efficient} provides the highest throughput per MB of memory used",
            severity="info",
            data=efficiency_data,
            chart_type="efficiency_radar",
            chart_data=self._prepare_efficiency_chart_data()
        ))

        # Performance variability insight
        cv_data = self.df.groupby('engine_name')['throughput_mb_s'].apply(lambda x: x.std()/x.mean()).to_dict()
        most_consistent = min(cv_data.keys(), key=lambda e: cv_data[e])

        if cv_data[most_consistent] > 0.3:
            severity = "warning"
            desc = f"All engines show significant performance variability. {most_consistent} is most consistent but still varies by {cv_data[most_consistent]:.1%}"
        else:
            severity = "info"
            desc = f"{most_consistent} shows excellent performance consistency (CV: {cv_data[most_consistent]:.1%})"

        insights.append(PerformanceInsight(
            title="Performance Consistency Analysis",
            description=desc,
            severity=severity,
            data=cv_data,
            chart_type="consistency_analysis",
            chart_data=self._prepare_consistency_chart_data()
        ))

        return insights

    def _prepare_scaling_chart_data(self) -> Dict[str, Any]:
        """Prepare data for scaling analysis charts."""
        chart_data = {}

        for engine in self.df['engine_name'].unique():
            engine_data = self.df[self.df['engine_name'] == engine]

            # Group by pattern count
            scaling_points = engine_data.groupby('pattern_count').agg({
                'throughput_mb_s': ['mean', 'std'],
                'memory_per_pattern_kb': ['mean', 'std']
            }).reset_index()

            chart_data[engine] = {
                'pattern_counts': scaling_points['pattern_count'].tolist(),
                'throughput_mean': scaling_points[('throughput_mb_s', 'mean')].tolist(),
                'throughput_std': scaling_points[('throughput_mb_s', 'std')].tolist(),
                'memory_mean': scaling_points[('memory_per_pattern_kb', 'mean')].tolist(),
                'memory_std': scaling_points[('memory_per_pattern_kb', 'std')].tolist()
            }

        return chart_data

    def _prepare_efficiency_chart_data(self) -> Dict[str, Any]:
        """Prepare data for efficiency radar charts."""
        efficiency_metrics = {}

        for engine in self.df['engine_name'].unique():
            engine_data = self.df[self.df['engine_name'] == engine]

            metrics = {
                'Throughput': float(engine_data['throughput_mb_s'].mean()),
                'Memory Efficiency': float(engine_data['throughput_per_mb_memory'].mean()),
                'Compilation Speed': float(1000 / engine_data['compilation_ms'].mean()),  # Inverted for radar
                'Consistency': float(1.0 / (1.0 + engine_data['throughput_mb_s'].std()/engine_data['throughput_mb_s'].mean())),
                'Scaling': float(1.0 / (1.0 + abs(engine_data['pattern_count'].corr(engine_data['throughput_mb_s']))))
            }

            efficiency_metrics[engine] = metrics

        return efficiency_metrics

    def _prepare_consistency_chart_data(self) -> Dict[str, Any]:
        """Prepare data for consistency analysis charts."""
        consistency_data = {}

        for engine in self.df['engine_name'].unique():
            engine_data = self.df[self.df['engine_name'] == engine]

            # Performance by conditions
            condition_consistency = {}
            for condition in ['pattern_count', 'corpus_bytes']:
                grouped = engine_data.groupby(condition)['throughput_mb_s'].agg(['mean', 'std'])
                condition_consistency[condition] = {
                    'values': grouped.index.tolist(),
                    'means': grouped['mean'].tolist(),
                    'stds': grouped['std'].tolist(),
                    'cvs': (grouped['std'] / grouped['mean']).tolist()
                }

            consistency_data[engine] = condition_consistency

        return consistency_data

    def export_complete_analysis(self) -> Dict[str, Any]:
        """Export comprehensive analysis for visualization."""
        return {
            'scaling_analysis': self.generate_scaling_analysis(),
            'comparative_analysis': self.generate_comparative_analysis(),
            'engine_profiles': {k: {
                'engine': v.engine,
                'throughput_characteristics': v.throughput_characteristics,
                'memory_characteristics': v.memory_characteristics,
                'scaling_behavior': v.scaling_behavior,
                'consistency_metrics': v.consistency_metrics,
                'strengths': v.strengths,
                'weaknesses': v.weaknesses,
                'use_cases': v.use_cases
            } for k, v in self.generate_engine_profiles().items()},
            'insights': [
                {
                    'title': insight.title,
                    'description': insight.description,
                    'severity': insight.severity,
                    'data': insight.data,
                    'chart_type': insight.chart_type,
                    'chart_data': insight.chart_data
                } for insight in self.generate_performance_insights()
            ],
            'raw_data': self.df.to_dict('records'),
            'summary_statistics': self._generate_summary_stats()
        }

    def _generate_summary_stats(self) -> Dict[str, Any]:
        """Generate comprehensive summary statistics."""
        return {
            'total_benchmarks': len(self.df),
            'engines_tested': list(self.df['engine_name'].unique()),
            'pattern_counts': sorted(list(self.df['pattern_count'].unique())),
            'corpus_sizes': sorted(list(self.df['corpus_bytes'].unique())),
            'date_range': {
                'total_iterations': len(self.df['iteration'].unique()),
                'conditions_tested': len(self.df[['pattern_count', 'corpus_bytes']].drop_duplicates())
            },
            'performance_ranges': {
                'throughput_range': [float(self.df['throughput_mb_s'].min()),
                                   float(self.df['throughput_mb_s'].max())],
                'memory_range': [float(self.df['memory_per_pattern_kb'].min()),
                               float(self.df['memory_per_pattern_kb'].max())],
                'compilation_range': [float(self.df['compilation_ms'].min()),
                                    float(self.df['compilation_ms'].max())]
            }
        }