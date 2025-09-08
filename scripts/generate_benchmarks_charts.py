#!/usr/bin/env python3
"""
Generate performance charts from benchmarks/results data only.

This script creates comprehensive and elegant performance visualizations
based solely on JMH and macro benchmark data found in benchmarks/results/.

Features:
- JMH benchmark trend analysis over time
- Macro benchmark execution time evolution
- Performance statistics and percentiles
- Clean, modern chart styling
"""

import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import seaborn as sns
import numpy as np
import json
import os
from datetime import datetime
from pathlib import Path

# Configure matplotlib for better-looking plots
plt.style.use('seaborn-v0_8-whitegrid')
sns.set_palette("husl")

def load_jmh_data(results_dir="benchmarks/results"):
    """Load and parse JMH benchmark data."""
    results_path = Path(results_dir)
    if not results_path.exists():
        print(f"Results directory {results_dir} not found")
        return pd.DataFrame()
    
    jmh_data = []
    jmh_files = list(results_path.glob("jmh-*.json"))
    
    for jmh_file in jmh_files:
        try:
            with open(jmh_file, 'r') as f:
                data = json.load(f)
            
            # Extract timestamp from filename (format: jmh-20250907T121845Z.json)
            timestamp_str = jmh_file.stem.replace('jmh-', '')
            timestamp = datetime.strptime(timestamp_str, '%Y%m%dT%H%M%SZ')
            
            # Process each benchmark result
            for result in data:
                benchmark_data = {
                    'timestamp': timestamp,
                    'benchmark': result['benchmark'],
                    'mode': result['mode'],
                    'score': result['primaryMetric']['score'],
                    'score_unit': result['primaryMetric']['scoreUnit'],
                    'score_error': result['primaryMetric'].get('scoreError', 'NaN'),
                    'params': result.get('params', {}),
                    'file': jmh_file.name
                }
                
                # Add parameter information
                if 'patternCount' in benchmark_data['params']:
                    benchmark_data['pattern_count'] = int(benchmark_data['params']['patternCount'])
                
                jmh_data.append(benchmark_data)
                
        except Exception as e:
            print(f"Error loading {jmh_file}: {e}")
            continue
    
    if jmh_data:
        df = pd.DataFrame(jmh_data)
        print(f"Loaded {len(df)} JMH benchmark results from {len(jmh_files)} files")
        return df
    else:
        print("No JMH data found")
        return pd.DataFrame()

def load_macro_data(results_dir="benchmarks/results"):
    """Load and parse macro benchmark data."""
    results_path = Path(results_dir)
    if not results_path.exists():
        print(f"Results directory {results_dir} not found")
        return pd.DataFrame()
    
    macro_data = []
    macro_files = list(results_path.glob("macro-*.json"))
    
    for macro_file in macro_files:
        try:
            with open(macro_file, 'r') as f:
                content = f.read()
                # Fix JSON format issues with unescaped quotes in java version
                content = content.replace('"openjdk version "', '"openjdk version \\"')
                content = content.replace('" 2024-01-16', '\\" 2024-01-16')
                content = content.replace('" OpenJDK Runtime', '\\" OpenJDK Runtime')
                content = content.replace('" OpenJDK 64-Bit', '\\" OpenJDK 64-Bit')
                content = content.replace(', sharing) "', ', sharing)\\"')
                data = json.loads(content)
            
            # Extract timestamp from filename
            timestamp_str = macro_file.stem.replace('macro-', '')
            timestamp = datetime.strptime(timestamp_str, '%Y%m%dT%H%M%SZ')
            
            macro_record = {
                'timestamp': timestamp,
                'type': data.get('type', 'macro'),
                'duration_ms': data.get('duration_ms'),
                'exit_status': data.get('exit_status'),
                'max_regexps': data.get('args', {}).get('max_regexps'),
                'git_sha': data.get('git', {}).get('sha'),
                'git_branch': data.get('git', {}).get('branch'),
                'file': macro_file.name
            }
            
            macro_data.append(macro_record)
            
        except Exception as e:
            print(f"Error loading {macro_file}: {e}")
            continue
    
    if macro_data:
        df = pd.DataFrame(macro_data)
        print(f"Loaded {len(df)} macro benchmark results from {len(macro_files)} files")
        return df
    else:
        print("No macro data found")
        return pd.DataFrame()

