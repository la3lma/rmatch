#!/usr/bin/env python3
"""
Comprehensive Performance Analysis Generator for Regex Benchmark Framework
Creates interactive performance analysis with memory usage comparison.
"""

import sys
import json
from pathlib import Path
import argparse

# Add the current directory to Python path for imports
sys.path.append(str(Path(__file__).parent))

# Import directly to avoid full package import and psutil dependency issues
from regex_bench.analysis.performance_analyzer import PerformanceAnalyzer
from regex_bench.analysis.chart_generator import ChartGenerator


def generate_comprehensive_analysis(db_path: Path, output_dir: Path = None):
    """Generate comprehensive performance analysis with interactive charts."""

    if output_dir is None:
        output_dir = Path("reports") / "performance_analysis"

    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"🔍 Analyzing performance data from: {db_path}")
    print(f"📁 Output directory: {output_dir}")

    # Initialize analyzer and chart generator
    analyzer = PerformanceAnalyzer(db_path)
    chart_generator = ChartGenerator(analyzer)

    print("\n📊 Generating performance summary...")
    performance_summary = analyzer.get_performance_summary()

    print("\n🧠 Analyzing memory usage...")
    memory_analysis = analyzer.analyze_memory_usage()
    memory_summary = analyzer.get_memory_comparison_summary()

    print("\n📈 Generating all interactive charts...")
    all_charts = chart_generator.generate_all_performance_charts()

    # Save raw data
    print("\n💾 Saving analysis data...")

    # Save performance summary
    with open(output_dir / "performance_summary.json", 'w') as f:
        json.dump(performance_summary, f, indent=2, default=str)

    # Save memory analysis
    with open(output_dir / "memory_analysis.json", 'w') as f:
        json.dump(memory_analysis, f, indent=2, default=str)

    with open(output_dir / "memory_summary.json", 'w') as f:
        json.dump(memory_summary, f, indent=2, default=str)

    # Save chart configurations
    chart_generator.save_charts_as_json(all_charts, output_dir / "charts_config.json")

    # Generate HTML report
    print("\n🌐 Generating interactive HTML report...")
    generate_html_report(performance_summary, memory_summary, all_charts, output_dir)

    print(f"\n✅ Analysis complete! Report generated at: {output_dir / 'performance_report.html'}")
    print(f"🔗 Open in browser: file://{(output_dir / 'performance_report.html').resolve()}")

    return output_dir


def generate_html_report(performance_summary, memory_summary, charts_config, output_dir):
    """Generate the final HTML report with all charts and analysis."""

    # Read the template
    template_path = Path(__file__).parent / "regex_bench" / "analysis" / "performance_report_template.html"

    with open(template_path, 'r') as f:
        template = f.read()

    # Create summary data for JavaScript
    summary_data = {
        'total_completed_jobs': performance_summary.get('total_completed_jobs', 0),
        'engines_tested': performance_summary.get('engines_tested', []),
        'pattern_counts_tested': performance_summary.get('pattern_counts_tested', []),
        'corpus_sizes_tested': performance_summary.get('corpus_sizes_tested', []),
        'memory_overview': memory_summary.get('engine_memory_overview', {}),
        'memory_winners': memory_summary.get('memory_winners', {})
    }

    # Add memory insights to the template
    memory_insights_html = generate_memory_insights_html(memory_summary)

    # Replace placeholders in template
    html_content = template.replace(
        '{CHART_DATA}', json.dumps(charts_config, indent=2)
    ).replace(
        '<script>',
        f'<script>\nconst summaryData = {json.dumps(summary_data, indent=2)};'
    )

    # Add memory analysis sections
    memory_sections_html = generate_memory_sections_html(memory_summary)

    # Insert memory sections before the closing body tag
    html_content = html_content.replace(
        '</div>\n\n    <script>',
        f'{memory_sections_html}\n    </div>\n\n    <script>'
    )

    # Write the final HTML
    with open(output_dir / 'performance_report.html', 'w') as f:
        f.write(html_content)


def generate_memory_insights_html(memory_summary):
    """Generate HTML for memory usage insights."""
    if not memory_summary.get('engine_memory_overview'):
        return ""

    insights = []

    # Find most memory-efficient engine
    memory_overview = memory_summary['engine_memory_overview']
    if memory_overview:
        min_memory_engine = min(memory_overview.keys(),
                              key=lambda e: memory_overview[e]['avg_memory_mb'])
        max_memory_engine = max(memory_overview.keys(),
                              key=lambda e: memory_overview[e]['avg_memory_mb'])

        min_mem = memory_overview[min_memory_engine]['avg_memory_mb']
        max_mem = memory_overview[max_memory_engine]['avg_memory_mb']

        insights.append(f"{min_memory_engine} is most memory-efficient (avg: {min_mem:.1f} MB)")
        insights.append(f"{max_memory_engine} uses most memory (avg: {max_mem:.1f} MB)")
        insights.append(f"Memory usage varies by {max_mem/min_mem:.1f}x between engines")

    return insights


