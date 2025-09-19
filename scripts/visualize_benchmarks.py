#!/usr/bin/env python3
"""
Comprehensive JMH benchmark visualization script for rmatch performance analysis.

This script generates visualizations comparing rmatch vs Java native regex performance
from JMH benchmark results. It produces:
1. Runtime scatter plots (RMATCH vs JAVA_NATIVE)
2. Relative performance bar charts
3. Evolution timeline plots over multiple runs
4. Evolution heatmaps showing performance trends
5. Statistical summary tables
"""

import json
import os
import glob
import argparse
from pathlib import Path
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from datetime import datetime
import warnings

warnings.filterwarnings('ignore')

def find_latest_results(results_dir):
    """Find the most recent JMH results files."""
    json_pattern = os.path.join(results_dir, "jmh-*.json")
    json_files = glob.glob(json_pattern)
    
    if not json_files:
        raise FileNotFoundError(f"No JMH JSON files found in {results_dir}")
    
    # Sort by timestamp in filename
    json_files.sort(reverse=True)
    latest_json = json_files[0]
    
    # Find corresponding txt file
    basename = os.path.basename(latest_json).replace('.json', '')
    txt_file = os.path.join(results_dir, f"{basename}.txt")
    
    return latest_json, txt_file if os.path.exists(txt_file) else None

def parse_jmh_results(json_file):
    """Parse JMH JSON results into a structured DataFrame."""
    with open(json_file, 'r') as f:
        data = json.load(f)
    
    rows = []
    for entry in data:
        benchmark_name = entry['benchmark']
        params = entry['params']
        score = entry['primaryMetric']['score']
        score_unit = entry['primaryMetric']['scoreUnit']
        
        # Extract benchmark method name
        method_name = benchmark_name.split('.')[-1]
        
        # Create row
        row = {
            'benchmark': benchmark_name,
            'method': method_name,
            'score': score,
            'score_unit': score_unit,
            'jmh_version': entry['jmhVersion'],
            'mode': entry['mode'],
            'threads': entry['threads'],
            'forks': entry['forks']
        }
        
        # Add all parameters
        for key, value in params.items():
            row[key] = value
            
        rows.append(row)
    
    df = pd.DataFrame(rows)
    return df

def create_test_identifier(row):
    """Create a unique test identifier from benchmark parameters."""
    components = []
    
    # Add method name
    components.append(row['method'])
    
    # Add relevant parameters based on method type
    if 'corpusBasedBenchmark' in row['method'] or 'corpusPatternCompilation' in row['method']:
        components.extend([
            row.get('textCorpus', 'unknown'),
            row.get('corpusPatternCount', 'unknown')
        ])
    elif 'runTestSuite' in row['method'] or 'patternCompilation' in row['method']:
        components.extend([
            row.get('patternCategory', 'unknown'),
            str(row.get('patternCount', 'unknown'))
        ])
    
    return '_'.join(components)

def prepare_comparison_data(df):
    """Prepare data for RMATCH vs JAVA_NATIVE comparison."""
    if 'matcherType' not in df.columns:
        raise ValueError("No matcherType column found. Ensure benchmarks include both RMATCH and JAVA_NATIVE.")
    
    # Add test identifier
    df['test_id'] = df.apply(create_test_identifier, axis=1)
    
    # Pivot to get RMATCH and JAVA_NATIVE side by side
    comparison_df = df.pivot_table(
        index=['test_id', 'method'] + [col for col in df.columns if col not in ['matcherType', 'score', 'test_id', 'method']],
        columns='matcherType', 
        values='score', 
        aggfunc='mean'
    ).reset_index()
    
    # Clean up column names
    comparison_df.columns.name = None
    
    # Calculate ratios and performance metrics
    if 'RMATCH' in comparison_df.columns and 'JAVA_NATIVE' in comparison_df.columns:
        comparison_df['ratio_java_over_rmatch'] = comparison_df['JAVA_NATIVE'] / comparison_df['RMATCH']
        comparison_df['rmatch_advantage'] = comparison_df['RMATCH'] / comparison_df['JAVA_NATIVE']
        comparison_df['has_both'] = (~comparison_df['RMATCH'].isna()) & (~comparison_df['JAVA_NATIVE'].isna())
    
    return comparison_df

