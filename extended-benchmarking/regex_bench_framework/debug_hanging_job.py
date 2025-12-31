#!/usr/bin/env python3
"""
Debug which job was hanging when timeout occurred.
"""

import sys
import sqlite3
from pathlib import Path

def debug_hanging_job(db_path):
    """Find the job that was hanging when timeout occurred."""
    if not db_path.exists():
        print(f"❌ Database not found: {db_path}")
        return

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    # Get the last job that was started but never completed
    hanging_jobs = conn.execute("""
        SELECT
            job_id, engine_name, pattern_count, input_size, iteration,
            status, started_at, completed_at,
            (CASE WHEN started_at IS NOT NULL AND completed_at IS NULL THEN 1 ELSE 0 END) as is_hanging
        FROM benchmark_jobs
        WHERE status = 'RUNNING' OR (started_at IS NOT NULL AND completed_at IS NULL)
        ORDER BY started_at DESC
    """).fetchall()

    print("🔍 Jobs that may have been hanging:")
    for job in hanging_jobs:
        print(f"   {job['engine_name']}: {job['pattern_count']} patterns, {job['input_size']}, iter {job['iteration']}")
        print(f"      Status: {job['status']}, Started: {job['started_at']}, Completed: {job['completed_at']}")

    # Get the last completed job to see what was working
    last_completed = conn.execute("""
        SELECT engine_name, pattern_count, input_size, iteration, completed_at
        FROM benchmark_jobs
        WHERE status = 'COMPLETED'
        ORDER BY completed_at DESC
        LIMIT 1
    """).fetchone()

    if last_completed:
        print(f"\n✅ Last successful job:")
        print(f"   {last_completed['engine_name']}: {last_completed['pattern_count']} patterns, {last_completed['input_size']}, iter {last_completed['iteration']}")
        print(f"   Completed at: {last_completed['completed_at']}")

    # Get the next job that would have run
    next_job = conn.execute("""
        SELECT engine_name, pattern_count, input_size, iteration
        FROM benchmark_jobs
        WHERE status = 'QUEUED'
        ORDER BY created_at
        LIMIT 1
    """).fetchone()

    if next_job:
        print(f"\n⏭️ Next job that would have run:")
        print(f"   {next_job['engine_name']}: {next_job['pattern_count']} patterns, {next_job['input_size']}, iter {next_job['iteration']}")

    conn.close()

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 debug_hanging_job.py <path_to_jobs.db>")
        print("Example: python3 debug_hanging_job.py results/phase1_20251220_144811/jobs.db")
        sys.exit(1)

    db_path = Path(sys.argv[1])
    debug_hanging_job(db_path)