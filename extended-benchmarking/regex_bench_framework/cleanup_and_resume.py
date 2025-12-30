#!/usr/bin/env python3
"""
Cleanup and resume system for benchmark runs.
Identifies and fixes suspicious results, implements smart timeout logic.
"""

import sqlite3
import sys
import logging
from pathlib import Path
from datetime import datetime
from typing import Dict, List, Tuple, Optional
import argparse

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
logger = logging.getLogger(__name__)


class BenchmarkCleaner:
    """Handles cleanup and resume operations for benchmark runs."""

    def __init__(self, results_dir: Path):
        self.results_dir = Path(results_dir)
        self.db_path = self.results_dir / "jobs.db"

        if not self.db_path.exists():
            raise FileNotFoundError(f"Database not found: {self.db_path}")

        self.conn = sqlite3.connect(self.db_path)
        self.conn.row_factory = sqlite3.Row

    def close(self):
        """Close database connection."""
        if self.conn:
            self.conn.close()

    def identify_suspicious_results(self) -> List[Dict]:
        """
        Identify suspicious results that need to be invalidated.

        Criteria for suspicious results:
        1. Completed in <2s with large datasets (>=1000 patterns)
        2. NULL match_count for completed jobs
        3. Impossibly fast times for large corpus sizes
        """
        query = """
        SELECT job_id, engine_name, pattern_count, input_size,
               duration_seconds, match_count, status, input_size_bytes
        FROM benchmark_jobs
        WHERE status = 'COMPLETED'
          AND duration_seconds IS NOT NULL
          AND pattern_count >= 1000
          AND (
              -- Suspiciously fast for large datasets
              (duration_seconds < 2.0) OR
              -- NULL match count for completed jobs is suspicious
              (match_count IS NULL) OR
              -- Impossibly fast for large corpus (linear extrapolation check)
              (input_size_bytes >= 100000000 AND duration_seconds < 5.0) OR  -- 100MB+ in <5s
              (input_size_bytes >= 1000000000 AND duration_seconds < 30.0)   -- 1GB+ in <30s
          )
        ORDER BY duration_seconds
        """

        cursor = self.conn.execute(query)
        results = [dict(row) for row in cursor.fetchall()]

        logger.info(f"Found {len(results)} suspicious results")
        for result in results[:5]:  # Log first 5 examples
            logger.info(f"  Suspicious: {result['engine_name']} {result['pattern_count']} patterns "
                       f"{result['input_size']} in {result['duration_seconds']:.2f}s")

        return results

    def identify_redundant_timeouts(self) -> List[Dict]:
        """
        Identify redundant timeout jobs that should be skipped.

        If one job with specific (engine, pattern_count, input_size) times out,
        other jobs with same parameters should be skipped rather than timing out again.
        """
        query = """
        SELECT engine_name, pattern_count, input_size, COUNT(*) as timeout_count,
               GROUP_CONCAT(job_id) as job_ids
        FROM benchmark_jobs
        WHERE status = 'TIMEOUT'
        GROUP BY engine_name, pattern_count, input_size
        HAVING COUNT(*) > 1
        ORDER BY timeout_count DESC
        """

        cursor = self.conn.execute(query)
        results = [dict(row) for row in cursor.fetchall()]

        logger.info(f"Found {len(results)} parameter combinations with multiple timeouts")
        for result in results:
            logger.info(f"  {result['engine_name']} {result['pattern_count']} patterns "
                       f"{result['input_size']}: {result['timeout_count']} timeouts")

        return results

    def calculate_reasonable_timeout(self, engine_name: str, pattern_count: int,
                                   input_size: str) -> Optional[float]:
        """
        Calculate reasonable timeout based on linear extrapolation from successful smaller jobs.

        Returns timeout in seconds, or None if no basis for extrapolation.
        """
        # Get successful jobs for same engine and pattern count
        query = """
        SELECT input_size, input_size_bytes, duration_seconds
        FROM benchmark_jobs
        WHERE engine_name = ?
          AND pattern_count = ?
          AND status = 'COMPLETED'
          AND duration_seconds IS NOT NULL
          AND match_count IS NOT NULL
          AND duration_seconds > 0.5  -- Exclude suspicious fast results
        ORDER BY input_size_bytes ASC
        """

        cursor = self.conn.execute(query, (engine_name, pattern_count))
        successful_jobs = [dict(row) for row in cursor.fetchall()]

        if not successful_jobs:
            # No basis for extrapolation, use conservative defaults
            size_defaults = {
                "1MB": 3600,    # 1 hour
                "10MB": 7200,   # 2 hours
                "100MB": 14400, # 4 hours
                "1GB": 28800    # 8 hours
            }
            return size_defaults.get(input_size, 14400)

        # Parse target size
        target_bytes = self._parse_size_to_bytes(input_size)
        if target_bytes is None:
            return 14400  # Default fallback

        # Find best reference point for linear extrapolation
        best_ref = None
        best_ratio = float('inf')

        for job in successful_jobs:
            if job['input_size_bytes'] <= target_bytes:
                ratio = target_bytes / job['input_size_bytes']
                if ratio < best_ratio:
                    best_ratio = ratio
                    best_ref = job

        if best_ref is None:
            # Use smallest successful job as reference
            best_ref = min(successful_jobs, key=lambda x: x['input_size_bytes'])
            best_ratio = target_bytes / best_ref['input_size_bytes']

        # Linear extrapolation with safety margin
        base_duration = best_ref['duration_seconds']
        extrapolated = base_duration * best_ratio
        safety_margin = 2.0  # 2x safety margin

        reasonable_timeout = extrapolated * safety_margin

        # Apply reasonable bounds
        min_timeout = 300   # 5 minutes minimum
        max_timeout = 86400 # 24 hours maximum

        reasonable_timeout = max(min_timeout, min(reasonable_timeout, max_timeout))

        logger.info(f"Calculated timeout for {engine_name} {pattern_count} {input_size}: "
                   f"{reasonable_timeout:.0f}s (based on {best_ref['input_size']} "
                   f"taking {base_duration:.1f}s, ratio={best_ratio:.1f})")

        return reasonable_timeout

    def _parse_size_to_bytes(self, size_str: str) -> Optional[int]:
        """Parse size string like '1MB', '10GB' to bytes."""
        size_str = size_str.upper().strip()

        if size_str.endswith('KB'):
            return int(float(size_str[:-2]) * 1024)
        elif size_str.endswith('MB'):
            return int(float(size_str[:-2]) * 1024 * 1024)
        elif size_str.endswith('GB'):
            return int(float(size_str[:-2]) * 1024 * 1024 * 1024)
        else:
            return None

    def invalidate_suspicious_results(self, dry_run: bool = False) -> int:
        """
        Invalidate suspicious results and re-queue them.

        Returns number of jobs invalidated.
        """
        suspicious = self.identify_suspicious_results()

        if not suspicious:
            logger.info("No suspicious results found")
            return 0

        if dry_run:
            logger.info(f"[DRY RUN] Would invalidate {len(suspicious)} suspicious results")
            return len(suspicious)

        # Mark suspicious results as invalid and re-queue them
        job_ids = [job['job_id'] for job in suspicious]
        placeholders = ','.join('?' * len(job_ids))

        query = f"""
        UPDATE benchmark_jobs
        SET status = 'QUEUED',
            error_message = 'Invalidated due to suspicious result (duration=' ||
                          COALESCE(duration_seconds, 'NULL') || 's, match_count=' ||
                          COALESCE(match_count, 'NULL') || ') - re-queued for proper execution',
            completed_at = NULL,
            duration_seconds = NULL,
            compilation_ns = NULL,
            scanning_ns = NULL,
            total_ns = NULL,
            match_count = NULL,
            patterns_compiled = NULL,
            memory_peak_bytes = NULL,
            memory_compilation_bytes = NULL,
            cpu_user_ms = NULL,
            cpu_system_ms = NULL,
            throughput_mbps = NULL,
            matches_per_second = NULL,
            result_json = NULL,
            raw_stdout = '',
            raw_stderr = ''
        WHERE job_id IN ({placeholders})
        """

        self.conn.execute(query, job_ids)
        self.conn.commit()

        logger.info(f"Invalidated {len(suspicious)} suspicious results and re-queued them")
        return len(suspicious)

    def skip_redundant_timeouts(self, dry_run: bool = False) -> int:
        """
        Skip redundant timeout jobs by marking them as SKIPPED_TIMEOUT_REDUNDANT.

        For each (engine, pattern_count, input_size) combination that has timeouts,
        ensure at least one job remains active (QUEUED or convert one timeout to QUEUED).

        Returns number of jobs skipped.
        """
        redundant_groups = self.identify_redundant_timeouts()
        skipped_count = 0

        for group in redundant_groups:
            engine_name = group['engine_name']
            pattern_count = group['pattern_count']
            input_size = group['input_size']

            # Check if this parameter combination has any active jobs (QUEUED or COMPLETED)
            active_check = self.conn.execute("""
                SELECT COUNT(*) as active_count
                FROM benchmark_jobs
                WHERE engine_name = ? AND pattern_count = ? AND input_size = ?
                  AND status IN ('QUEUED', 'COMPLETED')
            """, (engine_name, pattern_count, input_size)).fetchone()

            has_active_jobs = active_check['active_count'] > 0
            job_ids = group['job_ids'].split(',')

            if not has_active_jobs:
                # Convert first timeout to QUEUED, skip the rest
                job_to_queue = job_ids[0]
                jobs_to_skip = job_ids[1:]

                if dry_run:
                    logger.info(f"[DRY RUN] Would convert 1 timeout to QUEUED and skip {len(jobs_to_skip)} "
                               f"for {engine_name} {pattern_count} {input_size}")
                    skipped_count += len(jobs_to_skip)
                    continue

                # Convert first timeout to QUEUED for proper execution
                self.conn.execute("""
                    UPDATE benchmark_jobs
                    SET status = 'QUEUED',
                        completed_at = NULL,
                        duration_seconds = NULL,
                        error_message = 'Converted from timeout to queued - ensuring unique parameter coverage'
                    WHERE job_id = ? AND status = 'TIMEOUT'
                """, (job_to_queue,))

                logger.info(f"Converted 1 timeout to QUEUED for {engine_name} {pattern_count} {input_size}")
            else:
                # Has active jobs, can skip all timeouts except first
                jobs_to_skip = job_ids[1:]

            if jobs_to_skip:
                if dry_run:
                    logger.info(f"[DRY RUN] Would skip {len(jobs_to_skip)} redundant timeouts for "
                               f"{engine_name} {pattern_count} {input_size}")
                    skipped_count += len(jobs_to_skip)
                    continue

                placeholders = ','.join('?' * len(jobs_to_skip))
                query = f"""
                UPDATE benchmark_jobs
                SET status = 'SKIPPED_TIMEOUT_REDUNDANT',
                    completed_at = CURRENT_TIMESTAMP,
                    error_message = 'Skipped - redundant timeout (other job with same parameters already processed)'
                WHERE job_id IN ({placeholders}) AND status = 'TIMEOUT'
                """

                result = self.conn.execute(query, jobs_to_skip)
                updated = result.rowcount
                skipped_count += updated

                logger.info(f"Skipped {updated} redundant timeouts for "
                           f"{engine_name} {pattern_count} {input_size}")

        if not dry_run and skipped_count > 0:
            self.conn.commit()
            logger.info(f"Total skipped redundant timeouts: {skipped_count}")

        return skipped_count

    def update_job_timeouts(self, dry_run: bool = False) -> int:
        """
        Update timeout values for queued jobs based on reasonable extrapolations.

        Returns number of jobs updated.
        """
        # Get queued jobs that might need timeout updates
        query = """
        SELECT DISTINCT engine_name, pattern_count, input_size, timeout_seconds
        FROM benchmark_jobs
        WHERE status IN ('QUEUED', 'RUNNING')
        ORDER BY engine_name, pattern_count, input_size
        """

        cursor = self.conn.execute(query)
        job_configs = [dict(row) for row in cursor.fetchall()]

        updated_count = 0

        for config in job_configs:
            reasonable_timeout = self.calculate_reasonable_timeout(
                config['engine_name'],
                config['pattern_count'],
                config['input_size']
            )

            if reasonable_timeout and reasonable_timeout != config['timeout_seconds']:
                if dry_run:
                    logger.info(f"[DRY RUN] Would update timeout for "
                               f"{config['engine_name']} {config['pattern_count']} {config['input_size']} "
                               f"from {config['timeout_seconds']}s to {reasonable_timeout:.0f}s")
                    updated_count += 1
                    continue

                # Update timeout for all matching jobs
                update_query = """
                UPDATE benchmark_jobs
                SET timeout_seconds = ?
                WHERE engine_name = ?
                  AND pattern_count = ?
                  AND input_size = ?
                  AND status IN ('QUEUED', 'RUNNING')
                """

                result = self.conn.execute(update_query, (
                    reasonable_timeout,
                    config['engine_name'],
                    config['pattern_count'],
                    config['input_size']
                ))

                jobs_updated = result.rowcount
                if jobs_updated > 0:
                    updated_count += jobs_updated
                    logger.info(f"Updated timeout for {jobs_updated} jobs: "
                               f"{config['engine_name']} {config['pattern_count']} {config['input_size']} "
                               f"-> {reasonable_timeout:.0f}s")

        if not dry_run and updated_count > 0:
            self.conn.commit()
            logger.info(f"Total timeout updates: {updated_count}")

        return updated_count

    def generate_resume_report(self) -> Dict:
        """Generate a summary report of the cleanup and resume status."""
        # Count jobs by status
        status_query = """
        SELECT status, COUNT(*) as count
        FROM benchmark_jobs
        GROUP BY status
        ORDER BY count DESC
        """

        cursor = self.conn.execute(status_query)
        status_counts = {row['status']: row['count'] for row in cursor.fetchall()}

        # Get run info
        run_query = """
        SELECT run_id, status, created_at
        FROM benchmark_runs
        ORDER BY created_at DESC
        LIMIT 1
        """

        cursor = self.conn.execute(run_query)
        row = cursor.fetchone()
        run_info = dict(row) if row else {}

        # Calculate progress
        total_jobs = sum(status_counts.values())
        completed_jobs = status_counts.get('COMPLETED', 0)
        queued_jobs = status_counts.get('QUEUED', 0)

        progress_percent = (completed_jobs / total_jobs * 100) if total_jobs > 0 else 0

        report = {
            'timestamp': datetime.now().isoformat(),
            'database_path': str(self.db_path),
            'run_info': run_info,
            'status_counts': status_counts,
            'progress': {
                'total_jobs': total_jobs,
                'completed': completed_jobs,
                'queued': queued_jobs,
                'progress_percent': progress_percent
            }
        }

        return report


