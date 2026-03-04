#!/usr/bin/env python3
"""Debug subprocess execution to replicate framework behavior."""

import subprocess
import time
from pathlib import Path

def test_rmatch_subprocess():
    """Test rmatch using exact framework subprocess method."""
    print("🔍 Testing rmatch with Python subprocess.Popen (like framework)...")

    # Use exact paths like framework
    engine_path = Path("engines/rmatch")
    command = [
        "bash", "runner.sh",
        "../../results/quick_20251219_150730/data/patterns_10.txt",
        "../../results/quick_20251219_150730/data/corpus_100KB.txt"
    ]

    print(f"📍 Working directory: {engine_path.absolute()}")
    print(f"🚀 Command: {' '.join(command)}")

    try:
        start_time = time.time_ns()

        # Exactly like the fixed framework does it
        process = subprocess.Popen(
            command,
            cwd=engine_path,  # framework sets cwd=self.base_path
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            stdin=subprocess.DEVNULL,  # Fix Java subprocess hanging issue
            text=True
        )

        print(f"📋 Process PID: {process.pid}")
        print("⏳ Waiting for completion...")

        # Simulate framework's communicate call
        stdout, stderr = process.communicate(timeout=120)  # 2 minute timeout for debug

        end_time = time.time_ns()
        duration_ms = (end_time - start_time) / 1_000_000

        print(f"✅ Process completed in {duration_ms:.1f}ms")
        print(f"📤 Return code: {process.returncode}")
        print(f"📊 Stdout length: {len(stdout)} chars")
        print(f"📊 Stderr length: {len(stderr)} chars")

        if stdout:
            print("📈 Stdout preview:")
            print(stdout[:500] + "..." if len(stdout) > 500 else stdout)

        if stderr:
            print("📈 Stderr preview:")
            print(stderr[:500] + "..." if len(stderr) > 500 else stderr)

        return process.returncode == 0

    except subprocess.TimeoutExpired:
        print("⏱️ TIMEOUT: Process hung for 2 minutes")
        process.kill()
        return False
    except Exception as e:
        print(f"❌ Error: {e}")
        return False

if __name__ == "__main__":
    print("🚀 Framework Subprocess Debug Test")
    print("=" * 50)

    success = test_rmatch_subprocess()

    if success:
        print("\n✅ SUCCESS: rmatch worked with framework-style subprocess!")
    else:
        print("\n❌ FAILED: rmatch hung with framework-style subprocess!")
        print("This confirms the subprocess management issue!")