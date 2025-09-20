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
import re
from pathlib import Path
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from datetime import datetime
import warnings

warnings.filterwarnings('ignore')

def find_latest_run_results(results_dir):
    """Find all results files from the most recent test run."""
    json_pattern = os.path.join(results_dir, "jmh-*.json")
    json_files = glob.glob(json_pattern)
    
    if not json_files:
        raise FileNotFoundError(f"No JMH JSON files found in {results_dir}")
    
    # Group files by run ID
    run_groups = {}
    for json_file in json_files:
        basename = os.path.basename(json_file)
        # New format: jmh-{RUN_ID}-{timestamp}.json
        # Old format: jmh-{timestamp}.json
        if basename.count('-') >= 3:  # New format with run ID
            # Handle format: jmh-{SUITE_RUN_ID}-{INDIVIDUAL_TIMESTAMP}.json
            # Both SUITE_RUN_ID and INDIVIDUAL_TIMESTAMP can contain timestamps
            # The individual timestamp is always the last timestamp (YYYYMMDDTHHMMSSZ)
            # We want to extract just the SUITE_RUN_ID part
            
            # Find all timestamp patterns in the filename
            timestamps = re.findall(r'\d{8}T\d{6}Z', basename)
            if len(timestamps) >= 1:
                # The last timestamp is the individual run timestamp, remove it
                last_timestamp = timestamps[-1]
                # Find the last occurrence and split there
                last_timestamp_pos = basename.rfind(last_timestamp)
                if last_timestamp_pos > 0:
                    # Extract everything between 'jmh-' and the final '-{timestamp}'
                    prefix = basename[:last_timestamp_pos-1]  # Remove the dash before timestamp
                    run_id = prefix[4:]  # Remove 'jmh-' prefix
                else:
                    # Fallback to old logic
                    parts = basename.split('-')
                    run_id = '-'.join(parts[1:-1])
            else:
                # Fallback to old logic if no timestamps found
                parts = basename.split('-')
                run_id = '-'.join(parts[1:-1])
        else:  # Old format - use timestamp as run ID
            run_id = basename.replace('jmh-', '').replace('.json', '')
        
        if run_id not in run_groups:
            run_groups[run_id] = []
        run_groups[run_id].append(json_file)
    
    # Debug: Print all run groups
    print(f"Found {len(run_groups)} run groups:")
    for run_id, files in run_groups.items():
        print(f"  {run_id}: {len(files)} files")
    
    # Find the most recent run ID
    latest_run_id = max(run_groups.keys())
    latest_files = run_groups[latest_run_id]
    print(f"Selected latest run: {latest_run_id} with {len(latest_files)} files")
    
    # Sort files within the run by timestamp
    latest_files.sort(reverse=True)
    
    # Find corresponding txt files
    result_files = []
    for json_file in latest_files:
        basename = os.path.basename(json_file).replace('.json', '')
        txt_file = os.path.join(results_dir, f"{basename}.txt")
        result_files.append((json_file, txt_file if os.path.exists(txt_file) else None))
    
    return latest_run_id, result_files

def find_latest_results(results_dir):
    """Find the most recent JMH results files - backward compatibility wrapper."""
    run_id, result_files = find_latest_run_results(results_dir)
    if result_files:
        return result_files[0]  # Return first (most recent) file pair
    else:
        raise FileNotFoundError(f"No JMH JSON files found in {results_dir}")

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

