#!/usr/bin/env python3
"""
Generate specialized performance charts for large-scale pure Java matcher performance.

This script creates dedicated charts highlighting the performance of the pure Java 
matcher for 5K and 10K regex patterns, as requested in issue #174.
"""

import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import seaborn as sns
import numpy as np
import os
import json
from datetime import datetime
from pathlib import Path

# Set up matplotlib for better-looking plots
plt.style.use('default')
sns.set_palette("husl")

def load_large_scale_performance_data():
    """Load large-scale performance data from recent JSON files."""
    results = []
    
    # Load JSON data from benchmarks/results
    results_dir = Path("benchmarks/results")
    if results_dir.exists():
        json_files = list(results_dir.glob("performance-check-*.json"))
        for json_file in json_files:
            try:
                with open(json_file, 'r') as f:
                    data = json.load(f)
                    
                # Extract timestamp and performance data
                timestamp = data.get('timestamp')
                if timestamp and 'current_results' in data:
                    row_data = {
                        'timestamp': timestamp,
                        'datetime': pd.to_datetime(timestamp),
                        'source_file': json_file.name,
                    }
                    
                    # Extract performance metrics
                    if 'performance_result' in data:
                        perf_result = data['performance_result']
                        row_data.update({
                            'status': perf_result.get('status'),
                            'time_improvement_percent': perf_result.get('time_improvement_percent'),
                            'memory_improvement_percent': perf_result.get('memory_improvement_percent'),
                            'statistically_significant': perf_result.get('statistically_significant')
                        })
                    
                    # Extract current results
                    current_results = data['current_results']
                    rmatch_results = current_results.get('rmatch', {})
                    java_results = current_results.get('java', {})
                    
                    row_data.update({
                        'rmatch_avg_time_ms': rmatch_results.get('avg_time_ms'),
                        'rmatch_avg_memory_mb': rmatch_results.get('avg_memory_mb'),
                        'rmatch_count': rmatch_results.get('count'),
                        'java_avg_time_ms': java_results.get('avg_time_ms'),
                        'java_avg_memory_mb': java_results.get('avg_memory_mb'),
                        'java_count': java_results.get('count')
                    })
                    
                    # Try to infer pattern count from filename or performance characteristics
                    # This is a heuristic - larger times likely indicate larger pattern sets
                    java_time = java_results.get('avg_time_ms', 0)
                    if java_time > 4000:
                        row_data['estimated_pattern_count'] = '10K'
                    elif java_time > 2000:
                        row_data['estimated_pattern_count'] = '5K'
                    else:
                        row_data['estimated_pattern_count'] = 'Small'
                    
                    results.append(row_data)
                    print(f"Loaded large-scale performance data from {json_file}")
                    
            except Exception as e:
                print(f"Error loading {json_file}: {e}")
                continue
    
    return pd.DataFrame(results) if results else pd.DataFrame()

