"""
Fair pattern generator for cross-engine compatibility.

Generates regex patterns that work across all engines including rmatch,
which has limited regex syntax support compared to standard engines.
"""

import random
from typing import List, Dict, Any


class FairPatternGenerator:
    """Generate patterns that work across all regex engines."""

    def __init__(self, seed: int = 42):
        self.rng = random.Random(seed)

        # Basic patterns that rmatch can handle
        self.compatible_patterns = [
            # Literal patterns
            "hello",
            "error",
            "warning",
            "info",
            "debug",
            "failed",
            "success",
            "timeout",
            "connection",
            "server",

            # Character class patterns (rmatch supports these)
            "[0-9]",
            "[a-z]",
            "[A-Z]",
            "[a-zA-Z]",
            "[0-9a-f]",  # hex digits
            "[^0-9]",    # not digits
            "[^a-z]",    # not lowercase

            # Quantified patterns
            "[0-9]+",           # one or more digits
            "[a-zA-Z]*",        # zero or more letters
            "[0-9]?",           # optional digit
            "error+",           # repeated 'r'
            "test*",            # optional 'st'

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

            # Note: Anchor patterns (^, $) are NOT supported by rmatch
            # Removed: "^start", "end$", "^[0-9]+$" due to rmatch limitations

            # Dot metacharacter
            "a.b",
            "test.",
            ".com",

            # Parenthesized alternation
            "(cat|dog|bird)",
            "(red|green|blue)",
        ]

    def generate_patterns(self, count: int) -> List[str]:
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
        """Create a variation of a pattern (rmatch-compatible only)."""
        variations = [
            pattern + "+",              # Add quantifier
            pattern + "*",              # Add different quantifier
            pattern + "?",              # Add optional quantifier
            f"({pattern}|test)",        # Add alternation
            # Note: Anchor patterns (^, $) removed - not supported by rmatch
            f"{pattern}[0-9]",          # Add character class
            f"[a-z]{pattern}",          # Prefix with char class
        ]

        # Return a random variation that's different from original
        valid_variations = [v for v in variations if v != pattern]
        return self.rng.choice(valid_variations) if valid_variations else pattern

    def get_pattern_metadata(self) -> Dict[str, Any]:
        """Get metadata about the generated patterns."""
        return {
            "generator": "FairPatternGenerator",
            "compatible_engines": ["rmatch", "re2j", "java-native", "java-native-unfair", "java-native-optimized"],
            "excluded_features": [
                "backslash_escapes (\\d, \\s, \\w, \\b)",
                "exact_quantifiers ({m,n})",
                "lookahead_lookbehind",
                "backreferences",
                "case_insensitive_flags"
            ],
            "supported_features": [
                "character_classes [abc]",
                "quantifiers (?, *, +)",
                "alternation (a|b)",
                "anchors (^, $)",
                "dot_metacharacter",
                "literal_characters"
            ]
        }


def create_fair_pattern_file(output_path: str, count: int, seed: int = 42) -> Dict[str, Any]:
    """Create a pattern file with fair patterns and return metadata."""
    generator = FairPatternGenerator(seed=seed)
    patterns = generator.generate_patterns(count)

    # Write patterns to file
    with open(output_path, 'w') as f:
        for pattern in patterns:
            f.write(f"{pattern}\n")

    # Return metadata
    metadata = generator.get_pattern_metadata()
    metadata.update({
        "pattern_count": len(patterns),
        "seed": seed,
        "file_path": output_path,
        "patterns": patterns
    })

    return metadata


if __name__ == "__main__":
    # Test the generator
    generator = FairPatternGenerator()
    patterns = generator.generate_patterns(10)

    print("Fair patterns for cross-engine compatibility:")
    for i, pattern in enumerate(patterns, 1):
        print(f"{i:2d}. {pattern}")

    print(f"\nMetadata: {generator.get_pattern_metadata()}")