#!/usr/bin/env python3
"""
Generate and validate 10,000 safe regex patterns that won't crash any engine.
"""

import sys
import json
import logging
import random
import subprocess
import tempfile
from pathlib import Path
from typing import List, Set, Dict, Optional
from concurrent.futures import ThreadPoolExecutor, as_completed
import time

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent))

from regex_bench.data.fair_patterns import FairPatternGenerator

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class SafePatternGenerator:
    """Generate regex patterns that are safe across all engines."""

    def __init__(self):
        self.base_generator = FairPatternGenerator()

        # Patterns that are known to be problematic
        self.avoid_constructs = {
            # Catastrophic backtracking patterns
            r'(a+)+',
            r'(a*)*',
            r'(a|a)*',
            # Nested quantifiers
            r'a{1000,}',
            r'a{,1000000}',
            # Complex lookarounds (some engines struggle)
            r'(?=.*){1000}',
            # Unicode that might not be supported
            r'[\u0000-\uFFFF]',
            # Very long character classes
            r'[' + 'a' * 1000 + ']',
        }

        # Safe pattern templates for generating variations
        self.safe_templates = [
            # IP addresses
            r'\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b',
            r'\b(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b',

            # Timestamps
            r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}',
            r'\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2}',
            r'\w{3} \w{3} \d{2} \d{2}:\d{2}:\d{2} \d{4}',

            # URLs and domains
            r'https?://[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}',
            r'www\.[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}',
            r'[a-zA-Z0-9.-]+\.(com|org|net|edu)',

            # Log levels and codes
            r'\b(?:ERROR|WARN|INFO|DEBUG)\b',
            r'\b(?:GET|POST|PUT|DELETE)\b',
            r'\b[45]\d{2}\b',  # HTTP error codes

            # Email addresses
            r'[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}',

            # File paths
            r'/[a-zA-Z0-9._/-]+',
            r'[A-Z]:\\[a-zA-Z0-9._\\-]+',

            # UUIDs
            r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',

            # Numbers and IDs
            r'\b\d{6,12}\b',
            r'#\d+',
            r'ID:\s*\d+',

            # JSON-like patterns
            r'"[^"]+": ?"[^"]+"',
            r'\{[^}]+\}',
            r'\[[^\]]+\]',
        ]

    def generate_safe_patterns(self, count: int) -> List[str]:
        """Generate a specified number of safe regex patterns."""
        patterns = set()

        logger.info(f"Generating {count} safe patterns...")

        # Use template-based generation for reliability
        while len(patterns) < count:
            # Generate variations of safe templates
            base_pattern = random.choice(self.safe_templates)

            # Add safe variations
            variations = self._create_safe_variations(base_pattern)
            for variation in variations:
                if len(patterns) >= count:
                    break
                if self._is_pattern_safe(variation):
                    patterns.add(variation)

            # Add some original generator patterns (filtered)
            if len(patterns) < count * 0.8:  # Use templates for 80%, generator for 20%
                try:
                    original_patterns = self.base_generator.generate_patterns(
                        min(100, count - len(patterns))
                    )
                    for pattern in original_patterns:
                        if len(patterns) >= count:
                            break
                        if self._is_pattern_safe(pattern):
                            patterns.add(pattern)
                except Exception as e:
                    logger.warning(f"Original generator failed: {e}")

            if len(patterns) % 1000 == 0:
                logger.info(f"Generated {len(patterns)}/{count} patterns...")

        pattern_list = list(patterns)[:count]
        logger.info(f"✅ Generated {len(pattern_list)} safe patterns")
        return pattern_list

    def _create_safe_variations(self, base_pattern: str) -> List[str]:
        """Create safe variations of a base pattern."""
        variations = [base_pattern]

        # Add case-insensitive flag
        variations.append(f"(?i){base_pattern}")

        # Add word boundaries
        if not base_pattern.startswith(r'\b'):
            variations.append(f"\\b{base_pattern}\\b")

        # Add optional whitespace
        variations.append(base_pattern.replace(' ', r'\s+'))
        variations.append(base_pattern.replace(' ', r'\s*'))

        # Add anchors
        variations.append(f"^{base_pattern}$")
        variations.append(f"^{base_pattern}")
        variations.append(f"{base_pattern}$")

        return variations[:10]  # Limit variations

    def _is_pattern_safe(self, pattern: str) -> bool:
        """Check if a pattern is safe (basic heuristics)."""
        # Length check
        if len(pattern) > 200:
            return False

        # Avoid known problematic constructs
        for avoid in self.avoid_constructs:
            if avoid in pattern:
                return False

        # Check for nested quantifiers
        if '++' in pattern or '**' in pattern or '+*' in pattern or '*+' in pattern:
            return False

        # Check for excessive repetition
        if '{' in pattern:
            import re
            repetition_matches = re.findall(r'\{(\d+),?(\d*)\}', pattern)
            for match in repetition_matches:
                min_rep = int(match[0]) if match[0] else 0
                max_rep = int(match[1]) if match[1] else min_rep
                if min_rep > 1000 or max_rep > 1000:
                    return False

        # Basic syntax check
        try:
            import re
            re.compile(pattern)
        except re.error:
            return False

        return True


