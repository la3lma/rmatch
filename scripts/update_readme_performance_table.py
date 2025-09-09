#!/usr/bin/env python3
"""
Update the performance comparison table in README.md with latest benchmark results.
This script reads the latest rmatch and Java regex benchmark results and updates
the README.md file with current performance metrics and ratios.
"""

import json
import glob
import os
import re
from pathlib import Path
from datetime import datetime

def get_latest_benchmark_result(results_dir, pattern):
    """Get the most recent benchmark result matching the pattern"""
    files = glob.glob(os.path.join(results_dir, pattern))
    if not files:
        return None
    
    # Sort by timestamp in filename to get latest
    files.sort(key=lambda x: os.path.basename(x), reverse=True)
    
    for json_file in files:
        try:
            with open(json_file, 'r') as f:
                content = f.read()
                # Fix JSON issues with unescaped quotes in java version string
                if '"java": "' in content and 'version "' in content:
                    # Use regex to escape quotes within the java field
                    import re
                    content = re.sub(r'"java": "([^"]+)"([^"]*)"([^"]*)"', r'"java": "\1\\"\2\\"\3"', content)
                data = json.loads(content)
            return data
        except (json.JSONDecodeError, KeyError) as e:
            print(f"Warning: Skipping {json_file}: {e}")
            continue
    
    return None

def format_duration(duration_ms):
    """Format duration from milliseconds to human readable"""
    duration_s = duration_ms / 1000.0
    return f"{duration_s:.1f}s"

def format_memory(memory_mb):
    """Format memory from MB to human readable"""
    return f"{memory_mb}MB"

def calculate_ratio(rmatch_value, java_value):
    """Calculate ratio and format as human readable"""
    if java_value == 0:
        return "N/A"
    ratio = rmatch_value / java_value
    return f"{ratio:.1f}x"

