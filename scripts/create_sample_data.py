#!/usr/bin/env python3
"""
Generate sample JMH benchmark data with 5000 and 10000 pattern counts
to demonstrate the improved charts for GitHub issue #161.
"""

import json
import os
import random
from datetime import datetime, timedelta


def create_sample_jmh_data():
    """Create sample JMH data files with 5000 and 10000 pattern counts."""
    os.makedirs("benchmarks/results", exist_ok=True)
    
    # Base timestamp
    base_time = datetime(2025, 9, 7, 12, 0, 0)
    
    # Pattern counts to generate data for
    pattern_counts = [1, 5, 10, 100, 5000, 10000]
    
    for i in range(10):  # Generate 10 sample files
        timestamp = base_time + timedelta(hours=i, minutes=random.randint(0, 59))
        timestamp_str = timestamp.strftime("%Y%m%dT%H%M%SZ")
        
        jmh_results = []
        
        for pattern_count in pattern_counts:
            # Simulate realistic performance degradation with more patterns
            base_score = 2.0 + (pattern_count / 1000.0) * random.uniform(0.8, 1.2)
            score = base_score + random.uniform(-0.5, 0.5)
            
            result = {
                "jmhVersion": "1.37",
                "benchmark": "no.rmz.rmatch.benchmarks.CompileAndMatchBench.buildMatcher",
                "mode": "avgt",
                "threads": 1,
                "forks": 1,
                "jvm": "/opt/java/bin/java",
                "jvmArgs": ["-Xms1G", "-Xmx1G"],
                "jdkVersion": "21.0.2",
                "vmName": "OpenJDK 64-Bit Server VM",
                "vmVersion": "21.0.2+13-58",
                "warmupIterations": 1,
                "warmupTime": "1 s",
                "warmupBatchSize": 1,
                "measurementIterations": 1,
                "measurementTime": "1 s", 
                "measurementBatchSize": 1,
                "params": {
                    "patternCount": str(pattern_count)
                },
                "primaryMetric": {
                    "score": score,
                    "scoreError": "NaN",
                    "scoreConfidence": ["NaN", "NaN"],
                    "scorePercentiles": {
                        "0.0": score,
                        "50.0": score,
                        "90.0": score,
                        "95.0": score,
                        "99.0": score,
                        "99.9": score,
                        "99.99": score,
                        "99.999": score,
                        "99.9999": score,
                        "100.0": score
                    },
                    "scoreUnit": "us/op",
                    "rawData": [[score]]
                },
                "secondaryMetrics": {}
            }
            jmh_results.append(result)
        
        # Write to file
        filename = f"benchmarks/results/jmh-{timestamp_str}-sample.json"
        with open(filename, 'w') as f:
            json.dump(jmh_results, f, indent=2)
        
        print(f"Created sample file: {filename}")

def create_sample_macro_data():
    """Create sample macro data files with proper JSON formatting."""
    os.makedirs("benchmarks/results", exist_ok=True)
    
    base_time = datetime(2025, 9, 7, 14, 0, 0)
    
    for i in range(5):  # Generate 5 sample macro files
        timestamp = base_time + timedelta(hours=i*2, minutes=random.randint(0, 59))
        timestamp_str = timestamp.strftime("%Y%m%dT%H%M%SZ")
        
        pattern_count = 10000 if i % 2 == 0 else 5000
        duration = 15000 + pattern_count/10 + random.randint(-2000, 2000)
        
        macro_data = {
            "type": "macro",
            "timestamp": timestamp_str,
            "git": {"sha": "sample123", "branch": "main"},
            "java_version": "openjdk-21.0.2",
            "os": {"name": "Linux", "release": "6.8.0"},
            "args": {"max_regexps": pattern_count},
            "duration_ms": int(duration),
            "exit_status": 0,
            "log": f"benchmarks/results/macro-{timestamp_str}-sample.log"
        }
        
        filename = f"benchmarks/results/macro-{timestamp_str}-sample.json"
        with open(filename, 'w') as f:
            json.dump(macro_data, f, indent=2)
        
        # Create corresponding log file
        log_filename = f"benchmarks/results/macro-{timestamp_str}-sample.log"
        with open(log_filename, 'w') as f:
            f.write(f"BenchmarkTheWutheringHeightsCorpus, argx = [{pattern_count}]\n")
            f.write(f"Benchmarking wuthering heights for index {pattern_count}\n")
            f.write(f"Duration: {duration} ms\n")
        
        print(f"Created sample macro file: {filename}")
        print(f"Created sample log file: {log_filename}")

if __name__ == "__main__":
    print("🔧 Creating sample benchmark data with 5000 and 10000 patterns...")
    create_sample_jmh_data()
    create_sample_macro_data()
    print("✅ Sample data creation complete!")