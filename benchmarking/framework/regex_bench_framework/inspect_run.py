#!/usr/bin/env python3
"""
Detailed inspection of a benchmark run.
"""

import sys
import sqlite3
from pathlib import Path
from datetime import datetime

def inspect_run(db_path):
    """Inspect a benchmark run database for detailed information."""
    if not db_path.exists():
        print(f"❌ Database not found: {db_path}")
        return

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    print(f"🔍 Inspecting: {db_path}")
    print("=" * 60)

    # Get run information
    runs = conn.execute("""
        SELECT run_id, config_path, status, created_at, started_at, completed_at, created_by
        FROM benchmark_runs
        ORDER BY created_at DESC
    """).fetchall()

    for run in runs:
        print(f"\n📋 Run ID: {run['run_id']}")
        print(f"   Config: {run['config_path']}")
        print(f"   Status: {run['status']}")
        print(f"   Created: {run['created_at']}")
        print(f"   Started: {run['started_at']}")
        print(f"   Completed: {run['completed_at']}")
        print(f"   Created by: {run['created_by']}")

        # Get job statistics
        job_stats = conn.execute("""
            SELECT
                status,
                COUNT(*) as count,
                MIN(created_at) as first_job,
                MAX(completed_at) as last_job
            FROM benchmark_jobs
            WHERE run_id = ?
            GROUP BY status
        """, (run['run_id'],)).fetchall()

        print(f"\n   📊 Job Statistics:")
        total_jobs = 0
        for stat in job_stats:
            print(f"      {stat['status']}: {stat['count']} jobs")
            total_jobs += stat['count']
        print(f"      TOTAL: {total_jobs} jobs")

        # Get timing information
        timing_info = conn.execute("""
            SELECT
                MIN(created_at) as first_job_created,
                MIN(started_at) as first_job_started,
                MAX(completed_at) as last_job_completed,
                COUNT(*) as total_jobs
            FROM benchmark_jobs
            WHERE run_id = ?
        """, (run['run_id'],)).fetchone()

        if timing_info and timing_info['total_jobs'] > 0:
            print(f"\n   ⏱️  Execution Timeline:")
            print(f"      First job created: {timing_info['first_job_created']}")
            print(f"      First job started: {timing_info['first_job_started']}")
            print(f"      Last job completed: {timing_info['last_job_completed']}")

            # Calculate duration
            if timing_info['first_job_started'] and timing_info['last_job_completed']:
                try:
                    start_time = datetime.fromisoformat(timing_info['first_job_started'].replace('Z', '+00:00'))
                    end_time = datetime.fromisoformat(timing_info['last_job_completed'].replace('Z', '+00:00'))
                    duration = end_time - start_time
                    print(f"      Duration: {duration}")
                except Exception as e:
                    print(f"      Duration: Could not calculate ({e})")

        # Show sample of failed jobs if any exist
        failed_jobs = conn.execute("""
            SELECT job_id, engine_name, error_message, created_at, completed_at
            FROM benchmark_jobs
            WHERE run_id = ? AND status = 'FAILED'
            LIMIT 5
        """, (run['run_id'],)).fetchall()

        if failed_jobs:
            print(f"\n   ❌ Sample Failed Jobs:")
            for job in failed_jobs:
                error_msg = job['error_message'] or 'No error message'
                print(f"      {job['engine_name']}: {error_msg[:100]}...")

        # Show sample of completed jobs
        completed_jobs = conn.execute("""
            SELECT job_id, engine_name, pattern_count, input_size, iteration, completed_at
            FROM benchmark_jobs
            WHERE run_id = ? AND status = 'COMPLETED'
            LIMIT 5
        """, (run['run_id'],)).fetchall()

        if completed_jobs:
            print(f"\n   ✅ Sample Completed Jobs:")
            for job in completed_jobs:
                print(f"      {job['engine_name']}: {job['pattern_count']} patterns, {job['input_size']}, iter {job['iteration']}")

    conn.close()

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 inspect_run.py <path_to_jobs.db>")
        sys.exit(1)

    db_path = Path(sys.argv[1])
    inspect_run(db_path)