#!/usr/bin/env python3
"""
Standalone Performance Analysis Generator for Regex Benchmark Framework
Creates interactive performance analysis with memory usage comparison.
Avoids psutil dependency issues by importing only necessary modules.
"""

import sys
import json
import sqlite3
import numpy as np
import pandas as pd
from pathlib import Path
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple, Union
import argparse


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


class StandaloneAnalyzer:
    """Standalone analyzer that doesn't depend on full regex_bench package."""

    def __init__(self, db_path: Path):
        self.db_path = db_path
        self.conn = sqlite3.connect(str(db_path))

    def __del__(self):
        if hasattr(self, 'conn'):
            self.conn.close()

    def _parse_corpus_size(self, size_str: str) -> int:
        """Parse corpus size string to bytes."""
        size_map = {
            '1MB': 1024**2,
            '10MB': 10 * 1024**2,
            '100MB': 100 * 1024**2,
            '1GB': 1024**3
        }
        return size_map.get(size_str, 0)

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

    def get_analysis_summary(self):
        """Generate analysis summary."""
        metrics = self.get_completed_results()

        engines = list(set(m.engine for m in metrics))
        pattern_counts = sorted(list(set(m.pattern_count for m in metrics)))
        corpus_sizes = sorted(list(set(m.corpus_size for m in metrics)),
                            key=lambda x: self._parse_corpus_size(x))

        summary = {
            'total_completed_jobs': len(metrics),
            'engines_tested': engines,
            'pattern_counts_tested': pattern_counts,
            'corpus_sizes_tested': corpus_sizes,
            'engine_performance': {},
            'memory_overview': {}
        }

        # Engine-level performance and memory summary
        for engine in engines:
            engine_metrics = [m for m in metrics if m.engine == engine]
            if engine_metrics:
                throughputs = [m.throughput_mb_per_sec for m in engine_metrics]
                memory_values = [m.memory_compilation_bytes for m in engine_metrics
                               if m.memory_compilation_bytes is not None]

                summary['engine_performance'][engine] = {
                    'completed_jobs': len(engine_metrics),
                    'avg_throughput_mb_per_sec': float(np.mean(throughputs)),
                    'max_throughput_mb_per_sec': float(np.max(throughputs))
                }

                if memory_values:
                    memory_mb = [m / (1024 * 1024) for m in memory_values]
                    summary['memory_overview'][engine] = {
                        'avg_memory_mb': float(np.mean(memory_mb)),
                        'max_memory_mb': float(np.max(memory_mb)),
                        'min_memory_mb': float(np.min(memory_mb))
                    }

        return summary


def generate_simple_analysis(db_path: Path, output_dir: Path = None):
    """Generate a simple performance analysis without full dependencies."""

    if output_dir is None:
        output_dir = Path("reports") / "simple_analysis"

    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"🔍 Analyzing performance data from: {db_path}")
    print(f"📁 Output directory: {output_dir}")

    analyzer = StandaloneAnalyzer(db_path)

    print("\n📊 Generating performance summary...")
    summary = analyzer.get_analysis_summary()

    print("\n💾 Saving analysis data...")
    with open(output_dir / "analysis_summary.json", 'w') as f:
        json.dump(summary, f, indent=2, default=str)

    # Generate simple HTML report
    generate_simple_html(summary, output_dir)

    print(f"\n✅ Analysis complete! Report generated at: {output_dir / 'simple_report.html'}")
    print(f"🔗 Open in browser: file://{(output_dir / 'simple_report.html').resolve()}")

    return output_dir


def generate_simple_html(summary, output_dir):
    """Generate a simple HTML report without complex charts."""

    html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Regex Engine Performance Summary</title>
    <style>
        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            color: #333;
            background: #f8f9fa;
            margin: 0;
            padding: 20px;
            max-width: 1200px;
            margin: 0 auto;
        }}
        .header {{
            text-align: center;
            margin-bottom: 40px;
            padding: 30px;
            background: white;
            border-radius: 12px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }}
        .summary-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 40px;
        }}
        .summary-card {{
            background: white;
            padding: 25px;
            border-radius: 12px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            text-align: center;
        }}
        .summary-card h3 {{
            margin: 0 0 10px 0;
            color: #2c3e50;
        }}
        .summary-card .value {{
            font-size: 2em;
            font-weight: bold;
            color: #3498db;
            margin: 10px 0;
        }}
        .section {{
            background: white;
            margin: 30px 0;
            padding: 30px;
            border-radius: 12px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }}
        .engine-card {{
            background: #f8f9fa;
            margin: 20px 0;
            padding: 20px;
            border-radius: 8px;
            border-left: 4px solid #3498db;
        }}
        .engine-name {{
            font-size: 1.3em;
            font-weight: bold;
            color: #2c3e50;
            margin-bottom: 15px;
        }}
        .metric-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
        }}
        .metric {{
            display: flex;
            justify-content: space-between;
            padding: 8px 0;
            border-bottom: 1px solid #ecf0f1;
        }}
        .metric-label {{
            color: #666;
        }}
        .metric-value {{
            font-weight: bold;
            color: #2c3e50;
        }}
    </style>