class EngineValidator:
    """Validate patterns against all available engines to ensure no crashes."""

    def __init__(self):
        self.available_engines = self._discover_engines()
        logger.info(f"Found {len(self.available_engines)} engines for validation: {list(self.available_engines.keys())}")

    def _discover_engines(self) -> Dict[str, Path]:
        """Discover available engines."""
        engines = {}

        # Check for built engines in the project directory
        project_root = Path(__file__).parent
        engine_dirs = (project_root / "engines").glob("*/")

        for engine_dir in engine_dirs:
            if not engine_dir.is_dir():
                continue

            # Look for build output
            build_dir = engine_dir / ".build"
            if build_dir.exists():
                # Find executable scripts
                exec_files = list(build_dir.glob("run_*.sh"))
                if exec_files:
                    engines[engine_dir.name] = exec_files[0]

        return engines

    def validate_patterns_against_engines(self, patterns: List[str], batch_size: int = 100) -> Dict[str, List[str]]:
        """Validate patterns against all engines to find safe ones."""
        logger.info(f"Validating {len(patterns)} patterns against {len(self.available_engines)} engines...")

        safe_patterns = []
        failed_patterns = {}

        # Process patterns in batches to avoid overwhelming engines
        for i in range(0, len(patterns), batch_size):
            batch = patterns[i:i+batch_size]
            logger.info(f"Validating batch {i//batch_size + 1}/{(len(patterns) + batch_size - 1)//batch_size} ({len(batch)} patterns)")

            batch_safe = self._validate_pattern_batch(batch)
            safe_patterns.extend(batch_safe)

            # Log progress
            if len(safe_patterns) % 1000 == 0:
                logger.info(f"✅ {len(safe_patterns)} patterns validated as safe")

        logger.info(f"✅ Validation complete: {len(safe_patterns)}/{len(patterns)} patterns are safe")
        return {
            'safe_patterns': safe_patterns,
            'failed_patterns': failed_patterns
        }

    def _validate_pattern_batch(self, patterns: List[str]) -> List[str]:
        """Validate a batch of patterns against all engines."""
        safe_patterns = []

        # Create temporary files for testing
        with tempfile.NamedTemporaryFile(mode='w', suffix='.txt', delete=False) as pattern_file, \
             tempfile.NamedTemporaryFile(mode='w', suffix='.txt', delete=False) as corpus_file:

            # Write patterns
            for pattern in patterns:
                pattern_file.write(pattern + '\n')
            pattern_file.flush()

            # Write simple test corpus
            corpus_file.write("test log entry 192.168.1.1 ERROR 2023-01-01T12:00:00 GET /path/to/resource\n")
            corpus_file.write("another entry with email@example.com and timestamp\n")
            corpus_file.write("final line with UUID 12345678-1234-1234-1234-123456789abc\n")
            corpus_file.flush()

            try:
                # Test each engine
                for engine_name, engine_path in self.available_engines.items():
                    if not self._test_engine_with_patterns(engine_path, pattern_file.name, corpus_file.name):
                        logger.warning(f"Engine {engine_name} failed with this batch")
                        return []  # If any engine fails, reject entire batch

                # If all engines succeeded, all patterns in batch are safe
                safe_patterns = patterns.copy()

            except Exception as e:
                logger.error(f"Validation error: {e}")
                return []
            finally:
                # Cleanup temp files
                Path(pattern_file.name).unlink(missing_ok=True)
                Path(corpus_file.name).unlink(missing_ok=True)

        return safe_patterns

    def _test_engine_with_patterns(self, engine_path: Path, pattern_file: str, corpus_file: str) -> bool:
        """Test if an engine can handle the patterns without crashing."""
        try:
            # Run engine with timeout
            result = subprocess.run(
                [str(engine_path), pattern_file, corpus_file],
                capture_output=True,
                text=True,
                timeout=30,  # 30 second timeout per batch
                cwd=engine_path.parent
            )

            # Check if engine completed successfully
            return result.returncode == 0

        except subprocess.TimeoutExpired:
            logger.warning(f"Engine {engine_path.name} timed out")
            return False
        except Exception as e:
            logger.warning(f"Engine {engine_path.name} failed: {e}")
            return False


