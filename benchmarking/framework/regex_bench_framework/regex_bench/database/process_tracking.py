"""
Process tracking extension for benchmark database.

Tracks process lifecycle and enables detection/cleanup of orphaned processes.
"""

import sqlite3
import psutil
import time
import logging
from pathlib import Path
from typing import Optional, List, Dict, Any
from dataclasses import dataclass
from datetime import datetime

logger = logging.getLogger(__name__)


@dataclass
class ProcessInfo:
    """Process information for tracking."""
    pid: int
    ppid: int
    job_id: str
    run_id: str
    engine_name: str
    command_line: str
    status: str  # 'running', 'completed', 'killed', 'orphaned', 'failed'
    started_at: datetime
    ended_at: Optional[datetime] = None
    exit_code: Optional[int] = None
    cpu_percent: Optional[float] = None
    memory_mb: Optional[float] = None


def add_process_tracking_schema(conn: sqlite3.Connection):
    """Add process tracking table to existing database."""

    # Create benchmark_processes table
    conn.execute("""
        CREATE TABLE IF NOT EXISTS benchmark_processes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            pid INTEGER NOT NULL,                   -- Process ID
            ppid INTEGER,                           -- Parent Process ID
            job_id TEXT NOT NULL,                   -- Links to benchmark_jobs.job_id
            run_id TEXT NOT NULL,                   -- Links to benchmark_runs.run_id
            engine_name TEXT NOT NULL,              -- Engine being executed

            -- Process details
            command_line TEXT NOT NULL,             -- Full command line
            cwd TEXT,                               -- Working directory
            executable TEXT,                        -- Path to executable

            -- Process lifecycle
            status TEXT NOT NULL DEFAULT 'running', -- 'running', 'completed', 'killed', 'orphaned', 'failed'
            started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            ended_at TIMESTAMP NULL,
            duration_seconds REAL,
            exit_code INTEGER NULL,

            -- Resource usage (captured periodically)
            cpu_percent_peak REAL,                 -- Peak CPU usage
            memory_mb_peak REAL,                   -- Peak memory usage in MB
            cpu_time_user_ms INTEGER,              -- User CPU time
            cpu_time_system_ms INTEGER,            -- System CPU time

            -- Monitoring metadata
            last_monitored_at TIMESTAMP,           -- Last time process was checked
            monitor_count INTEGER DEFAULT 0,       -- Number of monitoring cycles
            resource_samples_json TEXT,            -- JSON array of resource samples

            -- Process relationship tracking
            parent_benchmark_pid INTEGER,          -- PID of benchmark runner
            child_pids TEXT,                        -- Comma-separated child PIDs

            -- Cleanup tracking
            cleanup_attempted BOOLEAN DEFAULT FALSE,
            cleanup_method TEXT,                    -- 'SIGTERM', 'SIGKILL', 'natural'
            cleanup_success BOOLEAN,

            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

            FOREIGN KEY(job_id) REFERENCES benchmark_jobs(job_id),
            FOREIGN KEY(run_id) REFERENCES benchmark_runs(run_id)
        )
    """)

    # Create indexes for process tracking
    conn.execute("""
        CREATE INDEX IF NOT EXISTS idx_processes_pid ON benchmark_processes(pid)
    """)
    conn.execute("""
        CREATE INDEX IF NOT EXISTS idx_processes_job ON benchmark_processes(job_id, status)
    """)
    conn.execute("""
        CREATE INDEX IF NOT EXISTS idx_processes_run ON benchmark_processes(run_id, status)
    """)
    conn.execute("""
        CREATE INDEX IF NOT EXISTS idx_processes_status_time ON benchmark_processes(status, started_at)
    """)

    # Create view for orphaned processes
    conn.execute("""
        CREATE VIEW IF NOT EXISTS orphaned_processes AS
        SELECT
            p.*,
            j.status as job_status,
            r.status as run_status,
            (strftime('%s', 'now') - strftime('%s', p.last_monitored_at)) as seconds_since_monitor
        FROM benchmark_processes p
        LEFT JOIN benchmark_jobs j ON p.job_id = j.job_id
        LEFT JOIN benchmark_runs r ON p.run_id = r.run_id
        WHERE p.status = 'running'
        AND (
            j.status IN ('COMPLETED', 'FAILED', 'CANCELLED', 'TIMEOUT')
            OR r.status IN ('COMPLETED', 'FAILED', 'CANCELLED')
            OR p.last_monitored_at < datetime('now', '-5 minutes')
        )
    """)

    conn.commit()
    logger.info("Process tracking schema added successfully")


