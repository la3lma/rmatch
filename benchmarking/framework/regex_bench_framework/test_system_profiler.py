#!/usr/bin/env python3
"""
Test script for SystemProfiler functionality.
"""

import sys
import logging
from pathlib import Path

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent))

from regex_bench.database.system_profiler import SystemProfiler

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def test_system_profiler():
    """Test system profile collection."""

    logger.info("Testing SystemProfiler...")

    # Create profiler
    profiler = SystemProfiler()

    # Collect profile
    profile = profiler.collect_profile()

    # Verify required fields
    required_fields = [
        'profile_id', 'hostname', 'cpu_model', 'cpu_architecture',
        'cpu_physical_cores', 'cpu_logical_cores', 'memory_total_gb',
        'os_name', 'os_version', 'python_version'
    ]

    for field in required_fields:
        assert field in profile, f"Missing required field: {field}"
        assert profile[field] is not None, f"Field {field} is None"

    logger.info(f"✅ Profile ID: {profile['profile_id'][:8]}...")
    logger.info(f"✅ Hostname: {profile['hostname']}")
    logger.info(f"✅ CPU: {profile['cpu_model']}")
    logger.info(f"✅ Cores: {profile['cpu_logical_cores']} logical, {profile['cpu_physical_cores']} physical")
    logger.info(f"✅ Memory: {profile['memory_total_gb']:.1f} GB")
    logger.info(f"✅ OS: {profile['os_name']} {profile['os_version']}")
    logger.info(f"✅ Python: {profile['python_version']}")

    # Check profile ID is stable (same system = same ID)
    profile2 = profiler.collect_profile()
    assert profile['profile_id'] == profile2['profile_id'], "Profile ID should be stable"
    logger.info("✅ Profile ID stability verified")

    # Check dependencies were collected
    if 'key_dependencies_json' in profile:
        import json
        deps = json.loads(profile['key_dependencies_json'])
        logger.info(f"✅ Dependencies collected: {len(deps)} packages")

        # Should have psutil since we use it
        assert 'psutil' in deps, "Should have psutil dependency"
        assert deps['psutil'] != 'not_installed', "psutil should be installed"
        logger.info(f"✅ psutil version: {deps['psutil']}")

    # Check performance baseline
    if profile.get('benchmark_baseline_score'):
        logger.info(f"✅ CPU baseline: {profile['benchmark_baseline_score']:.2f}ms")

    # Check virtualization detection
    logger.info(f"✅ Virtualization: {profile.get('is_virtualized', False)}")
    if profile.get('virtualization_type'):
        logger.info(f"✅ Virtualization type: {profile['virtualization_type']}")

    logger.info("🎉 SystemProfiler test passed!")
    return profile


if __name__ == "__main__":
    try:
        profile = test_system_profiler()

        # Pretty print some key info
        print("\n" + "="*60)
        print("SYSTEM PROFILE SUMMARY")
        print("="*60)
        print(f"Profile ID: {profile['profile_id']}")
        print(f"System: {profile['cpu_model']}")
        print(f"CPU Cores: {profile['cpu_logical_cores']} logical ({profile['cpu_physical_cores']} physical)")
        print(f"Memory: {profile['memory_total_gb']:.1f} GB")
        print(f"Storage: {profile.get('storage_available_gb', 'unknown'):.1f} GB available")
        print(f"OS: {profile['os_name']} {profile['os_version']} ({profile['cpu_architecture']})")
        print(f"Python: {profile['python_version']} ({profile['python_implementation']})")
        if profile.get('benchmark_baseline_score'):
            print(f"CPU Baseline: {profile['benchmark_baseline_score']:.2f}ms")
        print("="*60)

    except Exception as e:
        logger.error(f"❌ Test failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)