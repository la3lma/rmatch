"""
Job queue implementation for benchmark task management.
"""

import json
import sqlite3
import uuid
import logging
from datetime import datetime
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Dict, List, Optional, Any

from ..engines.base import EngineResult
from .schema import init_database
from ..logging.transaction_log import get_transaction_logger, TransactionLogger

logger = logging.getLogger(__name__)


@dataclass
class BenchmarkJob:
    """Represents a single benchmark job in the queue."""
    job_id: str
    run_id: str

    # Job configuration
    engine_name: str
    pattern_count: int
    input_size: str
    input_size_bytes: int
    iteration: int
    pattern_suite: str
    corpus_name: str

    # Job lifecycle
    status: str = 'QUEUED'  # QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, TIMEOUT
    created_at: Optional[datetime] = None
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    duration_seconds: Optional[float] = None

    # Execution tracking
    attempt_count: int = 0
    timeout_seconds: int = 14400  # Default to 4 hours, should be overridden by config
    error_message: Optional[str] = None

    # Performance metrics (filled after completion)
    compilation_ns: Optional[int] = None
    scanning_ns: Optional[int] = None
    total_ns: Optional[int] = None
    match_count: Optional[int] = None
    patterns_compiled: Optional[int] = None
    memory_peak_bytes: Optional[int] = None
    memory_compilation_bytes: Optional[int] = None
    cpu_user_ms: Optional[int] = None
    cpu_system_ms: Optional[int] = None

    # Calculated metrics
    throughput_mbps: Optional[float] = None
    matches_per_second: Optional[float] = None

    # Complete result data
    result_json: Optional[str] = None
    raw_stdout: str = ""
    raw_stderr: str = ""

    # Configuration context
    config_hash: str = ""
    job_config_json: Optional[str] = None

    @classmethod
    def create_new(cls, run_id: str, engine_name: str, pattern_count: int,
                   input_size: str, input_size_bytes: int, iteration: int,
                   pattern_suite: str, corpus_name: str, config_hash: str) -> 'BenchmarkJob':
        """Create a new benchmark job with generated ID."""
        return cls(
            job_id=str(uuid.uuid4()),
            run_id=run_id,
            engine_name=engine_name,
            pattern_count=pattern_count,
            input_size=input_size,
            input_size_bytes=input_size_bytes,
            iteration=iteration,
            pattern_suite=pattern_suite,
            corpus_name=corpus_name,
            config_hash=config_hash,
            created_at=datetime.now()
        )


