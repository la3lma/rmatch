#!/usr/bin/env python3
"""Create metadata file for 1K patterns to fix pattern detection issue."""

import json

def create_metadata():
    # Read all patterns from the 1K pattern file
    with open('benchmark_suites/log_mining/patterns_1000.txt', 'r') as f:
        patterns = [line.strip() for line in f if line.strip()]

    # Create metadata structure based on patterns_100_metadata.json
    metadata = {
        "suite_metadata": {
            "suite_name": "log_mining",
            "generator": "FairPatternGenerator",
            "seed": 42,
            "compatible_engines": [
                "rmatch",
                "re2j",
                "java-native-optimized",
                "java-native-unfair"
            ],
            "note": "Patterns designed for cross-engine compatibility - 1K scaled version"
        },
        "patterns": patterns,
        "pattern_count": len(patterns)
    }

    # Write to permanent location
    with open('benchmark_suites/log_mining/patterns_1000_metadata.json', 'w') as f:
        json.dump(metadata, f, indent=2)

    print(f"✅ Created metadata file with {len(patterns)} patterns")
    print(f"📍 Location: benchmark_suites/log_mining/patterns_1000_metadata.json")

    return metadata

if __name__ == "__main__":
    create_metadata()