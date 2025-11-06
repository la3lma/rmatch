#!/usr/bin/env python3
"""
Generate a performance plot from Java regex benchmark results.
Reads all java-*.json files from benchmarks/results/ and creates a timeline plot.
This creates the same type of visualization as the rmatch benchmark, allowing direct comparison.
"""

import glob
import json
import matplotlib.dates as mdates
import matplotlib.pyplot as plt
import os
from datetime import datetime
from pathlib import Path


def parse_timestamp(timestamp_str):
    """Parse timestamp format 20250909T091046Z to datetime with UTC timezone"""
    import pytz
    dt = datetime.strptime(timestamp_str, "%Y%m%dT%H%M%SZ")
    return dt.replace(tzinfo=pytz.UTC)

def load_java_results(results_dir):
    """Load all Java regex benchmark results from JSON files"""
    results = []
    pattern = os.path.join(results_dir, "java-*.json")
    
    for json_file in glob.glob(pattern):
        try:
            with open(json_file, 'r') as f:
                data = json.load(f)
                
            # Extract key data
            timestamp = parse_timestamp(data["timestamp"])
            duration_ms = data["duration_ms"]
            duration_s = duration_ms / 1000.0
            max_regexps = data.get("args", {}).get("max_regexps", "unknown")
            git_branch = data.get("git", {}).get("branch", "unknown")
            
            # Extract memory data if available
            memory_data = data.get("memory", {})
            memory_used_mb = memory_data.get("used_mb", 0)
            memory_peak_mb = memory_data.get("detailed", {}).get("peak_used_mb", memory_data.get("after_mb", 0))
            memory_pattern_loading_mb = memory_data.get("detailed", {}).get("pattern_loading_mb", 0)
            memory_matching_mb = memory_data.get("detailed", {}).get("matching_mb", 0)
            
            results.append({
                "timestamp": timestamp,
                "duration_s": duration_s,
                "duration_ms": duration_ms,
                "max_regexps": max_regexps,
                "branch": git_branch,
                "filename": os.path.basename(json_file),
                "memory_used_mb": memory_used_mb,
                "memory_peak_mb": memory_peak_mb,
                "memory_pattern_loading_mb": memory_pattern_loading_mb,
                "memory_matching_mb": memory_matching_mb,
                "has_memory_data": bool(memory_data)
            })
            
        except (json.JSONDecodeError, KeyError, ValueError) as e:
            print(f"Warning: Skipping {json_file}: {e}")
            continue
    
    # Sort by timestamp
    results.sort(key=lambda x: x["timestamp"])
    return results

