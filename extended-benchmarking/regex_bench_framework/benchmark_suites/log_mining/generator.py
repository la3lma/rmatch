#!/usr/bin/env python3
"""
Suite A: Log Mining Pattern Generator

Generates realistic log parsing patterns:
- Timestamps, IPs, URLs, error codes
- Anchored patterns typical in log analysis
- Reproducible with fixed seed
"""

import random
import re
import hashlib
from typing import List, Dict, Any
from pathlib import Path


class LogMiningPatternGenerator:
    """Generate realistic log mining regex patterns."""

    def __init__(self, seed: int = 42):
        self.random = random.Random(seed)
        self.seed = seed
        self.metadata = {
            "suite_name": "log_mining",
            "description": "Patterns for log file analysis and parsing",
            "pattern_types": ["timestamps", "ips", "urls", "error_codes", "key_values"],
            "difficulty": "medium",
            "expected_match_rate": "10-30%",
            "realistic_use_case": "Web server log analysis, application log parsing"
        }

    def generate_timestamp_patterns(self, count: int) -> List[str]:
        """Generate various timestamp format patterns."""
        base_formats = [
            # ISO 8601 variants
            r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}',
            r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}',
            r'\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2}',

            # US date formats
            r'\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2}',
            r'\d{1,2}/\d{1,2}/\d{4} \d{1,2}:\d{2}:\d{2}',

            # Log format timestamps
            r'\[[\d/: -]+\]',  # Bracketed timestamps
            r'\d{10,13}',      # Unix timestamps
            r'\d{13,16}',      # Millisecond timestamps

            # Syslog formats
            r'\w{3} \d{1,2} \d{2}:\d{2}:\d{2}',  # Jan 15 14:23:45
            r'\w{3} \w{3} \d{1,2} \d{2}:\d{2}:\d{2}',  # Mon Jan 15 14:23:45

            # Apache/Nginx log formats
            r'\d{2}/\w{3}/\d{4}:\d{2}:\d{2}:\d{2}',  # 15/Jan/2024:14:23:45
        ]

        patterns = []
        for _ in range(count):
            pattern = self.random.choice(base_formats)

            # Add optional components with probability
            if self.random.random() < 0.3:
                pattern += r'(\.\d{3})?'  # Optional milliseconds

            if self.random.random() < 0.2:
                pattern += r'( [+-]\d{4})?'  # Optional timezone

            if self.random.random() < 0.1:
                pattern += r'( UTC| GMT| EST| PST)?'  # Optional timezone names

            # Some patterns should be anchored to line start
            if self.random.random() < 0.4:
                pattern = '^' + pattern

            patterns.append(pattern)

        return patterns

    def generate_ip_patterns(self, count: int) -> List[str]:
        """Generate IP address patterns (IPv4 and IPv6)."""
        patterns = [
            # Basic IPv4
            r'\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b',

            # Strict IPv4 validation
            r'\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\b',

            # IPv4 in logs (with common prefixes)
            r'(?:ip|addr|from|client)[:\s]+\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}',

            # Private IPv4 ranges
            r'(?:192\.168\.|10\.|172\.(?:1[6-9]|2\d|3[01])\.)\d{1,3}\.\d{1,3}',

            # IPv6 patterns
            r'(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}',  # Full IPv6
            r'(?:::)?(?:[0-9a-fA-F]{1,4}(?:::|:))*[0-9a-fA-F]{1,4}',  # Compressed IPv6
            r'::1\b',  # Localhost IPv6

            # IPv4-mapped IPv6
            r'::ffff:\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}',
        ]

        result = []
        for _ in range(count):
            pattern = self.random.choice(patterns)
            result.append(pattern)

        return result

    def generate_url_patterns(self, count: int) -> List[str]:
        """Generate URL and URI patterns common in logs."""
        base_patterns = [
            # HTTP URLs
            r'https?://[^\s<>"\'{}|\\^`\[\]]+',

            # Request paths in logs
            r'(?:GET|POST|PUT|DELETE|PATCH)\s+(/[^\s]*)',

            # URL paths only
            r'/[a-zA-Z0-9._~:/?#\[\]@!$&\'()*+,;=-]*',

            # API endpoints
            r'/api/v\d+/[a-zA-Z0-9/_-]+',
            r'/rest/[a-zA-Z0-9/_-]+',

            # Static resources
            r'/static/[a-zA-Z0-9._/-]+\.(css|js|png|jpg|gif|ico)',
            r'\.(?:css|js|png|jpg|jpeg|gif|ico|svg|woff|woff2)(?:\?[^\s]*)?',

            # Common web paths
            r'/(?:admin|login|register|logout|profile|settings|dashboard)[^\s]*',
            r'/(?:index|home|about|contact)\.(?:html|php|jsp|asp)',

            # Query parameters
            r'\?[a-zA-Z0-9=&_-]+',

            # File paths in logs
            r'/var/log/[a-zA-Z0-9._/-]+',
            r'/tmp/[a-zA-Z0-9._/-]+',
            r'/home/[a-zA-Z0-9._/-]+',
        ]

        patterns = []
        for _ in range(count):
            pattern = self.random.choice(base_patterns)

            # Add HTTP status code capture sometimes
            if 'GET|POST' in pattern and self.random.random() < 0.3:
                pattern += r'\s+([1-5]\d{2})'

            patterns.append(pattern)

        return patterns

    def generate_error_patterns(self, count: int) -> List[str]:
        """Generate error code and status patterns."""
        patterns = [
            # HTTP status codes
            r'\b[1-5]\d{2}\b',  # Any HTTP status
            r'\b[45]\d{2}\b',   # Client/Server errors
            r'\b404\b',         # Not found specifically
            r'\b500\b',         # Internal server error

            # Log levels
            r'\b(?:ERROR|WARN|WARNING|FATAL|CRITICAL)\b',
            r'\[(?:ERROR|WARN|WARNING|FATAL|CRITICAL)\]',

            # Exception patterns
            r'Exception(?:\s+in|\s+at|:)',
            r'Error(?:\s+in|\s+at|:)',
            r'Failed\s+to\s+[a-zA-Z]+',
            r'Cannot\s+[a-zA-Z]+',
            r'Unable\s+to\s+[a-zA-Z]+',

            # Database errors
            r'(?:SQL|Database|DB)\s+(?:Error|Exception)',
            r'Connection\s+(?:refused|timeout|failed)',

            # Common error codes
            r'errno\s*:\s*\d+',
            r'exit\s+code\s+\d+',

            # Stack trace indicators
            r'at\s+[a-zA-Z0-9._$]+\([^)]+\)',
            r'Traceback\s+\(most\s+recent\s+call\s+last\)',
        ]

        result = []
        for _ in range(count):
            pattern = self.random.choice(patterns)

            # Make some case insensitive
            if self.random.random() < 0.3:
                pattern = '(?i)' + pattern

            result.append(pattern)

        return result

    def generate_keyvalue_patterns(self, count: int) -> List[str]:
        """Generate key-value pair patterns common in structured logs."""
        patterns = [
            # Basic key=value
            r'[a-zA-Z_][a-zA-Z0-9_]*=[^\s,;]+',

            # Key: value
            r'[a-zA-Z_][a-zA-Z0-9_]*:\s*[^\s,;]+',

            # JSON-like patterns
            r'"[a-zA-Z_][a-zA-Z0-9_]*"\s*:\s*"[^"]*"',
            r'"[a-zA-Z_][a-zA-Z0-9_]*"\s*:\s*\d+',
            r'"[a-zA-Z_][a-zA-Z0-9_]*"\s*:\s*(?:true|false)',

            # Specific common keys
            r'user(?:_?id|name)?[=:]\s*[a-zA-Z0-9._@-]+',
            r'session(?:_?id)?[=:]\s*[a-zA-Z0-9._-]+',
            r'request(?:_?id)?[=:]\s*[a-zA-Z0-9._-]+',
            r'(?:app|application)(?:_?id|_?name)?[=:]\s*[a-zA-Z0-9._-]+',

            # Measurements and metrics
            r'(?:time|duration|latency|response_time)[=:]\s*\d+(?:\.\d+)?(?:ms|s|us)?',
            r'(?:size|length|bytes)[=:]\s*\d+',
            r'(?:count|total|sum)[=:]\s*\d+',

            # Status and flags
            r'(?:status|state)[=:]\s*[a-zA-Z0-9_-]+',
            r'(?:success|failure|error)[=:]\s*(?:true|false|yes|no|\d+)',
        ]

        result = []
        for _ in range(count):
            pattern = self.random.choice(patterns)
            result.append(pattern)

        return result

    def generate_mixed_patterns(self, count: int) -> List[str]:
        """Generate complex patterns combining multiple elements."""
        patterns = []

        for _ in range(count):
            # Create compound patterns
            if self.random.random() < 0.3:
                # IP followed by timestamp
                pattern = r'\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}.*\d{4}-\d{2}-\d{2}'
            elif self.random.random() < 0.5:
                # HTTP request with status
                pattern = r'(?:GET|POST)\s+[^\s]+\s+"[^"]*"\s+[1-5]\d{2}'
            elif self.random.random() < 0.7:
                # User action pattern
                pattern = r'user[=:]\s*\w+.*(?:login|logout|action)[=:]\s*\w+'
            else:
                # Error with timestamp
                pattern = r'\d{4}-\d{2}-\d{2}.*(?:ERROR|WARN|FAIL)'

            patterns.append(pattern)

        return patterns

    def _estimate_complexity(self, pattern: str) -> str:
        """Estimate regex complexity based on features used."""
        complexity_indicators = {
            'high': [r'[*+?{].*[*+?{]', r'\(\?<', r'\(\?=', r'\(\?!', r'\(\?<=', r'\(\?<!'],  # Nested quantifiers, lookarounds
            'medium': [r'[*+?{]', r'[\[\]]', r'[|]', r'\\[dswDSW]'],  # Quantifiers, character classes, alternation, shortcuts
            'low': []  # Everything else
        }

        for level, indicators in complexity_indicators.items():
            if any(re.search(indicator, pattern) for indicator in indicators):
                return level

        return 'low'

    def _compute_statistics(self, pattern_metadata: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Compute statistics about generated patterns."""
        if not pattern_metadata:
            return {}

        lengths = [meta['length'] for meta in pattern_metadata]
        complexities = [meta['complexity'] for meta in pattern_metadata]

        complexity_counts = {
            'low': complexities.count('low'),
            'medium': complexities.count('medium'),
            'high': complexities.count('high')
        }

        category_counts = {}
        for meta in pattern_metadata:
            category = meta['category']
            category_counts[category] = category_counts.get(category, 0) + 1

        return {
            "total_patterns": len(pattern_metadata),
            "average_length": sum(lengths) / len(lengths) if lengths else 0,
            "min_length": min(lengths) if lengths else 0,
            "max_length": max(lengths) if lengths else 0,
            "complexity_distribution": complexity_counts,
            "category_distribution": category_counts,
            "estimated_match_rate": self.metadata["expected_match_rate"]
        }

    def generate(self, total_patterns: int) -> Dict[str, Any]:
        """Generate a complete log mining pattern suite."""

        # Distribute patterns across categories
        # Adjust distribution based on realistic log analysis needs
        distribution = {
            "timestamps": max(1, int(total_patterns * 0.25)),
            "ips": max(1, int(total_patterns * 0.20)),
            "urls": max(1, int(total_patterns * 0.25)),
            "error_codes": max(1, int(total_patterns * 0.15)),
            "key_values": max(1, int(total_patterns * 0.10)),
            "mixed": max(0, total_patterns - int(total_patterns * 0.95))  # Fill remainder
        }

        # Ensure we generate exactly the requested number
        actual_total = sum(distribution.values())
        if actual_total < total_patterns:
            distribution["mixed"] += total_patterns - actual_total
        elif actual_total > total_patterns:
            # Reduce largest category
            largest_cat = max(distribution.keys(), key=lambda k: distribution[k])
            distribution[largest_cat] -= actual_total - total_patterns

        patterns = []
        pattern_metadata = []

        # Generate each category
        generators = {
            "timestamps": self.generate_timestamp_patterns,
            "ips": self.generate_ip_patterns,
            "urls": self.generate_url_patterns,
            "error_codes": self.generate_error_patterns,
            "key_values": self.generate_keyvalue_patterns,
            "mixed": self.generate_mixed_patterns
        }

        for category, count in distribution.items():
            if count <= 0:
                continue

            cat_patterns = generators[category](count)
            patterns.extend(cat_patterns)

            # Record metadata for each pattern
            for i, pattern in enumerate(cat_patterns):
                pattern_metadata.append({
                    "index": len(pattern_metadata),
                    "pattern": pattern,
                    "category": category,
                    "length": len(pattern),
                    "complexity": self._estimate_complexity(pattern),
                    "features": self._extract_features(pattern)
                })

        # Shuffle to avoid category clustering
        combined = list(zip(patterns, pattern_metadata))
        self.random.shuffle(combined)
        patterns, pattern_metadata = zip(*combined) if combined else ([], [])

        # Reindex after shuffle
        for i, meta in enumerate(pattern_metadata):
            meta["index"] = i

        # Generate checksum for reproducibility verification
        content_hash = hashlib.sha256(
            ''.join(patterns).encode('utf-8')
        ).hexdigest()[:16]

        return {
            "suite_metadata": {
                **self.metadata,
                "version": "1.0",
                "generator_version": "log_mining_v1",
                "generated_at": "2024-12-19T12:30:00Z"  # TODO: actual timestamp
            },
            "generation_config": {
                "total_patterns": total_patterns,
                "seed": self.seed,
                "distribution": distribution,
                "content_checksum": content_hash
            },
            "patterns": list(patterns),
            "pattern_metadata": list(pattern_metadata),
            "statistics": self._compute_statistics(pattern_metadata)
        }

    def _extract_features(self, pattern: str) -> Dict[str, bool]:
        """Extract regex features used in pattern."""
        return {
            "has_anchors": bool(re.search(r'[\^$]', pattern)),
            "has_quantifiers": bool(re.search(r'[*+?{]', pattern)),
            "has_character_classes": bool(re.search(r'[\[\]]', pattern)),
            "has_groups": bool(re.search(r'[()]', pattern)),
            "has_alternation": bool(re.search(r'[|]', pattern)),
            "has_escape_sequences": bool(re.search(r'\\[dswDSW]', pattern)),
            "has_word_boundaries": bool(re.search(r'\\b', pattern)),
            "has_case_insensitive": pattern.startswith('(?i)'),
            "has_lookaround": bool(re.search(r'\(\?[=!<]', pattern))
        }


if __name__ == '__main__':
    # Test the generator
    generator = LogMiningPatternGenerator(seed=42)

    print("Generating 100 log mining patterns...")
    result = generator.generate(100)

    print(f"Generated {len(result['patterns'])} patterns")
    print(f"Categories: {result['statistics']['category_distribution']}")
    print(f"Complexity: {result['statistics']['complexity_distribution']}")
    print(f"Average length: {result['statistics']['average_length']:.1f}")

    # Show first few patterns
    print("\nSample patterns:")
    for i, pattern in enumerate(result['patterns'][:5]):
        meta = result['pattern_metadata'][i]
        print(f"  {i+1}. [{meta['category']}] {pattern}")