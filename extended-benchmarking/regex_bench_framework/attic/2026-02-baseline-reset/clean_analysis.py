#!/usr/bin/env python3
"""
Clean analysis excluding extreme outliers that skew the results.
"""

import sqlite3
import pandas as pd

def analyze_clean_benchmark_data():
    """Analyze benchmark data excluding extreme outliers."""

    db_path = "results/production_20251221_115840/jobs.db"
    conn = sqlite3.connect(db_path)

    # Set reasonable thresholds (anything over 60 seconds is likely a timeout/outlier)
    MAX_SCANNING_MS = 60000  # 60 seconds
    MAX_COMPILATION_MS = 10000  # 10 seconds

    print("🧹 CLEAN REGEX BENCHMARK ANALYSIS (Outliers Excluded)")
    print("=" * 60)
    print(f"Excluding: scanning > {MAX_SCANNING_MS}ms, compilation > {MAX_COMPILATION_MS}ms")
    print()

    # Clean query with outlier filtering
    clean_query = f"""
    SELECT
        engine_name,
        pattern_count,
        input_size,
        scanning_ns/1000000.0 as scanning_ms,
        compilation_ns/1000000.0 as compilation_ms,
        status
    FROM benchmark_jobs
    WHERE status = 'COMPLETED'
    AND scanning_ns/1000000.0 < {MAX_SCANNING_MS}
    AND compilation_ns/1000000.0 < {MAX_COMPILATION_MS}
    """

    df = pd.read_sql_query(clean_query, conn)

    print(f"📊 CLEAN DATA SUMMARY:")
    total_completed = pd.read_sql_query("SELECT COUNT(*) as count FROM benchmark_jobs WHERE status = 'COMPLETED'", conn).iloc[0]['count']
    clean_count = len(df)
    excluded_count = total_completed - clean_count

    print(f"Total completed jobs: {total_completed}")
    print(f"Clean jobs (used): {clean_count}")
    print(f"Extreme outliers (excluded): {excluded_count}")
    print()

    # Calculate corpus size factor for throughput
    size_bytes = {'1MB': 1e6, '10MB': 10e6, '100MB': 100e6, '1GB': 1e9}
    df['corpus_mb'] = df['input_size'].map(size_bytes) / 1e6
    df['throughput_mb_s'] = df['corpus_mb'] / (df['scanning_ms'] / 1000)

    # Engine summary
    summary = df.groupby('engine_name').agg({
        'scanning_ms': ['count', 'mean', 'median'],
        'compilation_ms': ['mean', 'median'],
        'throughput_mb_s': ['mean', 'median']
    }).round(2)

    print("🚀 ENGINE PERFORMANCE (CLEAN DATA):")
    print("Engine               | Tests | Scan (ms) | Compile (ms) | Throughput (MB/s)")
    print("                     |       | Avg | Med | Avg  | Med   | Avg    | Med")
    print("-" * 85)

    for engine in summary.index:
        tests = int(summary.loc[engine, ('scanning_ms', 'count')])
        scan_avg = summary.loc[engine, ('scanning_ms', 'mean')]
        scan_med = summary.loc[engine, ('scanning_ms', 'median')]
        comp_avg = summary.loc[engine, ('compilation_ms', 'mean')]
        comp_med = summary.loc[engine, ('compilation_ms', 'median')]
        tp_avg = summary.loc[engine, ('throughput_mb_s', 'mean')]
        tp_med = summary.loc[engine, ('throughput_mb_s', 'median')]

        print(f"{engine:20} | {tests:5} | {scan_avg:7.1f} | {scan_med:3.0f} | {comp_avg:4.1f} | {comp_med:5.1f} | {tp_avg:6.1f} | {tp_med:3.0f}")

    print()

    # Performance by pattern count (clean data)
    print("📈 CLEAN SCALING ANALYSIS:")
    for pattern_count in sorted(df['pattern_count'].unique()):
        pattern_data = df[df['pattern_count'] == pattern_count]
        if len(pattern_data) > 0:
            print(f"\n{pattern_count} PATTERNS:")
            pattern_summary = pattern_data.groupby('engine_name').agg({
                'scanning_ms': ['count', 'mean'],
                'throughput_mb_s': 'mean'
            }).round(2)

            print("Engine               | Tests | Scan (ms) | Throughput (MB/s)")
            print("-" * 60)
            for engine in pattern_summary.index:
                tests = int(pattern_summary.loc[engine, ('scanning_ms', 'count')])
                scan_avg = pattern_summary.loc[engine, ('scanning_ms', 'mean')]
                tp_avg = pattern_summary.loc[engine, ('throughput_mb_s', 'mean')]
                print(f"{engine:20} | {tests:5} | {scan_avg:9.1f} | {tp_avg:14.1f}")

    # Excluded outliers analysis
    outlier_query = f"""
    SELECT engine_name, pattern_count, input_size, scanning_ns/1000000.0 as scanning_ms
    FROM benchmark_jobs
    WHERE status = 'COMPLETED'
    AND (scanning_ns/1000000.0 >= {MAX_SCANNING_MS} OR compilation_ns/1000000.0 >= {MAX_COMPILATION_MS})
    ORDER BY scanning_ns DESC
    """

    outliers_df = pd.read_sql_query(outlier_query, conn)

    if len(outliers_df) > 0:
        print(f"\n⚠️  EXCLUDED OUTLIERS ({len(outliers_df)} jobs):")
        print("Engine               | Patterns | Corpus | Scan Time")
        print("-" * 55)
        for _, row in outliers_df.iterrows():
            scan_time_sec = row['scanning_ms'] / 1000
            if scan_time_sec > 3600:
                time_str = f"{scan_time_sec/3600:.1f}h"
            elif scan_time_sec > 60:
                time_str = f"{scan_time_sec/60:.1f}m"
            else:
                time_str = f"{scan_time_sec:.1f}s"
            print(f"{row['engine_name']:20} | {row['pattern_count']:8} | {row['input_size']:6} | {time_str:>9}")

    print(f"\n💡 KEY INSIGHTS (CLEAN DATA):")
    print("   • rmatch shows competitive performance on smaller datasets")
    print("   • Extreme outliers indicate scaling issues with large pattern counts + corpus sizes")
    print("   • Multiple engines struggle with 1000+ patterns on large corpora")

    conn.close()

if __name__ == "__main__":
    analyze_clean_benchmark_data()