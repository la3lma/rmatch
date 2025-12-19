#!/usr/bin/env python3
"""
Minimal test to isolate the subprocess hanging issue with rmatch.
Test different subprocess approaches to identify the exact cause.
"""

import subprocess
import time
import os
from pathlib import Path

def test_approach_1_basic_popen():
    """Test 1: Basic subprocess.Popen (current framework approach)"""
    print("🔬 Test 1: Basic subprocess.Popen (CURRENT FRAMEWORK)")
    print("-" * 60)

    try:
        command = [
            "bash", "runner.sh",
            "../../results/quick_20251219_150730/data/patterns_10.txt",
            "../../results/quick_20251219_150730/data/corpus_100KB.txt"
        ]

        start_time = time.time()

        process = subprocess.Popen(
            command,
            cwd="engines/rmatch",
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            stdin=subprocess.DEVNULL,
            text=True
        )

        # Short timeout to see if it hangs
        stdout, stderr = process.communicate(timeout=5)

        duration = time.time() - start_time
        print(f"✅ SUCCESS: Completed in {duration:.2f}s")
        print(f"Exit code: {process.returncode}")
        print(f"Stdout preview: {stdout[:200]}...")
        return True

    except subprocess.TimeoutExpired:
        print("❌ HUNG: Process hung for 5+ seconds")
        process.kill()
        return False
    except Exception as e:
        print(f"❌ ERROR: {e}")
        return False

def test_approach_2_direct_call():
    """Test 2: Direct subprocess.call"""
    print("🔬 Test 2: Direct subprocess.call")
    print("-" * 60)

    try:
        command = [
            "bash", "runner.sh",
            "../../results/quick_20251219_150730/data/patterns_10.txt",
            "../../results/quick_20251219_150730/data/corpus_100KB.txt"
        ]

        start_time = time.time()

        # Use subprocess.call with timeout
        result = subprocess.run(
            command,
            cwd="engines/rmatch",
            capture_output=True,
            text=True,
            timeout=5
        )

        duration = time.time() - start_time
        print(f"✅ SUCCESS: Completed in {duration:.2f}s")
        print(f"Exit code: {result.returncode}")
        print(f"Stdout preview: {result.stdout[:200]}...")
        return True

    except subprocess.TimeoutExpired:
        print("❌ HUNG: Process hung for 5+ seconds")
        return False
    except Exception as e:
        print(f"❌ ERROR: {e}")
        return False

def test_approach_3_no_pipes():
    """Test 3: subprocess.Popen with no pipes (inherit stdio)"""
    print("🔬 Test 3: subprocess.Popen with inherited stdio")
    print("-" * 60)

    try:
        command = [
            "bash", "runner.sh",
            "../../results/quick_20251219_150730/data/patterns_10.txt",
            "../../results/quick_20251219_150730/data/corpus_100KB.txt"
        ]

        start_time = time.time()

        process = subprocess.Popen(
            command,
            cwd="engines/rmatch",
            # No pipes - inherit from parent
        )

        # Wait with timeout
        try:
            process.wait(timeout=5)
            duration = time.time() - start_time
            print(f"✅ SUCCESS: Completed in {duration:.2f}s")
            print(f"Exit code: {process.returncode}")
            return True
        except subprocess.TimeoutExpired:
            print("❌ HUNG: Process hung for 5+ seconds")
            process.kill()
            return False

    except Exception as e:
        print(f"❌ ERROR: {e}")
        return False

def test_approach_4_shell_true():
    """Test 4: Using shell=True"""
    print("🔬 Test 4: subprocess with shell=True")
    print("-" * 60)

    try:
        command = "bash runner.sh ../../results/quick_20251219_150730/data/patterns_10.txt ../../results/quick_20251219_150730/data/corpus_100KB.txt"

        start_time = time.time()

        result = subprocess.run(
            command,
            cwd="engines/rmatch",
            shell=True,
            capture_output=True,
            text=True,
            timeout=5
        )

        duration = time.time() - start_time
        print(f"✅ SUCCESS: Completed in {duration:.2f}s")
        print(f"Exit code: {result.returncode}")
        print(f"Stdout preview: {result.stdout[:200]}...")
        return True

    except subprocess.TimeoutExpired:
        print("❌ HUNG: Process hung for 5+ seconds")
        return False
    except Exception as e:
        print(f"❌ ERROR: {e}")
        return False