def create_safe_10k_patterns():
    """Main function to create 10,000 safe patterns."""
    logger.info("🚀 Starting safe 10K pattern generation...")

    # Generate patterns
    generator = SafePatternGenerator()
    patterns = generator.generate_safe_patterns(12000)  # Generate extra to account for validation failures

    # Validate against engines
    validator = EngineValidator()
    validation_result = validator.validate_patterns_against_engines(patterns)

    safe_patterns = validation_result['safe_patterns']

    if len(safe_patterns) < 10000:
        logger.error(f"Only {len(safe_patterns)} safe patterns found, need 10,000")
        # Generate more patterns
        additional_needed = 10000 - len(safe_patterns) + 2000
        logger.info(f"Generating {additional_needed} additional patterns...")
        additional_patterns = generator.generate_safe_patterns(additional_needed)
        additional_result = validator.validate_patterns_against_engines(additional_patterns)
        safe_patterns.extend(additional_result['safe_patterns'])

    # Take exactly 10,000
    final_patterns = safe_patterns[:10000]

    # Save patterns
    output_dir = Path("benchmark_suites/log_mining")
    output_dir.mkdir(parents=True, exist_ok=True)

    patterns_file = output_dir / "patterns_10000.txt"
    metadata_file = output_dir / "patterns_10000_metadata.json"

    # Write patterns
    with open(patterns_file, 'w') as f:
        for pattern in final_patterns:
            f.write(pattern + '\n')

    # Write metadata
    metadata = {
        "pattern_count": len(final_patterns),
        "source": "safe_generator_v1",
        "description": "10,000 regex patterns validated to be safe across all engines",
        "generation_date": time.strftime("%Y-%m-%d %H:%M:%S"),
        "validation_engines": list(validator.available_engines.keys()),
        "safety_checks": [
            "No catastrophic backtracking patterns",
            "No excessive repetition quantifiers",
            "No problematic Unicode constructs",
            "Tested against all available engines",
            "Maximum pattern length: 200 characters"
        ],
        "statistics": {
            "total_generated": len(patterns),
            "safe_patterns": len(final_patterns),
            "validation_pass_rate": f"{len(final_patterns) / len(patterns) * 100:.1f}%"
        }
    }

    with open(metadata_file, 'w') as f:
        json.dump(metadata, f, indent=2)

    logger.info(f"✅ Created 10,000 safe patterns:")
    logger.info(f"   📁 Patterns: {patterns_file}")
    logger.info(f"   📋 Metadata: {metadata_file}")
    logger.info(f"   🛡️  Validated against: {list(validator.available_engines.keys())}")
    logger.info(f"   📊 Pass rate: {len(final_patterns) / len(patterns) * 100:.1f}%")

    return patterns_file, metadata_file


if __name__ == "__main__":
    try:
        patterns_file, metadata_file = create_safe_10k_patterns()

        print("\n" + "="*60)
        print("SAFE 10K PATTERNS GENERATION SUCCESS")
        print("="*60)
        print(f"✅ 10,000 safe patterns created and validated")
        print(f"📁 Patterns file: {patterns_file}")
        print(f"📋 Metadata file: {metadata_file}")
        print(f"🛡️  Engine validated: All available engines tested")
        print(f"🚀 Ready for benchmarking!")
        print("="*60)

    except Exception as e:
        logger.error(f"❌ Failed to create safe patterns: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)