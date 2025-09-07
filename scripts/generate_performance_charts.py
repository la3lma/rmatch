#!/usr/bin/env python3
"""
Generate performance evolution charts from historical CSV data and recent JSON performance reports.

This script reads existing performance logs from CSV files and generates charts
showing the evolution of performance metrics over time for both rmatch and Java regex.
It also processes newer JSON performance check reports from the benchmarks/results directory.

The generated charts are saved as PNG files that can be included in documentation.
"""

import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import seaborn as sns
import numpy as np
import os
import sys
import json
from datetime import datetime
from pathlib import Path

# Set up matplotlib for better-looking plots
plt.style.use('default')
sns.set_palette("husl")

def load_performance_data():
    """Load performance data from CSV files in the logs directory and JSON files in benchmarks/results."""
    dataframes = []
    
    # Load legacy CSV data
    logs_dir = Path("rmatch-tester/logs")
    if logs_dir.exists():
        csv_files = list(logs_dir.glob("*.csv"))
        for csv_file in csv_files:
            try:
                # Try to load the CSV file
                if csv_file.name == "large-corpus-log.csv":
                    # This file has a comprehensive format with comparison data
                    df = pd.read_csv(csv_file)
                    # Convert timestamp to datetime
                    if 'timestamp' in df.columns:
                        df['datetime'] = pd.to_datetime(df['timestamp'], unit='ms')
                        df['source_file'] = csv_file.name
                        df['data_type'] = 'comprehensive_csv'
                        dataframes.append(df)
                        print(f"Loaded {len(df)} records from {csv_file}")
                else:
                    # Check if it's a simple CSV file with time-series data
                    df = pd.read_csv(csv_file)
                    if len(df.columns) >= 3:
                        # Assume format: timestamp, duration, memory
                        df.columns = ['timestamp', 'duration', 'memory'] + list(df.columns[3:])
                        df['datetime'] = pd.to_datetime(df['timestamp'], unit='s', errors='coerce')
                        if not df['datetime'].isna().all():
                            # Add metadata from filename if possible
                            df['source_file'] = csv_file.name
                            df['data_type'] = 'simple_csv'
                            dataframes.append(df)
                            print(f"Loaded {len(df)} records from {csv_file}")
            except Exception as e:
                print(f"Error loading {csv_file}: {e}")
                continue
    
    # Load new JSON data from benchmarks/results
    results_dir = Path("benchmarks/results")
    if results_dir.exists():
        json_files = list(results_dir.glob("performance-check-*.json"))
        for json_file in json_files:
            try:
                with open(json_file, 'r') as f:
                    import json
                    data = json.load(f)
                    
                # Extract timestamp and performance data
                timestamp = data.get('timestamp')
                if timestamp:
                    # Create a row for this performance check
                    row_data = {
                        'datetime': pd.to_datetime(timestamp),
                        'source_file': json_file.name,
                        'data_type': 'performance_check_json'
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
                    if 'current_results' in data:
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
                    
                    # Create a DataFrame with this single row
                    df = pd.DataFrame([row_data])
                    dataframes.append(df)
                    print(f"Loaded performance check data from {json_file}")
                    
            except Exception as e:
                print(f"Error loading {json_file}: {e}")
                continue
    
    if not dataframes:
        print("No valid data found in CSV or JSON files")
        return []
    
    # Return the dataframes separately - don't combine them
    return dataframes

def create_performance_evolution_chart(df, output_dir="charts"):
    """Create a performance evolution chart showing metrics over time."""
    if df.empty:
        print("No data available for plotting")
        return
    
    # Ensure output directory exists
    os.makedirs(output_dir, exist_ok=True)
    
    # Check if we have comprehensive comparison data (with both rmatch and java metrics)
    comprehensive_df = df[df['source_file'] == 'large-corpus-log.csv'] if 'source_file' in df.columns else pd.DataFrame()
    
    if not comprehensive_df.empty and 'durationInMillis1' in comprehensive_df.columns:
        # This is comprehensive comparison data - prioritize this
        print(f"Creating comprehensive performance chart from {len(comprehensive_df)} records")
        create_comprehensive_performance_chart(comprehensive_df, output_dir)
    else:
        # This is simple time-series data
        print(f"Creating simple performance chart from {len(df)} records")  
        create_simple_performance_chart(df, output_dir)

def create_performance_check_chart(df, output_dir):
    """Create charts from performance check JSON data."""
    if 'datetime' not in df.columns:
        print("No datetime column found in performance check data")
        return
    
    # Remove rows with invalid dates
    df = df.dropna(subset=['datetime'])
    if df.empty:
        print("No valid datetime data found in performance check data")
        return
    
    # Sort by date
    df = df.sort_values('datetime')
    
    # Create figure with subplots
    fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(16, 12))
    fig.suptitle('Performance Check Results Evolution', fontsize=16, fontweight='bold')
    
    # 1. Execution time comparison
    if 'rmatch_avg_time_ms' in df.columns and 'java_avg_time_ms' in df.columns:
        valid_data = df.dropna(subset=['rmatch_avg_time_ms', 'java_avg_time_ms'])
        if not valid_data.empty:
            ax1.plot(valid_data['datetime'], valid_data['rmatch_avg_time_ms'], 'o-', 
                    label='rmatch', linewidth=2, markersize=6, color='#2E8B57')
            ax1.plot(valid_data['datetime'], valid_data['java_avg_time_ms'], 's--', 
                    label='Java regex', linewidth=2, markersize=6, color='#FF6B35')
            ax1.set_title('Average Execution Time Comparison')
            ax1.set_ylabel('Time (milliseconds)')
            ax1.legend()
            ax1.grid(True, alpha=0.3)
            format_time_axis(ax1)
    
    # 2. Memory usage comparison
    if 'rmatch_avg_memory_mb' in df.columns and 'java_avg_memory_mb' in df.columns:
        valid_data = df.dropna(subset=['rmatch_avg_memory_mb', 'java_avg_memory_mb'])
        if not valid_data.empty:
            ax2.plot(valid_data['datetime'], valid_data['rmatch_avg_memory_mb'], 'o-', 
                    label='rmatch', linewidth=2, markersize=6, color='#2E8B57')
            ax2.plot(valid_data['datetime'], valid_data['java_avg_memory_mb'], 's--', 
                    label='Java regex', linewidth=2, markersize=6, color='#FF6B35')
            ax2.set_title('Average Memory Usage Comparison')
            ax2.set_ylabel('Memory (MB)')
            ax2.legend()
            ax2.grid(True, alpha=0.3)
            format_time_axis(ax2)
    
    # 3. Performance improvement percentages
    if 'time_improvement_percent' in df.columns and 'memory_improvement_percent' in df.columns:
        valid_data = df.dropna(subset=['time_improvement_percent', 'memory_improvement_percent'])
        if not valid_data.empty:
            # Convert to percentages
            time_pct = valid_data['time_improvement_percent'] * 100
            memory_pct = valid_data['memory_improvement_percent'] * 100
            
            ax3.plot(valid_data['datetime'], time_pct, 'o-', 
                    label='Time improvement', linewidth=2, markersize=6, color='blue')
            ax3.plot(valid_data['datetime'], memory_pct, 's-', 
                    label='Memory improvement', linewidth=2, markersize=6, color='green')
            ax3.axhline(y=0, color='gray', linestyle='--', alpha=0.7)
            ax3.set_title('Performance Improvement Over Time')
            ax3.set_ylabel('Improvement (%)')
            ax3.legend()
            ax3.grid(True, alpha=0.3)
            format_time_axis(ax3)
    
    # 4. Test status over time
    if 'status' in df.columns:
        valid_data = df.dropna(subset=['status'])
        if not valid_data.empty:
            # Map status to numeric values for plotting
            status_map = {'PASS': 1, 'WARNING': 0, 'FAIL': -1}
            status_numeric = valid_data['status'].map(status_map)
            
            # Create scatter plot with colors
            colors = ['red' if s == 'FAIL' else 'orange' if s == 'WARNING' else 'green' 
                     for s in valid_data['status']]
            ax4.scatter(valid_data['datetime'], status_numeric, c=colors, s=100, alpha=0.7)
            ax4.set_title('Performance Test Status')
            ax4.set_ylabel('Status')
            ax4.set_yticks([-1, 0, 1])
            ax4.set_yticklabels(['FAIL', 'WARNING', 'PASS'])
            ax4.grid(True, alpha=0.3)
            format_time_axis(ax4)
    
    plt.tight_layout()
    
    # Save the chart
    chart_path = os.path.join(output_dir, 'performance_check_evolution.png')
    plt.savefig(chart_path, dpi=300, bbox_inches='tight')
    print(f"Performance check evolution chart saved to: {chart_path}")
    
    plt.close()

