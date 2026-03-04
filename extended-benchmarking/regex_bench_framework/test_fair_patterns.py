#!/usr/bin/env python3
"""Test fair patterns with rmatch to verify compilation."""

import sys
import subprocess
import tempfile
import random
from pathlib import Path

# Direct implementation to avoid framework dependencies
class FairPatternGenerator:
    """Generate patterns that work across all regex engines."""

    def __init__(self, seed: int = 42):
        self.rng = random.Random(seed)

        # Basic patterns that rmatch can handle
        self.compatible_patterns = [
            # Literal patterns
            "hello", "error", "warning", "info", "debug",
            "failed", "success", "timeout", "connection", "server",

            # Character class patterns (rmatch supports these)
            "[0-9]", "[a-z]", "[A-Z]", "[a-zA-Z]", "[0-9a-f]",
            "[^0-9]", "[^a-z]",

            # Quantified patterns
            "[0-9]+", "[a-zA-Z]*", "[0-9]?",
            "error+", "test*",

            # IP-like patterns (rmatch compatible)
            "[0-9][0-9][0-9].[0-9][0-9][0-9].[0-9][0-9][0-9].[0-9][0-9][0-9]",
            "[0-9]+.[0-9]+.[0-9]+.[0-9]+",

            # Date-like patterns (rmatch compatible)
            "[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]",
            "[0-9]+/[0-9]+/[0-9]+",

            # Time patterns
            "[0-9][0-9]:[0-9][0-9]:[0-9][0-9]",

            # Common log patterns
            "GET|POST|PUT|DELETE",
            "http|https",
            "200|404|500|301|302",

            # File patterns
            ".txt|.log|.json|.xml",
            ".jpg|.png|.gif|.pdf",

            # Simple word patterns
            "user|admin|guest",
            "start|stop|restart",
            "true|false",
            "yes|no",

            # Anchored patterns
            "^start",
            "end$",
            "^[0-9]+$",

            # Dot metacharacter
            "a.b",
            "test.",
            ".com",

            # Parenthesized alternation
            "(cat|dog|bird)",
            "(red|green|blue)",
        ]

    def generate_patterns(self, count: int):
        """Generate a list of fair patterns that work across all engines."""
        if count <= len(self.compatible_patterns):
            return self.rng.sample(self.compatible_patterns, count)

        # If we need more patterns, repeat and modify some
        patterns = self.compatible_patterns.copy()

        while len(patterns) < count:
            # Generate variations of existing patterns
            base_pattern = self.rng.choice(self.compatible_patterns)
            variation = self._create_variation(base_pattern)
            if variation not in patterns:
                patterns.append(variation)

        return patterns[:count]

    def _create_variation(self, pattern: str) -> str:
        """Create a variation of a pattern."""
        variations = [
            pattern + "+",              # Add quantifier
            pattern + "*",              # Add different quantifier
            pattern + "?",              # Add optional quantifier
            f"({pattern}|test)",        # Add alternation
            f"^{pattern}",              # Add anchor
            f"{pattern}$",              # Add end anchor
            f"{pattern}[0-9]",          # Add character class
            f"[a-z]{pattern}",          # Prefix with char class
        ]

        # Return a random variation that's different from original
        valid_variations = [v for v in variations if v != pattern]
        return self.rng.choice(valid_variations) if valid_variations else pattern

def test_rmatch_compilation():
    """Test that rmatch can compile fair patterns."""
    print("🧪 Testing fair pattern compilation with rmatch...")

    # Generate some fair patterns
    generator = FairPatternGenerator(seed=42)
    patterns = generator.generate_patterns(10)

    print(f"📝 Generated {len(patterns)} fair patterns:")
    for i, pattern in enumerate(patterns, 1):
        print(f"  {i:2d}. {pattern}")

    # Create temporary pattern file
    with tempfile.NamedTemporaryFile(mode='w', suffix='.txt', delete=False) as f:
        for pattern in patterns:
            f.write(f"{pattern}\n")
        pattern_file = f.name

    # Create temporary corpus file
    with tempfile.NamedTemporaryFile(mode='w', suffix='.txt', delete=False) as f:
        # Use simple test corpus that should match some patterns
        corpus = """hello world 12345 error warning
192.168.1.1 2023-12-19 debug info
GET /api/test HTTP/1.1 200 OK
user admin test.txt connection
start http://example.com end true false"""
        f.write(corpus)
        corpus_file = f.name

    try:
        # Test rmatch compilation
        print(f"\n🔍 Testing rmatch with fair patterns...")

        # Use the rmatch runner from the framework
        rmatch_runner = Path("engines/rmatch/runner.sh")
        if not rmatch_runner.exists():
            print(f"❌ rmatch runner not found at: {rmatch_runner}")
            return False

        # Run rmatch with fair patterns
        cmd = ["bash", str(rmatch_runner), pattern_file, corpus_file]

        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)

        if result.returncode == 0:
            print("✅ rmatch successfully compiled and executed fair patterns!")
            print("📊 Output:")
            print(result.stdout)
            return True
        else:
            print("❌ rmatch failed to compile fair patterns")
            print("🚨 Error output:")
            print(result.stderr)
            return False

    except subprocess.TimeoutExpired:
        print("⏱️ rmatch test timed out")
        return False
    except Exception as e:
        print(f"❌ Test failed with exception: {e}")
        return False
    finally:
        # Clean up temp files
        Path(pattern_file).unlink(missing_ok=True)
        Path(corpus_file).unlink(missing_ok=True)

def main():
    print("🚀 Fair Pattern Compatibility Test")
    print("=" * 50)

    success = test_rmatch_compilation()

    if success:
        print("\n✅ All tests passed! Fair patterns are compatible with rmatch.")
        print("🎯 Ready to run full benchmarks with fair patterns.")
    else:
        print("\n❌ Tests failed. Need to adjust fair patterns.")

    return 0 if success else 1

if __name__ == "__main__":
    sys.exit(main())