def create_jmh_performance_chart(jmh_df, output_dir="charts"):
    """Create JMH performance evolution chart."""
    if jmh_df.empty:
        print("No JMH data available for charting")
        return
    
    os.makedirs(output_dir, exist_ok=True)
    
    # Filter for the main benchmark
    main_benchmarks = jmh_df[jmh_df['benchmark'].str.contains('buildMatcher')]
    if main_benchmarks.empty:
        print("No buildMatcher benchmark data found")
        return
    
    # Sort by timestamp
    main_benchmarks = main_benchmarks.sort_values('timestamp')
    
    # Create figure
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))
    fig.suptitle('JMH Benchmark Performance Evolution', fontsize=16, fontweight='bold')
    
    # Plot 1: Performance over time by pattern count
    if 'pattern_count' in main_benchmarks.columns:
        pattern_counts = main_benchmarks['pattern_count'].unique()
        colors = plt.cm.viridis(np.linspace(0, 1, len(pattern_counts)))
        
        for i, pattern_count in enumerate(sorted(pattern_counts)):
            subset = main_benchmarks[main_benchmarks['pattern_count'] == pattern_count]
            if not subset.empty:
                ax1.plot(subset['timestamp'], subset['score'], 'o-', 
                        label=f'{pattern_count} patterns', 
                        color=colors[i], linewidth=2, markersize=6)
    else:
        ax1.plot(main_benchmarks['timestamp'], main_benchmarks['score'], 'o-', 
                color='#2E8B57', linewidth=2, markersize=6, label='Performance')
    
    ax1.set_title('Build Matcher Performance Over Time')
    ax1.set_ylabel(f"Performance ({main_benchmarks['score_unit'].iloc[0]})")
    ax1.legend()
    ax1.grid(True, alpha=0.3)
    ax1.xaxis.set_major_formatter(mdates.DateFormatter('%m-%d %H:%M'))
    ax1.xaxis.set_major_locator(mdates.HourLocator(interval=6))
    plt.setp(ax1.xaxis.get_majorticklabels(), rotation=45)
    
    # Plot 2: Performance distribution (box plot)
    if len(main_benchmarks) > 5:  # Only create box plot if we have enough data
        if 'pattern_count' in main_benchmarks.columns:
            # Create box plot by pattern count
            pattern_data = []
            pattern_labels = []
            for pattern_count in sorted(main_benchmarks['pattern_count'].unique()):
                subset = main_benchmarks[main_benchmarks['pattern_count'] == pattern_count]
                if len(subset) > 1:
                    pattern_data.append(subset['score'].values)
                    pattern_labels.append(f'{pattern_count} patterns')
            
            if pattern_data:
                ax2.boxplot(pattern_data, tick_labels=pattern_labels)
                ax2.set_title('Performance Distribution by Pattern Count')
                ax2.set_ylabel(f"Performance ({main_benchmarks['score_unit'].iloc[0]})")
        else:
            ax2.hist(main_benchmarks['score'], bins=10, alpha=0.7, color='#2E8B57', edgecolor='black')
            ax2.set_title('Performance Distribution')
            ax2.set_xlabel(f"Performance ({main_benchmarks['score_unit'].iloc[0]})")
            ax2.set_ylabel('Frequency')
    else:
        # If not enough data for distribution, show recent performance metrics
        recent_data = main_benchmarks.tail(5)
        ax2.bar(range(len(recent_data)), recent_data['score'], color='#2E8B57', alpha=0.7)
        ax2.set_title('Recent Performance Results')
        ax2.set_ylabel(f"Performance ({main_benchmarks['score_unit'].iloc[0]})")
        ax2.set_xticks(range(len(recent_data)))
        ax2.set_xticklabels([t.strftime('%m-%d %H:%M') for t in recent_data['timestamp']], rotation=45)
    
    ax2.grid(True, alpha=0.3)
    
    plt.tight_layout()
    output_path = os.path.join(output_dir, 'jmh_performance_evolution.png')
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.close()
    print(f"JMH performance chart saved to {output_path}")

