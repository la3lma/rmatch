#!/usr/bin/env python3
"""
Test script for database infrastructure.
"""

import sys
import tempfile
import logging
from pathlib import Path

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent))

from regex_bench.database.job_queue import JobQueue, BenchmarkJob
from regex_bench.engines.base import EngineResult

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def test_basic_database_operations():
    """Test basic database creation and job operations."""

    with tempfile.TemporaryDirectory() as temp_dir:
        db_path = Path(temp_dir) / "test.db"
        logger.info(f"Testing database at {db_path}")

        # Initialize job queue (creates database)
        queue = JobQueue(db_path)

        # Test 0: Create a system profile (required for foreign key)
        queue.conn.execute("""
            INSERT INTO system_profiles (
                profile_id, hostname, cpu_model, cpu_architecture,
                cpu_physical_cores, cpu_logical_cores, memory_total_gb,
                memory_available_gb, storage_available_gb, os_name,
                os_version, python_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            "dummy_profile", "test_host", "Test CPU", "x86_64",
            4, 8, 16.0, 8.0, 100.0, "TestOS", "1.0", "3.11"
        ))
        queue.conn.commit()
        logger.info("Created test system profile")

        # Create a benchmark run (required for foreign key)
        queue.conn.execute("""
            INSERT INTO benchmark_runs (
                run_id, config_path, config_json, system_profile_id,
                created_by, status
            ) VALUES (?, ?, ?, ?, ?, ?)
        """, (
            "test_run_001", "test_config.json", '{"test": true}',
            "dummy_profile", "test_user", "PREPARING"
        ))
        queue.conn.commit()
        logger.info("Created test benchmark run")

        # Test 1: Create a test job
        job = BenchmarkJob.create_new(
            run_id="test_run_001",
            engine_name="rmatch",
            pattern_count=10,
            input_size="1MB",
            input_size_bytes=1024*1024,
            iteration=1,
            pattern_suite="log_mining",
            corpus_name="test_corpus",
            config_hash="abc123"
        )

        logger.info(f"Created job: {job.job_id}")

        # Test 2: Enqueue the job
        queue.enqueue_job(job)
        logger.info(f"Enqueued job successfully")

        # Test 3: Check job count
        job_count = queue.get_job_count("test_run_001")
        assert job_count == 1, f"Expected 1 job, got {job_count}"
        logger.info(f"Job count correct: {job_count}")

        # Test 4: Get next job (should mark as RUNNING)
        next_job = queue.get_next_job("test_run_001")
        assert next_job is not None, "Should have gotten a job"
        assert next_job.status == 'RUNNING', f"Job status should be RUNNING, got {next_job.status}"
        logger.info(f"Retrieved job {next_job.job_id} with status {next_job.status}")

        # Test 5: Update job with result
        test_result = EngineResult(
            engine_name="rmatch",
            iteration=1,
            status="ok",
            compilation_ns=1000000,  # 1ms
            scanning_ns=5000000,    # 5ms
            total_ns=6000000,       # 6ms
            match_count=42,
            patterns_compiled=10,
            memory_peak_bytes=1024*1024,  # 1MB
            corpus_size_bytes=1024*1024,  # 1MB
            raw_stdout="Test output",
            raw_stderr=""
        )

        queue.update_job_status(next_job.job_id, 'COMPLETED', test_result)
        logger.info("Updated job status to COMPLETED with results")

        # Test 6: Check progress
        progress = queue.get_run_progress("test_run_001")
        assert progress['COMPLETED'] == 1, f"Expected 1 completed job, got {progress['COMPLETED']}"
        assert progress['total'] == 1, f"Expected 1 total job, got {progress['total']}"
        logger.info(f"Progress check passed: {progress}")

        # Test 7: Create second run for multiple jobs test
        queue.conn.execute("""
            INSERT INTO benchmark_runs (
                run_id, config_path, config_json, system_profile_id,
                created_by, status
            ) VALUES (?, ?, ?, ?, ?, ?)
        """, (
            "test_run_002", "test_config.json", '{"test": true}',
            "dummy_profile", "test_user", "PREPARING"
        ))
        queue.conn.commit()
        logger.info("Created second test benchmark run")

        # Create multiple jobs to test ordering
        for i in range(3):
            job = BenchmarkJob.create_new(
                run_id="test_run_002",
                engine_name=f"engine_{i}",
                pattern_count=100,
                input_size="10MB",
                input_size_bytes=10*1024*1024,
                iteration=1,
                pattern_suite="log_mining",
                corpus_name="test_corpus",
                config_hash="def456"
            )
            queue.enqueue_job(job)

        # Test 8: Verify job ordering (FIFO)
        first_job = queue.get_next_job("test_run_002")
        assert first_job.engine_name == "engine_0", f"Expected engine_0, got {first_job.engine_name}"
        logger.info(f"Job ordering correct: got {first_job.engine_name}")

        # Test 9: Check that no more jobs for run_001
        no_job = queue.get_next_job("test_run_001")
        assert no_job is None, "Should not have gotten another job for completed run"
        logger.info("No additional jobs for completed run - correct")

        queue.close()
        logger.info("All database tests passed!")

        return True


def test_job_dataclass():
    """Test BenchmarkJob dataclass functionality."""

    # Test job creation
    job = BenchmarkJob.create_new(
        run_id="test_run",
        engine_name="test_engine",
        pattern_count=1000,
        input_size="100MB",
        input_size_bytes=100*1024*1024,
        iteration=5,
        pattern_suite="test_suite",
        corpus_name="test_corpus",
        config_hash="test_hash"
    )

    assert job.job_id is not None, "Job ID should be generated"
    assert len(job.job_id) > 10, "Job ID should be a reasonable UUID"
    assert job.status == 'QUEUED', f"Initial status should be QUEUED, got {job.status}"
    assert job.created_at is not None, "Created timestamp should be set"

    logger.info(f"Job dataclass tests passed for job {job.job_id}")
    return True


if __name__ == "__main__":
    try:
        logger.info("Starting database tests...")

        # Test 1: Job dataclass
        test_job_dataclass()

        # Test 2: Database operations
        test_basic_database_operations()

        logger.info("🎉 All tests passed! Database infrastructure is working correctly.")

    except Exception as e:
        logger.error(f"❌ Test failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)