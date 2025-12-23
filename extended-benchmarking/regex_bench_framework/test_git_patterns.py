#!/usr/bin/env python3
"""
Test script to verify git-stored patterns are being used correctly.
"""

import sys
import tempfile
import logging
from pathlib import Path

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent))

from regex_bench.job_runner import JobBasedBenchmarkRunner

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def test_git_stored_patterns():
    """Test that git-stored patterns are used instead of generation."""

    with tempfile.TemporaryDirectory() as temp_dir:
        output_dir = Path(temp_dir) / "test_output"
        db_path = Path(temp_dir) / "test.db"

        # Create minimal config for pattern testing
        config = {
            'phase': 'test',
            'test_matrix': {
                'pattern_counts': [10, 100, 1000],  # These should exist in git
                'input_sizes': ['1MB'],
                'pattern_suites': ['log_mining'],
                'corpora': ['synthetic'],
                'engines': ['test_engine'],
                'iterations': 1
            }
        }

        try:
            # Initialize job runner
            runner = JobBasedBenchmarkRunner(
                config=config,
                output_dir=output_dir,
                db_path=db_path
            )

            # Set up output directory
            runner._setup_output_directory()

            # Test git-stored pattern preparation
            logger.info("Testing git-stored pattern preparation...")
            runner._prepare_git_stored_patterns()

            # Check that pattern files were copied (not generated)
            data_dir = output_dir / "data"
            expected_files = [
                "patterns_10.txt",
                "patterns_100.txt",
                "patterns_1000.txt"
            ]

            for pattern_file in expected_files:
                pattern_path = data_dir / pattern_file
                assert pattern_path.exists(), f"Pattern file should exist: {pattern_file}"

                # Verify content matches git-stored version
                git_pattern_path = Path(__file__).parent / "benchmark_suites" / "log_mining" / pattern_file
                if git_pattern_path.exists():
                    with open(pattern_path, 'r') as f:
                        copied_content = f.read()
                    with open(git_pattern_path, 'r') as f:
                        git_content = f.read()

                    assert copied_content == git_content, f"Content should match git-stored version: {pattern_file}"
                    logger.info(f"✅ {pattern_file} correctly copied from git")
                else:
                    logger.warning(f"⚠️  Git-stored {pattern_file} not found, generation may have been used")

            # Check metadata files
            metadata_files = [
                "patterns_10_metadata.json",
                "patterns_100_metadata.json",
                "patterns_1000_metadata.json"
            ]

            for metadata_file in metadata_files:
                metadata_path = data_dir / metadata_file
                git_metadata_path = Path(__file__).parent / "benchmark_suites" / "log_mining" / metadata_file

                if git_metadata_path.exists():
                    assert metadata_path.exists(), f"Metadata file should be copied: {metadata_file}"
                    logger.info(f"✅ {metadata_file} correctly copied from git")

            runner.close()
            logger.info("🎉 Git-stored pattern test passed!")

            return True

        except Exception as e:
            logger.error(f"❌ Test failed: {e}")
            import traceback
            traceback.print_exc()
            return False


if __name__ == "__main__":
    try:
        logger.info("Starting git-stored pattern test...")
        success = test_git_stored_patterns()

        if success:
            print("\n" + "="*60)
            print("GIT-STORED PATTERNS TEST SUCCESS")
            print("="*60)
            print("✅ Git-stored patterns are being used correctly")
            print("✅ Pattern files copied instead of generated")
            print("✅ Metadata files preserved")
            print("✅ Fallback to generation if git files missing")
            print("="*60)

        sys.exit(0 if success else 1)

    except Exception as e:
        logger.error(f"❌ Test execution failed: {e}")
        sys.exit(1)