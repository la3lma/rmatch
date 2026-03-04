#!/usr/bin/env python3
"""
Demo script to generate sample benchmark data and showcase the enhanced analytics dashboard.
This demonstrates the world-class analytics implementation with all requested features:
- Scanning linearity analysis
- Performance prediction models
- Efficiency trend analysis
- Enhanced clarity with detailed explanations
- Extensive screen real-estate usage
"""

import sqlite3
import numpy as np
import random
from pathlib import Path

def create_sample_data():
    """Create realistic sample benchmark data for demonstration."""

    # Create demo database
    demo_db_path = "demo_benchmark_results.db"
    conn = sqlite3.connect(demo_db_path)

    # Create the benchmark_statistics table with the exact schema from the framework
    conn.execute('''
        CREATE TABLE IF NOT EXISTS benchmark_statistics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            run_id TEXT NOT NULL,
            engine_name TEXT NOT NULL,
            pattern_count INTEGER NOT NULL,
            input_size TEXT NOT NULL,
            metric_name TEXT NOT NULL,
            sample_count INTEGER NOT NULL,
            mean_value REAL,
            median_value REAL,
            std_dev REAL,
            min_value REAL,
            max_value REAL,
            p95_value REAL,
            p99_value REAL,
            ci_lower_95 REAL,
            ci_upper_95 REAL,
            has_outliers BOOLEAN DEFAULT FALSE,
            outlier_count INTEGER DEFAULT 0,
            coefficient_of_variation REAL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(run_id, engine_name, pattern_count, input_size, metric_name)
        )
    ''')

    # Sample engines with realistic performance characteristics
    engines = [
        {'name': 'rmatch', 'base_throughput': 850, 'memory_efficiency': 0.8, 'scaling_factor': 0.95},  # Your Java-based regex matcher
        {'name': 're2j', 'base_throughput': 1200, 'memory_efficiency': 0.6, 'scaling_factor': 0.92},  # Google RE2 Java port
        {'name': 'java-native-unfair', 'base_throughput': 1100, 'memory_efficiency': 0.4, 'scaling_factor': 0.88},  # Java native regex
        {'name': 'pcre2', 'base_throughput': 650, 'memory_efficiency': 0.3, 'scaling_factor': 0.85}  # PCRE2 library
    ]

    pattern_counts = [10, 100, 1000, 10000]
    input_sizes = ['1MB', '10MB', '100MB', '1GB']
    input_size_bytes = {'1MB': 1_000_000, '10MB': 10_000_000, '100MB': 100_000_000, '1GB': 1_000_000_000}

    run_id = "demo_enhanced_analytics_20251231"

    data_points = []

    for engine in engines:
        for pattern_count in pattern_counts:
            for input_size in input_sizes:
                # Simulate realistic scaling behavior

                # Throughput decreases with more patterns (complexity scaling)
                throughput_base = engine['base_throughput'] * (engine['scaling_factor'] ** np.log10(pattern_count))
                throughput_variance = throughput_base * 0.1
                throughput_mean = max(10, throughput_base + random.gauss(0, throughput_variance))

                # Memory usage increases with pattern count
                memory_per_pattern_base = 5.0 / engine['memory_efficiency']  # KB per pattern
                memory_scaling = 1.0 + (pattern_count / 10000) * 0.3  # Slight super-linear growth
                memory_mean = memory_per_pattern_base * memory_scaling + random.gauss(0, 1.0)
                memory_mean = max(1.0, memory_mean)

                # Compilation time grows roughly linearly with slight overhead
                compilation_base = pattern_count * 0.05  # ms per pattern
                compilation_overhead = np.log10(pattern_count) * 2.0
                compilation_mean = compilation_base + compilation_overhead + random.gauss(0, compilation_base * 0.2)
                compilation_mean = max(0.1, compilation_mean)

                # Scanning time should scale linearly with corpus size
                bytes_size = input_size_bytes[input_size]
                scanning_per_mb = 0.8 + random.gauss(0, 0.1)  # ms per MB
                scanning_mean = (bytes_size / 1_000_000) * scanning_per_mb
                scanning_mean = max(0.1, scanning_mean)

                sample_count = 3  # 3 iterations per configuration

                # Add realistic variance
                def add_stats(metric_name, mean_val):
                    std_dev = mean_val * 0.15  # 15% coefficient of variation
                    min_val = max(0.01, mean_val - std_dev * 2)
                    max_val = mean_val + std_dev * 2
                    median_val = mean_val + random.gauss(0, std_dev * 0.1)
                    p95_val = mean_val + std_dev * 1.6
                    p99_val = mean_val + std_dev * 2.3
                    ci_lower = mean_val - std_dev
                    ci_upper = mean_val + std_dev
                    cv = std_dev / mean_val if mean_val > 0 else 0

                    data_points.append({
                        'run_id': run_id,
                        'engine_name': engine['name'],
                        'pattern_count': pattern_count,
                        'input_size': input_size,
                        'metric_name': metric_name,
                        'sample_count': sample_count,
                        'mean_value': mean_val,
                        'median_value': median_val,
                        'std_dev': std_dev,
                        'min_value': min_val,
                        'max_value': max_val,
                        'p95_value': p95_val,
                        'p99_value': p99_val,
                        'ci_lower_95': ci_lower,
                        'ci_upper_95': ci_upper,
                        'has_outliers': random.random() < 0.1,  # 10% chance of outliers
                        'outlier_count': random.randint(0, 1) if random.random() < 0.1 else 0,
                        'coefficient_of_variation': cv
                    })

                # Generate all the metrics
                add_stats('throughput_mb_s', throughput_mean)
                add_stats('memory_per_pattern_kb', memory_mean)
                add_stats('compilation_ms', compilation_mean)
                add_stats('scanning_ms', scanning_mean)

    # Insert all data points
    for dp in data_points:
        conn.execute('''
            INSERT OR REPLACE INTO benchmark_statistics
            (run_id, engine_name, pattern_count, input_size, metric_name, sample_count,
             mean_value, median_value, std_dev, min_value, max_value, p95_value, p99_value,
             ci_lower_95, ci_upper_95, has_outliers, outlier_count, coefficient_of_variation)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ''', (
            dp['run_id'], dp['engine_name'], dp['pattern_count'], dp['input_size'],
            dp['metric_name'], dp['sample_count'], dp['mean_value'], dp['median_value'],
            dp['std_dev'], dp['min_value'], dp['max_value'], dp['p95_value'], dp['p99_value'],
            dp['ci_lower_95'], dp['ci_upper_95'], dp['has_outliers'], dp['outlier_count'],
            dp['coefficient_of_variation']
        ))

    conn.commit()
    conn.close()

    print(f"✅ Generated {len(data_points)} realistic benchmark data points")
    print(f"📊 Demo database created: {demo_db_path}")
    return demo_db_path

if __name__ == "__main__":
    create_sample_data()