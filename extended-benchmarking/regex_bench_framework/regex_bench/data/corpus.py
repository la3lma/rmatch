"""
Corpus generation and management.
"""

import random
import string
from typing import Dict, Any


class CorpusManager:
    """Generate and manage test corpora."""

    def generate_corpus(self, size: str, config: Dict[str, Any]) -> str:
        """Generate a corpus of the specified size."""
        # Parse size string (e.g., "1MB", "100KB")
        size_bytes = self._parse_size(size)
        corpus_type = config.get('type', 'synthetic_controllable')

        if corpus_type == 'synthetic_controllable':
            return self._generate_synthetic_corpus(size_bytes, config)
        else:
            return self._generate_generic_corpus(size_bytes)

    def _parse_size(self, size: str) -> int:
        """Parse size string to bytes."""
        size = size.upper().strip()

        if size.endswith('KB'):
            return int(size[:-2]) * 1024
        elif size.endswith('MB'):
            return int(size[:-2]) * 1024 * 1024
        elif size.endswith('GB'):
            return int(size[:-2]) * 1024 * 1024 * 1024
        else:
            # Assume bytes
            return int(size)

    def _generate_synthetic_corpus(self, size_bytes: int, config: Dict[str, Any]) -> str:
        """Generate a synthetic corpus with controllable characteristics."""
        match_density = config.get('match_density', 'medium')
        line_count_target = config.get('line_count_target', {})
        character_distribution = config.get('character_distribution', 'log_like')

        lines = []
        current_size = 0

        # Generate lines until we reach target size
        line_templates = self._get_line_templates(character_distribution, match_density)

        while current_size < size_bytes:
            line = random.choice(line_templates)

            # Add some randomization
            line = self._randomize_line(line)

            lines.append(line)
            current_size += len(line) + 1  # +1 for newline

        return '\n'.join(lines)

    def _generate_generic_corpus(self, size_bytes: int) -> str:
        """Generate a simple generic corpus."""
        lines = []
        current_size = 0

        sample_lines = [
            "This is a sample log line with timestamp 2024-12-19 12:30:45",
            "ERROR: Connection failed to server 192.168.1.100",
            "INFO: User john.doe@example.com logged in successfully",
            "WARN: High memory usage detected: 85%",
            "DEBUG: Processing request /api/v1/users with method GET",
            "Connection established to database server",
            "Request processed in 125ms",
            "Cache hit ratio: 94.5%"
        ]

        while current_size < size_bytes:
            line = random.choice(sample_lines)
            lines.append(line)
            current_size += len(line) + 1

        return '\n'.join(lines)

    def _get_line_templates(self, distribution: str, match_density: str) -> list:
        """Get line templates based on character distribution and match density."""
        if distribution == 'log_like':
            templates = [
                "2024-12-19 12:30:{:02d} INFO [main] Processing request from 192.168.1.{}",
                "2024-12-19 12:30:{:02d} ERROR [worker] Failed to connect to database",
                "2024-12-19 12:30:{:02d} DEBUG [auth] User authentication successful for user{}",
                "GET /api/v1/users/{} HTTP/1.1 200 {}ms",
                "POST /login HTTP/1.1 401 Unauthorized",
                "Connection pool size: {} active, {} idle",
                "Memory usage: {}MB / {}MB ({}%)",
                "Cache statistics: {} hits, {} misses, {:.1f}% hit rate"
            ]
        else:
            # ASCII-only fallback
            templates = [
                "Sample text line {} with some content",
                "Another line containing data item {}",
                "Log entry number {} with status OK",
                "Processing item {} completed successfully"
            ]

        return templates

    def _randomize_line(self, template: str) -> str:
        """Add randomization to a line template."""
        # Simple randomization - replace {} with random values
        import re

        def replace_placeholder(match):
            return str(random.randint(1, 999))

        # Replace numeric placeholders
        line = re.sub(r'\{\}', replace_placeholder, template)

        # Replace format placeholders like {:02d}
        line = re.sub(r'\{:[\d\.]*[sd]\}', replace_placeholder, line)

        return line