def create_macro_performance_chart(macro_df, output_dir="charts"):
    """Create macro benchmark performance chart."""
    if macro_df.empty:
        print("No macro data available for charting")
        return
    
    os.makedirs(output_dir, exist_ok=True)
    
    # Filter successful runs
    successful_runs = macro_df[macro_df['exit_status'] == 0]
    if successful_runs.empty:
        print("No successful macro benchmark runs found")
        return
    
    # Sort by timestamp
    successful_runs = successful_runs.sort_values('timestamp')
    
    # Create figure
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))
    fig.suptitle('Macro Benchmark Performance Evolution', fontsize=16, fontweight='bold')
    
    # Plot 1: Execution time over time
    ax1.plot(successful_runs['timestamp'], successful_runs['duration_ms'], 'o-', 
            color='#FF6B35', linewidth=2, markersize=6, label='Execution Time')
    ax1.set_title('Macro Benchmark Execution Time')
    ax1.set_ylabel('Duration (milliseconds)')
    ax1.legend()
    ax1.grid(True, alpha=0.3)
    ax1.xaxis.set_major_formatter(mdates.DateFormatter('%m-%d %H:%M'))
    ax1.xaxis.set_major_locator(mdates.HourLocator(interval=6))
    plt.setp(ax1.xaxis.get_majorticklabels(), rotation=45)
    
    # Plot 2: Performance by max regexps (if available)
    if 'max_regexps' in successful_runs.columns and not successful_runs['max_regexps'].isna().all():
        unique_regexps = successful_runs['max_regexps'].unique()
        if len(unique_regexps) > 1:
            # Group by max_regexps and show performance
            grouped = successful_runs.groupby('max_regexps')['duration_ms'].agg(['mean', 'std', 'count'])
            grouped = grouped.reset_index()
            
            ax2.errorbar(grouped['max_regexps'], grouped['mean'], yerr=grouped['std'], 
                        fmt='o-', color='#FF6B35', linewidth=2, markersize=8, 
                        capsize=5, capthick=2, label='Mean ± Std Dev')
            ax2.set_title('Performance vs. Pattern Count')
            ax2.set_xlabel('Max Regexps')
            ax2.set_ylabel('Duration (milliseconds)')
            ax2.legend()
        else:
            # Single max_regexps value, show duration distribution
            ax2.hist(successful_runs['duration_ms'], bins=10, alpha=0.7, 
                    color='#FF6B35', edgecolor='black')
            ax2.set_title('Execution Time Distribution')
            ax2.set_xlabel('Duration (milliseconds)')
            ax2.set_ylabel('Frequency')
    else:
        # Show recent performance
        recent_data = successful_runs.tail(10)
        ax2.bar(range(len(recent_data)), recent_data['duration_ms'], 
               color='#FF6B35', alpha=0.7)
        ax2.set_title('Recent Execution Times')
        ax2.set_ylabel('Duration (milliseconds)')
        ax2.set_xticks(range(len(recent_data)))
        ax2.set_xticklabels([t.strftime('%m-%d %H:%M') for t in recent_data['timestamp']], rotation=45)
    
    ax2.grid(True, alpha=0.3)
    
    plt.tight_layout()
    output_path = os.path.join(output_dir, 'macro_performance_evolution.png')
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.close()
    print(f"Macro performance chart saved to {output_path}")