def create_comparison_overview_chart(df, output_dir):
    """Create a simplified overview chart for README inclusion showing recent performance checks."""
    if 'datetime' not in df.columns:
        print("No datetime column found in performance check data")
        return
    
    # Remove rows with invalid dates and get recent data (last 30 days)
    df = df.dropna(subset=['datetime'])
    if df.empty:
        print("No valid datetime data found in performance check data")
        return
    
    # Sort by date and take recent data
    df = df.sort_values('datetime')
    recent_cutoff = df['datetime'].max() - pd.Timedelta(days=30)
    recent_df = df[df['datetime'] >= recent_cutoff]
    
    if recent_df.empty:
        recent_df = df.tail(10)  # At least show last 10 data points
    
    fig, ax = plt.subplots(1, 1, figsize=(12, 6))
    
    # Calculate performance ratio if possible
    if 'rmatch_avg_time_ms' in recent_df.columns and 'java_avg_time_ms' in recent_df.columns:
        valid_data = recent_df.dropna(subset=['rmatch_avg_time_ms', 'java_avg_time_ms'])
        if not valid_data.empty:
            # Calculate ratio (rmatch / java)
            performance_ratio = valid_data['rmatch_avg_time_ms'] / valid_data['java_avg_time_ms']
            
            # Color points by status
            colors = []
            for status in valid_data['status']:
                if status == 'PASS':
                    colors.append('#2E8B57')  # Green
                elif status == 'WARNING':
                    colors.append('#FFA500')  # Orange  
                else:
                    colors.append('#FF4444')  # Red
            
            ax.scatter(valid_data['datetime'], performance_ratio, 
                      c=colors, s=80, alpha=0.8, edgecolors='black', linewidth=1)
            
            # Add trend line if we have enough points
            if len(valid_data) > 2:
                z = np.polyfit(mdates.date2num(valid_data['datetime']), performance_ratio, 1)
                p = np.poly1d(z)
                ax.plot(valid_data['datetime'], p(mdates.date2num(valid_data['datetime'])), 
                       "r--", alpha=0.7, linewidth=2, label='Trend')
            
            ax.axhline(y=1.0, color='gray', linestyle='--', alpha=0.7, label='Parity (1.0x)')
            ax.set_title('Recent Performance Test Results (rmatch vs Java regex)', fontsize=14, fontweight='bold')
            ax.set_ylabel('Performance Ratio (lower is better)', fontsize=12)
            ax.legend(['Trend', 'Parity', 'PASS', 'WARNING', 'FAIL'], fontsize=11)
            ax.grid(True, alpha=0.3)
            format_time_axis(ax)
            
            # Add some styling
            ax.spines['top'].set_visible(False)
            ax.spines['right'].set_visible(False)
            
            plt.tight_layout()
            
            # Save for README
            overview_chart_path = os.path.join(output_dir, 'performance_check_overview.png')
            plt.savefig(overview_chart_path, dpi=200, bbox_inches='tight')
            print(f"Performance check overview chart saved to: {overview_chart_path}")
            
            plt.close()

