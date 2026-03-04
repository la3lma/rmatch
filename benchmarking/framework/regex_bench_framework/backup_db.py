#!/usr/bin/env python3
"""
Database backup utility for regex benchmark framework.
Creates timestamped backups of database files and provides restore functionality.
"""

import os
import sys
import shutil
import datetime
import argparse
import sqlite3
from pathlib import Path

def get_timestamp():
    """Get timestamp string for backup naming."""
    return datetime.datetime.now().strftime("%Y%m%d_%H%M%S")

def find_db_files(results_dir="results"):
    """Find all .db files in results directories."""
    db_files = []
    results_path = Path(results_dir)

    if results_path.exists():
        for db_file in results_path.rglob("*.db"):
            db_files.append(db_file)

    return db_files

def create_backup(backup_name=None, results_dir="results", backup_dir="backups"):
    """Create a backup of all database files."""
    timestamp = get_timestamp()
    if backup_name:
        backup_name = f"{backup_name}_{timestamp}"
    else:
        backup_name = f"backup_{timestamp}"

    backup_path = Path(backup_dir) / backup_name
    backup_path.mkdir(parents=True, exist_ok=True)

    # Find all database files
    db_files = find_db_files(results_dir)

    if not db_files:
        print(f"No database files found in {results_dir}")
        return None

    backed_up_files = []

    for db_file in db_files:
        # Create relative path structure in backup
        rel_path = db_file.relative_to(results_dir)
        backup_file_path = backup_path / rel_path
        backup_file_path.parent.mkdir(parents=True, exist_ok=True)

        # Copy the database file
        shutil.copy2(db_file, backup_file_path)
        backed_up_files.append(str(rel_path))

        print(f"Backed up: {db_file} -> {backup_file_path}")

    # Create backup manifest
    manifest_path = backup_path / "backup_manifest.txt"
    with open(manifest_path, 'w') as f:
        f.write(f"Backup created: {datetime.datetime.now().isoformat()}\n")
        f.write(f"Source directory: {results_dir}\n")
        f.write(f"Files backed up:\n")
        for file_path in backed_up_files:
            f.write(f"  {file_path}\n")

    print(f"\nBackup completed: {backup_path}")
    print(f"Backed up {len(backed_up_files)} database files")
    return backup_path

def list_backups(backup_dir="backups"):
    """List available backups."""
    backup_path = Path(backup_dir)

    if not backup_path.exists():
        print(f"No backup directory found: {backup_dir}")
        return []

    backups = []
    for item in backup_path.iterdir():
        if item.is_dir() and (item / "backup_manifest.txt").exists():
            manifest_path = item / "backup_manifest.txt"
            try:
                with open(manifest_path) as f:
                    first_line = f.readline().strip()
                    timestamp = first_line.split(": ")[1] if ": " in first_line else "Unknown"
                backups.append((item.name, timestamp, item))
            except Exception as e:
                print(f"Warning: Could not read manifest for {item.name}: {e}")
                backups.append((item.name, "Unknown", item))

    backups.sort(key=lambda x: x[0], reverse=True)

    print("Available backups:")
    for backup_name, timestamp, backup_path in backups:
        print(f"  {backup_name} (created: {timestamp})")

    return backups

def restore_backup(backup_name, results_dir="results", backup_dir="backups", dry_run=False):
    """Restore a backup."""
    backup_path = Path(backup_dir) / backup_name

    if not backup_path.exists():
        print(f"Backup not found: {backup_path}")
        return False

    manifest_path = backup_path / "backup_manifest.txt"
    if not manifest_path.exists():
        print(f"Invalid backup: missing manifest file")
        return False

    # Read manifest
    with open(manifest_path) as f:
        manifest_content = f.read()
        print(f"Backup manifest:\n{manifest_content}")

    if dry_run:
        print("\nDry run - would restore the following files:")
        for db_file in backup_path.rglob("*.db"):
            rel_path = db_file.relative_to(backup_path)
            target_path = Path(results_dir) / rel_path
            print(f"  {db_file} -> {target_path}")
        return True

    # Confirm restoration
    response = input("\nAre you sure you want to restore this backup? This will OVERWRITE existing databases (y/N): ")
    if response.lower() != 'y':
        print("Restore cancelled")
        return False

    # Restore files
    restored_files = []
    for db_file in backup_path.rglob("*.db"):
        rel_path = db_file.relative_to(backup_path)
        target_path = Path(results_dir) / rel_path
        target_path.parent.mkdir(parents=True, exist_ok=True)

        shutil.copy2(db_file, target_path)
        restored_files.append(str(target_path))
        print(f"Restored: {db_file} -> {target_path}")

    print(f"\nRestore completed: {len(restored_files)} files restored")
    return True

def get_db_info(db_path):
    """Get basic information about a database."""
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()

        # Get table list
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
        tables = [row[0] for row in cursor.fetchall()]

        info = {"tables": tables, "table_counts": {}}

        # Get row counts for each table
        for table in tables:
            try:
                cursor.execute(f"SELECT COUNT(*) FROM {table}")
                count = cursor.fetchone()[0]
                info["table_counts"][table] = count
            except Exception as e:
                info["table_counts"][table] = f"Error: {e}"

        conn.close()
        return info
    except Exception as e:
        return {"error": str(e)}

def main():
    parser = argparse.ArgumentParser(description="Database backup utility")
    subparsers = parser.add_subparsers(dest="command", help="Commands")

    # Create backup
    backup_parser = subparsers.add_parser("create", help="Create a backup")
    backup_parser.add_argument("--name", help="Backup name (timestamp will be added)")
    backup_parser.add_argument("--results-dir", default="results", help="Results directory")
    backup_parser.add_argument("--backup-dir", default="backups", help="Backup directory")

    # List backups
    list_parser = subparsers.add_parser("list", help="List available backups")
    list_parser.add_argument("--backup-dir", default="backups", help="Backup directory")

    # Restore backup
    restore_parser = subparsers.add_parser("restore", help="Restore a backup")
    restore_parser.add_argument("backup_name", help="Backup name to restore")
    restore_parser.add_argument("--results-dir", default="results", help="Results directory")
    restore_parser.add_argument("--backup-dir", default="backups", help="Backup directory")
    restore_parser.add_argument("--dry-run", action="store_true", help="Show what would be restored without actually doing it")

    # Info command
    info_parser = subparsers.add_parser("info", help="Show database information")
    info_parser.add_argument("db_path", help="Path to database file")

    args = parser.parse_args()

    if args.command == "create":
        create_backup(args.name, args.results_dir, args.backup_dir)
    elif args.command == "list":
        list_backups(args.backup_dir)
    elif args.command == "restore":
        restore_backup(args.backup_name, args.results_dir, args.backup_dir, args.dry_run)
    elif args.command == "info":
        info = get_db_info(args.db_path)
        if "error" in info:
            print(f"Error reading database: {info['error']}")
        else:
            print(f"Database: {args.db_path}")
            print(f"Tables: {', '.join(info['tables'])}")
            for table, count in info["table_counts"].items():
                print(f"  {table}: {count} rows")
    else:
        parser.print_help()

if __name__ == "__main__":
    main()