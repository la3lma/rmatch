#!/usr/bin/env python3
"""
Create metadata files for pre-loaded patterns in output directory.
This fixes the Makefile Python syntax error by using a separate script.
"""

import json
import os
import sys

def create_metadata_for_patterns(output_dir):
    """Create metadata files for existing pattern files."""

    for count in [10, 100, 1000]:
        pattern_file = os.path.join(output_dir, f'patterns_{count}.txt')
        metadata_file = os.path.join(output_dir, f'patterns_{count}_metadata.json')

        if os.path.exists(pattern_file):
            with open(pattern_file, 'r') as f:
                patterns = [line.strip() for line in f if line.strip()]

            metadata = {
                'suite_metadata': {
                    'suite_name': 'log_mining',
                    'generator': 'FairPatternGenerator',
                    'seed': 12345,  # Match phase1.json seed
                    'compatible_engines': ['rmatch', 're2j', 'java-native-optimized', 'java-native-unfair'],
                    'note': 'Pre-validated patterns compatible with phase1.json configuration'
                },
                'patterns': patterns,
                'pattern_count': len(patterns)
            }

            with open(metadata_file, 'w') as f:
                json.dump(metadata, f, indent=2)

            print(f'✓ Created metadata for patterns_{count}.txt ({len(patterns)} patterns)')

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 create_pattern_metadata.py <output_dir>")
        sys.exit(1)

    output_dir = sys.argv[1]
    create_metadata_for_patterns(output_dir)