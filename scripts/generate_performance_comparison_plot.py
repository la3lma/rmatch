#!/usr/bin/env python3
"""
Generate a performance comparison plot showing rmatch vs Java regex ratios over time.
This chart shows the relative performance of rmatch compared to native Java regex,
with values above 1.0 meaning rmatch is slower/uses more memory than Java.
"""

import json
import glob
import os
from datetime import datetime
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
from pathlib import Path
from collections import defaultdict

def parse_timestamp(timestamp_str):
    """Parse timestamp format 20250909T091046Z to datetime"""
    return datetime.strptime(timestamp_str, "%Y%m%dT%H%M%SZ")

def load_benchmark_results(results_dir):
    """Load both rmatch and Java regex benchmark results and pair them by timestamp"""
    
    # Load rmatch results
    rmatch_results = {}
    pattern = os.path.join(results_dir, "macro-*.json")
    
    for json_file in glob.glob(pattern):
        try:
            with open(json_file, 'r') as f:
                content = f.read()
                # Fix JSON issues with unescaped quotes in java version string
                if '"java": "' in content and 'version "' in content:
                    import re
                    content = re.sub(r'"java": "([^"]+)"([^"]*)"([^"]*)"', r'"java": "\1\\"\2\\"\3"', content)
                data = json.loads(content)
            
            timestamp = parse_timestamp(data["timestamp"])
            duration_s = data["duration_ms"] / 1000.0
            memory_data = data.get("memory", {})
            memory_peak_mb = memory_data.get("detailed", {}).get("peak_used_mb", memory_data.get("after_mb", 0))
            memory_pattern_loading_mb = memory_data.get("detailed", {}).get("pattern_loading_mb", 0)
            memory_matching_mb = memory_data.get("detailed", {}).get("matching_mb", 0)
            max_regexps = data.get("args", {}).get("max_regexps", "unknown")
            
            rmatch_results[timestamp] = {
                "timestamp": timestamp,
                "duration_s": duration_s,
                "memory_peak_mb": memory_peak_mb,
                "memory_pattern_loading_mb": memory_pattern_loading_mb,
                "memory_matching_mb": memory_matching_mb,
                "max_regexps": max_regexps,
                "filename": os.path.basename(json_file)
            }
            
        except (json.JSONDecodeError, KeyError, ValueError) as e:
            print(f"Warning: Skipping rmatch file {json_file}: {e}")
            continue
    
    # Load Java regex results
    java_results = {}
    pattern = os.path.join(results_dir, "java-*.json")
    
    for json_file in glob.glob(pattern):
        try:
            with open(json_file, 'r') as f:
                data = json.load(f)
            
            timestamp = parse_timestamp(data["timestamp"])
            duration_s = data["duration_ms"] / 1000.0
            memory_data = data.get("memory", {})
            memory_peak_mb = memory_data.get("detailed", {}).get("peak_used_mb", memory_data.get("after_mb", 0))
            memory_pattern_loading_mb = memory_data.get("detailed", {}).get("pattern_loading_mb", 0)
            memory_matching_mb = memory_data.get("detailed", {}).get("matching_mb", 0)
            max_regexps = data.get("args", {}).get("max_regexps", "unknown")
            
            java_results[timestamp] = {
                "timestamp": timestamp,
                "duration_s": duration_s,
                "memory_peak_mb": memory_peak_mb,
                "memory_pattern_loading_mb": memory_pattern_loading_mb,
                "memory_matching_mb": memory_matching_mb,
                "max_regexps": max_regexps,
                "filename": os.path.basename(json_file)
            }
            
        except (json.JSONDecodeError, KeyError, ValueError) as e:
            print(f"Warning: Skipping Java file {json_file}: {e}")
            continue
    
    return rmatch_results, java_results

