"""
Enhanced report generator with database integration and comprehensive metadata.
"""

import json
import sqlite3
from pathlib import Path
from typing import Dict, Any, Optional, List
from datetime import datetime
from dataclasses import dataclass

from .formatter import HTMLFormatter, MarkdownFormatter


@dataclass
class BenchmarkRunMetadata:
    """Metadata about a benchmark run."""
    run_id: str
    config_path: str
    status: str
    created_at: str
    started_at: Optional[str]
    completed_at: Optional[str]
    created_by: str
    duration_seconds: Optional[float]

    # Job statistics
    total_jobs: int
    completed_jobs: int
    failed_jobs: int
    queued_jobs: int
    running_jobs: int

    # Timing information
    first_job_started: Optional[str]
    last_job_completed: Optional[str]
    actual_execution_duration: Optional[str]

    # System information
    system_profile_id: Optional[str]

    # Configuration details
    test_matrix: Optional[Dict[str, Any]]


class EnhancedReportGenerator:
    """Enhanced report generator with database integration."""

    def __init__(self):
        self.formatters = {
            'html': HTMLFormatter(),
            'markdown': MarkdownFormatter()
        }

    def generate_from_database(
        self,
        db_path: Path,
        output_dir: Path,
        run_id: Optional[str] = None,
        format: str = 'html',
        include_charts: bool = False
    ) -> Path:
        """Generate enhanced report from job database."""

        # Load metadata from database
        metadata = self._load_run_metadata(db_path, run_id)

        # Load traditional benchmark data if available
        benchmark_data = self._load_benchmark_data_if_available(output_dir.parent)

        # Combine database metadata with traditional data
        enhanced_data = {
            'metadata': metadata,
            'benchmark_results': benchmark_data,
            'generated_at': datetime.now().isoformat(),
            'database_path': str(db_path)
        }

        # Generate enhanced report
        formatter = self.formatters.get(format)
        if not formatter:
            raise ValueError(f"Unsupported format: {format}")

        if hasattr(formatter, 'generate_enhanced_report'):
            report_file = formatter.generate_enhanced_report(
                data=enhanced_data,
                output_dir=output_dir,
                include_charts=include_charts
            )
        else:
            # Fallback to traditional report with metadata injection
            report_file = formatter.generate_report(
                data=enhanced_data,
                output_dir=output_dir,
                include_charts=include_charts
            )

        return report_file

    def _load_run_metadata(self, db_path: Path, run_id: Optional[str] = None) -> BenchmarkRunMetadata:
        """Load comprehensive run metadata from database."""
        if not db_path.exists():
            raise FileNotFoundError(f"Database not found: {db_path}")

        conn = sqlite3.connect(db_path)
        conn.row_factory = sqlite3.Row

        try:
            # Get run information (latest if run_id not specified)
            if run_id:
                run_query = "SELECT * FROM benchmark_runs WHERE run_id = ?"
                run_params = (run_id,)
            else:
                run_query = "SELECT * FROM benchmark_runs ORDER BY created_at DESC LIMIT 1"
                run_params = ()

            run_row = conn.execute(run_query, run_params).fetchone()
            if not run_row:
                raise ValueError("No benchmark runs found in database")

            # Get job statistics
            job_stats = conn.execute("""
                SELECT
                    status,
                    COUNT(*) as count
                FROM benchmark_jobs
                WHERE run_id = ?
                GROUP BY status
            """, (run_row['run_id'],)).fetchall()

            # Process job statistics
            stats = {row['status']: row['count'] for row in job_stats}
            total_jobs = sum(stats.values())

            # Get timing information
            timing_info = conn.execute("""
                SELECT
                    MIN(started_at) as first_job_started,
                    MAX(completed_at) as last_job_completed,
                    COUNT(*) as total_jobs
                FROM benchmark_jobs
                WHERE run_id = ? AND started_at IS NOT NULL
            """, (run_row['run_id'],)).fetchone()

            # Calculate actual execution duration
            actual_duration = None
            if timing_info and timing_info['first_job_started'] and timing_info['last_job_completed']:
                try:
                    start_dt = datetime.fromisoformat(timing_info['first_job_started'].replace('Z', '+00:00'))
                    end_dt = datetime.fromisoformat(timing_info['last_job_completed'].replace('Z', '+00:00'))
                    duration = end_dt - start_dt
                    actual_duration = str(duration)
                except Exception:
                    actual_duration = None

            # Load test configuration if available
            test_matrix = None
            if run_row['config_json']:
                try:
                    config_data = json.loads(run_row['config_json'])
                    test_matrix = config_data.get('test_matrix')
                except Exception:
                    test_matrix = None

            # Calculate run duration
            run_duration = None
            if run_row['started_at'] and run_row['completed_at']:
                try:
                    start_dt = datetime.fromisoformat(run_row['started_at'].replace('Z', '+00:00'))
                    end_dt = datetime.fromisoformat(run_row['completed_at'].replace('Z', '+00:00'))
                    duration = end_dt - start_dt
                    run_duration = duration.total_seconds()
                except Exception:
                    run_duration = None

            return BenchmarkRunMetadata(
                run_id=run_row['run_id'],
                config_path=run_row['config_path'],
                status=run_row['status'],
                created_at=run_row['created_at'],
                started_at=run_row['started_at'],
                completed_at=run_row['completed_at'],
                created_by=run_row['created_by'],
                duration_seconds=run_duration,
                total_jobs=total_jobs,
                completed_jobs=stats.get('COMPLETED', 0),
                failed_jobs=stats.get('FAILED', 0),
                queued_jobs=stats.get('QUEUED', 0),
                running_jobs=stats.get('RUNNING', 0),
                first_job_started=timing_info['first_job_started'] if timing_info else None,
                last_job_completed=timing_info['last_job_completed'] if timing_info else None,
                actual_execution_duration=actual_duration,
                system_profile_id=run_row['system_profile_id'] if 'system_profile_id' in run_row.keys() else None,
                test_matrix=test_matrix
            )

        finally:
            conn.close()

    def _load_benchmark_data_if_available(self, results_dir: Path) -> Optional[Dict[str, Any]]:
        """Load traditional benchmark data if results.json exists."""
        results_file = results_dir / 'results.json'
        if results_file.exists():
            try:
                with open(results_file, 'r') as f:
                    return json.load(f)
            except Exception:
                return None
        return None

    def get_failure_summary(self, db_path: Path, run_id: Optional[str] = None) -> Dict[str, Any]:
        """Get summary of failures for troubleshooting."""
        if not db_path.exists():
            raise FileNotFoundError(f"Database not found: {db_path}")

        conn = sqlite3.connect(db_path)
        conn.row_factory = sqlite3.Row

        try:
            # Get latest run if not specified
            if not run_id:
                run_row = conn.execute("SELECT run_id FROM benchmark_runs ORDER BY created_at DESC LIMIT 1").fetchone()
                if not run_row:
                    raise ValueError("No benchmark runs found")
                run_id = run_row['run_id']

            # Get failure summary by engine and error
            failure_summary = conn.execute("""
                SELECT
                    engine_name,
                    error_message,
                    COUNT(*) as failure_count
                FROM benchmark_jobs
                WHERE run_id = ? AND status = 'FAILED'
                GROUP BY engine_name, error_message
                ORDER BY failure_count DESC
            """, (run_id,)).fetchall()

            # Get list of engines that worked vs failed
            engine_summary = conn.execute("""
                SELECT
                    engine_name,
                    status,
                    COUNT(*) as job_count
                FROM benchmark_jobs
                WHERE run_id = ?
                GROUP BY engine_name, status
                ORDER BY engine_name, status
            """, (run_id,)).fetchall()

            return {
                'run_id': run_id,
                'failure_summary': [dict(row) for row in failure_summary],
                'engine_summary': [dict(row) for row in engine_summary]
            }

        finally:
            conn.close()

    def generate_troubleshooting_report(self, db_path: Path, output_dir: Path) -> Path:
        """Generate a troubleshooting-focused report."""
        metadata = self._load_run_metadata(db_path)
        failure_summary = self.get_failure_summary(db_path)

        # Create troubleshooting report
        report_content = self._format_troubleshooting_report(metadata, failure_summary)

        output_path = output_dir / f"troubleshooting_report_{metadata.run_id[:8]}.md"
        with open(output_path, 'w') as f:
            f.write(report_content)

        return output_path

    def _format_troubleshooting_report(self, metadata: BenchmarkRunMetadata, failures: Dict[str, Any]) -> str:
        """Format a troubleshooting-focused report."""

        report = f"""# Benchmark Run Troubleshooting Report

## Run Metadata
- **Run ID**: {metadata.run_id}
- **Configuration**: {metadata.config_path}
- **Status**: {metadata.status}
- **Created**: {metadata.created_at}
- **Started**: {metadata.started_at or 'N/A'}
- **Completed**: {metadata.completed_at or 'N/A'}
- **Duration**: {metadata.duration_seconds:.2f}s if metadata.duration_seconds else 'N/A'
- **Created by**: {metadata.created_by}

## Job Execution Summary
- **Total Jobs**: {metadata.total_jobs}
- **Completed**: {metadata.completed_jobs} ({metadata.completed_jobs/metadata.total_jobs*100:.1f}%)
- **Failed**: {metadata.failed_jobs} ({metadata.failed_jobs/metadata.total_jobs*100:.1f}%)
- **Still Queued**: {metadata.queued_jobs} ({metadata.queued_jobs/metadata.total_jobs*100:.1f}%)
- **Running**: {metadata.running_jobs}

## Execution Timeline
- **First Job Started**: {metadata.first_job_started or 'N/A'}
- **Last Job Completed**: {metadata.last_job_completed or 'N/A'}
- **Actual Execution Duration**: {metadata.actual_execution_duration or 'N/A'}

## Test Matrix Configuration
```json
{json.dumps(metadata.test_matrix, indent=2) if metadata.test_matrix else 'N/A'}
```

## Failure Analysis

### Top Failure Reasons
"""

        for failure in failures['failure_summary'][:10]:  # Top 10 failures
            report += f"- **{failure['engine_name']}**: {failure['error_message']} ({failure['failure_count']} jobs)\n"

        report += "\n### Engine Status Summary\n"

        # Group by engine
        engines = {}
        for row in failures['engine_summary']:
            engine = row['engine_name']
            if engine not in engines:
                engines[engine] = {}
            engines[engine][row['status']] = row['job_count']

        for engine, statuses in engines.items():
            total = sum(statuses.values())
            completed = statuses.get('COMPLETED', 0)
            failed = statuses.get('FAILED', 0)
            queued = statuses.get('QUEUED', 0)

            status_icon = "✅" if failed == 0 else "❌" if completed == 0 else "⚠️"
            report += f"{status_icon} **{engine}**: {completed}/{total} completed, {failed} failed, {queued} queued\n"

        report += f"\n## Recommendations\n\n"

        if metadata.failed_jobs > metadata.completed_jobs:
            report += "🔧 **High Failure Rate**: Most jobs failed. Check engine availability and configuration.\n\n"

        if metadata.queued_jobs > 0:
            report += "⏸️ **Incomplete Execution**: Many jobs never started. The benchmark may have terminated early due to failures.\n\n"

        if metadata.actual_execution_duration and "0:00:" in metadata.actual_execution_duration:
            report += "⚡ **Very Short Duration**: The benchmark completed in under a minute, suggesting early termination.\n\n"

        report += "📋 **Next Steps**:\n"
        report += "1. Check that all required engines are installed and available\n"
        report += "2. Verify engine configuration in the test matrix\n"
        report += "3. Check engine build status with `make build-engines`\n"
        report += "4. Consider running with a smaller engine subset for testing\n"

        return report