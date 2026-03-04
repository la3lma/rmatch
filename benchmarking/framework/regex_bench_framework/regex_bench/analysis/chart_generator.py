#!/usr/bin/env python3
"""
Interactive Chart Generator for Regex Benchmark Performance Analysis
Creates Plotly.js-based interactive visualizations for performance data.
"""

import json
from pathlib import Path
from typing import Dict, List, Optional, Any
from dataclasses import asdict
import numpy as np
from .performance_analyzer import PerformanceAnalyzer, PerformanceMetrics


class ChartGenerator:
    """Generates interactive performance charts using Plotly.js."""

    def __init__(self, analyzer: PerformanceAnalyzer):
        self.analyzer = analyzer
        self.colors = {
            'java-native-unfair': '#e74c3c',  # Red
            're2j': '#3498db',                # Blue
            'rmatch': '#2ecc71',              # Green
        }

    def generate_throughput_vs_patterns_chart(self) -> Dict[str, Any]:
        """Generate throughput vs pattern complexity chart for each corpus size."""
        scaling_data = self.analyzer.analyze_scaling_behavior()

        charts = {}

        for corpus_size in ['1MB', '10MB', '100MB', '1GB']:
            traces = []

            for engine in scaling_data:
                if corpus_size in scaling_data[engine]['pattern_scaling']:
                    data_points = scaling_data[engine]['pattern_scaling'][corpus_size]

                    if data_points:
                        x_vals = [p['pattern_count'] for p in data_points]
                        y_vals = [p['throughput'] for p in data_points]

                        trace = {
                            'x': x_vals,
                            'y': y_vals,
                            'type': 'scatter',
                            'mode': 'lines+markers',
                            'name': engine,
                            'line': {'color': self.colors.get(engine, '#000000'), 'width': 3},
                            'marker': {'size': 8},
                            'hovertemplate': f'<b>{engine}</b><br>' +
                                           'Patterns: %{x}<br>' +
                                           'Throughput: %{y:.2f} MB/s<br>' +
                                           '<extra></extra>'
                        }
                        traces.append(trace)

            chart_config = {
                'data': traces,
                'layout': {
                    'title': {
                        'text': f'Throughput vs Pattern Complexity ({corpus_size} Corpus)',
                        'font': {'size': 16}
                    },
                    'xaxis': {
                        'title': 'Number of Patterns',
                        'type': 'log',
                        'tickvals': [10, 100, 1000, 10000],
                        'ticktext': ['10', '100', '1K', '10K']
                    },
                    'yaxis': {
                        'title': 'Throughput (MB/s)'
                    },
                    'hovermode': 'closest',
                    'showlegend': True,
                    'width': 600,
                    'height': 400
                },
                'config': {
                    'displayModeBar': True,
                    'toImageButtonOptions': {
                        'format': 'png',
                        'filename': f'throughput_vs_patterns_{corpus_size}',
                        'height': 400,
                        'width': 600,
                        'scale': 2
                    }
                }
            }
            charts[f'throughput_vs_patterns_{corpus_size}'] = chart_config

        return charts

    def generate_throughput_vs_corpus_chart(self) -> Dict[str, Any]:
        """Generate throughput vs corpus size chart for each pattern complexity."""
        scaling_data = self.analyzer.analyze_scaling_behavior()

        charts = {}

        for pattern_count in [10, 100, 1000, 10000]:
            traces = []

            for engine in scaling_data:
                if pattern_count in scaling_data[engine]['corpus_scaling']:
                    data_points = scaling_data[engine]['corpus_scaling'][pattern_count]

                    if data_points:
                        # Convert corpus sizes to numeric for proper ordering
                        x_vals = [p['corpus_bytes'] / (1024**2) for p in data_points]  # Convert to MB
                        y_vals = [p['throughput'] for p in data_points]
                        x_labels = [p['corpus_size'] for p in data_points]

                        trace = {
                            'x': x_vals,
                            'y': y_vals,
                            'type': 'scatter',
                            'mode': 'lines+markers',
                            'name': engine,
                            'line': {'color': self.colors.get(engine, '#000000'), 'width': 3},
                            'marker': {'size': 8},
                            'text': x_labels,
                            'hovertemplate': f'<b>{engine}</b><br>' +
                                           'Corpus Size: %{text}<br>' +
                                           'Throughput: %{y:.2f} MB/s<br>' +
                                           '<extra></extra>'
                        }
                        traces.append(trace)

            chart_config = {
                'data': traces,
                'layout': {
                    'title': {
                        'text': f'Throughput vs Corpus Size ({pattern_count} Patterns)',
                        'font': {'size': 16}
                    },
                    'xaxis': {
                        'title': 'Corpus Size (MB)',
                        'type': 'log',
                        'tickvals': [1, 10, 100, 1024],
                        'ticktext': ['1MB', '10MB', '100MB', '1GB']
                    },
                    'yaxis': {
                        'title': 'Throughput (MB/s)'
                    },
                    'hovermode': 'closest',
                    'showlegend': True,
                    'width': 600,
                    'height': 400
                }
            }
            charts[f'throughput_vs_corpus_{pattern_count}'] = chart_config

        return charts

    def generate_engine_comparison_heatmap(self) -> Dict[str, Any]:
        """Generate heatmap comparing engines across all configurations."""
        comparison_data = self.analyzer.get_engine_comparison_data()

        # Prepare data for heatmap
        engines = comparison_data['engines']
        pattern_counts = comparison_data['pattern_counts']
        corpus_sizes = comparison_data['corpus_sizes']

        charts = {}

        # Throughput heatmap
        for engine in engines:
            z_data = []
            y_labels = []
            x_labels = [str(pc) for pc in pattern_counts]

            for corpus_size in reversed(corpus_sizes):  # Reverse for better visualization
                row = []
                for pattern_count in pattern_counts:
                    config_key = f"{pattern_count}_{corpus_size}"
                    if (config_key in comparison_data['configurations'] and
                        engine in comparison_data['configurations'][config_key]):
                        throughput = comparison_data['configurations'][config_key][engine]['throughput']
                        row.append(throughput)
                    else:
                        row.append(None)  # No data
                z_data.append(row)
                y_labels.append(corpus_size)

            trace = {
                'z': z_data,
                'x': x_labels,
                'y': y_labels,
                'type': 'heatmap',
                'colorscale': 'Viridis',
                'hoverongaps': False,
                'hovertemplate': f'<b>{engine}</b><br>' +
                               'Patterns: %{x}<br>' +
                               'Corpus: %{y}<br>' +
                               'Throughput: %{z:.2f} MB/s<br>' +
                               '<extra></extra>'
            }

            chart_config = {
                'data': [trace],
                'layout': {
                    'title': {
                        'text': f'{engine} - Throughput Heatmap (MB/s)',
                        'font': {'size': 16}
                    },
                    'xaxis': {'title': 'Number of Patterns'},
                    'yaxis': {'title': 'Corpus Size'},
                    'width': 500,
                    'height': 400
                }
            }
            charts[f'heatmap_throughput_{engine}'] = chart_config

        return charts

    def generate_patterns_per_second_chart(self) -> Dict[str, Any]:
        """Generate patterns per second comparison chart."""
        comparison_data = self.analyzer.get_engine_comparison_data()

        charts = {}

        # Group by corpus size for patterns/sec analysis
        for corpus_size in comparison_data['corpus_sizes']:
            traces = []

            for engine in comparison_data['engines']:
                x_vals = []
                y_vals = []

                for pattern_count in comparison_data['pattern_counts']:
                    config_key = f"{pattern_count}_{corpus_size}"
                    if (config_key in comparison_data['configurations'] and
                        engine in comparison_data['configurations'][config_key]):

                        config = comparison_data['configurations'][config_key][engine]
                        x_vals.append(pattern_count)
                        y_vals.append(config['patterns_per_sec'])

                if x_vals and y_vals:
                    trace = {
                        'x': x_vals,
                        'y': y_vals,
                        'type': 'scatter',
                        'mode': 'lines+markers',
                        'name': engine,
                        'line': {'color': self.colors.get(engine, '#000000'), 'width': 3},
                        'marker': {'size': 8},
                        'hovertemplate': f'<b>{engine}</b><br>' +
                                       'Patterns: %{x}<br>' +
                                       'Patterns/sec: %{y:.0f}<br>' +
                                       '<extra></extra>'
                    }
                    traces.append(trace)

            chart_config = {
                'data': traces,
                'layout': {
                    'title': {
                        'text': f'Pattern Processing Rate ({corpus_size} Corpus)',
                        'font': {'size': 16}
                    },
                    'xaxis': {
                        'title': 'Number of Patterns',
                        'type': 'log',
                        'tickvals': [10, 100, 1000, 10000],
                        'ticktext': ['10', '100', '1K', '10K']
                    },
                    'yaxis': {
                        'title': 'Patterns Processed per Second',
                        'type': 'log'
                    },
                    'hovermode': 'closest',
                    'showlegend': True,
                    'width': 600,
                    'height': 400
                }
            }
            charts[f'patterns_per_sec_{corpus_size}'] = chart_config

        return charts

    def generate_compilation_vs_scanning_chart(self) -> Dict[str, Any]:
        """Generate chart showing compilation vs scanning time breakdown."""
        comparison_data = self.analyzer.get_engine_comparison_data()

        chart_data = []

        for engine in comparison_data['engines']:
            for pattern_count in comparison_data['pattern_counts']:
                for corpus_size in comparison_data['corpus_sizes']:
                    config_key = f"{pattern_count}_{corpus_size}"
                    if (config_key in comparison_data['configurations'] and
                        engine in comparison_data['configurations'][config_key]):

                        config = comparison_data['configurations'][config_key][engine]
                        comp_ms = config['compilation_ns'] / 1e6  # Convert to milliseconds
                        scan_ms = config['scanning_ns'] / 1e6

                        chart_data.append({
                            'engine': engine,
                            'pattern_count': pattern_count,
                            'corpus_size': corpus_size,
                            'compilation_ms': comp_ms,
                            'scanning_ms': scan_ms,
                            'config_label': f"{pattern_count}p_{corpus_size}"
                        })

        # Create stacked bar chart
        engines = comparison_data['engines']
        traces = []

        for engine in engines:
            engine_data = [d for d in chart_data if d['engine'] == engine]

            compilation_trace = {
                'x': [d['config_label'] for d in engine_data],
                'y': [d['compilation_ms'] for d in engine_data],
                'name': f'{engine} - Compilation',
                'type': 'bar',
                'marker': {'color': self.colors.get(engine, '#000000'), 'opacity': 0.7},
                'hovertemplate': f'<b>{engine} - Compilation</b><br>' +
                               'Config: %{x}<br>' +
                               'Time: %{y:.2f} ms<br>' +
                               '<extra></extra>'
            }

            scanning_trace = {
                'x': [d['config_label'] for d in engine_data],
                'y': [d['scanning_ms'] for d in engine_data],
                'name': f'{engine} - Scanning',
                'type': 'bar',
                'marker': {'color': self.colors.get(engine, '#000000'), 'opacity': 1.0},
                'hovertemplate': f'<b>{engine} - Scanning</b><br>' +
                               'Config: %{x}<br>' +
                               'Time: %{y:.2f} ms<br>' +
                               '<extra></extra>'
            }

            traces.extend([compilation_trace, scanning_trace])

        chart_config = {
            'data': traces,
            'layout': {
                'title': {
                    'text': 'Compilation vs Scanning Time Breakdown',
                    'font': {'size': 16}
                },
                'xaxis': {
                    'title': 'Configuration (patterns_corpus_size)',
                    'tickangle': 45
                },
                'yaxis': {
                    'title': 'Time (milliseconds)',
                    'type': 'log'
                },
                'barmode': 'group',
                'hovermode': 'closest',
                'showlegend': True,
                'width': 1000,
                'height': 500
            }
        }

        return {'compilation_vs_scanning': chart_config}

    def generate_memory_usage_charts(self) -> Dict[str, Any]:
        """Generate memory usage analysis charts."""
        memory_analysis = self.analyzer.analyze_memory_usage()
        charts = {}

        # Memory vs Pattern Complexity Charts
        for corpus_size in ['1MB', '10MB', '100MB', '1GB']:
            traces = []

            for engine in memory_analysis['memory_scaling']:
                if corpus_size in memory_analysis['memory_scaling'][engine]['pattern_scaling']:
                    data_points = memory_analysis['memory_scaling'][engine]['pattern_scaling'][corpus_size]

                    if data_points:
                        x_vals = [p['pattern_count'] for p in data_points]
                        y_vals = [p['memory_mb'] for p in data_points]

                        trace = {
                            'x': x_vals,
                            'y': y_vals,
                            'type': 'scatter',
                            'mode': 'lines+markers',
                            'name': engine,
                            'line': {'color': self.colors.get(engine, '#000000'), 'width': 3},
                            'marker': {'size': 8},
                            'hovertemplate': f'<b>{engine}</b><br>' +
                                           'Patterns: %{x}<br>' +
                                           'Memory: %{y:.1f} MB<br>' +
                                           '<extra></extra>'
                        }
                        traces.append(trace)

            chart_config = {
                'data': traces,
                'layout': {
                    'title': {
                        'text': f'Compilation Memory vs Pattern Complexity ({corpus_size} Corpus)',
                        'font': {'size': 16}
                    },
                    'xaxis': {
                        'title': 'Number of Patterns',
                        'type': 'log',
                        'tickvals': [10, 100, 1000, 10000],
                        'ticktext': ['10', '100', '1K', '10K']
                    },
                    'yaxis': {
                        'title': 'Compilation Memory (MB)',
                        'type': 'log'
                    },
                    'hovermode': 'closest',
                    'showlegend': True,
                    'width': 600,
                    'height': 400
                },
                'config': {
                    'displayModeBar': True,
                    'toImageButtonOptions': {
                        'format': 'png',
                        'filename': f'memory_vs_patterns_{corpus_size}',
                        'height': 400,
                        'width': 600,
                        'scale': 2
                    }
                }
            }
            charts[f'memory_vs_patterns_{corpus_size}'] = chart_config

        return charts

    def generate_memory_efficiency_charts(self) -> Dict[str, Any]:
        """Generate memory efficiency comparison charts."""
        memory_analysis = self.analyzer.analyze_memory_usage()
        charts = {}

        # Memory per pattern chart
        chart_data = []
        for config_key, engines in memory_analysis['efficiency_ratios'].items():
            pattern_count, corpus_size = config_key.split('_', 1)
            for engine, metrics in engines.items():
                chart_data.append({
                    'engine': engine,
                    'config': f"{pattern_count}p_{corpus_size}",
                    'memory_per_pattern': metrics['memory_per_pattern_mb'],
                    'total_memory': metrics['total_memory_mb'],
                    'pattern_count': int(pattern_count),
                    'corpus_size': corpus_size
                })

        # Create grouped bar chart for memory per pattern
        engines = list(set(d['engine'] for d in chart_data))
        traces = []

        for engine in engines:
            engine_data = [d for d in chart_data if d['engine'] == engine]

            trace = {
                'x': [d['config'] for d in engine_data],
                'y': [d['memory_per_pattern'] for d in engine_data],
                'name': engine,
                'type': 'bar',
                'marker': {'color': self.colors.get(engine, '#000000')},
                'hovertemplate': f'<b>{engine}</b><br>' +
                               'Config: %{x}<br>' +
                               'Memory/Pattern: %{y:.2f} MB<br>' +
                               '<extra></extra>'
            }
            traces.append(trace)

        chart_config = {
            'data': traces,
            'layout': {
                'title': {
                    'text': 'Memory Efficiency: Memory per Pattern',
                    'font': {'size': 16}
                },
                'xaxis': {
                    'title': 'Configuration (patterns_corpus_size)',
                    'tickangle': 45
                },
                'yaxis': {
                    'title': 'Memory per Pattern (MB)',
                    'type': 'log'
                },
                'barmode': 'group',
                'hovermode': 'closest',
                'showlegend': True,
                'width': 1000,
                'height': 500
            }
        }
        charts['memory_efficiency_per_pattern'] = chart_config

        # Memory heatmap for each engine
        for engine in engines:
            engine_data = [d for d in chart_data if d['engine'] == engine]

            # Create matrix data
            pattern_counts = sorted(list(set(d['pattern_count'] for d in engine_data)))
            corpus_sizes = sorted(list(set(d['corpus_size'] for d in engine_data)),
                                key=lambda x: self.analyzer._parse_corpus_size(x))

            z_data = []
            y_labels = []
            x_labels = [str(pc) for pc in pattern_counts]

            for corpus_size in reversed(corpus_sizes):  # Reverse for better visualization
                row = []
                for pattern_count in pattern_counts:
                    matching = [d for d in engine_data
                              if d['pattern_count'] == pattern_count and d['corpus_size'] == corpus_size]
                    if matching:
                        row.append(matching[0]['total_memory'])
                    else:
                        row.append(None)
                z_data.append(row)
                y_labels.append(corpus_size)

            trace = {
                'z': z_data,
                'x': x_labels,
                'y': y_labels,
                'type': 'heatmap',
                'colorscale': 'Reds',
                'hoverongaps': False,
                'hovertemplate': f'<b>{engine}</b><br>' +
                               'Patterns: %{x}<br>' +
                               'Corpus: %{y}<br>' +
                               'Memory: %{z:.1f} MB<br>' +
                               '<extra></extra>'
            }

            chart_config = {
                'data': [trace],
                'layout': {
                    'title': {
                        'text': f'{engine} - Compilation Memory Usage (MB)',
                        'font': {'size': 16}
                    },
                    'xaxis': {'title': 'Number of Patterns'},
                    'yaxis': {'title': 'Corpus Size'},
                    'width': 500,
                    'height': 400
                }
            }
            charts[f'memory_heatmap_{engine}'] = chart_config

        return charts

    def generate_memory_vs_performance_charts(self) -> Dict[str, Any]:
        """Generate charts comparing memory usage vs performance."""
        memory_analysis = self.analyzer.analyze_memory_usage()
        charts = {}

        # Memory vs Throughput scatter plot
        chart_data = []
        for config_key, engines in memory_analysis['efficiency_ratios'].items():
            for engine, metrics in engines.items():
                chart_data.append({
                    'engine': engine,
                    'memory_mb': metrics['total_memory_mb'],
                    'throughput': metrics['throughput_mb_per_sec'],
                    'config': config_key
                })

        engines = list(set(d['engine'] for d in chart_data))
        traces = []

        for engine in engines:
            engine_data = [d for d in chart_data if d['engine'] == engine]

            trace = {
                'x': [d['memory_mb'] for d in engine_data],
                'y': [d['throughput'] for d in engine_data],
                'mode': 'markers',
                'type': 'scatter',
                'name': engine,
                'marker': {
                    'color': self.colors.get(engine, '#000000'),
                    'size': 10,
                    'opacity': 0.7
                },
                'text': [d['config'] for d in engine_data],
                'hovertemplate': f'<b>{engine}</b><br>' +
                               'Memory: %{x:.1f} MB<br>' +
                               'Throughput: %{y:.1f} MB/s<br>' +
                               'Config: %{text}<br>' +
                               '<extra></extra>'
            }
            traces.append(trace)

        chart_config = {
            'data': traces,
            'layout': {
                'title': {
                    'text': 'Memory Usage vs Performance Trade-off',
                    'font': {'size': 16}
                },
                'xaxis': {
                    'title': 'Compilation Memory Usage (MB)',
                    'type': 'log'
                },
                'yaxis': {
                    'title': 'Throughput (MB/s)',
                    'type': 'log'
                },
                'hovermode': 'closest',
                'showlegend': True,
                'width': 700,
                'height': 500
            }
        }
        charts['memory_vs_performance'] = chart_config

        return charts

    def generate_all_performance_charts(self) -> Dict[str, Any]:
        """Generate all performance analysis charts."""
        all_charts = {}

        print("Generating throughput vs patterns charts...")
        all_charts.update(self.generate_throughput_vs_patterns_chart())

        print("Generating throughput vs corpus charts...")
        all_charts.update(self.generate_throughput_vs_corpus_chart())

        print("Generating heatmap charts...")
        all_charts.update(self.generate_engine_comparison_heatmap())

        print("Generating patterns per second charts...")
        all_charts.update(self.generate_patterns_per_second_chart())

        print("Generating compilation vs scanning chart...")
        all_charts.update(self.generate_compilation_vs_scanning_chart())

        print("Generating memory usage charts...")
        all_charts.update(self.generate_memory_usage_charts())

        print("Generating memory efficiency charts...")
        all_charts.update(self.generate_memory_efficiency_charts())

        print("Generating memory vs performance charts...")
        all_charts.update(self.generate_memory_vs_performance_charts())

        return all_charts

    def save_charts_as_json(self, charts: Dict[str, Any], output_file: Path) -> None:
        """Save charts configuration as JSON for use in HTML."""
        with open(output_file, 'w') as f:
            json.dump(charts, f, indent=2)

        print(f"Charts saved to: {output_file}")