def create_comprehensive_performance_chart(df, output_dir):
    """Create charts from comprehensive performance comparison data."""
    if 'datetime' not in df.columns:
        print("No datetime column found")
        return
    
    # Remove rows with invalid dates
    df = df.dropna(subset=['datetime'])
    if df.empty:
        print("No valid datetime data found")
        return
    
    # Sort by date
    df = df.sort_values('datetime')
    
    # Create figure with subplots
    fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(16, 12))
    fig.suptitle('rmatch Performance Evolution', fontsize=16, fontweight='bold')
    
    # 1. Duration comparison over time
    if 'durationInMillis1' in df.columns and 'durationInMillis2' in df.columns:
        ax1.plot(df['datetime'], df['durationInMillis1'], 'o-', label='rmatch', linewidth=2, markersize=4)
        ax1.plot(df['datetime'], df['durationInMillis2'], 's--', label='Java regex', linewidth=2, markersize=4)
        ax1.set_title('Execution Time Evolution')
        ax1.set_ylabel('Duration (milliseconds)')
        ax1.legend()
        ax1.grid(True, alpha=0.3)
        format_time_axis(ax1)
    
    # 2. Memory usage comparison over time
    if 'usedMemoryInMb1' in df.columns and 'usedMemoryInMb2' in df.columns:
        ax2.plot(df['datetime'], df['usedMemoryInMb1'], 'o-', label='rmatch', linewidth=2, markersize=4)
        ax2.plot(df['datetime'], df['usedMemoryInMb2'], 's--', label='Java regex', linewidth=2, markersize=4)
        ax2.set_title('Memory Usage Evolution')
        ax2.set_ylabel('Memory (MB)')
        ax2.legend()
        ax2.grid(True, alpha=0.3)
        format_time_axis(ax2)
    
    # 3. Performance ratio over time
    if 'durationInMillis1' in df.columns and 'durationInMillis2' in df.columns:
        # Calculate performance ratio (rmatch / java)
        df_ratio = df[df['durationInMillis2'] > 0].copy()
        df_ratio['performance_ratio'] = df_ratio['durationInMillis1'] / df_ratio['durationInMillis2']
        
        ax3.plot(df_ratio['datetime'], df_ratio['performance_ratio'], 'o-', 
                color='red', linewidth=2, markersize=4)
        ax3.axhline(y=1.0, color='gray', linestyle='--', alpha=0.7, label='Parity (1.0x)')
        ax3.set_title('Performance Ratio (rmatch/java)')
        ax3.set_ylabel('Ratio')
        ax3.legend()
        ax3.grid(True, alpha=0.3)
        format_time_axis(ax3)
        
        # Add trend line
        if len(df_ratio) > 2:
            z = np.polyfit(mdates.date2num(df_ratio['datetime']), df_ratio['performance_ratio'], 1)
            p = np.poly1d(z)
            ax3.plot(df_ratio['datetime'], p(mdates.date2num(df_ratio['datetime'])), 
                    "r--", alpha=0.5, linewidth=1, label='Trend')
            ax3.legend()
    
    # 4. Data points over time (showing test scale)
    if 'noOfRegexps' in df.columns:
        ax4.scatter(df['datetime'], df['noOfRegexps'], alpha=0.6, s=50)
        ax4.set_title('Test Scale Evolution')
        ax4.set_ylabel('Number of Regexps')
        ax4.grid(True, alpha=0.3)
        format_time_axis(ax4)
    
    plt.tight_layout()
    
    # Save the chart
    chart_path = os.path.join(output_dir, 'performance_evolution.png')
    plt.savefig(chart_path, dpi=300, bbox_inches='tight')
    print(f"Performance evolution chart saved to: {chart_path}")
    
    # Also create a simplified version for README
    create_readme_chart(df, output_dir)
    
    plt.close()

