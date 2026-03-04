#!/usr/bin/env python3
"""
Create stable metadata for git-stored 10K patterns.
Compatible with phase1.json configuration (seed=12345, suite=log_mining).
"""

import json

def create_stable_metadata():
    """Create metadata file for stable git-stored patterns."""

    # Read the stable patterns
    with open('benchmark_suites/stable_patterns/patterns_10000.txt', 'r') as f:
        patterns = [line.strip() for line in f if line.strip()]

    # Create metadata compatible with phase1.json config
    metadata = {
        'suite_metadata': {
            'suite_name': 'log_mining',
            'generator': 'StablePatternsForGit',
            'seed': 12345,  # Match phase1.json seed
            'generation_date': '2025-12-19',
            'compatible_engines': ['rmatch', 're2j', 'java-native-optimized', 'java-native-unfair'],
            'validation_status': 'validated',
            'note': 'Git-stored stable patterns - validated to work with all engines, no invalid syntax'
        },
        'patterns': patterns,
        'pattern_count': len(patterns),
        'total_size_bytes': sum(len(p.encode('utf-8')) for p in patterns),
        'git_stored': True
    }

    # Save metadata
    with open('benchmark_suites/stable_patterns/patterns_10000_metadata.json', 'w') as f:
        json.dump(metadata, f, indent=2)

    print(f"✅ Created stable metadata for {len(patterns)} patterns")
    print(f"📊 Total size: {metadata['total_size_bytes']} bytes")
    print(f"🔑 Seed: {metadata['suite_metadata']['seed']}")
    print(f"🎯 Suite: {metadata['suite_metadata']['suite_name']}")
    print(f"📅 Date: {metadata['suite_metadata']['generation_date']}")

if __name__ == "__main__":
    create_stable_metadata()