def test_approach_5_buffering():
    """Test 5: Different buffering strategies"""
    print("🔬 Test 5: subprocess.Popen with unbuffered output")
    print("-" * 60)

    try:
        command = [
            "bash", "runner.sh",
            "../../results/quick_20251219_150730/data/patterns_10.txt",
            "../../results/quick_20251219_150730/data/corpus_100KB.txt"
        ]

        start_time = time.time()

        process = subprocess.Popen(
            command,
            cwd="engines/rmatch",
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            stdin=subprocess.DEVNULL,
            text=True,
            bufsize=0,  # Unbuffered
            universal_newlines=True
        )

        stdout, stderr = process.communicate(timeout=5)

        duration = time.time() - start_time
        print(f"✅ SUCCESS: Completed in {duration:.2f}s")
        print(f"Exit code: {process.returncode}")
        print(f"Stdout preview: {stdout[:200]}...")
        return True

    except subprocess.TimeoutExpired:
        print("❌ HUNG: Process hung for 5+ seconds")
        process.kill()
        return False
    except Exception as e:
        print(f"❌ ERROR: {e}")
        return False

def main():
    print("🚀 Subprocess Hanging Investigation")
    print("=" * 70)
    print("Testing different subprocess approaches to isolate hanging issue...")
    print()

    # Verify files exist
    patterns_file = Path("results/quick_20251219_150730/data/patterns_10.txt")
    corpus_file = Path("results/quick_20251219_150730/data/corpus_100KB.txt")
    runner_file = Path("engines/rmatch/runner.sh")

    if not patterns_file.exists():
        print(f"❌ ERROR: Patterns file not found: {patterns_file}")
        return
    if not corpus_file.exists():
        print(f"❌ ERROR: Corpus file not found: {corpus_file}")
        return
    if not runner_file.exists():
        print(f"❌ ERROR: Runner script not found: {runner_file}")
        return

    print(f"✅ Files verified:")
    print(f"   📄 Patterns: {patterns_file} ({patterns_file.stat().st_size} bytes)")
    print(f"   📄 Corpus: {corpus_file} ({corpus_file.stat().st_size} bytes)")
    print(f"   📄 Runner: {runner_file}")
    print()

    # Run tests
    results = []

    tests = [
        ("Basic Popen (Framework)", test_approach_1_basic_popen),
        ("Direct subprocess.run", test_approach_2_direct_call),
        ("Popen no pipes", test_approach_3_no_pipes),
        ("Shell=True", test_approach_4_shell_true),
        ("Unbuffered Popen", test_approach_5_buffering),
    ]

    for name, test_func in tests:
        try:
            print()
            success = test_func()
            results.append((name, success))
            print()
        except KeyboardInterrupt:
            print("🛑 Test interrupted")
            break
        except Exception as e:
            print(f"🚨 Test crashed: {e}")
            results.append((name, False))
            print()

    # Summary
    print("🎯 SUMMARY")
    print("=" * 70)
    for name, success in results:
        status = "✅ WORKS" if success else "❌ HANGS/FAILS"
        print(f"  {status:<12} {name}")

    print()
    working = [name for name, success in results if success]
    hanging = [name for name, success in results if not success]

    if working:
        print(f"🟢 Working approaches: {', '.join(working)}")
    if hanging:
        print(f"🔴 Hanging approaches: {', '.join(hanging)}")

    if working and hanging:
        print()
        print("💡 CONCLUSION: The issue is specific to certain subprocess configurations!")
        print("   Use working approaches to fix the framework.")
    elif hanging and not working:
        print()
        print("⚠️  CONCLUSION: All subprocess approaches hang - deeper Java/system issue.")

if __name__ == "__main__":
    main()