def create_simple_performance_chart(df, output_dir):
    """Create charts from simple time-series performance data."""
    if 'datetime' not in df.columns:
        print("No datetime column found")
        return
    
    # Remove rows with invalid dates
    df = df.dropna(subset=['datetime'])
    if df.empty:
        print("No valid datetime data found")
        return
    
    # Sort by date
    df = df.sort_values('datetime')
    
    # Create figure with subplots
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 8))
    fig.suptitle('rmatch Performance Evolution', fontsize=16, fontweight='bold')
    
    # Duration over time
    if 'duration' in df.columns:
        ax1.plot(df['datetime'], df['duration'], 'o-', linewidth=2, markersize=4)
        ax1.set_title('Execution Time Evolution')
        ax1.set_ylabel('Duration (milliseconds)')
        ax1.grid(True, alpha=0.3)
        format_time_axis(ax1)
    
    # Memory usage over time
    if 'memory' in df.columns:
        ax2.plot(df['datetime'], df['memory'], 'o-', color='orange', linewidth=2, markersize=4)
        ax2.set_title('Memory Usage Evolution')
        ax2.set_ylabel('Memory (MB)')
        ax2.grid(True, alpha=0.3)
        format_time_axis(ax2)
    
    plt.tight_layout()
    
    # Save the chart
    chart_path = os.path.join(output_dir, 'simple_performance_evolution.png')
    plt.savefig(chart_path, dpi=300, bbox_inches='tight')
    print(f"Simple performance evolution chart saved to: {chart_path}")
    
    plt.close()

