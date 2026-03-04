#!/usr/bin/env python3
"""
Recovery utility for restoring benchmark results from transaction logs.
"""

import argparse
import sqlite3
from pathlib import Path
from regex_bench.logging.transaction_log import LogRecoveryManager, TransactionLogger

def main():
    parser = argparse.ArgumentParser(description="Recover benchmark results from transaction logs")
    subparsers = parser.add_subparsers(dest="command", help="Commands")

    # Show log stats
    stats_parser = subparsers.add_parser("stats", help="Show transaction log statistics")
    stats_parser.add_argument("log_path", help="Path to transaction log file")

    # List completed jobs
    list_parser = subparsers.add_parser("list", help="List completed jobs in transaction log")
    list_parser.add_argument("log_path", help="Path to transaction log file")
    list_parser.add_argument("--failures", action="store_true", help="Show failed jobs instead")

    # Recover to database
    recover_parser = subparsers.add_parser("recover", help="Recover results from log to database")
    recover_parser.add_argument("log_path", help="Path to transaction log file")
    recover_parser.add_argument("db_path", help="Path to database file")
    recover_parser.add_argument("--dry-run", action="store_true", help="Show what would be recovered")

    # Find logs for a run
    find_parser = subparsers.add_parser("find", help="Find transaction log for a run ID")
    find_parser.add_argument("run_id", help="Run ID to find logs for")
    find_parser.add_argument("--results-dir", default="results", help="Results directory to search")

    args = parser.parse_args()

    if args.command == "stats":
        show_log_stats(args.log_path)
    elif args.command == "list":
        list_jobs(args.log_path, failures=args.failures)
    elif args.command == "recover":
        recover_to_database(args.log_path, args.db_path, args.dry_run)
    elif args.command == "find":
        find_logs_for_run(args.run_id, args.results_dir)
    else:
        parser.print_help()

def show_log_stats(log_path):
    """Show statistics about a transaction log."""
    logger = TransactionLogger.__new__(TransactionLogger)
    logger.log_path = Path(log_path)

    stats = logger.get_log_stats()

    if not stats["exists"]:
        print(f"Log file does not exist: {log_path}")
        return

    print(f"Transaction Log: {stats['log_path']}")
    print(f"Created: {stats['created_at']}")
    print(f"File size: {stats['file_size_bytes']:,} bytes")
    print(f"Total entries: {stats['total_entries']:,}")
    print("\nEvent breakdown:")

    for event_type, count in stats["event_counts"].items():
        print(f"  {event_type}: {count:,}")

def list_jobs(log_path, failures=False):
    """List jobs from transaction log."""
    logger = TransactionLogger.__new__(TransactionLogger)
    logger.log_path = Path(log_path)

    if failures:
        jobs = logger.get_failed_jobs()
        print("Failed Jobs:")
    else:
        jobs = logger.get_completed_jobs()
        print("Completed Jobs:")

    for entry in jobs:
        job = entry['job']
        timestamp = entry['timestamp']
        engine = job['engine_name']
        patterns = job['pattern_count']
        corpus = job['input_size']

        if failures:
            error = entry.get('error', {})
            status = error.get('status', 'UNKNOWN')
            print(f"  {timestamp} | {engine} | {patterns} patterns | {corpus} | {status}")
        else:
            result = entry.get('result', {})
            duration = result.get('duration_seconds', 0)
            print(f"  {timestamp} | {engine} | {patterns} patterns | {corpus} | {duration:.1f}s")

def recover_to_database(log_path, db_path, dry_run):
    """Recover jobs from transaction log to database."""
    if not Path(log_path).exists():
        print(f"Error: Log file not found: {log_path}")
        return

    if not Path(db_path).exists():
        print(f"Error: Database file not found: {db_path}")
        return

    recovery_manager = LogRecoveryManager(log_path)

    # Connect to database
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    try:
        stats = recovery_manager.recover_to_database(conn, dry_run=dry_run)

        print(f"Recovery Results:")
        print(f"  Log entries processed: {stats['total_log_entries']}")
        print(f"  Jobs already in DB: {stats['jobs_already_in_db']}")
        print(f"  Jobs to recover: {stats['jobs_to_recover']}")

        if dry_run:
            print("  (Dry run - no changes made)")
        else:
            print(f"  Jobs successfully recovered: {stats['jobs_recovered']}")

        if stats['errors']:
            print(f"  Errors: {len(stats['errors'])}")
            for error in stats['errors']:
                print(f"    {error}")

    finally:
        conn.close()

def find_logs_for_run(run_id, results_dir):
    """Find transaction logs for a specific run ID."""
    results_path = Path(results_dir)

    if not results_path.exists():
        print(f"Results directory not found: {results_dir}")
        return

    # Search for log files
    log_files = []
    for log_file in results_path.rglob("transaction_log_*.jsonl"):
        if run_id in log_file.name:
            log_files.append(log_file)

    if not log_files:
        print(f"No transaction logs found for run ID: {run_id}")
        return

    print(f"Transaction logs found for run {run_id}:")
    for log_file in log_files:
        print(f"  {log_file}")

        # Show basic stats
        logger = TransactionLogger.__new__(TransactionLogger)
        logger.log_path = log_file
        stats = logger.get_log_stats()

        if stats["exists"]:
            completed = stats["event_counts"].get("job_completed", 0)
            failed = stats["event_counts"].get("job_failed", 0)
            print(f"    Completed jobs: {completed}, Failed jobs: {failed}")

if __name__ == "__main__":
    main()