#!/usr/bin/env python3
"""
Debug timeout calculation to verify the fix.
"""

def calculate_timeout(pattern_count, corpus_size_mb):
    # Base timeout: 45 seconds (30 + 15 as requested)
    # Scale linearly with the product of pattern count and corpus size in MB
    # Use a scaling factor that gives reasonable timeouts for large workloads
    # For 10K patterns × 100MB = should be ~145 seconds (45 + 100)
    base_timeout = 45
    scaling_factor = (pattern_count * corpus_size_mb) / 10000  # Fixed: was 100000, now 10000 for proper scaling
    dynamic_timeout = max(base_timeout, base_timeout + scaling_factor)
    timeout_seconds = int(min(dynamic_timeout, 600))  # Cap at 10 minutes for safety

    print(f"Debug timeout calculation:")
    print(f"  pattern_count: {pattern_count}")
    print(f"  corpus_size_mb: {corpus_size_mb}")
    print(f"  base_timeout: {base_timeout}")
    print(f"  scaling_factor: {scaling_factor}")
    print(f"  dynamic_timeout: {dynamic_timeout}")
    print(f"  timeout_seconds (final): {timeout_seconds}")

    return timeout_seconds

if __name__ == "__main__":
    print("Testing timeout calculation with 10K patterns and 100MB corpus:")
    timeout = calculate_timeout(10000, 100.0)
    print(f"\nFinal timeout: {timeout} seconds")

    print("\nTesting original broken calculation (for comparison):")
    base_timeout = 45
    scaling_factor_broken = (10000 * 100.0) / 100000  # Original broken calculation
    dynamic_timeout_broken = max(base_timeout, base_timeout + scaling_factor_broken)
    timeout_broken = int(min(dynamic_timeout_broken, 600))
    print(f"  Broken scaling_factor: {scaling_factor_broken}")
    print(f"  Broken timeout: {timeout_broken} seconds")