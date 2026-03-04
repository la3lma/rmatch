"""
Performance Analysis Module for Regex Benchmark Framework

This module provides comprehensive performance analysis capabilities including:
- Performance metrics aggregation and analysis
- Memory usage analysis and comparison
- Interactive chart generation using Plotly.js
- Comprehensive HTML report generation

Main Components:
- PerformanceAnalyzer: Core analysis engine for benchmark data
- ChartGenerator: Interactive visualization generator
- HTML templates: Professional report layouts with interactive charts

Usage:
    from regex_bench.analysis import PerformanceAnalyzer, ChartGenerator

    analyzer = PerformanceAnalyzer(db_path)
    chart_gen = ChartGenerator(analyzer)
    charts = chart_gen.generate_all_performance_charts()
"""

from .performance_analyzer import PerformanceAnalyzer, PerformanceMetrics
from .chart_generator import ChartGenerator

__all__ = ['PerformanceAnalyzer', 'PerformanceMetrics', 'ChartGenerator']