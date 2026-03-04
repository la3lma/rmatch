#!/usr/bin/env python3
"""
Test integration between SystemProfiler and database storage.
"""

import sys
import tempfile
import logging
from pathlib import Path

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent))

from regex_bench.database import JobQueue, SystemProfiler

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def test_system_profile_database_storage():
    """Test storing and retrieving system profiles from database."""

    with tempfile.TemporaryDirectory() as temp_dir:
        db_path = Path(temp_dir) / "test.db"
        logger.info(f"Testing system profile database storage at {db_path}")

        # Initialize database and profiler
        queue = JobQueue(db_path)
        profiler = SystemProfiler()

        # Collect system profile
        profile = profiler.collect_profile()
        profile_id = profile['profile_id']

        logger.info(f"Collected profile: {profile_id[:8]}...")

        # Store profile in database
        queue.conn.execute("""
            INSERT INTO system_profiles (
                profile_id, hostname, cpu_model, cpu_architecture,
                cpu_physical_cores, cpu_logical_cores, memory_total_gb,
                memory_available_gb, storage_available_gb, os_name,
                os_version, python_version, python_implementation,
                python_compiler, key_dependencies_json, env_variables_json,
                benchmark_baseline_score, is_virtualized, virtualization_type,
                profiled_at, profiled_by, notes, raw_system_info_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            profile['profile_id'], profile['hostname'], profile['cpu_model'],
            profile['cpu_architecture'], profile['cpu_physical_cores'],
            profile['cpu_logical_cores'], profile['memory_total_gb'],
            profile['memory_available_gb'], profile.get('storage_available_gb'),
            profile['os_name'], profile['os_version'], profile['python_version'],
            profile['python_implementation'], profile['python_compiler'],
            profile.get('key_dependencies_json'), profile.get('env_variables_json'),
            profile.get('benchmark_baseline_score'), profile.get('is_virtualized'),
            profile.get('virtualization_type'), profile['profiled_at'],
            profile['profiled_by'], profile['notes'], profile['raw_system_info_json']
        ))
        queue.conn.commit()

        logger.info("✅ System profile stored in database")

        # Retrieve profile from database
        cursor = queue.conn.execute(
            "SELECT * FROM system_profiles WHERE profile_id = ?",
            (profile_id,)
        )
        retrieved_row = cursor.fetchone()

        assert retrieved_row is not None, "Should have retrieved the profile"
        assert retrieved_row['profile_id'] == profile_id, "Profile ID should match"
        assert retrieved_row['hostname'] == profile['hostname'], "Hostname should match"
        assert retrieved_row['cpu_model'] == profile['cpu_model'], "CPU model should match"

        logger.info("✅ System profile retrieved successfully")
        logger.info(f"Retrieved: {retrieved_row['cpu_model']} | "
                   f"{retrieved_row['cpu_logical_cores']} cores | "
                   f"{retrieved_row['memory_total_gb']}GB RAM")

        # Test creating a benchmark run with this profile
        queue.conn.execute("""
            INSERT INTO benchmark_runs (
                run_id, config_path, config_json, system_profile_id,
                created_by, status
            ) VALUES (?, ?, ?, ?, ?, ?)
        """, (
            "integration_test_run", "test_config.json", '{"test": true}',
            profile_id, "test_user", "PREPARING"
        ))
        queue.conn.commit()

        logger.info("✅ Created benchmark run with system profile reference")

        # Verify foreign key relationship works
        cursor = queue.conn.execute("""
            SELECT r.run_id, r.status, s.hostname, s.cpu_model
            FROM benchmark_runs r
            JOIN system_profiles s ON r.system_profile_id = s.profile_id
            WHERE r.run_id = ?
        """, ("integration_test_run",))

        join_result = cursor.fetchone()
        assert join_result is not None, "Should have joined data"
        assert join_result['hostname'] == profile['hostname'], "Joined hostname should match"

        logger.info("✅ Foreign key relationship working correctly")

        queue.close()
        logger.info("🎉 System profile database integration test passed!")

        return True


if __name__ == "__main__":
    try:
        logger.info("Starting system profile database integration test...")
        test_system_profile_database_storage()
        logger.info("🎉 All integration tests passed!")

    except Exception as e:
        logger.error(f"❌ Integration test failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)