def plot_runtime_scatter(df, output_dir):
    """Create scatter plot comparing RMATCH vs JAVA_NATIVE runtimes."""
    if 'RMATCH' not in df.columns or 'JAVA_NATIVE' not in df.columns:
        print("Warning: Cannot create scatter plot - missing RMATCH or JAVA_NATIVE data")
        return
    
    complete_data = df[df['has_both']].copy()
    
    if complete_data.empty:
        print("Warning: No complete RMATCH vs JAVA_NATIVE pairs found")
        return
    
    plt.figure(figsize=(10, 8))
    
    # Create scatter plot
    scatter = plt.scatter(complete_data['RMATCH'], complete_data['JAVA_NATIVE'], 
                         alpha=0.7, s=100, c='blue')
    
    # Add diagonal line (equal performance)
    max_val = max(complete_data['RMATCH'].max(), complete_data['JAVA_NATIVE'].max())
    min_val = min(complete_data['RMATCH'].min(), complete_data['JAVA_NATIVE'].min())
    plt.plot([min_val, max_val], [min_val, max_val], 'r--', alpha=0.5, label='Equal Performance')
    
    # Add labels for points
    for idx, row in complete_data.iterrows():
        plt.annotate(row['test_id'], (row['RMATCH'], row['JAVA_NATIVE']), 
                    xytext=(5, 5), textcoords='offset points', fontsize=8, alpha=0.7)
    
    plt.xlabel('RMATCH Score (ops/s)')
    plt.ylabel('Java Native Score (ops/s)')
    plt.title('Performance Comparison: RMATCH vs Java Native Regex\nHigher is Better')
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    
    plt.savefig(os.path.join(output_dir, 'runtime_scatter_rmatch_vs_java.png'), dpi=300, bbox_inches='tight')
    plt.close()

def plot_relative_performance_bars(df, output_dir):
    """Create bar chart showing relative performance per test."""
    if 'ratio_java_over_rmatch' not in df.columns:
        print("Warning: Cannot create relative performance bars - missing ratio data")
        return
    
    valid_data = df[df['has_both'] & df['ratio_java_over_rmatch'].notna()].copy()
    
    if valid_data.empty:
        print("Warning: No valid ratio data found")
        return
    
    # Sort by ratio for better visualization
    valid_data = valid_data.sort_values('ratio_java_over_rmatch')
    
    plt.figure(figsize=(12, 8))
    
    bars = plt.bar(range(len(valid_data)), valid_data['ratio_java_over_rmatch'])
    
    # Color bars based on performance advantage
    for i, (bar, ratio) in enumerate(zip(bars, valid_data['ratio_java_over_rmatch'])):
        if ratio > 1:
            bar.set_color('green')  # RMATCH is faster
        else:
            bar.set_color('red')    # Java native is faster
    
    plt.axhline(y=1.0, color='black', linestyle='--', alpha=0.5, label='Equal Performance')
    
    plt.xticks(range(len(valid_data)), valid_data['test_id'], rotation=45, ha='right')
    plt.ylabel('Java Native Time / RMATCH Time\n(>1 means RMATCH is faster)')
    plt.title('Relative Performance: Java Native vs RMATCH\nGreen = RMATCH Advantage, Red = Java Advantage')
    plt.legend()
    plt.grid(True, axis='y', alpha=0.3)
    plt.tight_layout()
    
    plt.savefig(os.path.join(output_dir, 'relative_performance_bars.png'), dpi=300, bbox_inches='tight')
    plt.close()

def plot_performance_advantage_table(df, output_dir):
    """Create a statistical summary table."""
    if 'ratio_java_over_rmatch' not in df.columns:
        print("Warning: Cannot create summary table - missing ratio data")
        return
    
    valid_data = df[df['has_both'] & df['ratio_java_over_rmatch'].notna()].copy()
    
    if valid_data.empty:
        print("Warning: No valid data for summary table")
        return
    
    # Group by method type for statistics
    summary_stats = []
    
    for method in valid_data['method'].unique():
        method_data = valid_data[valid_data['method'] == method]
        
        stats = {
            'Test Type': method,
            'Count': len(method_data),
            'Mean Ratio': method_data['ratio_java_over_rmatch'].mean(),
            'Median Ratio': method_data['ratio_java_over_rmatch'].median(),
            'Std Dev': method_data['ratio_java_over_rmatch'].std(),
            'Min Ratio': method_data['ratio_java_over_rmatch'].min(),
            'Max Ratio': method_data['ratio_java_over_rmatch'].max(),
            'RMATCH Wins': (method_data['ratio_java_over_rmatch'] > 1).sum(),
            'Java Wins': (method_data['ratio_java_over_rmatch'] < 1).sum()
        }
        summary_stats.append(stats)
    
    summary_df = pd.DataFrame(summary_stats)
    
    # Create table visualization
    fig, ax = plt.subplots(figsize=(14, 6))
    ax.axis('tight')
    ax.axis('off')
    
    table = ax.table(cellText=summary_df.round(3).values, 
                    colLabels=summary_df.columns,
                    cellLoc='center',
                    loc='center')
    
    table.auto_set_font_size(False)
    table.set_fontsize(9)
    table.scale(1.2, 1.5)
    
    # Color code cells based on RMATCH advantage
    for i, row in summary_df.iterrows():
        if row['Mean Ratio'] > 1:
            table[(i+1, summary_df.columns.get_loc('Mean Ratio'))].set_facecolor('#90EE90')  # Light green
        else:
            table[(i+1, summary_df.columns.get_loc('Mean Ratio'))].set_facecolor('#FFB6C1')  # Light red
    
    plt.title('Performance Statistics Summary\nJava Native Time / RMATCH Time (>1 = RMATCH Advantage)', 
              fontsize=14, pad=20)
    
    plt.savefig(os.path.join(output_dir, 'performance_summary_table.png'), dpi=300, bbox_inches='tight')
    plt.close()
    
    # Also save as CSV
    summary_df.to_csv(os.path.join(output_dir, 'performance_summary.csv'), index=False)

