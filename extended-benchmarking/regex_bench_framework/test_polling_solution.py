#!/usr/bin/env python3
"""Test the polling-based rmatch solution."""

import sys
import subprocess
import tempfile
import time
import uuid
from pathlib import Path

def test_polling_solution():
    """Test rmatch with polling-based completion detection."""
    print("🔬 Testing Polling-Based rmatch Solution")
    print("=" * 60)

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
        # Create temporary output directory
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            temp_id = str(uuid.uuid4())[:8]
            stdout_file = temp_path / f"rmatch_stdout_{temp_id}.txt"
            stderr_file = temp_path / f"rmatch_stderr_{temp_id}.txt"

            print(f"   📁 Temp dir: {temp_path}")
            print(f"   📤 Stdout file: {stdout_file.name}")
            print(f"   📤 Stderr file: {stderr_file.name}")
            print()

            # Use file-based runner (like the framework)
            command = [
                "bash", "engines/rmatch/file_runner.sh",
                str(patterns_file.resolve()),
                str(corpus_file.resolve()),
                str(stdout_file.resolve()),
                str(stderr_file.resolve())
            ]

            print("🚀 Starting rmatch with polling-based detection...")
            print(f"   Command: {' '.join(command[:3])} <files>")

            start_time = time.time()

            # Start process without pipes
            process = subprocess.Popen(
                command,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                start_new_session=True
            )

            print(f"   🔢 Process PID: {process.pid}")

            # Implement the same polling logic as the framework
            poll_interval = 0.1  # 100ms
            max_wait_time = 10.0  # 10 seconds should be enough
            elapsed = 0.0
            completion_detected = False

            print("   ⏳ Polling for completion...")

            while elapsed < max_wait_time:
                # Check if process terminated normally
                poll_result = process.poll()
                if poll_result is not None:
                    print(f"   ✅ Process terminated normally (exit code: {poll_result})")
                    completion_detected = True
                    break

                # Check for output files indicating completion
                if stdout_file.exists() and stderr_file.exists():
                    try:
                        stdout_content = stdout_file.read_text()
                        stderr_content = stderr_file.read_text()

                        # Check for rmatch completion indicators
                        has_results = (
                            "COMPILATION_NS=" in stdout_content and
                            "ELAPSED_NS=" in stdout_content and
                            "MATCHES=" in stdout_content and
                            "PATTERNS_COMPILED=" in stdout_content
                        )

                        has_completion_msg = "Benchmark completed:" in stderr_content

                        if has_results and has_completion_msg:
                            print(f"   🎯 Completion detected via output analysis!")
                            print(f"   📊 Results found: {has_results}")
                            print(f"   📝 Completion message found: {has_completion_msg}")

                            # Force process termination (like the framework)
                            try:
                                process.terminate()
                                time.sleep(0.1)
                                if process.poll() is None:
                                    process.kill()
                                print(f"   🛑 Process forcibly terminated")
                            except:
                                pass

                            completion_detected = True
                            break

                    except (FileNotFoundError, PermissionError):
                        # Files not ready yet
                        pass

                # Sleep before next poll
                time.sleep(poll_interval)
                elapsed += poll_interval

                # Progress indicator
                if int(elapsed * 10) % 10 == 0:  # Every second
                    print(f"   ⏱️  Elapsed: {elapsed:.1f}s")

            duration = time.time() - start_time

            if not completion_detected:
                print(f"   ❌ TIMEOUT: No completion detected after {max_wait_time}s")
                try:
                    process.kill()
                except:
                    pass
                return False

            print(f"   ⏱️  Total time: {duration:.3f} seconds")

            # Read and display results
            stdout_content = ""
            stderr_content = ""

            if stdout_file.exists():
                stdout_content = stdout_file.read_text()
                print(f"   📊 Stdout size: {len(stdout_content)} chars")

            if stderr_file.exists():
                stderr_content = stderr_file.read_text()
                print(f"   📊 Stderr size: {len(stderr_content)} chars")

            print()
            print("📈 Results Preview:")
            if stdout_content:
                for line in stdout_content.split('\n')[:6]:
                    if line.strip():
                        print(f"   {line}")

            print()
            print("📝 Execution Log Preview:")
            if stderr_content:
                for line in stderr_content.split('\n')[:3]:
                    if line.strip():
                        print(f"   {line}")

            # Validate results
            success_indicators = [
                "COMPILATION_NS=" in stdout_content,
                "ELAPSED_NS=" in stdout_content,
                "MATCHES=" in stdout_content,
                "PATTERNS_COMPILED=" in stdout_content,
                "Benchmark completed:" in stderr_content
            ]

            print()
            print("🔍 Validation:")
            checks = [
                "Compilation time found",
                "Execution time found",
                "Match count found",
                "Patterns compiled found",
                "Completion message found"
            ]

            for check, success in zip(checks, success_indicators):
                status = "✅" if success else "❌"
                print(f"   {status} {check}")

            overall_success = all(success_indicators)

            if overall_success:
                print("\n🎉 SUCCESS: Polling-based solution works perfectly!")
                print("   ✅ rmatch completed successfully")
                print("   ✅ No subprocess hanging")
                print("   ✅ All expected output captured")
            else:
                print("\n❌ FAILED: Missing expected output")

            return overall_success

    except Exception as e:
        print(f"❌ ERROR: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_polling_solution()
    print()
    if success:
        print("🎯 CONCLUSION: Polling-based solution successfully fixes subprocess hanging!")
        print("   The framework can now use rmatch without hanging issues.")
    else:
        print("🚨 CONCLUSION: Polling-based solution still has issues.")

    exit(0 if success else 1)