def create_pure_java_performance_chart(df, output_dir="charts"):
    """Create dedicated charts showing pure Java matcher performance for different scales."""
    if df.empty:
        print("No large-scale performance data found")
        return
    
    # Ensure output directory exists
    os.makedirs(output_dir, exist_ok=True)
    
    # Sort by date
    df = df.sort_values('datetime')
    
    # Create figure with 2x2 layout focusing on Java performance
    fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(16, 12))
    fig.suptitle('Pure Java Matcher Performance Analysis (Large Pattern Sets)', 
                 fontsize=16, fontweight='bold', color='#8B0000')
    
    # 1. Java execution time by pattern scale
    if 'java_avg_time_ms' in df.columns and 'estimated_pattern_count' in df.columns:
        # Group by estimated pattern count
        for pattern_count in df['estimated_pattern_count'].unique():
            subset = df[df['estimated_pattern_count'] == pattern_count]
            if len(subset) > 0:
                color = '#DC143C' if pattern_count == '10K' else '#FF6B35' if pattern_count == '5K' else '#FFA500'
                marker = 'o' if pattern_count == '10K' else 's' if pattern_count == '5K' else '^'
                ax1.plot(subset['datetime'], subset['java_avg_time_ms'], 
                        marker=marker, linestyle='-', linewidth=3, markersize=8, 
                        color=color, label=f'Java regex ({pattern_count} patterns)', alpha=0.8)
        
        ax1.set_title('Java Regex Execution Time by Pattern Scale', fontweight='bold', color='#8B0000')
        ax1.set_ylabel('Execution Time (milliseconds)', fontweight='bold')
        ax1.legend(framealpha=0.9)
        ax1.grid(True, alpha=0.3)
        format_time_axis(ax1)
    
    # 2. Java memory usage by pattern scale  
    if 'java_avg_memory_mb' in df.columns and 'estimated_pattern_count' in df.columns:
        for pattern_count in df['estimated_pattern_count'].unique():
            subset = df[df['estimated_pattern_count'] == pattern_count]
            if len(subset) > 0:
                color = '#DC143C' if pattern_count == '10K' else '#FF6B35' if pattern_count == '5K' else '#FFA500'
                marker = 'o' if pattern_count == '10K' else 's' if pattern_count == '5K' else '^'
                ax2.plot(subset['datetime'], subset['java_avg_memory_mb'], 
                        marker=marker, linestyle='-', linewidth=3, markersize=8, 
                        color=color, label=f'Java regex ({pattern_count} patterns)', alpha=0.8)
        
        ax2.set_title('Java Regex Memory Usage by Pattern Scale', fontweight='bold', color='#8B0000')
        ax2.set_ylabel('Memory Usage (MB)', fontweight='bold')
        ax2.legend(framealpha=0.9)
        ax2.grid(True, alpha=0.3)
        format_time_axis(ax2)
    
    # 3. Performance ratios (rmatch/java) with pattern scale annotations
    if 'rmatch_avg_time_ms' in df.columns and 'java_avg_time_ms' in df.columns:
        df_ratio = df.dropna(subset=['rmatch_avg_time_ms', 'java_avg_time_ms']).copy()
        if not df_ratio.empty:
            df_ratio['performance_ratio'] = df_ratio['rmatch_avg_time_ms'] / df_ratio['java_avg_time_ms']
            
            for pattern_count in df_ratio['estimated_pattern_count'].unique():
                subset = df_ratio[df_ratio['estimated_pattern_count'] == pattern_count]
                if len(subset) > 0:
                    color = '#8B0000' if pattern_count == '10K' else '#4B0082' if pattern_count == '5K' else '#008B8B'
                    marker = 'o' if pattern_count == '10K' else 's' if pattern_count == '5K' else '^'
                    size = 120 if pattern_count == '10K' else 100 if pattern_count == '5K' else 80
                    ax3.scatter(subset['datetime'], subset['performance_ratio'], 
                               s=size, marker=marker, color=color, alpha=0.8, edgecolors='black', linewidth=1,
                               label=f'{pattern_count} patterns (rmatch/java ratio)')
            
            ax3.axhline(y=1.0, color='gray', linestyle='--', alpha=0.7, linewidth=2, label='Parity (1.0x)')
            ax3.set_title('Performance Ratios: rmatch vs Java by Pattern Scale', fontweight='bold', color='#191970')
            ax3.set_ylabel('Time Ratio (lower is better for rmatch)', fontweight='bold')
            ax3.legend(framealpha=0.9, fontsize=9)
            ax3.grid(True, alpha=0.3)
            format_time_axis(ax3)
            
            # Add interpretive text
            ax3.text(0.02, 0.98, 'Higher ratios indicate Java regex is faster\nLower ratios indicate rmatch is faster', 
                    transform=ax3.transAxes, fontsize=9, verticalalignment='top',
                    bbox=dict(boxstyle='round,pad=0.3', facecolor='lightblue', alpha=0.8, edgecolor='navy'))
    
    # 4. Throughput comparison (data processing rate)
    if 'java_avg_time_ms' in df.columns and 'estimated_pattern_count' in df.columns:
        # Calculate throughput - assuming corpus size is consistent
        # This is a rough estimation based on typical Wuthering Heights corpus size
        estimated_corpus_size_mb = 2.0  # rough estimate
        
        for pattern_count in df['estimated_pattern_count'].unique():
            subset = df[df['estimated_pattern_count'] == pattern_count]
            if len(subset) > 0:
                # Calculate throughput in MB/s
                java_throughput = (estimated_corpus_size_mb * 1000) / subset['java_avg_time_ms']  
                rmatch_throughput = (estimated_corpus_size_mb * 1000) / subset['rmatch_avg_time_ms'] if 'rmatch_avg_time_ms' in subset.columns else None
                
                color_java = '#DC143C' if pattern_count == '10K' else '#FF6B35' if pattern_count == '5K' else '#FFA500'
                color_rmatch = '#2E8B57' if pattern_count == '10K' else '#228B22' if pattern_count == '5K' else '#32CD32'
                
                ax4.plot(subset['datetime'], java_throughput, 
                        marker='s', linestyle='--', linewidth=2, markersize=6, 
                        color=color_java, label=f'Java regex ({pattern_count})', alpha=0.8)
                
                if rmatch_throughput is not None and not subset['rmatch_avg_time_ms'].isna().all():
                    ax4.plot(subset['datetime'], rmatch_throughput,
                            marker='o', linestyle='-', linewidth=2, markersize=6,
                            color=color_rmatch, label=f'rmatch ({pattern_count})', alpha=0.8)
        
        ax4.set_title('Throughput Comparison by Pattern Scale', fontweight='bold', color='#006400')
        ax4.set_ylabel('Throughput (MB/s)', fontweight='bold')
        ax4.legend(framealpha=0.9, fontsize=9)
        ax4.grid(True, alpha=0.3)
        format_time_axis(ax4)
    
    plt.tight_layout()
    
    # Save the chart
    chart_path = os.path.join(output_dir, 'pure_java_performance_large_scale.png')
    plt.savefig(chart_path, dpi=300, bbox_inches='tight')
    print(f"Pure Java large-scale performance chart saved to: {chart_path}")
    
    plt.close()

