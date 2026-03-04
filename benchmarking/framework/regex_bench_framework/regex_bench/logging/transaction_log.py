"""
Transaction logging system for benchmark results.
Provides append-only JSON log as a safety net against database corruption.
"""

import json
import os
import fcntl
import datetime
from pathlib import Path
from typing import Dict, Any, Optional, List
import logging
from dataclasses import asdict

logger = logging.getLogger(__name__)

class TransactionLogger:
    """
    Thread-safe append-only transaction logger for benchmark results.

    Records successful job completions to a JSON log file as a safety net
    against database corruption or loss.
    """

    def __init__(self, log_dir: str, run_id: str):
        """
        Initialize transaction logger for a specific run.

        Args:
            log_dir: Directory to store log files
            run_id: Unique identifier for the benchmark run
        """
        self.log_dir = Path(log_dir)
        self.run_id = run_id
        self.log_path = self.log_dir / f"transaction_log_{run_id}.jsonl"

        # Create log directory if it doesn't exist
        self.log_dir.mkdir(parents=True, exist_ok=True)

        # Initialize log file with run metadata
        self._initialize_log()

    def _initialize_log(self):
        """Initialize log file with run metadata if it doesn't exist."""
        if not self.log_path.exists():
            metadata = {
                "log_type": "benchmark_transaction_log",
                "run_id": self.run_id,
                "created_at": datetime.datetime.now().isoformat(),
                "version": "1.0",
                "description": "Sequential log of successful benchmark job completions"
            }
            self._append_entry({"event_type": "log_initialized", "metadata": metadata})
            logger.info(f"Initialized transaction log: {self.log_path}")

    def log_job_completion(self, job_data: Dict[str, Any], result_data: Dict[str, Any]):
        """
        Log a successful job completion.

        Args:
            job_data: Job information (job_id, engine, patterns, etc.)
            result_data: Benchmark result data (timing, memory, etc.)
        """
        entry = {
            "event_type": "job_completed",
            "timestamp": datetime.datetime.now().isoformat(),
            "job": job_data,
            "result": result_data
        }

        self._append_entry(entry)
        logger.debug(f"Logged job completion: {job_data.get('job_id', 'unknown')}")

    def log_job_failure(self, job_data: Dict[str, Any], error_info: Dict[str, Any]):
        """
        Log a job failure for tracking purposes.

        Args:
            job_data: Job information
            error_info: Error details (status, message, etc.)
        """
        entry = {
            "event_type": "job_failed",
            "timestamp": datetime.datetime.now().isoformat(),
            "job": job_data,
            "error": error_info
        }

        self._append_entry(entry)
        logger.debug(f"Logged job failure: {job_data.get('job_id', 'unknown')}")

    def log_run_event(self, event_type: str, data: Dict[str, Any]):
        """
        Log a run-level event (start, pause, resume, complete, etc.).

        Args:
            event_type: Type of event (run_started, run_paused, etc.)
            data: Event-specific data
        """
        entry = {
            "event_type": event_type,
            "timestamp": datetime.datetime.now().isoformat(),
            "data": data
        }

        self._append_entry(entry)
        logger.info(f"Logged run event: {event_type}")

    def _append_entry(self, entry: Dict[str, Any]):
        """
        Thread-safely append an entry to the log file.

        Args:
            entry: Dictionary to append as JSON line
        """
        try:
            # Use file locking for thread safety
            with open(self.log_path, 'a', encoding='utf-8') as f:
                fcntl.flock(f.fileno(), fcntl.LOCK_EX)
                try:
                    json.dump(entry, f, separators=(',', ':'))
                    f.write('\n')
                    f.flush()
                    os.fsync(f.fileno())  # Force write to disk
                finally:
                    fcntl.flock(f.fileno(), fcntl.LOCK_UN)
        except Exception as e:
            logger.error(f"Failed to write to transaction log: {e}")

    def read_log_entries(self, event_types: Optional[List[str]] = None) -> List[Dict[str, Any]]:
        """
        Read all entries from the log file.

        Args:
            event_types: Optional filter for specific event types

        Returns:
            List of log entries
        """
        entries = []

        if not self.log_path.exists():
            return entries

        try:
            with open(self.log_path, 'r', encoding='utf-8') as f:
                for line_num, line in enumerate(f, 1):
                    line = line.strip()
                    if not line:
                        continue

                    try:
                        entry = json.loads(line)
                        if event_types is None or entry.get('event_type') in event_types:
                            entries.append(entry)
                    except json.JSONDecodeError as e:
                        logger.warning(f"Invalid JSON at line {line_num}: {e}")

        except Exception as e:
            logger.error(f"Failed to read transaction log: {e}")

        return entries

    def get_completed_jobs(self) -> List[Dict[str, Any]]:
        """Get all successfully completed jobs from the log."""
        return self.read_log_entries(['job_completed'])

    def get_failed_jobs(self) -> List[Dict[str, Any]]:
        """Get all failed jobs from the log."""
        return self.read_log_entries(['job_failed'])

    def get_log_stats(self) -> Dict[str, Any]:
        """Get statistics about the transaction log."""
        if not self.log_path.exists():
            return {"exists": False}

        entries = self.read_log_entries()
        event_counts = {}

        for entry in entries:
            event_type = entry.get('event_type', 'unknown')
            event_counts[event_type] = event_counts.get(event_type, 0) + 1

        return {
            "exists": True,
            "log_path": str(self.log_path),
            "total_entries": len(entries),
            "event_counts": event_counts,
            "file_size_bytes": self.log_path.stat().st_size,
            "created_at": datetime.datetime.fromtimestamp(
                self.log_path.stat().st_ctime
            ).isoformat()
        }