class JobQueue:
    """SQLite-based job queue for benchmark execution."""

    def __init__(self, db_path: Path, output_dir: Optional[str] = None, run_id: Optional[str] = None):
        """Initialize job queue with database connection."""
        self.db_path = db_path
        self.conn = init_database(db_path)

        # Initialize transaction logger if output_dir and run_id are provided
        self.transaction_logger: Optional[TransactionLogger] = None
        if output_dir and run_id:
            self.transaction_logger = get_transaction_logger(output_dir, run_id)

    def close(self):
        """Close database connection."""
        if self.conn:
            self.conn.close()

    def enqueue_job(self, job: BenchmarkJob) -> str:
        """Add a job to the queue."""
        cursor = self.conn.execute("""
            INSERT INTO benchmark_jobs (
                job_id, run_id, engine_name, pattern_count, input_size,
                input_size_bytes, iteration, pattern_suite, corpus_name,
                status, created_at, timeout_seconds, config_hash, job_config_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            job.job_id, job.run_id, job.engine_name, job.pattern_count,
            job.input_size, job.input_size_bytes, job.iteration,
            job.pattern_suite, job.corpus_name, job.status,
            job.created_at, job.timeout_seconds, job.config_hash,
            job.job_config_json
        ))

        self.conn.commit()
        logger.debug(f"Enqueued job {job.job_id} for {job.engine_name}")
        return job.job_id

    def get_next_job(self, run_id: str) -> Optional[BenchmarkJob]:
        """Get next queued job and mark it as running."""
        # Start transaction
        self.conn.execute("BEGIN")

        try:
            # Find next queued job
            cursor = self.conn.execute("""
                SELECT * FROM benchmark_jobs
                WHERE run_id = ? AND status = 'QUEUED'
                ORDER BY created_at ASC
                LIMIT 1
            """, (run_id,))

            row = cursor.fetchone()
            if not row:
                self.conn.rollback()
                return None

            # Update status to RUNNING
            job_id = row['job_id']
            started_at = datetime.now()

            self.conn.execute("""
                UPDATE benchmark_jobs
                SET status = 'RUNNING', started_at = ?, attempt_count = attempt_count + 1
                WHERE job_id = ?
            """, (started_at, job_id))

            self.conn.commit()

            # Convert row to BenchmarkJob
            job = self._row_to_job(row)
            job.status = 'RUNNING'
            job.started_at = started_at
            job.attempt_count = (job.attempt_count or 0) + 1

            logger.debug(f"Retrieved job {job_id} for {job.engine_name}")
            return job

        except Exception as e:
            self.conn.rollback()
            logger.error(f"Failed to get next job: {e}")
            return None

    def update_job_status(self, job_id: str, status: str,
                         result: Optional[EngineResult] = None,
                         error_message: Optional[str] = None):
        """Update job status and optionally store results."""
        completed_at = datetime.now() if status in ['COMPLETED', 'FAILED'] else None

        # Calculate duration if job is completing
        duration_seconds = None
        if completed_at:
            cursor = self.conn.execute(
                "SELECT started_at FROM benchmark_jobs WHERE job_id = ?",
                (job_id,)
            )
            row = cursor.fetchone()
            if row and row['started_at']:
                started = datetime.fromisoformat(row['started_at'])
                duration_seconds = (completed_at - started).total_seconds()

        # Prepare result data
        result_json = None
        throughput_mbps = None
        matches_per_second = None

        if result:
            result_json = json.dumps(asdict(result), default=str)

            # Calculate derived metrics
            if result.scanning_ns and result.corpus_size_bytes:
                # Throughput = (bytes / nanoseconds) * conversion factor
                throughput_mbps = (result.corpus_size_bytes / result.scanning_ns) * 1000

            if result.scanning_ns and result.match_count:
                # Matches per second
                matches_per_second = result.match_count / (result.scanning_ns / 1e9)

        # Update database
        self.conn.execute("""
            UPDATE benchmark_jobs SET
                status = ?,
                completed_at = ?,
                duration_seconds = ?,
                error_message = ?,
                compilation_ns = ?,
                scanning_ns = ?,
                total_ns = ?,
                match_count = ?,
                patterns_compiled = ?,
                memory_peak_bytes = ?,
                memory_compilation_bytes = ?,
                cpu_user_ms = ?,
                cpu_system_ms = ?,
                throughput_mbps = ?,
                matches_per_second = ?,
                result_json = ?,
                raw_stdout = ?,
                raw_stderr = ?
            WHERE job_id = ?
        """, (
            status, completed_at, duration_seconds, error_message,
            result.compilation_ns if result else None,
            result.scanning_ns if result else None,
            result.total_ns if result else None,
            result.match_count if result else None,
            result.patterns_compiled if result else None,
            result.memory_peak_bytes if result else None,
            result.memory_compilation_bytes if result else None,
            result.cpu_user_ms if result else None,
            result.cpu_system_ms if result else None,
            throughput_mbps,
            matches_per_second,
            result_json,
            result.raw_stdout if result else "",
            result.raw_stderr if result else "",
            job_id
        ))

        self.conn.commit()
        logger.debug(f"Updated job {job_id} status to {status}")

        # Log to transaction log if available
        if self.transaction_logger and status in ['COMPLETED', 'FAILED', 'TIMEOUT']:
            try:
                # Get job details for logging
                cursor = self.conn.execute(
                    "SELECT * FROM benchmark_jobs WHERE job_id = ?",
                    (job_id,)
                )
                job_row = cursor.fetchone()

                if job_row:
                    job_data = {
                        'job_id': job_id,
                        'run_id': job_row['run_id'],
                        'engine_name': job_row['engine_name'],
                        'pattern_count': job_row['pattern_count'],
                        'input_size': job_row['input_size'],
                        'corpus_name': job_row['corpus_name'],
                        'pattern_suite': job_row['pattern_suite'],
                        'iteration': job_row['iteration']
                    }

                    if status == 'COMPLETED' and result:
                        # Log successful completion
                        result_data = asdict(result)
                        result_data['duration_seconds'] = duration_seconds
                        result_data['throughput_mbps'] = throughput_mbps
                        result_data['matches_per_second'] = matches_per_second
                        self.transaction_logger.log_job_completion(job_data, result_data)
                    else:
                        # Log failure
                        error_data = {
                            'status': status,
                            'error_message': error_message,
                            'duration_seconds': duration_seconds
                        }
                        self.transaction_logger.log_job_failure(job_data, error_data)

            except Exception as e:
                logger.error(f"Failed to write to transaction log: {e}")

    def get_job_count(self, run_id: str) -> int:
        """Get total number of jobs for a run."""
        cursor = self.conn.execute(
            "SELECT COUNT(*) FROM benchmark_jobs WHERE run_id = ?",
            (run_id,)
        )
        return cursor.fetchone()[0]

    def get_completed_count(self, run_id: str) -> int:
        """Get number of completed jobs for a run."""
        cursor = self.conn.execute(
            "SELECT COUNT(*) FROM benchmark_jobs WHERE run_id = ? AND status = 'COMPLETED'",
            (run_id,)
        )
        return cursor.fetchone()[0]

    def get_total_count(self, run_id: str) -> int:
        """Get total number of jobs for a run (alias for get_job_count)."""
        return self.get_job_count(run_id)

    def get_run_progress(self, run_id: str) -> Dict[str, int]:
        """Get progress summary for a run."""
        cursor = self.conn.execute("""
            SELECT
                status,
                COUNT(*) as count
            FROM benchmark_jobs
            WHERE run_id = ?
            GROUP BY status
        """, (run_id,))

        progress = {
            'QUEUED': 0,
            'RUNNING': 0,
            'COMPLETED': 0,
            'FAILED': 0,
            'CANCELLED': 0,
            'TIMEOUT': 0
        }

        for row in cursor:
            progress[row['status']] = row['count']

        progress['total'] = sum(progress.values())
        return progress

    def get_latest_incomplete_run(self) -> Optional[str]:
        """Get the run_id of the latest incomplete run."""
        cursor = self.conn.execute("""
            SELECT run_id FROM benchmark_runs
            WHERE status IN ('PREPARING', 'RUNNING')
            ORDER BY created_at DESC
            LIMIT 1
        """)

        row = cursor.fetchone()
        return row['run_id'] if row else None

    def get_latest_run_id(self) -> Optional[str]:
        """Get the run_id of the most recent run."""
        cursor = self.conn.execute("""
            SELECT run_id FROM benchmark_runs
            ORDER BY created_at DESC
            LIMIT 1
        """)

        row = cursor.fetchone()
        return row['run_id'] if row else None

    def apply_adaptive_iteration_logic(self, completed_job: BenchmarkJob,
                                     duration_threshold: float = 100.0) -> int:
        """
        Apply adaptive iteration logic after first iteration completes.
        Marks remaining iterations as skipped if duration exceeds threshold.

        Args:
            completed_job: The completed first iteration job
            duration_threshold: Threshold in seconds to determine if more iterations should run

        Returns:
            Number of jobs marked as skipped
        """
        # Only process first iteration (iteration=0)
        if completed_job.iteration != 0:
            return 0

        # Only apply logic for successful jobs
        if completed_job.status != 'COMPLETED' or not completed_job.duration_seconds:
            return 0

        # Use corpus-size-aware thresholds for better performance optimization
        corpus_size_aware_threshold = self._get_adaptive_threshold(completed_job.input_size)

        # Check duration threshold
        if completed_job.duration_seconds < corpus_size_aware_threshold:
            logger.info(f"Allowing additional iterations for {completed_job.engine_name} "
                       f"(duration {completed_job.duration_seconds:.1f}s < {corpus_size_aware_threshold}s threshold for {completed_job.input_size})")
            return 0

        # Duration exceeded threshold - mark remaining iterations as skipped
        logger.info(f"Marking additional iterations as skipped for {completed_job.engine_name} "
                   f"(duration {completed_job.duration_seconds:.1f}s >= {corpus_size_aware_threshold}s threshold for {completed_job.input_size})")

        # Find and mark all remaining QUEUED iterations for this combination as skipped
        skipped_count = self.conn.execute("""
            UPDATE benchmark_jobs
            SET status = 'SKIPPED_LOWVARIANCE',
                completed_at = CURRENT_TIMESTAMP,
                error_message = 'Skipped due to low variance heuristic - first iteration took '
                               || ? || 's (>=' || ? || 's threshold)'
            WHERE run_id = ?
              AND engine_name = ?
              AND pattern_count = ?
              AND input_size = ?
              AND pattern_suite = ?
              AND corpus_name = ?
              AND iteration > 0
              AND status = 'QUEUED'
        """, (
            completed_job.duration_seconds,
            duration_threshold,
            completed_job.run_id,
            completed_job.engine_name,
            completed_job.pattern_count,
            completed_job.input_size,
            completed_job.pattern_suite,
            completed_job.corpus_name
        )).rowcount

        self.conn.commit()

        if skipped_count > 0:
            logger.info(f"Marked {skipped_count} iterations as SKIPPED_LOWVARIANCE for "
                       f"{completed_job.engine_name} {completed_job.pattern_count} patterns {completed_job.input_size}")

        return skipped_count

    def _get_adaptive_threshold(self, input_size: str) -> float:
        """
        Get corpus-size-aware adaptive iteration threshold.

        For large corpus sizes, variance is minimal and iterations are wasteful.
        Use aggressive thresholds to save hours of execution time.

        Args:
            input_size: Corpus size string (e.g., "1GB", "100MB", "10MB", "1MB")

        Returns:
            Threshold in seconds for adaptive iteration logic
        """
        size_thresholds = {
            "1GB": 30.0,    # Very aggressive - 1GB jobs take 2+ hours, variance is minimal
            "100MB": 60.0,  # Aggressive - 100MB jobs take significant time
            "10MB": 100.0,  # Default threshold
            "1MB": 100.0    # Default threshold
        }

        return size_thresholds.get(input_size, 100.0)  # Default to 100s for unknown sizes

    def get_job_by_id(self, job_id: str) -> Optional[BenchmarkJob]:
        """Get a job by its ID."""
        cursor = self.conn.execute("""
            SELECT * FROM benchmark_jobs WHERE job_id = ?
        """, (job_id,))

        row = cursor.fetchone()
        if row:
            return self._row_to_job(row)
        return None

    def _row_to_job(self, row: sqlite3.Row) -> BenchmarkJob:
        """Convert database row to BenchmarkJob object."""
        return BenchmarkJob(
            job_id=row['job_id'],
            run_id=row['run_id'],
            engine_name=row['engine_name'],
            pattern_count=row['pattern_count'],
            input_size=row['input_size'],
            input_size_bytes=row['input_size_bytes'],
            iteration=row['iteration'],
            pattern_suite=row['pattern_suite'],
            corpus_name=row['corpus_name'],
            status=row['status'],
            created_at=datetime.fromisoformat(row['created_at']) if row['created_at'] else None,
            started_at=datetime.fromisoformat(row['started_at']) if row['started_at'] else None,
            completed_at=datetime.fromisoformat(row['completed_at']) if row['completed_at'] else None,
            duration_seconds=row['duration_seconds'],
            attempt_count=row['attempt_count'],
            timeout_seconds=row['timeout_seconds'],
            error_message=row['error_message'],
            compilation_ns=row['compilation_ns'],
            scanning_ns=row['scanning_ns'],
            total_ns=row['total_ns'],
            match_count=row['match_count'],
            patterns_compiled=row['patterns_compiled'],
            memory_peak_bytes=row['memory_peak_bytes'],
            memory_compilation_bytes=row['memory_compilation_bytes'],
            cpu_user_ms=row['cpu_user_ms'],
            cpu_system_ms=row['cpu_system_ms'],
            throughput_mbps=row['throughput_mbps'],
            matches_per_second=row['matches_per_second'],
            result_json=row['result_json'],
            raw_stdout=row['raw_stdout'] or "",
            raw_stderr=row['raw_stderr'] or "",
            config_hash=row['config_hash'],
            job_config_json=row['job_config_json']
        )