def create_readme_chart(df, output_dir):
    """Create a simplified chart suitable for README inclusion."""
    fig, ax = plt.subplots(1, 1, figsize=(10, 6))
    
    # Calculate performance ratio if possible
    if 'durationInMillis1' in df.columns and 'durationInMillis2' in df.columns:
        df_ratio = df[df['durationInMillis2'] > 0].copy()
        df_ratio['performance_ratio'] = df_ratio['durationInMillis1'] / df_ratio['durationInMillis2']
        
        ax.plot(df_ratio['datetime'], df_ratio['performance_ratio'], 'o-', 
               color='#2E8B57', linewidth=3, markersize=6, label='rmatch vs Java regex')
        ax.axhline(y=1.0, color='gray', linestyle='--', alpha=0.7, label='Parity (1.0x)')
        
        # Add trend line
        if len(df_ratio) > 2:
            z = np.polyfit(mdates.date2num(df_ratio['datetime']), df_ratio['performance_ratio'], 1)
            p = np.poly1d(z)
            ax.plot(df_ratio['datetime'], p(mdates.date2num(df_ratio['datetime'])), 
                   "r--", alpha=0.7, linewidth=2, label='Trend')
        
        ax.set_title('rmatch Performance Evolution vs Java regex', fontsize=14, fontweight='bold')
        ax.set_ylabel('Performance Ratio (lower is better)', fontsize=12)
        ax.legend(fontsize=11)
        ax.grid(True, alpha=0.3)
        format_time_axis(ax)
        
        # Add some styling
        ax.spines['top'].set_visible(False)
        ax.spines['right'].set_visible(False)
        
        plt.tight_layout()
        
        # Save for README
        readme_chart_path = os.path.join(output_dir, 'performance_trend.png')
        plt.savefig(readme_chart_path, dpi=200, bbox_inches='tight')
        print(f"README performance chart saved to: {readme_chart_path}")
        
        plt.close()

def format_time_axis(ax):
    """Format time axis to be more readable."""
    ax.xaxis.set_major_formatter(mdates.DateFormatter('%Y-%m'))
    ax.xaxis.set_major_locator(mdates.MonthLocator(interval=2))
    plt.setp(ax.xaxis.get_majorticklabels(), rotation=45)

