#!/usr/bin/env python3
"""
Generate performance evolution charts from historical CSV data.

This script reads existing performance logs from CSV files and generates charts
showing the evolution of performance metrics over time for both rmatch and Java regex.

The generated charts are saved as PNG files that can be included in documentation.
"""

import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import seaborn as sns
import numpy as np
import os
import sys
from datetime import datetime
from pathlib import Path

# Set up matplotlib for better-looking plots
plt.style.use('default')
sns.set_palette("husl")

def load_performance_data():
    """Load performance data from CSV files in the logs directory."""
    logs_dir = Path("rmatch-tester/logs")
    if not logs_dir.exists():
        print(f"Warning: Logs directory {logs_dir} does not exist")
        return pd.DataFrame()
    
    csv_files = list(logs_dir.glob("*.csv"))
    if not csv_files:
        print(f"Warning: No CSV files found in {logs_dir}")
        return pd.DataFrame()
    
    dataframes = []
    
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
                        dataframes.append(df)
                        print(f"Loaded {len(df)} records from {csv_file}")
        except Exception as e:
            print(f"Error loading {csv_file}: {e}")
            continue
    
    if not dataframes:
        print("No valid data found in CSV files")
        return pd.DataFrame()
    
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
    
    # Find comprehensive data first (large-corpus-log.csv)
    comprehensive_df = None
    simple_dfs = []
    
    for df in dataframes:
        if 'source_file' in df.columns and df['source_file'].iloc[0] == 'large-corpus-log.csv':
            comprehensive_df = df
        else:
            simple_dfs.append(df)
    
    # Generate charts - prefer comprehensive data
    if comprehensive_df is not None:
        print(f"Creating comprehensive performance chart from {len(comprehensive_df)} records")
        create_comprehensive_performance_chart(comprehensive_df, output_dir)
    elif simple_dfs:
        # Combine simple data
        combined_simple = pd.concat(simple_dfs, ignore_index=True, sort=False)
        print(f"Creating simple performance chart from {len(combined_simple)} records")  
        create_simple_performance_chart(combined_simple, output_dir)
    
    print("Performance chart generation completed!")

if __name__ == "__main__":
    main()