</head>
<body>
    <div class="header">
        <h1>🚀 Regex Engine Performance Summary</h1>
        <p>Analysis of {summary['total_completed_jobs']} completed benchmark jobs</p>
    </div>

    <div class="summary-grid">
        <div class="summary-card">
            <h3>Total Jobs</h3>
            <div class="value">{summary['total_completed_jobs']}</div>
        </div>
        <div class="summary-card">
            <h3>Engines Tested</h3>
            <div class="value">{len(summary['engines_tested'])}</div>
        </div>
        <div class="summary-card">
            <h3>Pattern Complexities</h3>
            <div class="value">{len(summary['pattern_counts_tested'])}</div>
        </div>
        <div class="summary-card">
            <h3>Corpus Sizes</h3>
            <div class="value">{len(summary['corpus_sizes_tested'])}</div>
        </div>
    </div>

    <div class="section">
        <h2>📈 Engine Performance Comparison</h2>"""

    # Add engine performance cards
    for engine, perf in summary['engine_performance'].items():
        memory_info = summary['memory_overview'].get(engine, {})

        html_content += f"""
        <div class="engine-card">
            <div class="engine-name">{engine}</div>
            <div class="metric-grid">
                <div class="metric">
                    <span class="metric-label">Completed Jobs</span>
                    <span class="metric-value">{perf['completed_jobs']}</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Avg Throughput</span>
                    <span class="metric-value">{perf['avg_throughput_mb_per_sec']:.1f} MB/s</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Max Throughput</span>
                    <span class="metric-value">{perf['max_throughput_mb_per_sec']:.1f} MB/s</span>
                </div>"""

        if memory_info:
            html_content += f"""
                <div class="metric">
                    <span class="metric-label">Avg Memory</span>
                    <span class="metric-value">{memory_info['avg_memory_mb']:.1f} MB</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Max Memory</span>
                    <span class="metric-value">{memory_info['max_memory_mb']:.1f} MB</span>
                </div>
                <div class="metric">
                    <span class="metric-label">Min Memory</span>
                    <span class="metric-value">{memory_info['min_memory_mb']:.1f} MB</span>
                </div>"""

        html_content += """
            </div>
        </div>"""

    html_content += """
    </div>

    <div class="section">
        <h2>🔧 Test Configuration</h2>
        <p><strong>Pattern Counts:</strong> """ + ", ".join(map(str, summary['pattern_counts_tested'])) + """</p>
        <p><strong>Corpus Sizes:</strong> """ + ", ".join(summary['corpus_sizes_tested']) + """</p>
        <p><strong>Engines:</strong> """ + ", ".join(summary['engines_tested']) + """</p>
    </div>

    <footer style="text-align: center; margin-top: 40px; color: #666;">
        <p>Generated on """ + str(pd.Timestamp.now().strftime("%Y-%m-%d %H:%M:%S")) + """</p>
        <p>For full interactive analysis, use: <code>make analysis</code></p>
    </footer>

</body>
</html>"""

    with open(output_dir / 'simple_report.html', 'w') as f:
        f.write(html_content)


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Generate standalone performance analysis for regex benchmarks"
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
        help="Output directory for analysis reports"
    )

    args = parser.parse_args()

    if not args.db_path.exists():
        print(f"❌ Error: Database file not found: {args.db_path}")
        sys.exit(1)

    try:
        output_dir = generate_simple_analysis(args.db_path, args.output_dir)

        # Load and display key insights
        with open(output_dir / "analysis_summary.json") as f:
            summary = json.load(f)

        print(f"\n📋 Analysis Summary:")
        print(f"   • Total completed jobs: {summary.get('total_completed_jobs', 0)}")
        print(f"   • Engines tested: {', '.join(summary.get('engines_tested', []))}")
        print(f"   • Pattern complexities: {summary.get('pattern_counts_tested', [])}")
        print(f"   • Corpus sizes: {summary.get('corpus_sizes_tested', [])}")

        memory_overview = summary.get('memory_overview', {})
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