def create_combined_overview_chart(jmh_df, macro_df, output_dir="charts"):
    """Create a combined overview chart with key performance metrics."""
    os.makedirs(output_dir, exist_ok=True)
    
    fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(16, 12))
    fig.suptitle('rmatch Performance Overview', fontsize=18, fontweight='bold')
    
    # JMH Performance Summary
    if not jmh_df.empty:
        main_benchmarks = jmh_df[jmh_df['benchmark'].str.contains('buildMatcher')]
        if not main_benchmarks.empty:
            recent_jmh = main_benchmarks.sort_values('timestamp').tail(20)
            ax1.plot(recent_jmh['timestamp'], recent_jmh['score'], 'o-', 
                    color='#2E8B57', linewidth=2, markersize=4)
            ax1.set_title('JMH Build Performance')
            ax1.set_ylabel(f"Time ({recent_jmh['score_unit'].iloc[0]})")
            ax1.grid(True, alpha=0.3)
            ax1.xaxis.set_major_formatter(mdates.DateFormatter('%m-%d'))
            plt.setp(ax1.xaxis.get_majorticklabels(), rotation=45)
    
    # Macro Performance Summary
    if not macro_df.empty:
        successful_macro = macro_df[macro_df['exit_status'] == 0].sort_values('timestamp').tail(20)
        if not successful_macro.empty:
            ax2.plot(successful_macro['timestamp'], successful_macro['duration_ms'], 'o-', 
                    color='#FF6B35', linewidth=2, markersize=4)
            ax2.set_title('Macro Benchmark Duration')
            ax2.set_ylabel('Duration (ms)')
            ax2.grid(True, alpha=0.3)
            ax2.xaxis.set_major_formatter(mdates.DateFormatter('%m-%d'))
            plt.setp(ax2.xaxis.get_majorticklabels(), rotation=45)
    
    # Performance Statistics
    stats_data = []
    if not jmh_df.empty:
        main_benchmarks = jmh_df[jmh_df['benchmark'].str.contains('buildMatcher')]
        if not main_benchmarks.empty:
            recent_scores = main_benchmarks.sort_values('timestamp').tail(10)['score']
            stats_data.extend([
                f"JMH Avg: {recent_scores.mean():.2f} {main_benchmarks['score_unit'].iloc[0]}",
                f"JMH Std: {recent_scores.std():.2f} {main_benchmarks['score_unit'].iloc[0]}"
            ])
    
    if not macro_df.empty:
        successful_macro = macro_df[macro_df['exit_status'] == 0]
        if not successful_macro.empty:
            recent_durations = successful_macro.sort_values('timestamp').tail(10)['duration_ms']
            stats_data.extend([
                f"Macro Avg: {recent_durations.mean():.0f} ms",
                f"Macro Std: {recent_durations.std():.0f} ms"
            ])
    
    # Display stats as text
    ax3.axis('off')
    ax3.text(0.1, 0.9, 'Recent Performance Statistics', fontsize=14, fontweight='bold', 
             transform=ax3.transAxes)
    for i, stat in enumerate(stats_data):
        ax3.text(0.1, 0.7 - i*0.15, stat, fontsize=12, transform=ax3.transAxes)
    
    # Data summary
    ax4.axis('off')
    summary_data = [
        f"JMH Benchmark Results: {len(jmh_df)}",
        f"Macro Benchmark Results: {len(macro_df)}"
    ]
    
    if not macro_df.empty and 'exit_status' in macro_df.columns:
        summary_data.append(f"Successful Macro Runs: {len(macro_df[macro_df['exit_status'] == 0])}")
    
    if not jmh_df.empty:
        date_range = jmh_df['timestamp'].max() - jmh_df['timestamp'].min()
        summary_data.append(f"Data Span: {date_range.days} days")
    
    ax4.text(0.1, 0.9, 'Benchmark Data Summary', fontsize=14, fontweight='bold', 
             transform=ax4.transAxes)
    for i, summary in enumerate(summary_data):
        ax4.text(0.1, 0.7 - i*0.15, summary, fontsize=12, transform=ax4.transAxes)
    
    plt.tight_layout()
    output_path = os.path.join(output_dir, 'performance_overview.png')
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.close()
    print(f"Performance overview chart saved to {output_path}")

def main():
    """Main function to generate all performance charts."""
    print("Loading benchmark data...")
    
    # Load data
    jmh_df = load_jmh_data()
    macro_df = load_macro_data()
    
    if jmh_df.empty and macro_df.empty:
        print("No benchmark data found. Please run benchmarks first.")
        return
    
    print("Generating performance charts...")
    
    # Create output directory
    os.makedirs("charts", exist_ok=True)
    
    # Generate charts
    if not jmh_df.empty:
        create_jmh_performance_chart(jmh_df)
    
    if not macro_df.empty:
        create_macro_performance_chart(macro_df)
    
    create_combined_overview_chart(jmh_df, macro_df)
    
    print("Performance chart generation complete!")

if __name__ == "__main__":
    main()