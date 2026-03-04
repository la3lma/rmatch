"""
Pattern suite management and generation.
"""

from pathlib import Path
import sys
from typing import Dict, Any

# Add benchmark_suites to path for import
benchmark_suites_path = Path(__file__).parent.parent.parent / "benchmark_suites"
sys.path.insert(0, str(benchmark_suites_path))

from .fair_patterns import FairPatternGenerator


class PatternSuite:
    """Pattern suite generator and manager."""

    def __init__(self, suite_name: str = "log_mining", seed: int = 42):
        self.suite_name = suite_name
        self.seed = seed

    def generate(self, count: int) -> Dict[str, Any]:
        """Generate patterns for the specified suite."""
        # Check if this is the stable_patterns suite
        if self.suite_name == "stable_patterns":
            return self._load_stable_patterns(count)

        # Use fair patterns for ALL other suites to ensure compatibility with rmatch
        generator = FairPatternGenerator(seed=self.seed)
        patterns = generator.generate_patterns(count)

        return {
            "suite_metadata": {
                "suite_name": self.suite_name,
                "generator": "FairPatternGenerator",
                "seed": self.seed,
                "compatible_engines": ["rmatch", "re2j", "java-native"],
                "note": "Patterns designed for cross-engine compatibility"
            },
            "patterns": patterns,
            "pattern_count": len(patterns)
        }

    def _load_stable_patterns(self, count: int) -> Dict[str, Any]:
        """Load patterns from the stable patterns file."""
        import json

        # Path to stable patterns
        stable_patterns_dir = benchmark_suites_path / "stable_patterns"
        patterns_file = stable_patterns_dir / "patterns_10000.txt"
        metadata_file = stable_patterns_dir / "patterns_10000_metadata.json"

        if not patterns_file.exists():
            raise FileNotFoundError(f"Stable patterns file not found: {patterns_file}")

        # Load patterns from file
        with open(patterns_file, 'r') as f:
            all_patterns = [line.strip() for line in f if line.strip()]

        # Take the requested count (or all if count exceeds available)
        patterns = all_patterns[:count]

        # Load metadata if available
        suite_metadata = {
            "suite_name": "stable_patterns",
            "generator": "StablePatternsForGit",
            "seed": self.seed,
            "compatible_engines": ["rmatch", "re2j", "java-native-optimized", "java-native-unfair"],
            "validation_status": "validated",
            "note": "Git-stored stable patterns - validated to work with all engines, no invalid syntax",
            "source_file": str(patterns_file),
            "total_available_patterns": len(all_patterns),
            "patterns_used": len(patterns)
        }

        if metadata_file.exists():
            try:
                with open(metadata_file, 'r') as f:
                    file_metadata = json.load(f)
                    if 'suite_metadata' in file_metadata:
                        suite_metadata.update(file_metadata['suite_metadata'])
            except (json.JSONDecodeError, KeyError):
                # Continue with default metadata if file is malformed
                pass

        return {
            "suite_metadata": suite_metadata,
            "patterns": patterns,
            "pattern_count": len(patterns)
        }

    def _generate_simple_patterns(self, count: int) -> Dict[str, Any]:
        """Fallback generator for simple patterns."""
        patterns = [
            r'\d{4}-\d{2}-\d{2}',  # Date
            r'\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b',  # IP
            r'https?://[^\s]+',  # URL
            r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b',  # Email
            r'\b(?:ERROR|WARN|INFO|DEBUG)\b',  # Log levels
        ]

        # Repeat patterns to reach desired count
        result_patterns = []
        while len(result_patterns) < count:
            result_patterns.extend(patterns)

        result_patterns = result_patterns[:count]

        return {
            "suite_metadata": {
                "suite_name": self.suite_name,
                "description": f"Simple {self.suite_name} patterns",
                "version": "1.0"
            },
            "patterns": result_patterns,
            "pattern_metadata": [
                {
                    "index": i,
                    "pattern": pattern,
                    "category": "simple",
                    "length": len(pattern),
                    "complexity": "low"
                }
                for i, pattern in enumerate(result_patterns)
            ],
            "statistics": {
                "total_patterns": len(result_patterns),
                "average_length": sum(len(p) for p in result_patterns) / len(result_patterns),
                "complexity_distribution": {"low": count}
            }
        }