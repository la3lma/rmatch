"""
Multi-run report generator that creates comprehensive summaries of ALL benchmark runs.
Provides visual comparisons and trend analysis across different runs.
"""

import json
import sqlite3
from pathlib import Path
from typing import Dict, Any, List, Optional
from datetime import datetime
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import plotly.express as px
import pandas as pd

class MultiRunReportGenerator:
    """Generate comprehensive reports comparing multiple benchmark runs."""

    def __init__(self):
        self.runs_data = []
        self.engine_performance = {}

    def scan_results_directory(self, results_dir: Path) -> List[Dict[str, Any]]:
        """Scan results directory and collect data from all runs."""
        runs = []

        for run_dir in results_dir.glob("*_*"):
            if not run_dir.is_dir():
                continue

            # Check if this is a benchmark run directory
            jobs_db = run_dir / "jobs.db"
            metadata_file = run_dir / "metadata.json"

            if not jobs_db.exists():
                continue

            try:
                run_data = self._extract_run_data(run_dir, jobs_db, metadata_file)
                if run_data:
                    runs.append(run_data)
            except Exception as e:
                print(f"Warning: Failed to process {run_dir}: {e}")

        return sorted(runs, key=lambda x: x.get('started_at', ''))

    def _extract_run_data(self, run_dir: Path, jobs_db: Path, metadata_file: Path) -> Optional[Dict[str, Any]]:
        """Extract comprehensive data from a single benchmark run."""
        try:
            # Connect to database
            conn = sqlite3.connect(jobs_db)
            conn.row_factory = sqlite3.Row

            # Get run metadata
            run_meta = conn.execute("""
                SELECT run_id, config_path, status, created_at, started_at, completed_at,
                       created_by, duration_seconds
                FROM benchmark_runs
                ORDER BY created_at DESC LIMIT 1
            """).fetchone()

            if not run_meta:
                return None

            # Get job statistics
            job_stats = conn.execute("""
                SELECT
                    engine_name,
                    COUNT(*) as total_jobs,
                    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed,
                    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed,
                    SUM(CASE WHEN status = 'QUEUED' THEN 1 ELSE 0 END) as queued,
                    AVG(CASE WHEN status = 'COMPLETED' THEN total_ns/1000000.0 ELSE NULL END) as avg_time_ms,
                    AVG(CASE WHEN status = 'COMPLETED' THEN throughput_mbps ELSE NULL END) as avg_throughput
                FROM benchmark_jobs
                GROUP BY engine_name
            """).fetchall()

            # Get performance data
            performance_data = conn.execute("""
                SELECT
                    engine_name, pattern_count, input_size, iteration,
                    total_ns/1000000.0 as execution_time_ms,
                    compilation_ns/1000000.0 as compilation_time_ms,
                    throughput_mbps as scanning_throughput_mb_per_sec,
                    match_count, status
                FROM benchmark_jobs
                WHERE status = 'COMPLETED'
                ORDER BY engine_name, pattern_count, input_size, iteration
            """).fetchall()

            # Get test matrix info
            test_matrix = None
            if metadata_file.exists():
                try:
                    with open(metadata_file) as f:
                        metadata = json.load(f)
                        test_matrix = metadata.get('test_matrix')
                except:
                    pass

            conn.close()

            return {
                'run_dir': str(run_dir),
                'run_id': run_meta['run_id'],
                'config_path': run_meta['config_path'],
                'status': run_meta['status'],
                'created_at': run_meta['created_at'],
                'started_at': run_meta['started_at'],
                'completed_at': run_meta['completed_at'],
                'created_by': run_meta['created_by'],
                'duration_seconds': run_meta['duration_seconds'],
                'job_stats': [dict(row) for row in job_stats],
                'performance_data': [dict(row) for row in performance_data],
                'test_matrix': test_matrix,
                'run_name': run_dir.name
            }

        except Exception as e:
            print(f"Error extracting data from {run_dir}: {e}")
            return None

    def generate_comprehensive_report(self, results_dir: Path, output_dir: Path) -> Path:
        """Generate a comprehensive multi-run report with charts and analysis."""
        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)

        # Scan and collect all run data
        runs = self.scan_results_directory(results_dir)

        if not runs:
            raise ValueError(f"No valid benchmark runs found in {results_dir}")

        # Generate charts
        charts = self._generate_charts(runs, output_dir)

        # Generate HTML report
        html_file = output_dir / "comprehensive_report.html"
        self._generate_html_report(runs, charts, html_file)

        return html_file

    def _generate_charts(self, runs: List[Dict[str, Any]], output_dir: Path) -> Dict[str, str]:
        """Generate all charts for the comprehensive report."""
        charts = {}

        # 1. Runs Timeline Chart
        charts['timeline'] = self._create_runs_timeline_chart(runs, output_dir)

        # 2. Engine Performance Comparison
        charts['engine_performance'] = self._create_engine_performance_chart(runs, output_dir)

        # 3. Throughput Trends
        charts['throughput_trends'] = self._create_throughput_trends_chart(runs, output_dir)

        # 4. Job Success Rates
        charts['success_rates'] = self._create_success_rates_chart(runs, output_dir)

        # 5. Pattern Count vs Performance
        charts['pattern_scaling'] = self._create_pattern_scaling_chart(runs, output_dir)

        return charts

    def _create_runs_timeline_chart(self, runs: List[Dict[str, Any]], output_dir: Path) -> str:
        """Create a timeline showing all benchmark runs."""
        fig = go.Figure()

        for i, run in enumerate(runs):
            start_time = run.get('started_at') or run.get('created_at')
            end_time = run.get('completed_at')

            if start_time and end_time:
                fig.add_trace(go.Scatter(
                    x=[start_time, end_time],
                    y=[run['run_name'], run['run_name']],
                    mode='lines+markers',
                    name=run['run_name'],
                    line=dict(width=8),
                    showlegend=False
                ))

        fig.update_layout(
            title="Benchmark Runs Timeline",
            xaxis_title="Time",
            yaxis_title="Run",
            height=max(400, len(runs) * 30)
        )

        chart_file = output_dir / "runs_timeline.html"
        fig.write_html(str(chart_file))
        return str(chart_file.name)

    def _create_engine_performance_chart(self, runs: List[Dict[str, Any]], output_dir: Path) -> str:
        """Create engine performance comparison across runs."""
        # Aggregate performance data
        engine_data = {}

        for run in runs:
            for job_stat in run.get('job_stats', []):
                engine = job_stat['engine_name']
                if engine not in engine_data:
                    engine_data[engine] = {
                        'throughput': [],
                        'completion_rate': [],
                        'run_names': []
                    }

                if job_stat['avg_throughput']:
                    engine_data[engine]['throughput'].append(job_stat['avg_throughput'])
                    completion_rate = job_stat['completed'] / max(job_stat['total_jobs'], 1) * 100
                    engine_data[engine]['completion_rate'].append(completion_rate)
                    engine_data[engine]['run_names'].append(run['run_name'])

        # Create subplots
        fig = make_subplots(
            rows=2, cols=1,
            subplot_titles=['Average Throughput (MB/s)', 'Job Completion Rate (%)'],
            shared_xaxes=True
        )

        for engine, data in engine_data.items():
            fig.add_trace(
                go.Scatter(x=data['run_names'], y=data['throughput'],
                          mode='lines+markers', name=f'{engine} Throughput'),
                row=1, col=1
            )
            fig.add_trace(
                go.Scatter(x=data['run_names'], y=data['completion_rate'],
                          mode='lines+markers', name=f'{engine} Success Rate'),
                row=2, col=1
            )

        fig.update_layout(height=600, title="Engine Performance Across Runs")

        chart_file = output_dir / "engine_performance.html"
        fig.write_html(str(chart_file))
        return str(chart_file.name)

    def _create_throughput_trends_chart(self, runs: List[Dict[str, Any]], output_dir: Path) -> str:
        """Create throughput trends chart."""
        # Collect all performance data
        all_data = []
        for run in runs:
            for perf in run.get('performance_data', []):
                all_data.append({
                    'run_name': run['run_name'],
                    'engine': perf['engine_name'],
                    'pattern_count': perf['pattern_count'],
                    'input_size': perf['input_size'],
                    'throughput': perf['scanning_throughput_mb_per_sec']
                })

        if not all_data:
            return ""

        df = pd.DataFrame(all_data)

        fig = px.box(df, x='engine', y='throughput', color='engine',
                     title="Throughput Distribution by Engine",
                     labels={'throughput': 'Throughput (MB/s)', 'engine': 'Engine'})

        chart_file = output_dir / "throughput_trends.html"
        fig.write_html(str(chart_file))
        return str(chart_file.name)

    def _create_success_rates_chart(self, runs: List[Dict[str, Any]], output_dir: Path) -> str:
        """Create job success rates chart."""
        success_data = []

        for run in runs:
            for job_stat in run.get('job_stats', []):
                success_rate = job_stat['completed'] / max(job_stat['total_jobs'], 1) * 100
                success_data.append({
                    'run': run['run_name'],
                    'engine': job_stat['engine_name'],
                    'success_rate': success_rate,
                    'total_jobs': job_stat['total_jobs'],
                    'completed': job_stat['completed'],
                    'failed': job_stat['failed']
                })

        if not success_data:
            return ""

        df = pd.DataFrame(success_data)

        fig = px.bar(df, x='run', y='success_rate', color='engine',
                     title="Job Success Rates by Run and Engine",
                     labels={'success_rate': 'Success Rate (%)', 'run': 'Benchmark Run'})

        chart_file = output_dir / "success_rates.html"
        fig.write_html(str(chart_file))
        return str(chart_file.name)

    def _create_pattern_scaling_chart(self, runs: List[Dict[str, Any]], output_dir: Path) -> str:
        """Create pattern count vs performance scaling chart."""
        scaling_data = []

        for run in runs:
            for perf in run.get('performance_data', []):
                scaling_data.append({
                    'run_name': run['run_name'],
                    'engine': perf['engine_name'],
                    'pattern_count': perf['pattern_count'],
                    'execution_time': perf['execution_time_ms'],
                    'throughput': perf['scanning_throughput_mb_per_sec']
                })

        if not scaling_data:
            return ""

        df = pd.DataFrame(scaling_data)

        fig = px.scatter(df, x='pattern_count', y='throughput', color='engine',
                        size='execution_time', hover_data=['run_name'],
                        title="Pattern Count vs Throughput Scaling",
                        labels={'pattern_count': 'Pattern Count', 'throughput': 'Throughput (MB/s)'})

        chart_file = output_dir / "pattern_scaling.html"
        fig.write_html(str(chart_file))
        return str(chart_file.name)

    def _generate_html_report(self, runs: List[Dict[str, Any]], charts: Dict[str, str], output_file: Path):
        """Generate the main HTML report file."""
        html_content = f"""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Comprehensive Benchmark Analysis Report</title>
    <style>
        body {{ font-family: Arial, sans-serif; margin: 20px; }}
        .header {{ background-color: #f0f0f0; padding: 20px; border-radius: 5px; }}
        .summary {{ margin: 20px 0; }}
        .run-summary {{ border: 1px solid #ddd; margin: 10px 0; padding: 15px; border-radius: 5px; }}
        .chart-section {{ margin: 30px 0; }}
        .chart-embed {{ width: 100%; height: 600px; border: none; }}
        table {{ border-collapse: collapse; width: 100%; }}
        th, td {{ border: 1px solid #ddd; padding: 8px; text-align: left; }}
        th {{ background-color: #f2f2f2; }}
        .status-completed {{ color: green; }}
        .status-failed {{ color: red; }}
        .status-running {{ color: orange; }}
    </style>
</head>
<body>
    <div class="header">
        <h1>🎯 Comprehensive Benchmark Analysis Report</h1>
        <p>Generated on {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}</p>
        <p><strong>Total Runs Analyzed:</strong> {len(runs)}</p>
    </div>

    <div class="summary">
        <h2>📊 Executive Summary</h2>
        {self._generate_executive_summary(runs)}
    </div>

    <div class="chart-section">
        <h2>📈 Performance Analysis Charts</h2>

        <h3>🕒 Runs Timeline</h3>
        <iframe src="{charts.get('timeline', '')}" class="chart-embed"></iframe>

        <h3>⚡ Engine Performance Comparison</h3>
        <iframe src="{charts.get('engine_performance', '')}" class="chart-embed"></iframe>

        <h3>📈 Throughput Trends</h3>
        <iframe src="{charts.get('throughput_trends', '')}" class="chart-embed"></iframe>

        <h3>✅ Success Rates</h3>
        <iframe src="{charts.get('success_rates', '')}" class="chart-embed"></iframe>

        <h3>📐 Pattern Scaling Analysis</h3>
        <iframe src="{charts.get('pattern_scaling', '')}" class="chart-embed"></iframe>
    </div>

    <div class="runs-detail">
        <h2>🔍 Detailed Run Analysis</h2>
        {self._generate_detailed_runs_section(runs)}
    </div>

    <div class="recommendations">
        <h2>💡 Recommendations</h2>
        {self._generate_recommendations(runs)}
    </div>
</body>
</html>
"""

        with open(output_file, 'w') as f:
            f.write(html_content)

    def _generate_executive_summary(self, runs: List[Dict[str, Any]]) -> str:
        """Generate executive summary section."""
        total_jobs = sum(sum(stat['total_jobs'] for stat in run.get('job_stats', [])) for run in runs)
        total_completed = sum(sum(stat['completed'] for stat in run.get('job_stats', [])) for run in runs)
        total_failed = sum(sum(stat['failed'] for stat in run.get('job_stats', [])) for run in runs)

        success_rate = (total_completed / max(total_jobs, 1)) * 100

        # Get unique engines
        all_engines = set()
        for run in runs:
            for stat in run.get('job_stats', []):
                all_engines.add(stat['engine_name'])

        return f"""
        <ul>
            <li><strong>Total Jobs Executed:</strong> {total_jobs:,}</li>
            <li><strong>Successful Jobs:</strong> {total_completed:,} ({success_rate:.1f}%)</li>
            <li><strong>Failed Jobs:</strong> {total_failed:,}</li>
            <li><strong>Engines Tested:</strong> {len(all_engines)} ({', '.join(sorted(all_engines))})</li>
            <li><strong>Date Range:</strong> {self._get_date_range(runs)}</li>
        </ul>
        """

    def _generate_detailed_runs_section(self, runs: List[Dict[str, Any]]) -> str:
        """Generate detailed runs analysis section."""
        runs_html = ""

        for run in runs:
            status_class = f"status-{run['status'].lower()}"
            runs_html += f"""
            <div class="run-summary">
                <h3>{run['run_name']} <span class="{status_class}">({run['status']})</span></h3>
                <p><strong>Started:</strong> {run.get('started_at', 'N/A')}</p>
                <p><strong>Duration:</strong> {run.get('duration_seconds', 'N/A')} seconds</p>
                <p><strong>Config:</strong> {run.get('config_path', 'N/A')}</p>

                <table>
                    <tr><th>Engine</th><th>Total Jobs</th><th>Completed</th><th>Failed</th><th>Avg Throughput (MB/s)</th></tr>
            """

            for stat in run.get('job_stats', []):
                avg_throughput = f"{stat['avg_throughput']:.2f}" if stat['avg_throughput'] else "N/A"
                runs_html += f"""
                    <tr>
                        <td>{stat['engine_name']}</td>
                        <td>{stat['total_jobs']}</td>
                        <td>{stat['completed']}</td>
                        <td>{stat['failed']}</td>
                        <td>{avg_throughput}</td>
                    </tr>
                """

            runs_html += "</table></div>"

        return runs_html

    def _generate_recommendations(self, runs: List[Dict[str, Any]]) -> str:
        """Generate recommendations based on analysis."""
        recommendations = []

        # Analyze completion rates
        for run in runs:
            for stat in run.get('job_stats', []):
                completion_rate = stat['completed'] / max(stat['total_jobs'], 1)
                if completion_rate < 0.8:
                    recommendations.append(f"⚠️ {stat['engine_name']} in {run['run_name']} has low completion rate ({completion_rate*100:.1f}%) - investigate timeout/error issues")

        # Analyze performance trends
        recommendations.append("📊 Review the throughput trends chart to identify performance regressions")
        recommendations.append("🔄 Consider running additional tests with engines showing high failure rates")
        recommendations.append("📈 Use the pattern scaling analysis to optimize test configurations")

        if not recommendations:
            recommendations = ["✅ All runs completed successfully with good performance characteristics"]

        return "<ul>" + "".join(f"<li>{rec}</li>" for rec in recommendations) + "</ul>"

    def _get_date_range(self, runs: List[Dict[str, Any]]) -> str:
        """Get date range of all runs."""
        dates = []
        for run in runs:
            if run.get('started_at'):
                dates.append(run['started_at'])
            elif run.get('created_at'):
                dates.append(run['created_at'])

        if not dates:
            return "N/A"

        dates.sort()
        if len(dates) == 1:
            return dates[0].split('T')[0]
        else:
            return f"{dates[0].split('T')[0]} to {dates[-1].split('T')[0]}"