class LogRecoveryManager:
    """
    Manager for recovering benchmark results from transaction logs.
    """

    def __init__(self, log_path: str):
        """
        Initialize recovery manager.

        Args:
            log_path: Path to the transaction log file
        """
        self.log_path = Path(log_path)
        self.logger = TransactionLogger.__new__(TransactionLogger)  # Create instance without __init__
        self.logger.log_path = self.log_path

    def recover_to_database(self, db_connection, dry_run: bool = False) -> Dict[str, Any]:
        """
        Recover completed jobs from transaction log to database.

        Args:
            db_connection: SQLite database connection
            dry_run: If True, only report what would be recovered

        Returns:
            Recovery statistics
        """
        completed_jobs = self.logger.get_completed_jobs()

        recovery_stats = {
            "total_log_entries": len(completed_jobs),
            "jobs_to_recover": 0,
            "jobs_already_in_db": 0,
            "jobs_recovered": 0,
            "errors": []
        }

        for entry in completed_jobs:
            try:
                job_data = entry['job']
                job_id = job_data.get('job_id')

                if not job_id:
                    recovery_stats["errors"].append("Missing job_id in log entry")
                    continue

                # Check if job already exists in database
                cursor = db_connection.execute(
                    "SELECT status FROM benchmark_jobs WHERE job_id = ?",
                    (job_id,)
                )
                existing = cursor.fetchone()

                if existing and existing['status'] == 'COMPLETED':
                    recovery_stats["jobs_already_in_db"] += 1
                    continue

                recovery_stats["jobs_to_recover"] += 1

                if not dry_run:
                    # Recover the job result to database
                    self._restore_job_to_db(db_connection, entry)
                    recovery_stats["jobs_recovered"] += 1

            except Exception as e:
                error_msg = f"Failed to recover job {job_data.get('job_id', 'unknown')}: {e}"
                recovery_stats["errors"].append(error_msg)
                logger.error(error_msg)

        return recovery_stats

    def _restore_job_to_db(self, db_connection, log_entry: Dict[str, Any]):
        """Restore a single job entry to the database."""
        job = log_entry['job']
        result = log_entry['result']

        # Update job with completion data
        db_connection.execute("""
            UPDATE benchmark_jobs SET
                status = 'COMPLETED',
                completed_at = ?,
                duration_seconds = ?,
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
            log_entry['timestamp'],
            result.get('duration_seconds'),
            result.get('compilation_ns'),
            result.get('scanning_ns'),
            result.get('total_ns'),
            result.get('match_count'),
            result.get('patterns_compiled'),
            result.get('memory_peak_bytes'),
            result.get('memory_compilation_bytes'),
            result.get('cpu_user_ms'),
            result.get('cpu_system_ms'),
            result.get('throughput_mbps'),
            result.get('matches_per_second'),
            json.dumps(result) if result else None,
            result.get('raw_stdout', ''),
            result.get('raw_stderr', ''),
            job['job_id']
        ))

        db_connection.commit()


# Utility functions for integration
def get_transaction_logger(output_dir: str, run_id: str) -> TransactionLogger:
    """Get or create a transaction logger for a run."""
    log_dir = Path(output_dir) / "logs"
    return TransactionLogger(str(log_dir), run_id)

def create_recovery_manager(log_path: str) -> LogRecoveryManager:
    """Create a recovery manager for a transaction log."""
    return LogRecoveryManager(log_path)