def generate_memory_sections_html(memory_summary):
    """Generate HTML sections for memory analysis."""
    return f'''
        <!-- Memory Usage Analysis -->
        <div class="section">
            <h2>🧠 Memory Usage Analysis</h2>
            <div class="insight-box">
                <h3>Memory Usage Insights</h3>
                <ul class="insight-list">
                    <li>Compilation memory varies significantly with pattern complexity</li>
                    <li>Memory efficiency is crucial for large-scale pattern matching</li>
                    <li>Trade-offs exist between memory usage and performance</li>
                </ul>
            </div>

            <div class="chart-tabs">
                <button class="tab-button active" onclick="showTab('memory-patterns', '1MB')">1MB Corpus</button>
                <button class="tab-button" onclick="showTab('memory-patterns', '10MB')">10MB Corpus</button>
                <button class="tab-button" onclick="showTab('memory-patterns', '100MB')">100MB Corpus</button>
                <button class="tab-button" onclick="showTab('memory-patterns', '1GB')">1GB Corpus</button>
            </div>

            <div id="memory-patterns-1MB" class="tab-content active">
                <div id="chart_memory_vs_patterns_1MB" class="chart-container"></div>
            </div>
            <div id="memory-patterns-10MB" class="tab-content">
                <div id="chart_memory_vs_patterns_10MB" class="chart-container"></div>
            </div>
            <div id="memory-patterns-100MB" class="tab-content">
                <div id="chart_memory_vs_patterns_100MB" class="chart-container"></div>
            </div>
            <div id="memory-patterns-1GB" class="tab-content">
                <div id="chart_memory_vs_patterns_1GB" class="chart-container"></div>
            </div>
        </div>

        <!-- Memory Efficiency -->
        <div class="section">
            <h2>⚡ Memory Efficiency Comparison</h2>
            <div class="insight-box">
                <h3>Efficiency Metrics</h3>
                <ul class="insight-list">
                    <li>Memory per pattern shows algorithmic efficiency</li>
                    <li>Lower memory usage enables larger pattern sets</li>
                    <li>Memory heatmaps reveal scaling patterns</li>
                </ul>
            </div>

            <div id="chart_memory_efficiency_per_pattern" class="chart-container"></div>

            <div class="chart-grid">
                <div id="chart_memory_heatmap_java-native-unfair" class="chart-container"></div>
                <div id="chart_memory_heatmap_re2j" class="chart-container"></div>
                <div id="chart_memory_heatmap_rmatch" class="chart-container"></div>
            </div>
        </div>

        <!-- Memory vs Performance Trade-offs -->
        <div class="section">
            <h2>⚖️ Memory vs Performance Trade-offs</h2>
            <div class="insight-box">
                <h3>Trade-off Analysis</h3>
                <ul class="insight-list">
                    <li>Higher memory usage may enable better performance</li>
                    <li>Optimal balance depends on system constraints</li>
                    <li>Different engines show different trade-off patterns</li>
                </ul>
            </div>

            <div id="chart_memory_vs_performance" class="chart-container"></div>
        </div>
    '''


def main():
    """Main entry point for the performance analysis generator."""
    parser = argparse.ArgumentParser(
        description="Generate comprehensive performance analysis for regex benchmarks"
    )
    parser.add_argument(
        "db_path",
        type=Path,
        help="Path to the benchmark database file"
    )
    parser.add_argument(
        "--output-dir", "-o",
        type=Path,
        default=None,
        help="Output directory for analysis reports (default: reports/performance_analysis)"
    )

    args = parser.parse_args()

    if not args.db_path.exists():
        print(f"❌ Error: Database file not found: {args.db_path}")
        sys.exit(1)

    try:
        output_dir = generate_comprehensive_analysis(args.db_path, args.output_dir)

        print(f"\n📋 Analysis Summary:")

        # Load and display key insights
        with open(output_dir / "performance_summary.json") as f:
            summary = json.load(f)

        print(f"   • Total completed jobs: {summary.get('total_completed_jobs', 0)}")
        print(f"   • Engines tested: {', '.join(summary.get('engines_tested', []))}")
        print(f"   • Pattern complexities: {summary.get('pattern_counts_tested', [])}")
        print(f"   • Corpus sizes: {summary.get('corpus_sizes_tested', [])}")

        if output_dir.joinpath("memory_summary.json").exists():
            with open(output_dir / "memory_summary.json") as f:
                memory_summary = json.load(f)

            memory_overview = memory_summary.get('engine_memory_overview', {})
            if memory_overview:
                print(f"\n🧠 Memory Usage Overview:")
                for engine, stats in memory_overview.items():
                    print(f"   • {engine}: avg {stats['avg_memory_mb']:.1f} MB, "
                          f"max {stats['max_memory_mb']:.1f} MB")

    except Exception as e:
        print(f"❌ Error generating analysis: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()