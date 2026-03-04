#!/usr/bin/env python3
"""
Test script to verify SQLite threading fix.
"""

import sys
import tempfile
import logging
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent))

from regex_bench.database import JobQueue, BenchmarkJob
from regex_bench.engines.base import EngineResult

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def test_thread_worker(job_data, db_path):
    """Simulate what a worker thread does."""
    # Create thread-local database connection
    thread_queue = JobQueue(db_path)

    try:
        job_id, engine_name = job_data

        # Simulate job update
        result = EngineResult(
            engine_name=engine_name,
            iteration=1,
            status="ok",
            compilation_ns=1000000,
            scanning_ns=5000000,
            total_ns=6000000
        )

        thread_queue.update_job_status(job_id, 'COMPLETED', result)
        logger.info(f"Thread updated job {job_id} successfully")

        return True

    finally:
        thread_queue.close()


def test_sqlite_threading():
    """Test that SQLite threading issue is resolved."""

    with tempfile.TemporaryDirectory() as temp_dir:
        db_path = Path(temp_dir) / "test.db"

        # Initialize main database connection
        main_queue = JobQueue(db_path)

        # Create test run and system profile
        main_queue.conn.execute("""
            INSERT INTO system_profiles (
                profile_id, hostname, cpu_model, cpu_architecture,
                cpu_physical_cores, cpu_logical_cores, memory_total_gb,
                memory_available_gb, storage_available_gb, os_name,
                os_version, python_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            "test_profile", "test_host", "Test CPU", "x86_64",
            4, 8, 16.0, 8.0, 100.0, "TestOS", "1.0", "3.11"
        ))

        main_queue.conn.execute("""
            INSERT INTO benchmark_runs (
                run_id, config_path, config_json, system_profile_id,
                created_by, status
            ) VALUES (?, ?, ?, ?, ?, ?)
        """, (
            "test_run", "test_config.json", '{"test": true}',
            "test_profile", "test_user", "PREPARING"
        ))
        main_queue.conn.commit()

        # Create test jobs
        jobs = []
        for i in range(4):  # Test with 4 parallel jobs
            job = BenchmarkJob.create_new(
                run_id="test_run",
                engine_name=f"engine_{i}",
                pattern_count=100,
                input_size="10MB",
                input_size_bytes=10*1024*1024,
                iteration=1,
                pattern_suite="test_suite",
                corpus_name="test_corpus",
                config_hash="test_hash"
            )
            main_queue.enqueue_job(job)
            jobs.append((job.job_id, job.engine_name))

        logger.info(f"Created {len(jobs)} test jobs")

        # Test parallel execution with ThreadPoolExecutor
        logger.info("Testing parallel database updates...")

        with ThreadPoolExecutor(max_workers=4) as executor:
            futures = []
            for job_data in jobs:
                future = executor.submit(test_thread_worker, job_data, db_path)
                futures.append(future)

            # Wait for all threads to complete
            results = [future.result() for future in futures]

        # Verify all jobs were updated successfully
        success_count = sum(1 for r in results if r)
        logger.info(f"Successfully updated {success_count}/{len(jobs)} jobs")

        # Verify database state
        cursor = main_queue.conn.execute(
            "SELECT COUNT(*) FROM benchmark_jobs WHERE status = 'COMPLETED'"
        )
        completed_count = cursor.fetchone()[0]

        main_queue.close()

        assert success_count == len(jobs), f"Expected {len(jobs)} successes, got {success_count}"
        assert completed_count == len(jobs), f"Expected {len(jobs)} completed in DB, got {completed_count}"

        logger.info("🎉 SQLite threading test passed!")
        return True


if __name__ == "__main__":
    try:
        logger.info("Testing SQLite threading fix...")
        success = test_sqlite_threading()

        if success:
            print("\n" + "="*60)
            print("SQLITE THREADING FIX SUCCESS")
            print("="*60)
            print("✅ Thread-local database connections working")
            print("✅ Parallel job updates successful")
            print("✅ No SQLite threading errors")
            print("✅ Database state consistent")
            print("="*60)

        sys.exit(0 if success else 1)

    except Exception as e:
        logger.error(f"❌ Threading test failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)