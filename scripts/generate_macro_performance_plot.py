#!/usr/bin/env python3
"""
Generate a performance plot from macro benchmark results.
Reads all macro-*.json files from benchmarks/results/ and creates a timeline plot.
"""

import json
import glob
import os
from datetime import datetime
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
from pathlib import Path

def parse_timestamp(timestamp_str):
    """Parse timestamp format 20250909T091046Z to datetime"""
    return datetime.strptime(timestamp_str, "%Y%m%dT%H%M%SZ")

def load_macro_results(results_dir):
    """Load all macro benchmark results from JSON files"""
    results = []
    pattern = os.path.join(results_dir, "macro-*.json")
    
    for json_file in glob.glob(pattern):
        try:
            with open(json_file, 'r') as f:
                content = f.read()
                # Fix JSON issues with unescaped quotes in java version string
                content = content.replace('"openjdk version "21.0.2"', '"openjdk version \\"21.0.2\\"')
                content = content.replace('"OpenJDK Runtime Environment (build 21.0.2+13-58) OpenJDK 64-Bit Server VM (build 21.0.2+13-58, mixed mode, sharing) "', '"OpenJDK Runtime Environment (build 21.0.2+13-58) OpenJDK 64-Bit Server VM (build 21.0.2+13-58, mixed mode, sharing)"')
                data = json.loads(content)
                
            # Extract key data
            timestamp = parse_timestamp(data["timestamp"])
            duration_ms = data["duration_ms"]
            duration_s = duration_ms / 1000.0
            max_regexps = data.get("args", {}).get("max_regexps", "unknown")
            git_branch = data.get("git", {}).get("branch", "unknown")
            
            results.append({
                "timestamp": timestamp,
                "duration_s": duration_s,
                "duration_ms": duration_ms,
                "max_regexps": max_regexps,
                "branch": git_branch,
                "filename": os.path.basename(json_file)
            })
            
        except (json.JSONDecodeError, KeyError, ValueError) as e:
            print(f"Warning: Skipping {json_file}: {e}")
            continue
    
    # Sort by timestamp
    results.sort(key=lambda x: x["timestamp"])
    return results

def create_performance_plot(results, output_path):
    """Create a performance timeline plot"""
    if not results:
        print("No results to plot")
        return
    
    fig, ax = plt.subplots(figsize=(14, 8))
    
    # Extract data for plotting
    timestamps = [r["timestamp"] for r in results]
    durations = [r["duration_s"] for r in results]
    
    # Color code by performance ranges
    colors = []
    for r in results:
        if r["duration_s"] < 2:
            colors.append('green')   # Excellent performance
        elif r["duration_s"] < 15:
            colors.append('gold')    # Good performance  
        else:
            colors.append('red')     # Needs improvement
    
    # Create scatter plot
    scatter = ax.scatter(timestamps, durations, c=colors, alpha=0.7, s=60)
    
    # Add trend line
    ax.plot(timestamps, durations, 'b-', alpha=0.3, linewidth=1)
    
    # Formatting
    ax.set_xlabel('Date/Time', fontsize=12)
    ax.set_ylabel('Duration (seconds)', fontsize=12)
    ax.set_title('rmatch Macro Benchmark Performance History\n(10,000 regex patterns on Wuthering Heights corpus)', 
                 fontsize=14, fontweight='bold')
    
    # Format x-axis
    ax.xaxis.set_major_formatter(mdates.DateFormatter('%m/%d\n%H:%M'))
    ax.xaxis.set_major_locator(mdates.DayLocator(interval=1))
    ax.xaxis.set_minor_locator(mdates.HourLocator(interval=6))
    plt.setp(ax.xaxis.get_majorticklabels(), rotation=45, ha='right')
    
    # Add horizontal reference lines
    ax.axhline(y=2, color='green', linestyle='--', alpha=0.5, label='Excellent: <2s')
    ax.axhline(y=15, color='gold', linestyle='--', alpha=0.5, label='Good: <15s')
    
    # Add legend for colors
    from matplotlib.patches import Patch
    legend_elements = [
        Patch(facecolor='green', label='Excellent (<2s)'),
        Patch(facecolor='gold', label='Good (2-15s)'),
        Patch(facecolor='red', label='Needs Work (>15s)'),
        plt.Line2D([0], [0], color='green', linestyle='--', alpha=0.5, label='Excellent: <2s'),
        plt.Line2D([0], [0], color='gold', linestyle='--', alpha=0.5, label='Good: <15s')
    ]
    ax.legend(handles=legend_elements, loc='upper right')
    
    # Add performance statistics
    recent_results = results[-5:]  # Last 5 runs
    if recent_results:
        avg_recent = sum(r["duration_s"] for r in recent_results) / len(recent_results)
        min_time = min(r["duration_s"] for r in results)
        max_time = max(r["duration_s"] for r in results)
        
        stats_text = f'Recent Avg: {avg_recent:.1f}s | Best: {min_time:.1f}s | Worst: {max_time:.1f}s'
        ax.text(0.02, 0.98, stats_text, transform=ax.transAxes, 
                bbox=dict(boxstyle="round,pad=0.3", facecolor="lightblue", alpha=0.7),
                verticalalignment='top', fontsize=10)
    
    # Grid
    ax.grid(True, alpha=0.3)
    
    # Tight layout
    plt.tight_layout()
    
    # Save plot
    plt.savefig(output_path, dpi=300, bbox_inches='tight', facecolor='white')
    print(f"Performance plot saved to: {output_path}")
    
    return len(results)

def main():
    """Main function"""
    # Setup paths
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    results_dir = project_root / "benchmarks" / "results"
    output_path = project_root / "performance_timeline.png"
    
    print(f"Scanning for macro benchmark results in: {results_dir}")
    
    # Load and process results
    results = load_macro_results(results_dir)
    print(f"Found {len(results)} macro benchmark results")
    
    if results:
        # Show summary
        print(f"Date range: {results[0]['timestamp'].strftime('%Y-%m-%d %H:%M')} to {results[-1]['timestamp'].strftime('%Y-%m-%d %H:%M')}")
        print(f"Performance range: {min(r['duration_s'] for r in results):.1f}s to {max(r['duration_s'] for r in results):.1f}s")
        
        # Create plot
        count = create_performance_plot(results, output_path)
        print(f"Successfully plotted {count} benchmark results")
    else:
        print("No valid macro benchmark results found!")

if __name__ == "__main__":
    main()