def update_readme_table(readme_path, rmatch_data, java_data):
    """Update the performance comparison table in README.md"""
    
    # Extract performance metrics
    rmatch_duration_ms = rmatch_data.get("duration_ms", 0)
    rmatch_memory = rmatch_data.get("memory", {})
    rmatch_peak_mb = rmatch_memory.get("detailed", {}).get("peak_used_mb", rmatch_memory.get("after_mb", 0))
    rmatch_pattern_loading_mb = rmatch_memory.get("detailed", {}).get("pattern_loading_mb", 0)
    rmatch_matching_mb = rmatch_memory.get("detailed", {}).get("matching_mb", 0)
    rmatch_regexps = rmatch_data.get("args", {}).get("max_regexps", "unknown")
    
    java_duration_ms = java_data.get("duration_ms", 0)
    java_memory = java_data.get("memory", {})
    java_peak_mb = java_memory.get("detailed", {}).get("peak_used_mb", java_memory.get("after_mb", 0))
    java_pattern_loading_mb = java_memory.get("detailed", {}).get("pattern_loading_mb", 0)
    java_matching_mb = java_memory.get("detailed", {}).get("matching_mb", 0)
    java_regexps = java_data.get("args", {}).get("max_regexps", "unknown")
    
    # Format values
    rmatch_duration_str = format_duration(rmatch_duration_ms)
    java_duration_str = format_duration(java_duration_ms)
    duration_ratio = calculate_ratio(rmatch_duration_ms / 1000.0, java_duration_ms / 1000.0)
    
    rmatch_peak_str = format_memory(rmatch_peak_mb)
    java_peak_str = format_memory(java_peak_mb)
    peak_ratio = calculate_ratio(rmatch_peak_mb, java_peak_mb)
    
    rmatch_pattern_str = format_memory(rmatch_pattern_loading_mb)
    java_pattern_str = format_memory(java_pattern_loading_mb)
    pattern_ratio = calculate_ratio(rmatch_pattern_loading_mb, java_pattern_loading_mb)
    
    rmatch_matching_str = format_memory(rmatch_matching_mb)
    java_matching_str = format_memory(java_matching_mb)
    matching_ratio = calculate_ratio(rmatch_matching_mb, java_matching_mb)
    
    # Determine performance description for ratio
    duration_ratio_float = rmatch_duration_ms / java_duration_ms if java_duration_ms > 0 else 0
    if duration_ratio_float > 1:
        performance_desc = f"{duration_ratio} slower"
    elif duration_ratio_float < 1:
        performance_desc = f"{1/duration_ratio_float:.1f}x faster"
    else:
        performance_desc = "same speed"
    
    peak_ratio_float = rmatch_peak_mb / java_peak_mb if java_peak_mb > 0 else 0
    if peak_ratio_float > 1:
        peak_desc = f"{peak_ratio} more memory"
    elif peak_ratio_float < 1 and rmatch_peak_mb > 0:
        peak_desc = f"{java_peak_mb/rmatch_peak_mb:.1f}x less memory"
    else:
        peak_desc = "same memory"
    
    pattern_ratio_float = rmatch_pattern_loading_mb / java_pattern_loading_mb if java_pattern_loading_mb > 0 else 0
    if pattern_ratio_float > 1:
        pattern_desc = f"{pattern_ratio} more memory"
    elif pattern_ratio_float < 1 and rmatch_pattern_loading_mb > 0:
        pattern_desc = f"{java_pattern_loading_mb/rmatch_pattern_loading_mb:.1f}x less memory"
    else:
        pattern_desc = "same memory"
    
    matching_ratio_float = rmatch_matching_mb / java_matching_mb if java_matching_mb > 0 else 0
    if matching_ratio_float > 1:
        matching_desc = f"{matching_ratio} more memory"
    elif matching_ratio_float < 1 and rmatch_matching_mb > 0:
        matching_desc = f"{java_matching_mb/rmatch_matching_mb:.1f}x less memory"
    else:
        matching_desc = "same memory"
    
    # Create the new table
    new_table = f"""## Current Performance Comparison

| Metric | rmatch | Java Regex | Ratio (rmatch/java) |
|--------|--------|------------|---------------------|
| **{rmatch_regexps} patterns** | {rmatch_duration_str} | {java_duration_str} | {performance_desc} |
| **Peak Memory** | {rmatch_peak_str} | {java_peak_str} | {peak_desc} |
| **Pattern Loading** | {rmatch_pattern_str} | {java_pattern_str} | {pattern_desc} |
| **Matching Phase** | {rmatch_matching_str} | {java_matching_str} | {matching_desc} |

*Latest benchmark comparison between rmatch and native Java regex (java.util.regex.Pattern) on {rmatch_regexps} regex patterns against Wuthering Heights corpus. Updated: {datetime.now().strftime('%Y-%m-%d %H:%M UTC')}*"""
    
    # Read current README
    with open(readme_path, 'r') as f:
        lines = f.readlines()
    
    # Find the start and end of the performance comparison section
    start_idx = None
    end_idx = None
    
    for i, line in enumerate(lines):
        if line.strip() == "## Current Performance Comparison":
            start_idx = i
        elif start_idx is not None and line.strip() == "---":
            end_idx = i
            break
    
    # Replace the section
    if start_idx is not None and end_idx is not None:
        # Replace the section with new content
        new_lines = lines[:start_idx] + [new_table + "\n\n"] + lines[end_idx:]
    elif start_idx is not None:
        # Found start but no end - replace until end of file or next major section
        next_section = None
        for i in range(start_idx + 1, len(lines)):
            if lines[i].startswith("## "):
                next_section = i
                break
        
        if next_section:
            new_lines = lines[:start_idx] + [new_table + "\n\n---\n\n"] + lines[next_section:]
        else:
            new_lines = lines[:start_idx] + [new_table + "\n\n"]
    else:
        # Section not found - add it after the title
        title_end = None
        for i, line in enumerate(lines):
            if line.strip().startswith("==="):
                title_end = i + 1
                break
        
        if title_end:
            new_lines = lines[:title_end] + ["\n" + new_table + "\n\n---\n\n"] + lines[title_end:]
        else:
            new_lines = [new_table + "\n\n---\n\n"] + lines
    
    updated_content = "".join(new_lines)
    
    # Write updated content
    with open(readme_path, 'w') as f:
        f.write(updated_content)
    
    print(f"Updated README.md performance table:")
    print(f"  rmatch: {rmatch_duration_str}, {rmatch_peak_str} peak memory")
    print(f"  Java:   {java_duration_str}, {java_peak_str} peak memory")
    print(f"  Ratios: {performance_desc}, {peak_desc}")

def main():
    """Main function"""
    # Setup paths
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    results_dir = project_root / "benchmarks" / "results"
    readme_path = project_root / "README.md"
    
    print(f"Scanning for latest benchmark results in: {results_dir}")
    
    # Get latest results
    rmatch_data = get_latest_benchmark_result(results_dir, "macro-*.json")
    java_data = get_latest_benchmark_result(results_dir, "java-*.json")
    
    if not rmatch_data:
        print("ERROR: No rmatch benchmark results found!")
        return 1
    
    if not java_data:
        print("ERROR: No Java regex benchmark results found!")
        return 1
    
    # Update README
    update_readme_table(readme_path, rmatch_data, java_data)
    print("README.md performance table updated successfully!")
    
    return 0

if __name__ == "__main__":
    exit(main())