def main():
    parser = argparse.ArgumentParser(description='Cleanup and resume benchmark runs')
    parser.add_argument('results_dir', help='Path to results directory')
    parser.add_argument('--dry-run', action='store_true',
                       help='Show what would be done without making changes')
    parser.add_argument('--invalidate-suspicious', action='store_true',
                       help='Invalidate suspicious results and re-queue them')
    parser.add_argument('--skip-redundant-timeouts', action='store_true',
                       help='Skip redundant timeout jobs')
    parser.add_argument('--update-timeouts', action='store_true',
                       help='Update job timeouts based on reasonable extrapolations')
    parser.add_argument('--all', action='store_true',
                       help='Perform all cleanup operations')
    parser.add_argument('--report', action='store_true',
                       help='Generate status report')

    args = parser.parse_args()

    try:
        cleaner = BenchmarkCleaner(args.results_dir)

        if args.all or args.report:
            report = cleaner.generate_resume_report()
            print(f"\n=== Benchmark Status Report ===")
            print(f"Timestamp: {report['timestamp']}")
            print(f"Database: {report['database_path']}")
            print(f"Total jobs: {report['progress']['total_jobs']}")
            print(f"Completed: {report['progress']['completed']}")
            print(f"Queued: {report['progress']['queued']}")
            print(f"Progress: {report['progress']['progress_percent']:.1f}%")
            print(f"\nStatus breakdown:")
            for status, count in report['status_counts'].items():
                print(f"  {status}: {count}")
            print()

        operations_performed = 0

        if args.all or args.invalidate_suspicious:
            print("=== Invalidating Suspicious Results ===")
            count = cleaner.invalidate_suspicious_results(dry_run=args.dry_run)
            print(f"{'[DRY RUN] ' if args.dry_run else ''}Invalidated {count} suspicious results\n")
            operations_performed += 1

        if args.all or args.skip_redundant_timeouts:
            print("=== Skipping Redundant Timeouts ===")
            count = cleaner.skip_redundant_timeouts(dry_run=args.dry_run)
            print(f"{'[DRY RUN] ' if args.dry_run else ''}Skipped {count} redundant timeouts\n")
            operations_performed += 1

        if args.all or args.update_timeouts:
            print("=== Updating Job Timeouts ===")
            count = cleaner.update_job_timeouts(dry_run=args.dry_run)
            print(f"{'[DRY RUN] ' if args.dry_run else ''}Updated {count} job timeouts\n")
            operations_performed += 1

        if operations_performed == 0 and not args.report:
            print("No operations specified. Use --help for available options.")
            print("Common usage: --all --dry-run (to see what would be done)")

    except Exception as e:
        logger.error(f"Error: {e}")
        sys.exit(1)
    finally:
        if 'cleaner' in locals():
            cleaner.close()


if __name__ == '__main__':
    main()