class ProcessTracker:
    """Manages process tracking for benchmark jobs."""

    def __init__(self, conn: sqlite3.Connection):
        self.conn = conn
        self.conn.row_factory = sqlite3.Row

    def register_process(self, pid: int, job_id: str, run_id: str, engine_name: str) -> bool:
        """Register a new process for tracking."""
        try:
            process = psutil.Process(pid)

            process_info = ProcessInfo(
                pid=pid,
                ppid=process.ppid(),
                job_id=job_id,
                run_id=run_id,
                engine_name=engine_name,
                command_line=' '.join(process.cmdline()),
                status='running',
                started_at=datetime.fromtimestamp(process.create_time())
            )

            self._insert_process(process_info, process)
            logger.info(f"Registered process {pid} for job {job_id}")
            return True

        except (psutil.NoSuchProcess, psutil.AccessDenied, Exception) as e:
            logger.warning(f"Failed to register process {pid}: {e}")
            return False

    def update_process_status(self, pid: int, status: str, exit_code: Optional[int] = None):
        """Update process status and end time."""
        self.conn.execute("""
            UPDATE benchmark_processes
            SET status = ?, exit_code = ?, ended_at = CURRENT_TIMESTAMP,
                duration_seconds = (strftime('%s', 'now') - strftime('%s', started_at)),
                updated_at = CURRENT_TIMESTAMP
            WHERE pid = ?
        """, (status, exit_code, pid))
        self.conn.commit()
        logger.debug(f"Updated process {pid} status to {status}")

    def monitor_processes(self) -> Dict[str, int]:
        """Monitor all running processes and update their resource usage."""
        cursor = self.conn.execute("""
            SELECT pid, job_id FROM benchmark_processes
            WHERE status = 'running'
        """)

        running_processes = cursor.fetchall()
        status_counts = {'running': 0, 'completed': 0, 'orphaned': 0, 'failed': 0}

        for proc_row in running_processes:
            pid = proc_row['pid']
            job_id = proc_row['job_id']

            try:
                process = psutil.Process(pid)
                if process.is_running():
                    # Update resource usage
                    self._update_process_resources(pid, process)
                    status_counts['running'] += 1
                else:
                    self.update_process_status(pid, 'completed', process.returncode)
                    status_counts['completed'] += 1

            except psutil.NoSuchProcess:
                self.update_process_status(pid, 'completed')
                status_counts['completed'] += 1
            except Exception as e:
                logger.warning(f"Error monitoring process {pid}: {e}")
                self.update_process_status(pid, 'failed')
                status_counts['failed'] += 1

        return status_counts

    def detect_orphaned_processes(self) -> List[Dict[str, Any]]:
        """Detect processes that should be cleaned up."""
        cursor = self.conn.execute("""
            SELECT * FROM orphaned_processes
            ORDER BY seconds_since_monitor DESC
        """)

        orphaned = []
        for row in cursor.fetchall():
            orphan_info = dict(row)

            # Try to find actual system process
            try:
                process = psutil.Process(row['pid'])
                if process.is_running():
                    # Check if it's actually a benchmark process
                    cmdline = ' '.join(process.cmdline())
                    if any(pattern in cmdline.lower() for pattern in
                          ['rmatchbenchmark', 'javanativebenchmark', 'benchmark']):
                        orphan_info['system_status'] = 'running'
                        orphan_info['actual_cmdline'] = cmdline
                        orphaned.append(orphan_info)
                    else:
                        # Not a benchmark process anymore, mark as completed
                        self.update_process_status(row['pid'], 'completed')
                else:
                    self.update_process_status(row['pid'], 'completed')

            except psutil.NoSuchProcess:
                self.update_process_status(row['pid'], 'completed')
            except Exception as e:
                logger.warning(f"Error checking orphaned process {row['pid']}: {e}")

        return orphaned

    def cleanup_orphaned_processes(self, max_processes: int = 50) -> Dict[str, int]:
        """Clean up orphaned benchmark processes."""
        orphaned = self.detect_orphaned_processes()
        results = {'killed': 0, 'failed': 0, 'skipped': 0}

        for proc_info in orphaned[:max_processes]:  # Limit for safety
            pid = proc_info['pid']

            try:
                process = psutil.Process(pid)

                # Mark cleanup attempt
                self.conn.execute("""
                    UPDATE benchmark_processes
                    SET cleanup_attempted = TRUE, cleanup_method = 'SIGTERM',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE pid = ?
                """, (pid,))

                # Try graceful termination first
                process.terminate()

                # Wait a bit, then force kill if needed
                try:
                    process.wait(timeout=3)
                    self.update_process_status(pid, 'killed', process.returncode)
                    self._mark_cleanup_success(pid, 'SIGTERM')
                    results['killed'] += 1
                    logger.info(f"Gracefully terminated orphaned process {pid}")

                except psutil.TimeoutExpired:
                    process.kill()
                    self.update_process_status(pid, 'killed', -9)
                    self._mark_cleanup_success(pid, 'SIGKILL')
                    results['killed'] += 1
                    logger.info(f"Force killed orphaned process {pid}")

            except psutil.NoSuchProcess:
                self.update_process_status(pid, 'completed')
                results['skipped'] += 1
            except Exception as e:
                logger.error(f"Failed to clean up process {pid}: {e}")
                self._mark_cleanup_failed(pid)
                results['failed'] += 1

        self.conn.commit()
        return results

    def get_process_stats(self) -> Dict[str, Any]:
        """Get process tracking statistics."""
        cursor = self.conn.execute("""
            SELECT
                status,
                COUNT(*) as count,
                AVG(duration_seconds) as avg_duration,
                MAX(cpu_percent_peak) as max_cpu,
                MAX(memory_mb_peak) as max_memory
            FROM benchmark_processes
            GROUP BY status
        """)

        stats = {}
        for row in cursor.fetchall():
            stats[row['status']] = {
                'count': row['count'],
                'avg_duration': row['avg_duration'],
                'max_cpu': row['max_cpu'],
                'max_memory': row['max_memory']
            }

        # Add current system processes matching our patterns
        current_benchmark_procs = []
        for proc in psutil.process_iter(['pid', 'cmdline']):
            try:
                cmdline = ' '.join(proc.info['cmdline'] or [])
                if any(pattern in cmdline.lower() for pattern in
                      ['rmatchbenchmark', 'javanativebenchmark']):
                    current_benchmark_procs.append(proc.info['pid'])
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue

        stats['system_benchmark_processes'] = len(current_benchmark_procs)
        stats['system_process_pids'] = current_benchmark_procs

        return stats

    def _insert_process(self, process_info: ProcessInfo, psutil_process: psutil.Process):
        """Insert process information into database."""
        try:
            cpu_percent = psutil_process.cpu_percent()
            memory_info = psutil_process.memory_info()
            memory_mb = memory_info.rss / (1024 * 1024)

            self.conn.execute("""
                INSERT INTO benchmark_processes (
                    pid, ppid, job_id, run_id, engine_name, command_line,
                    cwd, executable, status, started_at,
                    cpu_percent_peak, memory_mb_peak,
                    last_monitored_at, monitor_count,
                    parent_benchmark_pid
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 1, ?)
            """, (
                process_info.pid, process_info.ppid, process_info.job_id,
                process_info.run_id, process_info.engine_name, process_info.command_line,
                psutil_process.cwd(), psutil_process.exe(), process_info.status,
                process_info.started_at, cpu_percent, memory_mb, psutil_process.ppid()
            ))

        except Exception as e:
            logger.warning(f"Failed to insert detailed process info: {e}")
            # Fallback to basic info
            self.conn.execute("""
                INSERT INTO benchmark_processes (
                    pid, job_id, run_id, engine_name, command_line, status
                ) VALUES (?, ?, ?, ?, ?, ?)
            """, (
                process_info.pid, process_info.job_id, process_info.run_id,
                process_info.engine_name, process_info.command_line, process_info.status
            ))

    def _update_process_resources(self, pid: int, process: psutil.Process):
        """Update process resource usage."""
        try:
            cpu_percent = process.cpu_percent()
            memory_mb = process.memory_info().rss / (1024 * 1024)

            self.conn.execute("""
                UPDATE benchmark_processes
                SET cpu_percent_peak = MAX(COALESCE(cpu_percent_peak, 0), ?),
                    memory_mb_peak = MAX(COALESCE(memory_mb_peak, 0), ?),
                    last_monitored_at = CURRENT_TIMESTAMP,
                    monitor_count = monitor_count + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE pid = ?
            """, (cpu_percent, memory_mb, pid))

        except Exception as e:
            logger.debug(f"Failed to update resources for process {pid}: {e}")

    def _mark_cleanup_success(self, pid: int, method: str):
        """Mark successful process cleanup."""
        self.conn.execute("""
            UPDATE benchmark_processes
            SET cleanup_success = TRUE, cleanup_method = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE pid = ?
        """, (method, pid))

    def _mark_cleanup_failed(self, pid: int):
        """Mark failed process cleanup."""
        self.conn.execute("""
            UPDATE benchmark_processes
            SET cleanup_success = FALSE,
                updated_at = CURRENT_TIMESTAMP
            WHERE pid = ?
        """, (pid,))