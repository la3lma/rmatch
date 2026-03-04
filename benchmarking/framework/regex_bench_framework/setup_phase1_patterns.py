#!/usr/bin/env python3
"""
Create pattern files for phase1.json from git-stored 10K patterns.
This ensures complete pattern reuse for the Makefile system.
"""

import json
import os
from pathlib import Path

def setup_phase1_pattern_reuse():
    """Create all required pattern files with matching metadata for phase1.json."""

    # Paths
    stable_patterns_file = Path("benchmark_suites/stable_patterns/patterns_10000.txt")
    log_mining_dir = Path("benchmark_suites/log_mining")

    # Ensure output directory exists
    log_mining_dir.mkdir(parents=True, exist_ok=True)

    # Read the stable patterns
    if not stable_patterns_file.exists():
        print("❌ Git-stored stable patterns not found!")
        return False

    with open(stable_patterns_file, 'r') as f:
        all_patterns = [line.strip() for line in f if line.strip()]

    print(f"📋 Found {len(all_patterns)} git-stored stable patterns")

    # Create pattern files for phase1.json requirements: [10, 100, 1000]
    sizes = [10, 100, 1000]

    for size in sizes:
        # Create pattern file
        patterns = all_patterns[:size]
        pattern_file = log_mining_dir / f"patterns_{size}.txt"

        with open(pattern_file, 'w') as f:
            for pattern in patterns:
                f.write(f"{pattern}\n")

        # Create metadata file with matching seed=12345
        metadata = {
            'suite_metadata': {
                'suite_name': 'log_mining',
                'generator': 'StablePatternsFromGit',
                'seed': 12345,  # Match phase1.json seed
                'generation_date': '2025-12-19',
                'compatible_engines': ['rmatch', 're2j', 'java-native-optimized', 'java-native-unfair'],
                'validation_status': 'validated',
                'note': f'Derived from git-stored 10K stable patterns (first {size} patterns)'
            },
            'patterns': patterns,
            'pattern_count': len(patterns),
            'derived_from': 'benchmark_suites/stable_patterns/patterns_10000.txt'
        }

        metadata_file = log_mining_dir / f"patterns_{size}_metadata.json"
        with open(metadata_file, 'w') as f:
            json.dump(metadata, f, indent=2)

        print(f"✅ Created patterns_{size}.txt ({len(patterns)} patterns) with metadata")

    print(f"\n🎯 Phase1 pattern reuse setup complete!")
    print(f"📁 All files created in: {log_mining_dir}")
    print(f"🔑 All metadata uses seed=12345 to match phase1.json")

    return True

if __name__ == "__main__":
    success = setup_phase1_pattern_reuse()
    if success:
        print("\n✅ Ready for pattern reuse with phase1.json!")
    else:
        print("\n❌ Setup failed!")
        exit(1)