def find_closest_pairs(rmatch_results, java_results, max_time_diff_minutes=60):
    """Find rmatch and Java results that are close in time for comparison"""
    comparison_points = []
    
    for rmatch_time, rmatch_data in rmatch_results.items():
        best_match = None
        best_time_diff = None
        
        # Look for the closest Java result within the time window
        for java_time, java_data in java_results.items():
            time_diff = abs((rmatch_time - java_time).total_seconds() / 60)  # in minutes
            
            # Only consider if same number of regexps
            if (rmatch_data["max_regexps"] == java_data["max_regexps"] and 
                time_diff <= max_time_diff_minutes):
                
                if best_match is None or time_diff < best_time_diff:
                    best_match = java_data
                    best_time_diff = time_diff
        
        if best_match:
            # Calculate ratios (rmatch / java)
            duration_ratio = rmatch_data["duration_s"] / best_match["duration_s"] if best_match["duration_s"] > 0 else 0
            
            memory_peak_ratio = rmatch_data["memory_peak_mb"] / best_match["memory_peak_mb"] if best_match["memory_peak_mb"] > 0 else 0
            
            memory_pattern_ratio = rmatch_data["memory_pattern_loading_mb"] / best_match["memory_pattern_loading_mb"] if best_match["memory_pattern_loading_mb"] > 0 else 0
            
            memory_matching_ratio = rmatch_data["memory_matching_mb"] / best_match["memory_matching_mb"] if best_match["memory_matching_mb"] > 0 else 0
            
            comparison_points.append({
                "timestamp": rmatch_time,
                "duration_ratio": duration_ratio,
                "memory_peak_ratio": memory_peak_ratio,
                "memory_pattern_ratio": memory_pattern_ratio,
                "memory_matching_ratio": memory_matching_ratio,
                "max_regexps": rmatch_data["max_regexps"],
                "time_diff_minutes": best_time_diff,
                "rmatch_file": rmatch_data["filename"],
                "java_file": best_match["filename"]
            })
    
    # Sort by timestamp
    comparison_points.sort(key=lambda x: x["timestamp"])
    return comparison_points

