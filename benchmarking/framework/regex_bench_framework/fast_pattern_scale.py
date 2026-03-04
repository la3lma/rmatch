#!/usr/bin/env python3
import sys
import random

def scale_patterns(input_file, output_file, target_count):
    """Scale up patterns by replicating with variations"""
    print(f"📊 Scaling {input_file} to {target_count} patterns...")
    
    # Read existing patterns
    with open(input_file, 'r') as f:
        base_patterns = [line.strip() for line in f if line.strip()]
    
    print(f"✓ Read {len(base_patterns)} base patterns")
    
    # Generate scaled patterns
    scaled_patterns = []
    random.seed(42)  # Consistent results
    
    for i in range(target_count):
        # Cycle through base patterns with variations
        base_idx = i % len(base_patterns)
        pattern = base_patterns[base_idx]
        
        # Add simple variations for uniqueness (10% of patterns get slight variations)
        if i >= len(base_patterns) and random.random() < 0.1:
            # Add case insensitive flag to some patterns
            if not pattern.startswith('(?i)') and '(?i)' not in pattern:
                pattern = f"(?i){pattern}"
            # Or add word boundary to others
            elif random.random() < 0.5 and '\\b' not in pattern:
                pattern = f"\\b{pattern}\\b"
                
        scaled_patterns.append(pattern)
    
    # Write scaled patterns
    with open(output_file, 'w') as f:
        for pattern in scaled_patterns:
            f.write(f"{pattern}\n")
    
    print(f"✅ Generated {len(scaled_patterns)} patterns in {output_file}")

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python3 fast_pattern_scale.py <input_file> <output_file> <target_count>")
        sys.exit(1)
    
    input_file, output_file, target_count = sys.argv[1], sys.argv[2], int(sys.argv[3])
    scale_patterns(input_file, output_file, target_count)