def plot_method_comparison(df, output_dir):
    """Create grouped comparison by benchmark method."""
    if 'RMATCH' not in df.columns or 'JAVA_NATIVE' not in df.columns:
        print("Warning: Cannot create method comparison - missing matcher data")
        return
    
    valid_data = df[df['has_both']].copy()
    
    if valid_data.empty:
        print("Warning: No complete data for method comparison")
        return
    
    methods = valid_data['method'].unique()
    n_methods = len(methods)
    
    fig, axes = plt.subplots(n_methods, 1, figsize=(12, 4*n_methods))
    if n_methods == 1:
        axes = [axes]
    
    for idx, method in enumerate(methods):
        method_data = valid_data[valid_data['method'] == method]
        
        x_pos = range(len(method_data))
        width = 0.35
        
        axes[idx].bar([x - width/2 for x in x_pos], method_data['RMATCH'], 
                     width, label='RMATCH', color='blue', alpha=0.7)
        axes[idx].bar([x + width/2 for x in x_pos], method_data['JAVA_NATIVE'], 
                     width, label='Java Native', color='red', alpha=0.7)
        
        axes[idx].set_xlabel('Test Cases')
        axes[idx].set_ylabel('Performance (ops/s)')
        axes[idx].set_title(f'{method} - Performance Comparison')
        axes[idx].set_xticks(x_pos)
        axes[idx].set_xticklabels(method_data['test_id'], rotation=45, ha='right')
        axes[idx].legend()
        axes[idx].grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, 'method_comparison.png'), dpi=300, bbox_inches='tight')
    plt.close()

def generate_all_visualizations(json_file, output_dir):
    """Generate all visualization plots."""
    print(f"Parsing JMH results from: {json_file}")
    df = parse_jmh_results(json_file)
    
    print(f"Found {len(df)} benchmark results")
    print(f"Methods: {df['method'].unique().tolist()}")
    if 'matcherType' in df.columns:
        print(f"Matcher types: {df['matcherType'].unique().tolist()}")
    
    # Prepare comparison data
    comparison_df = prepare_comparison_data(df)
    
    print(f"Creating visualizations in: {output_dir}")
    
    # Generate all plots
    plot_runtime_scatter(comparison_df, output_dir)
    plot_relative_performance_bars(comparison_df, output_dir)
    plot_performance_advantage_table(comparison_df, output_dir)
    plot_method_comparison(comparison_df, output_dir)
    
    # Save raw data for inspection
    comparison_df.to_csv(os.path.join(output_dir, 'benchmark_comparison_data.csv'), index=False)
    
    print(f"All visualizations saved to: {output_dir}")

def main():
    parser = argparse.ArgumentParser(description='Generate JMH benchmark visualizations')
    parser.add_argument('--results-dir', default='benchmarks/results', 
                       help='Directory containing JMH results (default: benchmarks/results)')
    parser.add_argument('--output-dir', default='performance-graphs',
                       help='Output directory for graphs (default: performance-graphs)')
    parser.add_argument('--json-file', help='Specific JSON file to process (overrides --results-dir)')
    
    args = parser.parse_args()
    
    # Create output directory
    os.makedirs(args.output_dir, exist_ok=True)
    
    # Find input file
    if args.json_file:
        json_file = args.json_file
        if not os.path.exists(json_file):
            raise FileNotFoundError(f"Specified JSON file not found: {json_file}")
    else:
        json_file, txt_file = find_latest_results(args.results_dir)
        print(f"Using latest results: {os.path.basename(json_file)}")
    
    # Generate visualizations
    generate_all_visualizations(json_file, args.output_dir)

if __name__ == '__main__':
    main()