def main():
    """Main function to generate performance charts."""
    print("Starting performance chart generation...")
    
    # Load performance data
    dataframes = load_performance_data()
    
    if not dataframes:
        print("No performance data found. Exiting.")
        return
    
    # Create output directory
    output_dir = "charts"
    
    # Separate different types of data
    comprehensive_df = None
    simple_dfs = []
    performance_check_dfs = []
    
    for df in dataframes:
        if 'data_type' in df.columns:
            data_type = df['data_type'].iloc[0] if len(df) > 0 else None
            if data_type == 'comprehensive_csv':
                comprehensive_df = df
            elif data_type == 'simple_csv':
                simple_dfs.append(df)
            elif data_type == 'performance_check_json':
                performance_check_dfs.append(df)
        else:
            # Legacy handling - check source file
            if 'source_file' in df.columns and df['source_file'].iloc[0] == 'large-corpus-log.csv':
                comprehensive_df = df
            else:
                simple_dfs.append(df)
    
    # Generate charts for different data types
    
    # 1. Generate comprehensive performance chart (legacy CSV data)
    if comprehensive_df is not None:
        print(f"Creating comprehensive performance chart from {len(comprehensive_df)} records")
        create_comprehensive_performance_chart(comprehensive_df, output_dir)
    
    # 2. Generate performance check charts (new JSON data)
    if performance_check_dfs:
        combined_check_df = pd.concat(performance_check_dfs, ignore_index=True, sort=False)
        print(f"Creating performance check charts from {len(combined_check_df)} records")
        create_performance_check_chart(combined_check_df, output_dir)
        
        # Only create overview chart if we have recent performance check data
        if len(combined_check_df) > 0:
            create_comparison_overview_chart(combined_check_df, output_dir)
    
    # 3. Generate simple performance chart (simple CSV data)
    if simple_dfs:
        combined_simple = pd.concat(simple_dfs, ignore_index=True, sort=False)
        print(f"Creating simple performance chart from {len(combined_simple)} records")  
        create_simple_performance_chart(combined_simple, output_dir)
    
    # 4. Create a combined summary chart if we have multiple data sources
    if comprehensive_df is not None and performance_check_dfs:
        print("Creating combined summary chart")
        create_combined_summary_chart(comprehensive_df, combined_check_df, output_dir)
    
    print("Performance chart generation completed!")

def create_combined_summary_chart(comprehensive_df, performance_check_df, output_dir):
    """Create a summary chart combining legacy and new performance data."""
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))
    fig.suptitle('rmatch Performance Evolution Summary', fontsize=16, fontweight='bold')
    
    # Top chart: Legacy comprehensive data (performance ratio)
    if 'durationInMillis1' in comprehensive_df.columns and 'durationInMillis2' in comprehensive_df.columns:
        df_ratio = comprehensive_df[comprehensive_df['durationInMillis2'] > 0].copy()
        df_ratio['performance_ratio'] = df_ratio['durationInMillis1'] / df_ratio['durationInMillis2']
        
        ax1.plot(df_ratio['datetime'], df_ratio['performance_ratio'], 'o-', 
                color='#2E8B57', linewidth=2, markersize=4, alpha=0.7, label='Historical data (CSV)')
        ax1.axhline(y=1.0, color='gray', linestyle='--', alpha=0.7)
        ax1.set_title('Historical Performance Data (rmatch/java ratio)')
        ax1.set_ylabel('Performance Ratio')
        ax1.legend()
        ax1.grid(True, alpha=0.3)
        format_time_axis(ax1)
    
    # Bottom chart: Recent performance check data
    if 'rmatch_avg_time_ms' in performance_check_df.columns and 'java_avg_time_ms' in performance_check_df.columns:
        valid_data = performance_check_df.dropna(subset=['rmatch_avg_time_ms', 'java_avg_time_ms'])
        if not valid_data.empty:
            performance_ratio = valid_data['rmatch_avg_time_ms'] / valid_data['java_avg_time_ms']
            
            # Color points by status
            colors = []
            for status in valid_data['status']:
                if status == 'PASS':
                    colors.append('#2E8B57')  # Green
                elif status == 'WARNING':
                    colors.append('#FFA500')  # Orange  
                else:
                    colors.append('#FF4444')  # Red
            
            ax2.scatter(valid_data['datetime'], performance_ratio, 
                       c=colors, s=60, alpha=0.8, edgecolors='black', linewidth=1, label='Recent tests (JSON)')
            ax2.axhline(y=1.0, color='gray', linestyle='--', alpha=0.7)
            ax2.set_title('Recent Performance Test Results')
            ax2.set_ylabel('Performance Ratio')
            ax2.legend()
            ax2.grid(True, alpha=0.3)
            format_time_axis(ax2)
    
    plt.tight_layout()
    
    # Save the summary chart
    summary_chart_path = os.path.join(output_dir, 'performance_summary.png')
    plt.savefig(summary_chart_path, dpi=300, bbox_inches='tight')
    print(f"Performance summary chart saved to: {summary_chart_path}")
    
    plt.close()

if __name__ == "__main__":
    main()