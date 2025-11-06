#!/usr/bin/env python3
"""
Fix malformed JSON files in benchmarks/results directory.
Common issues:
1. Unescaped quotes in Java version strings
2. Incomplete/truncated JSON structures
3. Log messages mixed into JSON content
"""

import json
import re
import glob
import os
import shutil
from pathlib import Path

def backup_file(file_path):
    """Create a backup of the original file"""
    backup_path = file_path + ".backup"
    if not os.path.exists(backup_path):
        shutil.copy2(file_path, backup_path)
        print(f"Backed up {file_path} to {backup_path}")

def fix_java_version_quotes(content):
    """Fix unescaped quotes in Java version strings"""
    # Pattern to match the java field with unescaped quotes
    pattern = r'"java":\s*"([^"]*)"([^"]*)"([^"]*)"'
    replacement = r'"java": "\1\\"\2\\"\3"'
    return re.sub(pattern, replacement, content)

def try_fix_incomplete_json(content):
    """Attempt to fix incomplete JSON by adding missing closing braces"""
    # Count opening and closing braces
    open_braces = content.count('{')
    close_braces = content.count('}')
    
    if open_braces > close_braces:
        # Try to find where the JSON got cut off
        lines = content.split('\n')
        last_valid_line = -1
        
        for i, line in enumerate(lines):
            stripped = line.strip()
            # Look for lines that might indicate truncation
            if (stripped and 
                not stripped.startswith('"') and 
                not stripped.startswith('}') and 
                not stripped.startswith(']') and
                not stripped.endswith(',') and
                not stripped.endswith('{') and
                not stripped.endswith('}')):
                # This might be where corruption started
                last_valid_line = i - 1
                break
        
        if last_valid_line > 0:
            # Truncate at the last valid line and try to close the JSON
            truncated = '\n'.join(lines[:last_valid_line + 1])
            
            # Remove trailing comma if present
            truncated = re.sub(r',\s*$', '', truncated.strip())
            
            # Add missing closing braces
            missing_braces = truncated.count('{') - truncated.count('}')
            for _ in range(missing_braces):
                truncated += '\n}'
                
            return truncated
    
    return content

def fix_json_file(file_path):
    """Attempt to fix a malformed JSON file"""
    print(f"Attempting to fix: {file_path}")
    
    # Backup original file
    backup_file(file_path)
    
    try:
        with open(file_path, 'r') as f:
            content = f.read()
        
        original_content = content
        
        # Fix unescaped quotes in Java version
        content = fix_java_version_quotes(content)
        
        # Try to parse - if it fails, attempt more aggressive fixes
        try:
            json.loads(content)
            if content != original_content:
                with open(file_path, 'w') as f:
                    f.write(content)
                print(f"✅ Fixed quotes in: {file_path}")
                return True
        except json.JSONDecodeError as e:
            print(f"Still invalid after quote fix: {e}")
            
            # Try to fix incomplete JSON
            content = try_fix_incomplete_json(content)
            
            try:
                json.loads(content)
                with open(file_path, 'w') as f:
                    f.write(content)
                print(f"✅ Fixed incomplete JSON in: {file_path}")
                return True
            except json.JSONDecodeError as e:
                print(f"❌ Could not fix: {file_path} - {e}")
                return False
                
    except Exception as e:
        print(f"❌ Error processing {file_path}: {e}")
        return False

def main():
    """Fix all malformed JSON files in benchmarks/results"""
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    results_dir = project_root / "benchmarks" / "results"
    
    print(f"Scanning for malformed JSON files in: {results_dir}")
    
    # Find malformed macro files
    malformed_files = []
    for pattern in ["macro-*.json", "java-*.json"]:
        for json_file in glob.glob(str(results_dir / pattern)):
            try:
                with open(json_file, 'r') as f:
                    json.load(f)
            except (json.JSONDecodeError, ValueError):
                malformed_files.append(json_file)
    
    print(f"Found {len(malformed_files)} malformed files")
    
    fixed_count = 0
    for file_path in malformed_files:
        if fix_json_file(file_path):
            fixed_count += 1
    
    print(f"\n🎉 Summary: Fixed {fixed_count} out of {len(malformed_files)} files")
    
    # Verify fixes
    print("\nVerifying fixes...")
    still_broken = []
    for file_path in malformed_files:
        try:
            with open(file_path, 'r') as f:
                json.load(f)
        except (json.JSONDecodeError, ValueError):
            still_broken.append(file_path)
    
    if still_broken:
        print(f"⚠️  {len(still_broken)} files still need manual attention:")
        for file_path in still_broken[:5]:  # Show first 5
            print(f"  - {file_path}")
        if len(still_broken) > 5:
            print(f"  ... and {len(still_broken) - 5} more")
    else:
        print("✅ All files are now valid JSON!")

if __name__ == "__main__":
    main()