def create_scale_comparison_chart(df, output_dir="charts"):
    """Create a focused comparison chart for 5K vs 10K pattern performance."""
    if df.empty:
        print("No data available for scale comparison")
        return
    
    # Ensure output directory exists
    os.makedirs(output_dir, exist_ok=True)
    
    # Filter for 5K and 10K data only
    scale_data = df[df['estimated_pattern_count'].isin(['5K', '10K'])].copy()
    if scale_data.empty:
        print("No 5K or 10K pattern data found for comparison")
        return
    
    # Create figure with 2x2 layout for detailed comparison
    fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(16, 10))
    fig.suptitle('Java Regex Performance: 5K vs 10K Pattern Comparison', 
                 fontsize=16, fontweight='bold', color='#8B0000')
    
    # 1. Direct execution time comparison
    if 'java_avg_time_ms' in scale_data.columns:
        data_5k = scale_data[scale_data['estimated_pattern_count'] == '5K']
        data_10k = scale_data[scale_data['estimated_pattern_count'] == '10K']
        
        if not data_5k.empty:
            ax1.bar(['5K Patterns'], [data_5k['java_avg_time_ms'].mean()], 
                   color='#FF6B35', alpha=0.8, width=0.6, edgecolor='black')
            ax1.text(0, data_5k['java_avg_time_ms'].mean() + 100, 
                    f'{data_5k["java_avg_time_ms"].mean():.0f}ms', 
                    ha='center', fontweight='bold')
        
        if not data_10k.empty:
            ax1.bar(['10K Patterns'], [data_10k['java_avg_time_ms'].mean()], 
                   color='#DC143C', alpha=0.8, width=0.6, edgecolor='black')
            ax1.text(1, data_10k['java_avg_time_ms'].mean() + 200,
                    f'{data_10k["java_avg_time_ms"].mean():.0f}ms', 
                    ha='center', fontweight='bold')
        
        ax1.set_title('Java Execution Time by Scale', fontweight='bold')
        ax1.set_ylabel('Execution Time (ms)', fontweight='bold')
        ax1.grid(True, alpha=0.3, axis='y')
    
    # 2. Direct memory usage comparison
    if 'java_avg_memory_mb' in scale_data.columns:
        if not data_5k.empty:
            ax2.bar(['5K Patterns'], [data_5k['java_avg_memory_mb'].mean()], 
                   color='#FF6B35', alpha=0.8, width=0.6, edgecolor='black')
            ax2.text(0, data_5k['java_avg_memory_mb'].mean() + 20, 
                    f'{data_5k["java_avg_memory_mb"].mean():.0f}MB', 
                    ha='center', fontweight='bold')
        
        if not data_10k.empty:
            ax2.bar(['10K Patterns'], [data_10k['java_avg_memory_mb'].mean()], 
                   color='#DC143C', alpha=0.8, width=0.6, edgecolor='black')  
            ax2.text(1, data_10k['java_avg_memory_mb'].mean() + 40,
                    f'{data_10k["java_avg_memory_mb"].mean():.0f}MB', 
                    ha='center', fontweight='bold')
        
        ax2.set_title('Java Memory Usage by Scale', fontweight='bold')
        ax2.set_ylabel('Memory Usage (MB)', fontweight='bold')
        ax2.grid(True, alpha=0.3, axis='y')
    
    # 3. Performance scaling analysis
    if not data_5k.empty and not data_10k.empty:
        time_5k = data_5k['java_avg_time_ms'].mean()
        time_10k = data_10k['java_avg_time_ms'].mean()
        mem_5k = data_5k['java_avg_memory_mb'].mean()
        mem_10k = data_10k['java_avg_memory_mb'].mean()
        
        time_ratio = time_10k / time_5k if time_5k > 0 else 0
        mem_ratio = mem_10k / mem_5k if mem_5k > 0 else 0
        
        # Theoretical linear scaling would be 2x
        bars = ax3.bar(['Time Scaling\n(10K/5K)', 'Memory Scaling\n(10K/5K)', 'Linear\n(theoretical)'], 
                       [time_ratio, mem_ratio, 2.0],
                       color=['#4B0082', '#008B8B', '#808080'], alpha=0.8, edgecolor='black')
        
        # Add value labels on bars
        for bar, value in zip(bars, [time_ratio, mem_ratio, 2.0]):
            height = bar.get_height()
            ax3.text(bar.get_x() + bar.get_width()/2., height + 0.05,
                    f'{value:.2f}x', ha='center', fontweight='bold')
        
        ax3.set_title('Scaling Analysis: 10K vs 5K Patterns', fontweight='bold')
        ax3.set_ylabel('Scaling Factor', fontweight='bold')
        ax3.grid(True, alpha=0.3, axis='y')
        ax3.axhline(y=2.0, color='red', linestyle='--', alpha=0.7, label='Linear scaling')
    
    # 4. rmatch vs Java comparison across scales
    if 'rmatch_avg_time_ms' in scale_data.columns and 'java_avg_time_ms' in scale_data.columns:
        categories = []
        rmatch_times = []
        java_times = []
        
        for scale in ['5K', '10K']:
            subset = scale_data[scale_data['estimated_pattern_count'] == scale]
            if not subset.empty:
                categories.extend([f'{scale}\nrmatch', f'{scale}\nJava'])
                rmatch_times.extend([subset['rmatch_avg_time_ms'].mean(), 0])
                java_times.extend([0, subset['java_avg_time_ms'].mean()])
        
        x_pos = np.arange(len(categories))
        width = 0.35
        
        if rmatch_times:
            bars1 = ax4.bar(x_pos, rmatch_times, width, label='rmatch', color='#2E8B57', alpha=0.8, edgecolor='black')
        if java_times:
            bars2 = ax4.bar(x_pos, java_times, width, label='Java regex', color='#DC143C', alpha=0.8, edgecolor='black')
        
        # Add value labels
        for bars in [bars1, bars2] if 'bars1' in locals() and 'bars2' in locals() else ([bars1] if 'bars1' in locals() else [bars2] if 'bars2' in locals() else []):
            for bar in bars:
                height = bar.get_height()
                if height > 0:
                    ax4.text(bar.get_x() + bar.get_width()/2., height + height*0.01,
                            f'{height:.0f}ms', ha='center', fontweight='bold', fontsize=9)
        
        ax4.set_title('rmatch vs Java: Cross-Scale Comparison', fontweight='bold')
        ax4.set_ylabel('Execution Time (ms)', fontweight='bold')
        ax4.set_xticks(x_pos)
        ax4.set_xticklabels(categories)
        ax4.legend()
        ax4.grid(True, alpha=0.3, axis='y')
    
    plt.tight_layout()
    
    # Save the chart
    chart_path = os.path.join(output_dir, 'large_scale_comparison_5k_10k.png')
    plt.savefig(chart_path, dpi=300, bbox_inches='tight')
    print(f"Large-scale comparison chart (5K vs 10K) saved to: {chart_path}")
    
    plt.close()

def format_time_axis(ax):
    """Format time axis to be more readable for recent data."""
    ax.xaxis.set_major_formatter(mdates.DateFormatter('%Y-%m-%d %H:%M'))
    ax.xaxis.set_major_locator(mdates.AutoDateLocator())
    plt.setp(ax.xaxis.get_majorticklabels(), rotation=45)

def main():
    """Main function to generate large-scale performance charts."""
    print("Starting large-scale performance chart generation...")
    
    # Load performance data
    df = load_large_scale_performance_data()
    
    if df.empty:
        print("No large-scale performance data found. Run performance tests first.")
        return
    
    print(f"Loaded {len(df)} performance records")
    print("Pattern scale distribution:")
    if 'estimated_pattern_count' in df.columns:
        print(df['estimated_pattern_count'].value_counts())
    
    # Create output directory
    output_dir = "charts"
    
    # Generate specialized charts
    print("Generating pure Java performance chart...")
    create_pure_java_performance_chart(df, output_dir)
    
    print("Generating scale comparison chart...")
    create_scale_comparison_chart(df, output_dir)
    
    print("Large-scale performance chart generation completed!")

if __name__ == "__main__":
    main()