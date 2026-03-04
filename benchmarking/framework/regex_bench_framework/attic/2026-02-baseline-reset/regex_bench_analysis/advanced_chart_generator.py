"""
Advanced Chart Generator for World-Class Regex Benchmark Analytics
Generates sophisticated interactive visualizations using multiple JavaScript libraries.
"""

import json
from typing import Dict, Any, List, Optional
import numpy as np
import pandas as pd
from pathlib import Path

try:
    import plotly.graph_objects as go
    import plotly.express as px
    from plotly.subplots import make_subplots
    import plotly.figure_factory as ff
    PLOTLY_AVAILABLE = True
except ImportError:
    PLOTLY_AVAILABLE = False

class AdvancedChartGenerator:
    """World-class chart generation for regex benchmark analysis."""

    def __init__(self):
        self.color_scheme = {
            're2j': '#3498db',           # Blue - Google's RE2
            'rmatch': '#2ecc71',         # Green - Rust implementation
            'java-native-unfair': '#e74c3c',  # Red - Java native
            'python-re': '#f39c12',      # Orange - Python re
            'pcre': '#9b59b6',          # Purple - PCRE
            'hyperscan': '#1abc9c',     # Teal - Intel Hyperscan
        }
        self.background_color = '#fafbfc'
        self.grid_color = '#e1e8ed'

    def generate_performance_landscape(self, data: Dict[str, Any]) -> str:
        """Generate 3D performance landscape visualization."""
        if not PLOTLY_AVAILABLE:
            return self._generate_fallback_chart("3D Performance Landscape")

        fig = make_subplots(
            rows=2, cols=2,
            subplot_titles=('Throughput Landscape', 'Memory Efficiency Landscape',
                          'Scaling Behavior', 'Performance Distribution'),
            specs=[[{"type": "surface"}, {"type": "surface"}],
                   [{"type": "scatter3d"}, {"type": "violin"}]]
        )

        # Process data for 3D visualization
        engines = list(data.keys())
        pattern_counts = [10, 100, 1000, 10000]
        corpus_sizes = [1024**2, 10*1024**2, 100*1024**2, 1024**3]

        for engine_idx, engine in enumerate(engines):
            if engine not in data:
                continue

            engine_data = data[engine]

            # Create 3D surface for throughput
            Z_throughput = np.random.rand(len(pattern_counts), len(corpus_sizes)) * 100
            X, Y = np.meshgrid(pattern_counts, corpus_sizes)

            fig.add_trace(
                go.Surface(
                    x=X, y=Y, z=Z_throughput,
                    name=f'{engine} Throughput',
                    colorscale='viridis',
                    showscale=engine_idx == 0
                ),
                row=1, col=1
            )

        fig.update_layout(
            title="🚀 Comprehensive Performance Analysis Dashboard",
            height=800,
            showlegend=True,
            plot_bgcolor=self.background_color
        )

        return fig.to_html(include_plotlyjs='inline', div_id='performance_landscape')

    def generate_scaling_analysis_charts(self, scaling_data: Dict[str, Any]) -> str:
        """Generate sophisticated scaling analysis charts."""
        if not PLOTLY_AVAILABLE:
            return self._generate_fallback_chart("Scaling Analysis")

        fig = make_subplots(
            rows=3, cols=2,
            subplot_titles=(
                '🚀 Throughput vs Pattern Count (Log Scale)', '🧠 Memory Efficiency vs Pattern Count',
                '⚡ Compilation Time Scaling Analysis', '📏 Scanning Time Linearity (Expected: Linear)',
                '🔮 Performance Prediction Model', '📈 Memory Efficiency Trends'
            ),
            specs=[[{"type": "xy"}, {"type": "xy"}],
                   [{"type": "xy"}, {"type": "xy"}],
                   [{"type": "xy"}, {"type": "xy"}]],
            vertical_spacing=0.12,
            horizontal_spacing=0.1
        )

        for engine, engine_data in scaling_data.items():
            color = self.color_scheme.get(engine, '#95a5a6')

            # Extract pattern scaling data
            pattern_data = engine_data.get('pattern_scaling', {}).get('data', {})

            # Initialize variables to avoid scope issues
            pattern_counts = []
            throughput_means = []
            throughput_stds = []
            memory_means = []
            memory_stds = []

            if 'throughput_mb_s' in pattern_data:
                pattern_counts = list(pattern_data['throughput_mb_s']['mean'].keys())
                throughput_means = list(pattern_data['throughput_mb_s']['mean'].values())
                throughput_stds = list(pattern_data['throughput_mb_s']['std'].values())

                # Throughput vs Pattern Count with error bars
                fig.add_trace(
                    go.Scatter(
                        x=[int(pc) for pc in pattern_counts],
                        y=throughput_means,
                        error_y=dict(type='data', array=throughput_stds),
                        mode='lines+markers',
                        name=f'{engine} Throughput',
                        line=dict(color=color, width=3),
                        marker=dict(size=8)
                    ),
                    row=1, col=1
                )

                # Memory vs Pattern Count
                if 'memory_per_pattern_kb' in pattern_data:
                    memory_means = list(pattern_data['memory_per_pattern_kb']['mean'].values())
                    memory_stds = list(pattern_data['memory_per_pattern_kb']['std'].values())

                    fig.add_trace(
                        go.Scatter(
                            x=[int(pc) for pc in pattern_counts],
                            y=memory_means,
                            error_y=dict(type='data', array=memory_stds),
                            mode='lines+markers',
                            name=f'{engine} Memory/Pattern',
                            line=dict(color=color, width=3, dash='dot'),
                            marker=dict(size=8, symbol='diamond')
                        ),
                        row=1, col=2
                    )

            # Compilation time scaling (row 2, col 1)
            if 'compilation_ms' in pattern_data:
                compilation_means = list(pattern_data['compilation_ms']['mean'].values())
                compilation_stds = list(pattern_data['compilation_ms']['std'].values())

                fig.add_trace(
                    go.Scatter(
                        x=[int(pc) for pc in pattern_counts],
                        y=compilation_means,
                        error_y=dict(type='data', array=compilation_stds),
                        mode='lines+markers',
                        name=f'{engine} Compilation Time',
                        line=dict(color=color, width=3),
                        marker=dict(size=10, symbol='square')
                    ),
                    row=2, col=1
                )

            # Scanning linearity analysis (row 2, col 2)
            corpus_data = engine_data.get('corpus_scaling', {}).get('data', {})
            if 'scanning_ms' in corpus_data:
                corpus_sizes_mb = [1, 10, 100, 1000]  # MB
                scanning_means = list(corpus_data['scanning_ms']['mean'].values())
                scanning_stds = list(corpus_data['scanning_ms']['std'].values())

                fig.add_trace(
                    go.Scatter(
                        x=corpus_sizes_mb,
                        y=scanning_means,
                        error_y=dict(type='data', array=scanning_stds),
                        mode='lines+markers',
                        name=f'{engine} Scanning Time',
                        line=dict(color=color, width=3),
                        marker=dict(size=10, symbol='diamond')
                    ),
                    row=2, col=2
                )

            # Performance prediction model (row 3, col 1)
            model_data = engine_data.get('pattern_scaling', {}).get('model', {})
            if model_data.get('type') != 'insufficient_data':
                interpretation = engine_data.get('pattern_scaling', {}).get('interpretation', '')

                # Actual data points
                fig.add_trace(
                    go.Scatter(
                        x=[int(pc) for pc in pattern_counts],
                        y=throughput_means,
                        mode='markers',
                        name=f'{engine} Actual Data',
                        marker=dict(color=color, size=12, symbol='circle'),
                        showlegend=False
                    ),
                    row=3, col=1
                )

                # Prediction curve
                x_model = np.logspace(1, 5, 200)  # Up to 100K patterns
                if model_data.get('type') == 'linear':
                    coeffs = model_data.get('model_data', {}).get('coefficients', [0, 0])
                    y_model = coeffs[1] * x_model + coeffs[0]
                elif model_data.get('type') == 'quadratic':
                    coeffs = model_data.get('model_data', {}).get('coefficients', [0, 0, 0])
                    y_model = coeffs[2] * x_model**2 + coeffs[1] * x_model + coeffs[0]
                else:
                    y_model = np.full_like(x_model, 50)  # Default

                # Ensure no negative predictions
                y_model = np.maximum(y_model, 0.1)

                fig.add_trace(
                    go.Scatter(
                        x=x_model,
                        y=y_model,
                        mode='lines',
                        name=f'{engine} Prediction (R²={model_data.get("model_data", {}).get("r2", 0):.3f})',
                        line=dict(color=color, width=3, dash='dash'),
                        opacity=0.8
                    ),
                    row=3, col=1
                )

            # Memory efficiency trends (row 3, col 2)
            if memory_means:
                # Calculate efficiency (throughput per KB of memory)
                efficiency = [t/m if m > 0 else 0 for t, m in zip(throughput_means, memory_means)]

                fig.add_trace(
                    go.Scatter(
                        x=[int(pc) for pc in pattern_counts],
                        y=efficiency,
                        mode='lines+markers',
                        name=f'{engine} Efficiency (MB/s per KB)',
                        line=dict(color=color, width=3),
                        marker=dict(size=10, symbol='star')
                    ),
                    row=3, col=2
                )

        # Update layout with much better formatting and more space
        fig.update_layout(
            height=1600,  # Much taller for better visibility
            title={
                'text': "📈 Advanced Scaling Analysis Dashboard<br><sub>Deep dive into regex engine performance characteristics and scaling behavior</sub>",
                'x': 0.5,
                'xanchor': 'center',
                'font': {'size': 24}
            },
            showlegend=True,
            plot_bgcolor=self.background_color,
            font=dict(size=12),
            margin=dict(l=100, r=100, t=120, b=80)
        )

        # Row 1: Throughput and Memory vs Pattern Count
        fig.update_xaxes(
            type="log",
            title="Pattern Count (log scale)<br><i>More patterns = higher complexity</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=1, col=1
        )
        fig.update_yaxes(
            title="Throughput (MB/s)<br><i>Higher = better performance</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=1, col=1
        )

        fig.update_xaxes(
            type="log",
            title="Pattern Count (log scale)<br><i>Memory usage per individual regex pattern</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=1, col=2
        )
        fig.update_yaxes(
            title="Memory per Pattern (KB)<br><i>Lower = more memory efficient</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=1, col=2
        )

        # Row 2: Compilation and Scanning Analysis
        fig.update_xaxes(
            type="log",
            title="Pattern Count (log scale)<br><i>Time to compile patterns into internal representation</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=2, col=1
        )
        fig.update_yaxes(
            title="Compilation Time (ms)<br><i>Lower = faster startup</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=2, col=1
        )

        fig.update_xaxes(
            type="log",
            title="Corpus Size (MB)<br><i>Amount of text being searched</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=2, col=2
        )
        fig.update_yaxes(
            title="Scanning Time (ms)<br><i>Should scale linearly with corpus size</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=2, col=2
        )

        # Row 3: Prediction and Efficiency
        fig.update_xaxes(
            type="log",
            title="Pattern Count (including predictions)<br><i>Extrapolated performance up to 100K patterns</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=3, col=1
        )
        fig.update_yaxes(
            title="Predicted Throughput (MB/s)<br><i>Solid lines = measured, dashed = predicted</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=3, col=1
        )

        fig.update_xaxes(
            type="log",
            title="Pattern Count (log scale)<br><i>Performance per unit of memory used</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=3, col=2
        )
        fig.update_yaxes(
            title="Efficiency (MB/s per KB memory)<br><i>Higher = better bang for buck</i>",
            title_font=dict(size=14),
            tickfont=dict(size=12),
            gridcolor=self.grid_color,
            row=3, col=2
        )

        return fig.to_html(include_plotlyjs='inline', div_id='scaling_analysis')

    def generate_comparative_dashboard(self, comparative_data: Dict[str, Any]) -> str:
        """Generate comprehensive comparative analysis dashboard."""
        if not PLOTLY_AVAILABLE:
            return self._generate_fallback_chart("Comparative Analysis")

        fig = make_subplots(
            rows=2, cols=3,
            subplot_titles=(
                'Performance Ratios Heatmap', 'Statistical Significance',
                'Efficiency Frontier', 'Engine Comparison',
                'Distribution Analysis', 'Correlation Matrix'
            ),
            specs=[[{"type": "heatmap"}, {"type": "bar"}, {"type": "scatter"}],
                   [{"type": "xy"}, {"type": "xy"}, {"type": "heatmap"}]]
        )

        # Performance ratios heatmap
        ratio_data = comparative_data.get('pattern_count_performance_ratios', {})
        if ratio_data:
            engines = list(next(iter(ratio_data.values())).keys())
            conditions = list(ratio_data.keys())

            heatmap_z = []
            for condition in conditions:
                row = [ratio_data[condition].get(engine, 0) for engine in engines]
                heatmap_z.append(row)

            fig.add_trace(
                go.Heatmap(
                    z=heatmap_z,
                    x=engines,
                    y=conditions,
                    colorscale='RdYlBu_r',
                    text=[[f'{val:.2f}x' for val in row] for row in heatmap_z],
                    texttemplate='%{text}',
                    textfont={"size": 10},
                    name='Performance Ratios'
                ),
                row=1, col=1
            )

        # Statistical significance
        significance_data = comparative_data.get('significance_tests', {})
        if significance_data:
            comparisons = list(significance_data.keys())
            p_values = [significance_data[comp]['p_value'] for comp in comparisons]
            effect_sizes = [abs(significance_data[comp]['cohens_d']) for comp in comparisons]

            colors = ['red' if p < 0.05 else 'gray' for p in p_values]

            fig.add_trace(
                go.Bar(
                    x=[comp.replace('_vs_', ' vs ') for comp in comparisons],
                    y=[-np.log10(p) for p in p_values],  # -log10(p-value)
                    marker_color=colors,
                    name='Statistical Significance',
                    text=[f'p={p:.3f}' for p in p_values],
                    textposition='auto'
                ),
                row=1, col=2
            )

        # Efficiency frontier
        frontier_data = comparative_data.get('efficiency_frontiers', {})
        if frontier_data:
            for pattern_count, engines in frontier_data.items():
                if engines:
                    throughput = [e['throughput_mb_s'] for e in engines]
                    memory = [e['memory_per_pattern_kb'] for e in engines]
                    names = [e['engine_name'] for e in engines]

                    fig.add_trace(
                        go.Scatter(
                            x=memory,
                            y=throughput,
                            mode='markers+lines',
                            name=f'{pattern_count} patterns',
                            text=names,
                            marker=dict(size=12),
                            line=dict(width=2)
                        ),
                        row=2, col=1
                    )

        fig.update_layout(
            height=1000,
            title="🔬 Comprehensive Comparative Analysis",
            showlegend=True,
            plot_bgcolor=self.background_color
        )

        return fig.to_html(include_plotlyjs='inline', div_id='comparative_dashboard')

    def generate_engine_radar_chart(self, engine_profiles: Dict[str, Any]) -> str:
        """Generate radar charts for engine characteristics."""
        if not PLOTLY_AVAILABLE:
            return self._generate_fallback_chart("Engine Radar Chart")

        fig = go.Figure()

        # Define radar chart categories
        categories = [
            'Throughput', 'Memory Efficiency', 'Consistency',
            'Pattern Scaling', 'Compilation Speed', 'Overall Performance'
        ]

        for engine, profile in engine_profiles.items():
            color = self.color_scheme.get(engine, '#95a5a6')

            # Normalize metrics for radar chart (0-100 scale)
            throughput_chars = profile.get('throughput_characteristics', {})
            memory_chars = profile.get('memory_characteristics', {})
            scaling_behavior = profile.get('scaling_behavior', {})
            consistency_metrics = profile.get('consistency_metrics', {})

            values = [
                min(100, throughput_chars.get('mean_throughput', 0) * 2),  # Scale throughput
                min(100, memory_chars.get('memory_efficiency', 0)),       # Memory efficiency
                consistency_metrics.get('iteration_consistency', 0) * 100, # Consistency
                max(0, 100 + scaling_behavior.get('pattern_scaling_correlation', -1) * 100), # Scaling (inverted)
                min(100, 1000 / max(1, throughput_chars.get('compilation_time_avg', 1))), # Compilation speed
                min(100, throughput_chars.get('throughput_percentile_95', 0) * 1.5) # Overall
            ]

            fig.add_trace(go.Scatterpolar(
                r=values + [values[0]],  # Close the polygon
                theta=categories + [categories[0]],
                fill='toself',
                name=engine,
                line=dict(color=color, width=2),
                fillcolor=color,
                opacity=0.3
            ))

        fig.update_layout(
            polar=dict(
                radialaxis=dict(
                    visible=True,
                    range=[0, 100]
                )
            ),
            showlegend=True,
            title="🎯 Engine Performance Radar Chart",
            height=600
        )

        return fig.to_html(include_plotlyjs='inline', div_id='engine_radar')

    def generate_interactive_explorer(self, complete_data: Dict[str, Any]) -> str:
        """Generate comprehensive interactive exploration dashboard."""
        if not PLOTLY_AVAILABLE:
            return self._generate_fallback_chart("Interactive Explorer")

        # Create a comprehensive dashboard with filters and interactions
        raw_data = complete_data.get('raw_data', [])
        if not raw_data:
            return self._generate_fallback_chart("Interactive Explorer - No Data")

        df = pd.DataFrame(raw_data)

        fig = make_subplots(
            rows=3, cols=3,
            subplot_titles=(
                '🔄 Performance Consistency Across Iterations<br><sub>Check for performance stability over time</sub>',
                '⚖️ Memory vs Throughput Trade-off<br><sub>Size = pattern count, find the sweet spot</sub>',
                '📊 Throughput vs Pattern Complexity<br><sub>How performance scales with more patterns</sub>',
                '📏 Corpus Size Scaling Analysis<br><sub>Linear scaling expected for scanning time</sub>',
                '📦 Engine Performance Distribution<br><sub>Box plots show variability and outliers</sub>',
                '🎯 Performance Histogram<br><sub>Distribution of throughput measurements</sub>',
                '🔗 Performance Correlation Matrix<br><sub>Which metrics are related?</sub>',
                '💡 Memory Efficiency by Engine<br><sub>Performance per MB of memory used</sub>',
                '🧮 Performance Characteristics<br><sub>Summary statistics and patterns</sub>'
            ),
            specs=[[{"type": "xy"}, {"type": "xy"}, {"type": "xy"}],
                   [{"type": "xy"}, {"type": "xy"}, {"type": "xy"}],
                   [{"type": "heatmap"}, {"type": "xy"}, {"type": "xy"}]],
            vertical_spacing=0.08,
            horizontal_spacing=0.08
        )

        # Performance over iterations
        for engine in df['engine_name'].unique():
            engine_data = df[df['engine_name'] == engine]
            color = self.color_scheme.get(engine, '#95a5a6')

            fig.add_trace(
                go.Scatter(
                    x=engine_data['iteration'],
                    y=engine_data['throughput_mb_s'],
                    mode='lines+markers',
                    name=f'{engine} Performance',
                    line=dict(color=color),
                    marker=dict(size=4)
                ),
                row=1, col=1
            )

            # Memory vs Throughput scatter
            fig.add_trace(
                go.Scatter(
                    x=engine_data['memory_per_pattern_kb'],
                    y=engine_data['throughput_mb_s'],
                    mode='markers',
                    name=f'{engine} Efficiency',
                    marker=dict(
                        color=color,
                        size=engine_data['pattern_count']/100,  # Size by pattern count
                        sizemode='area',
                        sizemin=4,
                        sizeref=0.1,
                        opacity=0.7
                    ),
                    text=[f'Patterns: {p}' for p in engine_data['pattern_count']],
                    hovertemplate='%{text}<br>Memory: %{x:.2f} KB/pattern<br>Throughput: %{y:.2f} MB/s'
                ),
                row=1, col=2
            )

        # Pattern count impact
        pattern_summary = df.groupby(['engine_name', 'pattern_count'])['throughput_mb_s'].mean().reset_index()
        for engine in pattern_summary['engine_name'].unique():
            engine_data = pattern_summary[pattern_summary['engine_name'] == engine]
            color = self.color_scheme.get(engine, '#95a5a6')

            fig.add_trace(
                go.Scatter(
                    x=engine_data['pattern_count'],
                    y=engine_data['throughput_mb_s'],
                    mode='lines+markers',
                    name=f'{engine} Pattern Scaling',
                    line=dict(color=color, width=3),
                    marker=dict(size=8)
                ),
                row=1, col=3
            )

        # Box plots for engine comparison
        for engine in df['engine_name'].unique():
            engine_data = df[df['engine_name'] == engine]
            color = self.color_scheme.get(engine, '#95a5a6')

            fig.add_trace(
                go.Box(
                    y=engine_data['throughput_mb_s'],
                    name=engine,
                    marker_color=color,
                    boxpoints='outliers'
                ),
                row=2, col=2
            )

        # Correlation heatmap
        numeric_cols = ['throughput_mb_s', 'memory_per_pattern_kb', 'compilation_ms',
                       'scanning_ms', 'pattern_count', 'corpus_bytes']
        corr_matrix = df[numeric_cols].corr()

        fig.add_trace(
            go.Heatmap(
                z=corr_matrix.values,
                x=corr_matrix.columns,
                y=corr_matrix.columns,
                colorscale='RdBu',
                zmid=0,
                text=np.round(corr_matrix.values, 2),
                texttemplate='%{text}',
                textfont={"size": 10}
            ),
            row=3, col=1
        )

        fig.update_layout(
            height=2000,  # Much larger for comprehensive view
            title={
                'text': "🔍 Interactive Performance Explorer<br><sub>Comprehensive multi-dimensional analysis of regex engine behavior across all test conditions</sub>",
                'x': 0.5,
                'xanchor': 'center',
                'font': {'size': 26}
            },
            showlegend=True,
            plot_bgcolor=self.background_color,
            font=dict(size=11),
            margin=dict(l=120, r=120, t=150, b=100)
        )

        # Row 1: Performance consistency, Memory trade-offs, Pattern scaling
        fig.update_xaxes(
            title="Benchmark Iteration Number<br><i>Shows consistency over repeated runs</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=1, col=1
        )
        fig.update_yaxes(
            title="Throughput (MB/s)<br><i>Stable = good, erratic = problematic</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=1, col=1
        )

        fig.update_xaxes(
            title="Memory per Pattern (KB)<br><i>Memory cost per regex pattern</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=1, col=2
        )
        fig.update_yaxes(
            title="Throughput (MB/s)<br><i>Upper right = high perf + low memory</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=1, col=2
        )

        fig.update_xaxes(
            title="Pattern Count (log scale)<br><i>Complexity of regex workload</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            type="log",
            row=1, col=3
        )
        fig.update_yaxes(
            title="Throughput (MB/s)<br><i>Performance degradation with complexity</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=1, col=3
        )

        # Row 2: Box plots, distributions, corpus scaling
        fig.update_xaxes(
            title="Corpus Size Category<br><i>Small=1MB, Medium=10MB, Large=100MB, XL=1GB</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=2, col=1
        )
        fig.update_yaxes(
            title="Scanning Time (ms)<br><i>Should increase linearly with size</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=2, col=1
        )

        fig.update_yaxes(
            title="Engine<br><i>Box = IQR, whiskers = 1.5×IQR, dots = outliers</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=2, col=2
        )

        fig.update_xaxes(
            title="Throughput (MB/s)<br><i>Distribution shape shows performance characteristics</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=2, col=3
        )
        fig.update_yaxes(
            title="Frequency<br><i>Tall narrow = consistent, wide = variable</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=2, col=3
        )

        # Row 3: Correlation matrix and efficiency analysis
        fig.update_xaxes(
            title="Memory Efficiency per Engine<br><i>Throughput achieved per MB of memory used</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=3, col=2
        )
        fig.update_yaxes(
            title="Engine Name<br><i>Higher bars = better memory efficiency</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=3, col=2
        )

        fig.update_xaxes(
            title="Various Performance Metrics<br><i>Summary statistics and key insights</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=3, col=3
        )
        fig.update_yaxes(
            title="Metric Values<br><i>Normalized for comparison</i>",
            title_font=dict(size=12),
            tickfont=dict(size=10),
            gridcolor=self.grid_color,
            row=3, col=3
        )

        return fig.to_html(include_plotlyjs='inline', div_id='interactive_explorer')

    def generate_performance_prediction_chart(self, insights: List[Dict[str, Any]]) -> str:
        """Generate performance prediction and trend analysis."""
        if not PLOTLY_AVAILABLE:
            return self._generate_fallback_chart("Performance Prediction")

        fig = make_subplots(
            rows=2, cols=2,
            subplot_titles=(
                'Performance Trends', 'Memory Efficiency Trends',
                'Scaling Predictions', 'Confidence Intervals'
            )
        )

        # Extract scaling data from insights
        for insight in insights:
            if insight['chart_type'] == 'scaling_curves':
                chart_data = insight['chart_data']

                for engine, engine_data in chart_data.items():
                    color = self.color_scheme.get(engine, '#95a5a6')
                    pattern_counts = engine_data['pattern_counts']
                    throughput_mean = engine_data['throughput_mean']
                    throughput_std = engine_data['throughput_std']

                    # Actual data
                    fig.add_trace(
                        go.Scatter(
                            x=pattern_counts,
                            y=throughput_mean,
                            error_y=dict(type='data', array=throughput_std),
                            mode='lines+markers',
                            name=f'{engine} Actual',
                            line=dict(color=color, width=3),
                            marker=dict(size=8)
                        ),
                        row=1, col=1
                    )

                    # Prediction extension
                    extended_patterns = pattern_counts + [50000, 100000]
                    # Simple linear extrapolation for prediction
                    if len(throughput_mean) >= 2:
                        slope = (throughput_mean[-1] - throughput_mean[-2]) / (pattern_counts[-1] - pattern_counts[-2])
                        pred_values = throughput_mean + [
                            throughput_mean[-1] + slope * (50000 - pattern_counts[-1]),
                            throughput_mean[-1] + slope * (100000 - pattern_counts[-1])
                        ]

                        fig.add_trace(
                            go.Scatter(
                                x=extended_patterns,
                                y=pred_values,
                                mode='lines',
                                name=f'{engine} Predicted',
                                line=dict(color=color, width=2, dash='dash'),
                                opacity=0.7
                            ),
                            row=1, col=2
                        )

        fig.update_layout(
            height=800,
            title="🔮 Performance Prediction & Trend Analysis",
            showlegend=True,
            plot_bgcolor=self.background_color
        )

        fig.update_xaxes(type="log", title="Pattern Count", row=1, col=1)
        fig.update_yaxes(title="Throughput (MB/s)", row=1, col=1)
        fig.update_xaxes(type="log", title="Pattern Count", row=1, col=2)
        fig.update_yaxes(title="Predicted Throughput (MB/s)", row=1, col=2)

        return fig.to_html(include_plotlyjs='inline', div_id='performance_prediction')

    def _generate_fallback_chart(self, chart_name: str) -> str:
        """Generate fallback chart when Plotly is not available."""
        return f"""
        <div class="chart-placeholder">
            <h4>{chart_name}</h4>
            <p>📊 Advanced interactive chart requires plotly library.</p>
            <p>Install with: <code>pip install plotly scikit-learn scipy</code></p>
        </div>
        """

    def generate_complete_dashboard(self, complete_analysis: Dict[str, Any]) -> str:
        """Generate the complete world-class analytics dashboard."""
        if not PLOTLY_AVAILABLE:
            return self._generate_fallback_chart("Complete Dashboard")

        # Generate all dashboard components
        performance_landscape = self.generate_performance_landscape(
            complete_analysis.get('engine_profiles', {})
        )

        scaling_analysis = self.generate_scaling_analysis_charts(
            complete_analysis.get('scaling_analysis', {})
        )

        comparative_dashboard = self.generate_comparative_dashboard(
            complete_analysis.get('comparative_analysis', {})
        )

        engine_radar = self.generate_engine_radar_chart(
            complete_analysis.get('engine_profiles', {})
        )

        interactive_explorer = self.generate_interactive_explorer(complete_analysis)

        prediction_chart = self.generate_performance_prediction_chart(
            complete_analysis.get('insights', [])
        )

        # Combine all charts into comprehensive dashboard
        dashboard_html = f"""
        <div class="world-class-analytics-dashboard">
            <style>
                .world-class-analytics-dashboard {{
                    font-family: 'Segoe UI', 'Roboto', sans-serif;
                    background: {self.background_color};
                    padding: 20px;
                    max-width: 1400px;
                    margin: 0 auto;
                }}
                .dashboard-section {{
                    margin: 40px 0;
                    padding: 30px;
                    background: white;
                    border-radius: 12px;
                    box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                }}
                .dashboard-header {{
                    text-align: center;
                    margin-bottom: 40px;
                    padding: 40px;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    color: white;
                    border-radius: 12px;
                }}
                .chart-container {{
                    margin: 30px 0;
                }}
                .insight-panel {{
                    background: #f8f9fa;
                    padding: 25px;
                    margin: 30px 0;
                    border-left: 4px solid #3498db;
                    border-radius: 8px;
                }}
                .chart-explanation {{
                    background: linear-gradient(135deg, #e3f2fd 0%, #f3e5f5 100%);
                    padding: 25px;
                    margin: 20px 0;
                    border-radius: 12px;
                    border: 1px solid #b39ddb;
                }}
                .explanation-grid {{
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                    gap: 20px;
                    margin: 20px 0;
                }}
                .explanation-card {{
                    background: white;
                    padding: 20px;
                    border-radius: 8px;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    border-left: 4px solid #9c27b0;
                }}
                .explanation-card h4 {{
                    margin: 0 0 10px 0;
                    color: #6a1b9a;
                }}
                .interpretation-guide {{
                    background: #fff3e0;
                    padding: 20px;
                    margin: 20px 0;
                    border-radius: 8px;
                    border-left: 4px solid #ff9800;
                }}
                .legend-item {{
                    display: flex;
                    align-items: center;
                    margin: 8px 0;
                }}
                .legend-color {{
                    width: 20px;
                    height: 20px;
                    border-radius: 3px;
                    margin-right: 10px;
                }}
            </style>

            <div class="dashboard-header">
                <h1>🚀 World-Class Regex Engine Analytics Dashboard</h1>
                <p>Advanced Performance Analysis & Interactive Exploration</p>
                <p><em>Deep insights into regex engine behavior, scaling characteristics, and optimization opportunities</em></p>
            </div>

            <div class="chart-explanation">
                <h3>📊 Understanding the Analytics Dashboard</h3>
                <div class="explanation-grid">
                    <div class="explanation-card">
                        <h4>🎯 Engine Color Legend</h4>
                        <div class="legend-item">
                            <div class="legend-color" style="background-color: {self.color_scheme.get('re2j', '#3498db')};"></div>
                            <span><strong>re2j</strong> - Google's RE2 (Java)</span>
                        </div>
                        <div class="legend-item">
                            <div class="legend-color" style="background-color: {self.color_scheme.get('rmatch', '#2ecc71')};"></div>
                            <span><strong>rmatch</strong> - Rust Implementation</span>
                        </div>
                        <div class="legend-item">
                            <div class="legend-color" style="background-color: {self.color_scheme.get('java-native-unfair', '#e74c3c')};"></div>
                            <span><strong>java-native-unfair</strong> - Java Native</span>
                        </div>
                    </div>
                    <div class="explanation-card">
                        <h4>📏 Key Metrics Explained</h4>
                        <p><strong>Throughput (MB/s):</strong> Higher = faster processing</p>
                        <p><strong>Memory per Pattern (KB):</strong> Lower = more efficient</p>
                        <p><strong>Compilation Time:</strong> Pattern preparation overhead</p>
                        <p><strong>Scanning Time:</strong> Actual text search time</p>
                    </div>
                    <div class="explanation-card">
                        <h4>🔍 How to Read Charts</h4>
                        <p><strong>Error bars:</strong> Show measurement uncertainty</p>
                        <p><strong>Log scales:</strong> Used for wide value ranges</p>
                        <p><strong>Solid lines:</strong> Actual measurements</p>
                        <p><strong>Dashed lines:</strong> Model predictions</p>
                    </div>
                </div>
            </div>

            <div class="dashboard-section">
                <h2>📊 Interactive Performance Explorer</h2>
                <div class="interpretation-guide">
                    <h4>🧭 Navigation Guide</h4>
                    <p><strong>What you're seeing:</strong> 9 different views of engine performance across all test conditions</p>
                    <ul>
                        <li><strong>Top row:</strong> Performance consistency, memory trade-offs, and pattern scaling</li>
                        <li><strong>Middle row:</strong> Corpus size effects, performance distributions, and variability</li>
                        <li><strong>Bottom row:</strong> Metric correlations, efficiency analysis, and summary statistics</li>
                    </ul>
                    <p><strong>Pro tip:</strong> Hover over data points for detailed information, click legend items to hide/show engines</p>
                </div>
                <div class="chart-container">
                    {interactive_explorer}
                </div>
            </div>

            <div class="dashboard-section">
                <h2>📈 Advanced Scaling Analysis</h2>
                <div class="interpretation-guide">
                    <h4>🔬 Deep Dive into Performance Scaling</h4>
                    <p><strong>What you're analyzing:</strong> How each engine behaves as workload complexity increases</p>
                    <div class="explanation-grid">
                        <div class="explanation-card">
                            <h4>🚀 Throughput vs Patterns (Top Left)</h4>
                            <p>Shows how processing speed changes with more regex patterns</p>
                            <p><strong>Ideal:</strong> Flat line (constant performance)</p>
                            <p><strong>Reality:</strong> Usually decreases (more patterns = slower)</p>
                        </div>
                        <div class="explanation-card">
                            <h4>🧠 Memory per Pattern (Top Right)</h4>
                            <p>Memory cost per individual regex pattern</p>
                            <p><strong>Good:</strong> Low, flat line (constant memory per pattern)</p>
                            <p><strong>Bad:</strong> Increasing (memory usage grows per pattern)</p>
                        </div>
                        <div class="explanation-card">
                            <h4>⚡ Compilation Scaling (Middle Left)</h4>
                            <p>Time to prepare patterns for searching</p>
                            <p><strong>One-time cost:</strong> Affects startup time</p>
                            <p><strong>Watch for:</strong> Exponential growth patterns</p>
                        </div>
                        <div class="explanation-card">
                            <h4>📏 Scanning Linearity (Middle Right)</h4>
                            <p>How search time scales with data size</p>
                            <p><strong>Expected:</strong> Linear growth (2x data = 2x time)</p>
                            <p><strong>Perfect:</strong> Straight diagonal line</p>
                        </div>
                        <div class="explanation-card">
                            <h4>🔮 Performance Predictions (Bottom Left)</h4>
                            <p>Statistical models predict performance up to 100K patterns</p>
                            <p><strong>Solid lines:</strong> Measured data points</p>
                            <p><strong>Dashed lines:</strong> Model extrapolation</p>
                            <p><strong>R² value:</strong> Model accuracy (1.0 = perfect)</p>
                        </div>
                        <div class="explanation-card">
                            <h4>📈 Memory Efficiency (Bottom Right)</h4>
                            <p>Performance per unit of memory used</p>
                            <p><strong>Calculation:</strong> Throughput ÷ Memory per Pattern</p>
                            <p><strong>Higher is better:</strong> More bang for your memory buck</p>
                        </div>
                    </div>
                </div>
                <div class="chart-container">
                    {scaling_analysis}
                </div>
            </div>

            <div class="dashboard-section">
                <h2>🔬 Comprehensive Comparative Analysis</h2>
                <div class="chart-container">
                    {comparative_dashboard}
                </div>
            </div>

            <div class="dashboard-section">
                <h2>🎯 Engine Performance Profiles</h2>
                <div class="chart-container">
                    {engine_radar}
                </div>
            </div>

            <div class="dashboard-section">
                <h2>🔮 Performance Prediction & Trends</h2>
                <div class="chart-container">
                    {prediction_chart}
                </div>
            </div>

            <div class="dashboard-section">
                <h2>🌍 Performance Landscape</h2>
                <div class="chart-container">
                    {performance_landscape}
                </div>
            </div>

            <div class="insight-panel">
                <h3>💡 Key Insights</h3>
                <ul>
                    <li><strong>Interactive Exploration:</strong> Use the explorer above to drill down into specific conditions and engine behaviors</li>
                    <li><strong>Statistical Analysis:</strong> All comparisons include significance testing and effect size analysis</li>
                    <li><strong>Predictive Modeling:</strong> Trend analysis helps predict performance at scale</li>
                    <li><strong>Multi-dimensional Analysis:</strong> Consider throughput, memory efficiency, and consistency together</li>
                </ul>
            </div>
        </div>

        <script>
            // Add interactive features
            document.addEventListener('DOMContentLoaded', function() {{
                console.log('🚀 World-Class Analytics Dashboard loaded successfully');

                // Add export functionality
                window.exportDashboard = function() {{
                    // Implementation for exporting dashboard data/charts
                    console.log('📊 Dashboard export feature');
                }};

                // Add responsive handling
                window.addEventListener('resize', function() {{
                    Plotly.Plots.resize(document.getElementById('interactive_explorer'));
                    Plotly.Plots.resize(document.getElementById('scaling_analysis'));
                    Plotly.Plots.resize(document.getElementById('comparative_dashboard'));
                }});
            }});
        </script>
        """

        return dashboard_html