def create_performance_plot(results, output_path):
    """Create a performance timeline plot with optional memory data"""
    if not results:
        print("No results to plot")
        return
    
    # Check if we have memory data for recent results
    memory_results = [r for r in results if r["has_memory_data"]]
    has_memory_data = len(memory_results) > 0
    
    if has_memory_data:
        fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 12), sharex=True)
        fig.suptitle('Java Regex Benchmark Performance & Memory History\\n(Native java.util.regex.Pattern on Wuthering Heights corpus)', 
                     fontsize=16, fontweight='bold')
    else:
        fig, ax1 = plt.subplots(figsize=(14, 8))
        ax1.set_title('Java Regex Benchmark Performance History\\n(Native java.util.regex.Pattern on Wuthering Heights corpus)', 
                     fontsize=14, fontweight='bold')
    
    # Extract data for plotting
    timestamps = [r["timestamp"] for r in results]
    durations = [r["duration_s"] for r in results]
    
    # Color code by performance ranges (same as rmatch for comparison)
    colors = []
    for r in results:
        if r["duration_s"] < 2:
            colors.append('green')   # Excellent performance
        elif r["duration_s"] < 15:
            colors.append('gold')    # Good performance  
        else:
            colors.append('red')     # Needs improvement
    
    # Create performance scatter plot
    scatter1 = ax1.scatter(timestamps, durations, c=colors, alpha=0.7, s=60)
    
    # Add trend line
    ax1.plot(timestamps, durations, 'b-', alpha=0.3, linewidth=1)
    
    # Formatting for performance plot
    if not has_memory_data:
        ax1.set_xlabel('Date/Time', fontsize=12)
    ax1.set_ylabel('Duration (seconds)', fontsize=12)
    
    # Format x-axis (use bottom axis for shared plots)
    bottom_ax = ax2 if has_memory_data else ax1
    # Intelligent date formatting based on data range
    if timestamps:
        time_range = (max(timestamps) - min(timestamps)).days
        
        # Special case for single data point or very short range
        if time_range == 0 or len(timestamps) == 1:
            bottom_ax.xaxis.set_major_formatter(mdates.DateFormatter('%m/%d\n%H:%M UTC'))
            bottom_ax.xaxis.set_major_locator(plt.MaxNLocator(3))  # Very limited ticks for single point
        elif time_range <= 7:  # Less than a week
            bottom_ax.xaxis.set_major_formatter(mdates.DateFormatter('%m/%d\n%H:%M UTC'))
            bottom_ax.xaxis.set_major_locator(mdates.DayLocator(interval=1))
            bottom_ax.xaxis.set_minor_locator(mdates.HourLocator(interval=6))
        elif time_range <= 30:  # Less than a month
            bottom_ax.xaxis.set_major_formatter(mdates.DateFormatter('%m/%d UTC'))
            bottom_ax.xaxis.set_major_locator(mdates.DayLocator(interval=max(1, time_range // 6)))
        else:  # Longer range
            bottom_ax.xaxis.set_major_formatter(mdates.DateFormatter('%Y-%m UTC'))
            bottom_ax.xaxis.set_major_locator(plt.MaxNLocator(8))
        
    plt.setp(bottom_ax.xaxis.get_majorticklabels(), rotation=45, ha='right', fontsize=9)
    bottom_ax.set_xlabel('Date/Time', fontsize=12)
    
    # Add horizontal reference lines to performance plot
    ax1.axhline(y=2, color='green', linestyle='--', alpha=0.5, label='Excellent: <2s')
    ax1.axhline(y=15, color='gold', linestyle='--', alpha=0.5, label='Good: <15s')
    
    # Add legend for performance colors
    from matplotlib.patches import Patch
    legend_elements = [
        Patch(facecolor='green', label='Excellent (<2s)'),
        Patch(facecolor='gold', label='Good (2-15s)'),
        Patch(facecolor='red', label='Needs Work (>15s)'),
        plt.Line2D([0], [0], color='green', linestyle='--', alpha=0.5, label='Excellent: <2s'),
        plt.Line2D([0], [0], color='gold', linestyle='--', alpha=0.5, label='Good: <15s')
    ]
    ax1.legend(handles=legend_elements, loc='upper right')
    
    # Add memory plot if we have memory data
    if has_memory_data:
        # Extract memory data for plotting
        memory_timestamps = [r["timestamp"] for r in memory_results]
        memory_peak = [r["memory_peak_mb"] for r in memory_results]
        memory_pattern_loading = [r["memory_pattern_loading_mb"] for r in memory_results]
        memory_matching = [r["memory_matching_mb"] for r in memory_results]
        
        # Create stacked area plot for memory breakdown
        ax2.fill_between(memory_timestamps, 0, memory_pattern_loading, 
                        alpha=0.6, color='lightblue', label='Pattern Loading')
        ax2.fill_between(memory_timestamps, memory_pattern_loading, 
                        [p + m for p, m in zip(memory_pattern_loading, memory_matching)],
                        alpha=0.6, color='orange', label='Matching')
        
        # Add peak memory line
        ax2.plot(memory_timestamps, memory_peak, 'r-', alpha=0.8, linewidth=2, label='Peak Memory')
        ax2.scatter(memory_timestamps, memory_peak, c='red', alpha=0.8, s=40)
        
        # Format memory plot
        ax2.set_ylabel('Memory Usage (MB)', fontsize=12)
        ax2.legend(loc='upper right')
        ax2.grid(True, alpha=0.3)
    
    # Add performance statistics
    recent_results = results[-5:]  # Last 5 runs
    if recent_results:
        avg_recent = sum(r["duration_s"] for r in recent_results) / len(recent_results)
        min_time = min(r["duration_s"] for r in results)
        max_time = max(r["duration_s"] for r in results)
        
        stats_text = f'Recent Avg: {avg_recent:.1f}s | Best: {min_time:.1f}s | Worst: {max_time:.1f}s'
        
        # Add memory stats if available
        if has_memory_data:
            recent_memory = [r for r in recent_results if r["has_memory_data"]]
            if recent_memory:
                avg_memory = sum(r["memory_peak_mb"] for r in recent_memory) / len(recent_memory)
                max_memory = max(r["memory_peak_mb"] for r in memory_results)
                stats_text += f' | Avg Memory: {avg_memory:.0f}MB | Peak: {max_memory:.0f}MB'
        
        ax1.text(0.02, 0.98, stats_text, transform=ax1.transAxes, 
                bbox=dict(boxstyle="round,pad=0.3", facecolor="lightgreen", alpha=0.7),
                verticalalignment='top', fontsize=10)
    
    # Grid
    ax1.grid(True, alpha=0.3)
    
    # Tight layout
    plt.tight_layout()
    
    # Save plot
    plt.savefig(output_path, dpi=300, bbox_inches='tight', facecolor='white')
    print(f"Java regex performance plot saved to: {output_path}")
    
    return len(results)

def main():
    """Main function"""
    # Setup paths
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    results_dir = project_root / "benchmarks" / "results"
    output_path = project_root / "java_performance_timeline.png"
    
    print(f"Scanning for Java regex benchmark results in: {results_dir}")
    
    # Load and process results
    results = load_java_results(results_dir)
    print(f"Found {len(results)} Java regex benchmark results")
    
    if results:
        # Show summary
        print(f"Date range: {results[0]['timestamp'].strftime('%Y-%m-%d %H:%M UTC')} to {results[-1]['timestamp'].strftime('%Y-%m-%d %H:%M UTC')}")
        print(f"Performance range: {min(r['duration_s'] for r in results):.1f}s to {max(r['duration_s'] for r in results):.1f}s")
        
        # Create plot
        count = create_performance_plot(results, output_path)
        print(f"Successfully plotted {count} Java regex benchmark results")
    else:
        print("No valid Java regex benchmark results found!")

if __name__ == "__main__":
    main()