#!/usr/bin/env python3
"""Direct test of file_runner.sh without framework imports."""

import subprocess
import time
import tempfile
from pathlib import Path

def test_file_runner_direct():
    """Test file_runner.sh directly."""
    print("🧪 Testing file_runner.sh Direct")
    print("=" * 50)

    # Setup test data
    patterns_file = Path("results/quick_20251219_150730/data/patterns_10.txt")
    corpus_file = Path("results/quick_20251219_150730/data/corpus_100KB.txt")

    if not patterns_file.exists():
        print(f"❌ Patterns file not found: {patterns_file}")
        return False
    if not corpus_file.exists():
        print(f"❌ Corpus file not found: {corpus_file}")
        return False

    print(f"✅ Test files found:")
    print(f"   📄 Patterns: {patterns_file}")
    print(f"   📄 Corpus: {corpus_file}")

    try:
        # Create temporary output files
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            stdout_file = temp_path / "stdout.txt"
            stderr_file = temp_path / "stderr.txt"

            print(f"   📁 Temp dir: {temp_path}")
            print()

            # Test file_runner.sh
            command = [
                "bash", "engines/rmatch/file_runner.sh",
                str(patterns_file.resolve()),
                str(corpus_file.resolve()),
                str(stdout_file.resolve()),
                str(stderr_file.resolve())
            ]

            print("🚀 Running file_runner.sh...")
            print(f"   Command: {' '.join(command)}")

            start_time = time.time()

            # Run without pipes - this should not hang!
            result = subprocess.run(
                command,
                timeout=10,  # Should complete quickly
                text=True
            )

            duration = time.time() - start_time

            print(f"⏱️  Completed in {duration:.3f} seconds")
            print(f"   Exit code: {result.returncode}")

            # Check output files
            stdout_content = ""
            stderr_content = ""

            if stdout_file.exists():
                stdout_content = stdout_file.read_text()
                print(f"   📤 Stdout file size: {len(stdout_content)} chars")
            else:
                print("   ❌ No stdout file created")

            if stderr_file.exists():
                stderr_content = stderr_file.read_text()
                print(f"   📤 Stderr file size: {len(stderr_content)} chars")
            else:
                print("   ❌ No stderr file created")

            print()
            print("📊 Output Preview:")
            if stdout_content:
                print("   Stdout:")
                for line in stdout_content.split('\n')[:5]:
                    if line.strip():
                        print(f"      {line}")

            if stderr_content:
                print("   Stderr:")
                for line in stderr_content.split('\n')[:3]:
                    if line.strip():
                        print(f"      {line}")

            # Check for expected output patterns
            success_indicators = [
                "COMPILATION_NS=" in stdout_content,
                "ELAPSED_NS=" in stdout_content,
                "MATCHES=" in stdout_content,
                "PATTERNS_COMPILED=" in stdout_content,
                result.returncode == 0
            ]

            print()
            print("🔍 Success Indicators:")
            indicators = [
                "COMPILATION_NS found",
                "ELAPSED_NS found",
                "MATCHES found",
                "PATTERNS_COMPILED found",
                "Exit code 0"
            ]

            for indicator, success in zip(indicators, success_indicators):
                status = "✅" if success else "❌"
                print(f"   {status} {indicator}")

            overall_success = all(success_indicators)

            if overall_success:
                print("\n🎉 SUCCESS: file_runner.sh works perfectly!")
                print("   File-based communication avoids subprocess hanging!")
            else:
                print("\n❌ FAILED: file_runner.sh did not work as expected")

            return overall_success

    except subprocess.TimeoutExpired:
        print("❌ TIMEOUT: file_runner.sh hung!")
        return False
    except Exception as e:
        print(f"❌ ERROR: {e}")
        return False

if __name__ == "__main__":
    success = test_file_runner_direct()
    print()
    if success:
        print("🎯 CONCLUSION: File-based solution successfully bypasses subprocess hanging!")
    else:
        print("🚨 CONCLUSION: File-based solution still has issues.")

    exit(0 if success else 1)