def parse_jmh_run_results(result_files):
    """Parse all JMH results from a single test run into a combined DataFrame."""
    all_dataframes = []
    
    for json_file, txt_file in result_files:
        print(f"Parsing {os.path.basename(json_file)}...")
        df = parse_jmh_results(json_file)
        
        # Add source file information
        df['source_file'] = os.path.basename(json_file)
        
        all_dataframes.append(df)
    
    if not all_dataframes:
        raise ValueError("No valid JMH result files found")
    
    # Combine all results
    combined_df = pd.concat(all_dataframes, ignore_index=True)
    
    print(f"Combined {len(all_dataframes)} result files with {len(combined_df)} total benchmarks")
    return combined_df

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
    
    # Filter for tests that have matcherType (some tests don't have this parameter)
    comparison_df = df.dropna(subset=['matcherType']).copy()
    
    if comparison_df.empty:
        raise ValueError("No tests found with matcherType parameter. Cannot create comparison plots.")
    
    print(f"Filtered to {len(comparison_df)} benchmarks with matcherType")
    print(f"Available matcherTypes: {comparison_df['matcherType'].unique()}")
    
    # Add test identifier (excluding matcherType so identical tests get grouped together)
    comparison_df['test_id'] = comparison_df.apply(create_test_identifier, axis=1)
    
    # Define key columns for grouping (exclude matcherType and variable parameters)
    grouping_cols = ['test_id', 'method', 'benchmark', 'score_unit']
    
    # Add stable parameter columns that should be the same for comparison
    param_cols = ['corpusPatternCount', 'patternCategory', 'patternCount', 'textCorpus']
    for col in param_cols:
        if col in comparison_df.columns:
            grouping_cols.append(col)
    
    # Pivot to get RMATCH and JAVA_NATIVE side by side
    pivoted_df = comparison_df.pivot_table(
        index=grouping_cols,
        columns='matcherType', 
        values='score', 
        aggfunc='mean'
    ).reset_index()
    
    # Clean up column names
    pivoted_df.columns.name = None
    
    # Calculate ratios and performance metrics
    if 'RMATCH' in pivoted_df.columns and 'JAVA_NATIVE' in pivoted_df.columns:
        pivoted_df['ratio_java_over_rmatch'] = pivoted_df['JAVA_NATIVE'] / pivoted_df['RMATCH']
        pivoted_df['rmatch_advantage'] = pivoted_df['RMATCH'] / pivoted_df['JAVA_NATIVE']
        pivoted_df['has_both'] = (~pivoted_df['RMATCH'].isna()) & (~pivoted_df['JAVA_NATIVE'].isna())
    
    return pivoted_df

