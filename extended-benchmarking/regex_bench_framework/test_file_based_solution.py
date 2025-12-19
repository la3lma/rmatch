#!/usr/bin/env python3
"""Test the file-based rmatch solution."""

import sys
from pathlib import Path
import time

# Add framework to path
sys.path.insert(0, str(Path(__file__).parent / "regex_bench"))

from regex_bench.engines.external import ExternalEngine

def test_file_based_rmatch():
    """Test rmatch with file-based communication."""
    print("🧪 Testing File-Based rmatch Solution")
    print("=" * 60)

    # Setup test data
    patterns_file = Path("results/quick_20251219_150730/data/patterns_10.txt")
    corpus_file = Path("results/quick_20251219_150730/data/corpus_100KB.txt")
    output_dir = Path("/tmp/rmatch_test")
    output_dir.mkdir(exist_ok=True)

    # Check files exist
    if not patterns_file.exists():
        print(f"❌ Patterns file not found: {patterns_file}")
        return False
    if not corpus_file.exists():
        print(f"❌ Corpus file not found: {corpus_file}")
        return False

    print(f"✅ Test files found:")
    print(f"   📄 Patterns: {patterns_file} ({patterns_file.stat().st_size} bytes)")
    print(f"   📄 Corpus: {corpus_file} ({corpus_file.stat().st_size} bytes)")
    print(f"   📁 Output: {output_dir}")
    print()

    try:
        # Create rmatch engine instance
        config = {
            "metadata": {
                "command": ["bash", "runner.sh", "{patterns}", "{corpus}"],
                "output_format": {
                    "match_pattern": r"MATCHES=(\d+)",
                    "time_pattern": r"ELAPSED_NS=(\d+)",
                    "compilation_pattern": r"COMPILATION_NS=(\d+)",
                    "memory_pattern": r"MEMORY_BYTES=(\d+)"
                }
            }
        }

        engine = ExternalEngine("rmatch", config, Path("engines/rmatch"))

        print("🚀 Starting rmatch with file-based communication...")
        start_time = time.time()

        # This should use the new file-based method
        result = engine.run(patterns_file, corpus_file, 1, output_dir)

        duration = time.time() - start_time

        print(f"⏱️  Completed in {duration:.3f} seconds")
        print()

        # Check results
        print("📊 Results:")
        print(f"   Status: {result.status}")
        print(f"   Engine: {result.engine_name}")
        print(f"   Total time: {result.total_ns / 1_000_000:.1f}ms")

        if hasattr(result, 'compilation_ns') and result.compilation_ns:
            print(f"   Compilation: {result.compilation_ns / 1_000_000:.1f}ms")
        if hasattr(result, 'scanning_ns') and result.scanning_ns:
            print(f"   Scanning: {result.scanning_ns / 1_000_000:.1f}ms")
        if hasattr(result, 'match_count') and result.match_count is not None:
            print(f"   Matches: {result.match_count:,}")
        if hasattr(result, 'patterns_compiled') and result.patterns_compiled:
            print(f"   Patterns compiled: {result.patterns_compiled}")

        print()
        print("📤 Raw Output Preview:")
        if result.raw_stdout:
            print(f"   Stdout: {result.raw_stdout[:200]}...")
        if result.raw_stderr:
            print(f"   Stderr: {result.raw_stderr[:200]}...")

        if result.status == "ok":
            print("\n✅ SUCCESS: File-based rmatch execution completed!")
            return True
        else:
            print(f"\n❌ FAILED: Status = {result.status}")
            if result.notes:
                print(f"   Notes: {result.notes}")
            return False

    except Exception as e:
        duration = time.time() - start_time
        print(f"❌ CRASHED after {duration:.3f} seconds: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_file_based_rmatch()
    exit(0 if success else 1)