def create_comparison_plot(comparison_points, output_path):
    """Create a performance comparison plot showing rmatch/java ratios over time"""
    if not comparison_points:
        print("No comparison points to plot")
        return
    
    # Create figure with subplots for different metrics
    fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(16, 12), sharex=True)
    fig.suptitle('rmatch vs Java Regex Performance Comparison\\n(Ratios over Time - Values > 1.0 mean rmatch is slower/uses more memory)', 
                 fontsize=16, fontweight='bold')
    
    # Extract data for plotting
    timestamps = [point["timestamp"] for point in comparison_points]
    duration_ratios = [point["duration_ratio"] for point in comparison_points]
    memory_peak_ratios = [point["memory_peak_ratio"] for point in comparison_points]
    memory_pattern_ratios = [point["memory_pattern_ratio"] for point in comparison_points]
    memory_matching_ratios = [point["memory_matching_ratio"] for point in comparison_points]
    
    # Performance ratio plot
    ax1.plot(timestamps, duration_ratios, 'b-o', alpha=0.7, markersize=4, linewidth=2)
    ax1.axhline(y=1.0, color='red', linestyle='--', alpha=0.7, label='Equal Performance (1.0x)')
    ax1.set_ylabel('Performance Ratio\\n(rmatch time / java time)', fontsize=10)
    ax1.set_title('Performance: Higher = rmatch is slower', fontsize=12)
    ax1.grid(True, alpha=0.3)
    ax1.legend()
    
    # Add performance ratio statistics
    if duration_ratios:
        avg_perf = sum(duration_ratios) / len(duration_ratios)
        latest_perf = duration_ratios[-1] if duration_ratios else 0
        ax1.text(0.02, 0.98, f'Latest: {latest_perf:.1f}x | Avg: {avg_perf:.1f}x', 
                transform=ax1.transAxes, bbox=dict(boxstyle="round,pad=0.3", facecolor="lightblue", alpha=0.7),
                verticalalignment='top', fontsize=9)
    
    # Peak memory ratio plot
    ax2.plot(timestamps, memory_peak_ratios, 'g-o', alpha=0.7, markersize=4, linewidth=2)
    ax2.axhline(y=1.0, color='red', linestyle='--', alpha=0.7, label='Equal Memory (1.0x)')
    ax2.set_ylabel('Peak Memory Ratio\\n(rmatch MB / java MB)', fontsize=10)
    ax2.set_title('Peak Memory: Higher = rmatch uses more memory', fontsize=12)
    ax2.grid(True, alpha=0.3)
    ax2.legend()
    
    # Add memory ratio statistics
    if memory_peak_ratios:
        avg_mem = sum(memory_peak_ratios) / len(memory_peak_ratios)
        latest_mem = memory_peak_ratios[-1] if memory_peak_ratios else 0
        ax2.text(0.02, 0.98, f'Latest: {latest_mem:.1f}x | Avg: {avg_mem:.1f}x', 
                transform=ax2.transAxes, bbox=dict(boxstyle="round,pad=0.3", facecolor="lightgreen", alpha=0.7),
                verticalalignment='top', fontsize=9)
    
    # Pattern loading memory ratio plot
    ax3.plot(timestamps, memory_pattern_ratios, 'm-o', alpha=0.7, markersize=4, linewidth=2)
    ax3.axhline(y=1.0, color='red', linestyle='--', alpha=0.7, label='Equal Memory (1.0x)')
    ax3.set_ylabel('Pattern Loading Memory Ratio\\n(rmatch MB / java MB)', fontsize=10)
    ax3.set_title('Pattern Loading Memory', fontsize=12)
    ax3.grid(True, alpha=0.3)
    ax3.legend()
    
    # Matching memory ratio plot
    ax4.plot(timestamps, memory_matching_ratios, 'orange', marker='o', alpha=0.7, markersize=4, linewidth=2)
    ax4.axhline(y=1.0, color='red', linestyle='--', alpha=0.7, label='Equal Memory (1.0x)')
    ax4.set_ylabel('Matching Memory Ratio\\n(rmatch MB / java MB)', fontsize=10)
    ax4.set_title('Matching Phase Memory', fontsize=12)
    ax4.grid(True, alpha=0.3)
    ax4.legend()
    
    # Format x-axis (shared across all subplots)
    for ax in [ax3, ax4]:
        ax.xaxis.set_major_formatter(mdates.DateFormatter('%m/%d\\n%H:%M'))
        ax.xaxis.set_major_locator(mdates.DayLocator(interval=1))
        ax.xaxis.set_minor_locator(mdates.HourLocator(interval=6))
        plt.setp(ax.xaxis.get_majorticklabels(), rotation=45, ha='right')
    
    ax3.set_xlabel('Date/Time', fontsize=12)
    ax4.set_xlabel('Date/Time', fontsize=12)
    
    # Add overall statistics text
    if comparison_points:
        total_points = len(comparison_points)
        date_range = f"{timestamps[0].strftime('%Y-%m-%d')} to {timestamps[-1].strftime('%Y-%m-%d')}"
        stats_text = f'Comparison Points: {total_points} | Date Range: {date_range}'
        
        fig.text(0.5, 0.02, stats_text, ha='center', fontsize=10, 
                bbox=dict(boxstyle="round,pad=0.5", facecolor="lightyellow", alpha=0.8))
    
    # Tight layout
    plt.tight_layout()
    plt.subplots_adjust(top=0.93, bottom=0.12)
    
    # Save plot
    plt.savefig(output_path, dpi=300, bbox_inches='tight', facecolor='white')
    print(f"Performance comparison plot saved to: {output_path}")
    
    return len(comparison_points)

def main():
    """Main function"""
    # Setup paths
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    results_dir = project_root / "benchmarks" / "results"
    output_path = project_root / "performance_comparison.png"
    
    print(f"Scanning for benchmark results in: {results_dir}")
    
    # Load benchmark results
    rmatch_results, java_results = load_benchmark_results(results_dir)
    print(f"Found {len(rmatch_results)} rmatch results and {len(java_results)} Java regex results")
    
    # Find comparison pairs
    comparison_points = find_closest_pairs(rmatch_results, java_results)
    print(f"Found {len(comparison_points)} comparison pairs")
    
    if comparison_points:
        # Show summary of comparison points
        latest = comparison_points[-1]
        print(f"Latest comparison ({latest['timestamp'].strftime('%Y-%m-%d %H:%M')}):")
        print(f"  Performance ratio: {latest['duration_ratio']:.1f}x (rmatch vs java)")
        print(f"  Memory ratio: {latest['memory_peak_ratio']:.1f}x (rmatch vs java)")
        print(f"  Pattern count: {latest['max_regexps']}")
        
        # Create plot
        count = create_comparison_plot(comparison_points, output_path)
        print(f"Successfully plotted {count} comparison points")
    else:
        print("No valid comparison pairs found!")
        print("Make sure you have both rmatch (macro-*.json) and Java regex (java-*.json) benchmark results")
        print("with similar timestamps and regex counts.")

if __name__ == "__main__":
    main()