def plot_runtime_scatter(df, output_dir):
    """Create 2D box and whisker plot comparing RMATCH vs JAVA_NATIVE runtimes."""
    if 'RMATCH' not in df.columns or 'JAVA_NATIVE' not in df.columns:
        print("Warning: Cannot create runtime plot - missing RMATCH or JAVA_NATIVE data")
        return
    
    complete_data = df[df['has_both']].copy()
    
    if complete_data.empty:
        print("Warning: No complete RMATCH vs JAVA_NATIVE pairs found")
        return
    
    # Group data by test categories for 2D box plots
    test_categories = complete_data['test_id'].unique()
    
    if len(test_categories) == 0:
        print("Warning: No test categories found")
        return
    
    # Create figure with extra space for wide legend
    fig, ax = plt.subplots(figsize=(20, 10))
    
    # Calculate range for minimum box sizing
    max_val = max(complete_data['RMATCH'].max(), complete_data['JAVA_NATIVE'].max())
    min_val = min(complete_data['RMATCH'].min(), complete_data['JAVA_NATIVE'].min())
    
    # Generate distinct colors for each category (cycle through color maps if needed)
    if len(test_categories) <= 10:
        colors = plt.cm.tab10(np.linspace(0, 1, len(test_categories)))
    elif len(test_categories) <= 20:
        colors = list(plt.cm.tab10(np.linspace(0, 1, 10))) + list(plt.cm.tab20(np.linspace(0, 1, 20)))[10:]
        colors = colors[:len(test_categories)]
    else:
        # For more than 20 categories, use multiple color maps
        colors = []
        color_maps = [plt.cm.tab10, plt.cm.tab20, plt.cm.Set1, plt.cm.Set2, plt.cm.Set3]
        for i in range(len(test_categories)):
            cmap = color_maps[i // 20 % len(color_maps)]
            colors.append(cmap((i % 20) / 20))
    
    legend_elements = []
    plot_handles = []  # Store actual plot elements for legend
    
    for i, test_id in enumerate(test_categories):
        test_data = complete_data[complete_data['test_id'] == test_id]
        if len(test_data) == 0:
            continue
            
        java_values = test_data['JAVA_NATIVE'].values
        rmatch_values = test_data['RMATCH'].values
        
        # Calculate statistics for both dimensions
        java_q1, java_median, java_q3 = np.percentile(java_values, [25, 50, 75])
        rmatch_q1, rmatch_median, rmatch_q3 = np.percentile(rmatch_values, [25, 50, 75])
        
        java_iqr = java_q3 - java_q1
        rmatch_iqr = rmatch_q3 - rmatch_q1
        
        # Calculate whisker extents (1.5 * IQR)
        java_lower = max(java_values.min(), java_q1 - 1.5 * java_iqr)
        java_upper = min(java_values.max(), java_q3 + 1.5 * java_iqr)
        rmatch_lower = max(rmatch_values.min(), rmatch_q1 - 1.5 * rmatch_iqr)
        rmatch_upper = min(rmatch_values.max(), rmatch_q3 + 1.5 * rmatch_iqr)
        
        color = colors[i % len(colors)]
        
        # Draw the main rectangle (Q1 to Q3 in both dimensions) with minimum size
        rect_width = max(java_q3 - java_q1, (max_val - min_val) * 0.02)  # Minimum 2% of range
        rect_height = max(rmatch_q3 - rmatch_q1, (max_val - min_val) * 0.02)  # Minimum 2% of range
        
        # Center the rectangle if we're using minimum size
        if java_q3 - java_q1 < (max_val - min_val) * 0.02:
            rect_x = java_median - rect_width/2
        else:
            rect_x = java_q1
            
        if rmatch_q3 - rmatch_q1 < (max_val - min_val) * 0.02:
            rect_y = rmatch_median - rect_height/2
        else:
            rect_y = rmatch_q1
            
        rect = plt.Rectangle((rect_x, rect_y), rect_width, rect_height, 
                           facecolor=color, alpha=0.4, edgecolor=color, linewidth=2)
        ax.add_patch(rect)
        
        # Draw median point as a numbered circle
        scatter_handle = ax.scatter([java_median], [rmatch_median], c=[color], marker='o', 
                                  s=300, edgecolors='black', linewidth=2, zorder=10, alpha=0.8)
        
        # Add the number on top of the circle
        test_number = i + 1
        ax.text(java_median, rmatch_median, str(test_number), ha='center', va='center',
                fontsize=10, fontweight='bold', color='white', zorder=11)
        
        plot_handles.append(scatter_handle)
        
        # Draw whiskers with same color
        # Horizontal whiskers (Java dimension)
        ax.plot([java_lower, java_q1], [rmatch_median, rmatch_median], color=color, linewidth=2)  # Left whisker
        ax.plot([java_q3, java_upper], [rmatch_median, rmatch_median], color=color, linewidth=2)  # Right whisker
        ax.plot([java_lower, java_lower], [rmatch_median-rmatch_iqr*0.05, rmatch_median+rmatch_iqr*0.05], color=color, linewidth=2)  # Left cap
        ax.plot([java_upper, java_upper], [rmatch_median-rmatch_iqr*0.05, rmatch_median+rmatch_iqr*0.05], color=color, linewidth=2)  # Right cap
        
        # Vertical whiskers (RMATCH dimension)
        ax.plot([java_median, java_median], [rmatch_lower, rmatch_q1], color=color, linewidth=2)  # Bottom whisker
        ax.plot([java_median, java_median], [rmatch_q3, rmatch_upper], color=color, linewidth=2)  # Top whisker
        ax.plot([java_median-java_iqr*0.05, java_median+java_iqr*0.05], [rmatch_lower, rmatch_lower], color=color, linewidth=2)  # Bottom cap
        ax.plot([java_median-java_iqr*0.05, java_median+java_iqr*0.05], [rmatch_upper, rmatch_upper], color=color, linewidth=2)  # Top cap
        
        # Plot outliers with matching color
        java_outliers = java_values[(java_values < java_lower) | (java_values > java_upper)]
        rmatch_outliers_for_java = rmatch_values[(java_values < java_lower) | (java_values > java_upper)]
        if len(java_outliers) > 0:
            ax.scatter(java_outliers, rmatch_outliers_for_java, c=[color]*len(java_outliers), 
                      marker='x', s=30, alpha=0.8, linewidth=2)
        
        rmatch_outliers = rmatch_values[(rmatch_values < rmatch_lower) | (rmatch_values > rmatch_upper)]
        java_outliers_for_rmatch = java_values[(rmatch_values < rmatch_lower) | (rmatch_values > rmatch_upper)]
        if len(rmatch_outliers) > 0:
            ax.scatter(java_outliers_for_rmatch, rmatch_outliers, c=[color]*len(rmatch_outliers), 
                      marker='x', s=30, alpha=0.8, linewidth=2)
        
        # Create readable label for this test category with number prefix
        readable_label = test_id.replace('corpusBasedBenchmark_', 'Corpus_').replace('patternCompilationBenchmark_', 'PatternComp_')
        readable_label = readable_label.replace('runTestSuite_', 'TestSuite_').replace('functionalVerification_', 'FuncVerify_')
        readable_label = readable_label.replace('buildMatcher_', 'BuildMatch_').replace('matchOnce_', 'MatchOnce_')
        readable_label = readable_label.replace('compressedState', 'CompState').replace('createMultiple', 'CreateMult')
        # Don't truncate - let the legend box expand to fit
        
        # Set label with number prefix for legend
        numbered_label = f"{test_number}. {readable_label}"
        scatter_handle.set_label(numbered_label)
    
    # Add diagonal line (equal performance)
    ax.plot([min_val, max_val], [min_val, max_val], 'r--', alpha=0.7, linewidth=3, label='Equal Performance')
    
    # Debug: Print legend information
    print(f"Number of test categories processed: {len(plot_handles)}")
    print(f"Plot handles created: {len(plot_handles)}")
    
    # Create a single comprehensive legend with all elements
    try:
        if plot_handles and len(plot_handles) > 0:
            print(f"Creating comprehensive legend with {len(plot_handles)} test categories + equal performance line")
            
            # Create equal performance line element
            from matplotlib.lines import Line2D
            equal_perf_element = Line2D([0], [0], color='red', linestyle='--', linewidth=3, label='Equal Performance Line')
            
            # Combine all legend elements
            all_handles = plot_handles + [equal_perf_element]
            
            # Create single comprehensive legend with better spacing and readability
            comprehensive_legend = ax.legend(handles=all_handles, loc='center left', bbox_to_anchor=(1.01, 0.5),
                                           fontsize=7, title='Test Categories & Reference', title_fontsize=9,
                                           frameon=True, framealpha=0.95, facecolor='white', 
                                           edgecolor='gray', ncol=1,
                                           columnspacing=0.5, handletextpad=0.3, borderpad=0.5,
                                           handlelength=1.5, handleheight=1.5, markerscale=0.6)
            
            print("Comprehensive legend created successfully")
            print(f"Legend contains {len(all_handles)} total elements")
            
        else:
            print("No plot handles available for legend")
            
    except Exception as e:
        print(f"Comprehensive legend creation failed: {e}")
        import traceback
        traceback.print_exc()
    
    # Generate timestamp for the plot
    from datetime import datetime
    plot_timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    ax.set_xlabel('Java Native Score (ops/s)', fontsize=14, fontweight='bold')
    ax.set_ylabel('RMATCH Score (ops/s)', fontsize=14, fontweight='bold')
    ax.set_title('2D Performance Comparison: RMATCH vs Java Native Regex\n' +
                '(Rectangles = Q1-Q3 range, Markers = Median, Whiskers = 1.5×IQR, X = Outliers)\n' +
                f'Generated: {plot_timestamp}', 
                fontsize=16, fontweight='bold', pad=20)
    
    ax.grid(True, alpha=0.3)
    
    # Add text explanation in top-left, with corrected interpretation
    ax.text(0.02, 0.98, 'Points below diagonal: Java Native faster\nPoints above diagonal: RMATCH faster', 
           transform=ax.transAxes, fontsize=11, verticalalignment='top',
           bbox=dict(boxstyle='round,pad=0.5', facecolor='lightcyan', alpha=0.9))
    
    # Adjust layout to accommodate wide external legend
    plt.subplots_adjust(right=0.65)  # Leave more space for legend (35% of figure width)
    
    # Save with bbox_inches='tight' to ensure legend is fully included
    plt.savefig(os.path.join(output_dir, 'runtime_scatter_rmatch_vs_java.png'), dpi=300, 
                bbox_inches='tight', facecolor='white', edgecolor='none', pad_inches=0.2)
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
    """Generate all visualization plots from a single file."""
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

def generate_run_visualizations(result_files, run_id, output_dir):
    """Generate all visualization plots from a complete test run."""
    print(f"Parsing JMH results from test run: {run_id}")
    df = parse_jmh_run_results(result_files)
    
    print(f"Found {len(df)} benchmark results across {len(result_files)} files")
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
    
    # Create a run summary file
    run_summary = {
        'run_id': run_id,
        'timestamp': datetime.now().isoformat(),
        'total_benchmarks': len(df),
        'result_files': [os.path.basename(f[0]) for f in result_files],
        'methods': df['method'].unique().tolist(),
        'matcher_types': df['matcherType'].unique().tolist() if 'matcherType' in df.columns else []
    }
    
    import json
    with open(os.path.join(output_dir, 'run_summary.json'), 'w') as f:
        json.dump(run_summary, f, indent=2)
    
    print(f"All visualizations saved to: {output_dir}")
    print(f"Test run summary: {run_id} ({len(result_files)} files, {len(df)} benchmarks)")

def main():
    parser = argparse.ArgumentParser(description='Generate JMH benchmark visualizations')
    parser.add_argument('--results-dir', default='benchmarks/results', 
                       help='Directory containing JMH results (default: benchmarks/results)')
    parser.add_argument('--output-dir', default='performance-graphs',
                       help='Output directory for graphs (default: performance-graphs)')
    parser.add_argument('--json-file', help='Specific JSON file to process (overrides --results-dir)')
    parser.add_argument('--run-id', help='Specific run ID to process (processes all files from that run)')
    
    args = parser.parse_args()
    
    # Create output directory
    os.makedirs(args.output_dir, exist_ok=True)
    
    # Determine processing mode
    if args.json_file:
        # Single file mode (backward compatibility)
        json_file = args.json_file
        if not os.path.exists(json_file):
            raise FileNotFoundError(f"Specified JSON file not found: {json_file}")
        print(f"Processing single file: {os.path.basename(json_file)}")
        generate_all_visualizations(json_file, args.output_dir)
        
    elif args.run_id:
        # Specific run mode
        json_pattern = os.path.join(args.results_dir, f"jmh-{args.run_id}-*.json")
        json_files = glob.glob(json_pattern)
        if not json_files:
            raise FileNotFoundError(f"No JMH files found for run ID: {args.run_id}")
        
        # Create result file pairs
        result_files = []
        for json_file in sorted(json_files):
            basename = os.path.basename(json_file).replace('.json', '')
            txt_file = os.path.join(args.results_dir, f"{basename}.txt")
            result_files.append((json_file, txt_file if os.path.exists(txt_file) else None))
        
        print(f"Processing run {args.run_id} with {len(result_files)} files")
        generate_run_visualizations(result_files, args.run_id, args.output_dir)
        
    else:
        # Latest run mode (new default behavior)
        try:
            run_id, result_files = find_latest_run_results(args.results_dir)
            print(f"Processing latest run: {run_id} ({len(result_files)} files)")
            generate_run_visualizations(result_files, run_id, args.output_dir)
        except FileNotFoundError:
            # Fallback to single file mode for backward compatibility
            json_file, txt_file = find_latest_results(args.results_dir)
            print(f"Using latest single result: {os.path.basename(json_file)}")
            generate_all_visualizations(json_file, args.output_dir)

if __name__ == '__main__':
    main()