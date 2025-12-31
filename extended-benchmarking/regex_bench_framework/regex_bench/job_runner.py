"""
Job-based benchmark runner that adds resilient job queue on top of existing runner.
Preserves all existing execution logic while adding crash recovery and progress tracking.
"""

import json
import hashlib
import logging
import os
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Any, Optional

from .runner import BenchmarkRunner
from .engines import EngineResult
from .database import JobQueue, BenchmarkJob, SystemProfiler
from .utils import GitMetadata

logger = logging.getLogger(__name__)


class JobBasedBenchmarkRunner(BenchmarkRunner):
    """
    Enhanced BenchmarkRunner with persistent job queue for crash recovery.

    Preserves all existing execution logic while adding:
    - Persistent job queue with SQLite storage
    - Crash recovery and resume capability
    - System profiling for hardware tracking
    - Progress visibility during execution
    """

    def __init__(self, config: Dict[str, Any], output_dir: Path,
                 parallel_engines: int = 1, db_path: Optional[Path] = None):
        """Initialize job-based runner."""
        # Force sequential execution for fair benchmarking - always use 1 worker
        parallel_engines = 1
        # Initialize parent with existing logic
        super().__init__(config, output_dir, parallel_engines)

        # Database and job queue setup
        self.db_path = db_path or self.output_dir / "jobs.db"
        self.job_queue = JobQueue(self.db_path, str(self.output_dir), self.run_id)

        # System profiling
        self.system_profiler = SystemProfiler()
        self.system_profile_id = None

        logger.info(f"Job-based runner initialized with database: {self.db_path}")

    def run_with_jobs(self) -> str:
        """
        Main entry point for job-based execution.
        Returns the run_id for tracking.
        """
        logger.info(f"Starting job-based benchmark run")

        # Get or create system profile
        self.system_profile_id = self._get_or_create_system_profile()

        # Create or resume benchmark run
        run_id = self._get_or_create_run()

        try:
            # Check if this is a resume or fresh start
            job_count = self.job_queue.get_job_count(run_id)

            if job_count == 0:
                logger.info("Starting fresh benchmark run")
                self._setup_fresh_run(run_id)
            else:
                logger.info(f"Resuming benchmark run with {job_count} jobs")
                self._resume_existing_run(run_id)

            # Execute job queue
            self._execute_job_queue(run_id)

            # Analysis and results (preserve existing logic)
            self._finalize_run(run_id)

            logger.info(f"Job-based benchmark completed: {run_id}")
            return run_id

        except Exception as e:
            self._mark_run_failed(run_id, str(e))
            logger.error(f"Job-based benchmark failed: {e}")
            raise

    def _get_or_create_system_profile(self) -> str:
        """Get existing system profile or create new one."""
        # Collect system profile
        profile_data = self.system_profiler.collect_profile()
        profile_id = profile_data['profile_id']

        # Check if profile already exists
        cursor = self.job_queue.conn.execute(
            "SELECT profile_id FROM system_profiles WHERE profile_id = ?",
            (profile_id,)
        )

        existing_profile = cursor.fetchone()

        if existing_profile:
            logger.info(f"Using existing system profile: {profile_id[:8]}...")
        else:
            # Store new system profile
            self._store_system_profile(profile_data)
            logger.info(f"Created new system profile: {profile_id[:8]}...")
            logger.info(f"System: {profile_data['cpu_model']} | "
                       f"{profile_data['cpu_logical_cores']} cores | "
                       f"{profile_data['memory_total_gb']:.1f}GB RAM")

        return profile_id

    def _store_system_profile(self, profile_data: Dict) -> None:
        """Store system profile in database."""
        self.job_queue.conn.execute("""
            INSERT INTO system_profiles (
                profile_id, hostname, cpu_model, cpu_architecture,
                cpu_physical_cores, cpu_logical_cores, memory_total_gb,
                memory_available_gb, storage_available_gb, os_name,
                os_version, python_version, python_implementation,
                python_compiler, key_dependencies_json, env_variables_json,
                benchmark_baseline_score, is_virtualized, virtualization_type,
                profiled_at, profiled_by, notes, raw_system_info_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            profile_data['profile_id'], profile_data['hostname'],
            profile_data['cpu_model'], profile_data['cpu_architecture'],
            profile_data['cpu_physical_cores'], profile_data['cpu_logical_cores'],
            profile_data['memory_total_gb'], profile_data['memory_available_gb'],
            profile_data.get('storage_available_gb'), profile_data['os_name'],
            profile_data['os_version'], profile_data['python_version'],
            profile_data['python_implementation'], profile_data['python_compiler'],
            profile_data.get('key_dependencies_json'),
            profile_data.get('env_variables_json'),
            profile_data.get('benchmark_baseline_score'),
            profile_data.get('is_virtualized'), profile_data.get('virtualization_type'),
            profile_data['profiled_at'], profile_data['profiled_by'],
            profile_data['notes'], profile_data['raw_system_info_json']
        ))
        self.job_queue.conn.commit()

    def _get_or_create_run(self) -> str:
        """Get existing incomplete run or create new one."""
        # Check for incomplete runs with same config
        config_hash = self._compute_config_hash()

        cursor = self.job_queue.conn.execute("""
            SELECT run_id FROM benchmark_runs
            WHERE config_hash = ? AND status IN ('PREPARING', 'RUNNING')
            ORDER BY created_at DESC
            LIMIT 1
        """, (config_hash,))

        existing_run = cursor.fetchone()

        if existing_run:
            run_id = existing_run['run_id']
            logger.info(f"Found existing incomplete run: {run_id}")
            return run_id
        else:
            # Create new run
            return self._create_new_run()

    def _create_new_run(self) -> str:
        """Create a new benchmark run."""
        run_id = self.run_id  # Use existing run_id from parent class

        # Get git metadata
        git_metadata = self._get_git_metadata()

        self.job_queue.conn.execute("""
            INSERT INTO benchmark_runs (
                run_id, config_path, config_json, system_profile_id,
                git_commit_sha, git_branch, git_is_dirty,
                status, created_by, config_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            run_id,
            str(self.config.get('config_path', 'unknown')),
            json.dumps(self.config, indent=2),
            self.system_profile_id,
            git_metadata.get('commit_sha'),
            git_metadata.get('branch'),
            git_metadata.get('is_dirty', False),
            'PREPARING',
            os.getenv('USER', 'unknown'),
            self._compute_config_hash()
        ))
        self.job_queue.conn.commit()

        logger.info(f"Created new benchmark run: {run_id}")
        return run_id

    def _setup_fresh_run(self, run_id: str) -> None:
        """Setup fresh benchmark run with job creation."""
        logger.info("Setting up fresh benchmark run...")

        # PRESERVE EXISTING SETUP LOGIC
        self._setup_output_directory()
        engines = self._discover_and_validate_engines()
        test_combinations = self._generate_test_matrix()

        # Use git-stored patterns instead of generating new ones
        self._prepare_git_stored_patterns()

        # Still need corpus generation (this usually can't be pre-stored)
        self._prepare_corpus_files()

        # Create jobs from test combinations
        self._create_jobs_from_combinations(run_id, test_combinations)

        # Update run status
        self.job_queue.conn.execute(
            "UPDATE benchmark_runs SET status = 'RUNNING', started_at = ? WHERE run_id = ?",
            (datetime.now(), run_id)
        )
        self.job_queue.conn.commit()

    def _resume_existing_run(self, run_id: str) -> None:
        """Resume existing benchmark run."""
        logger.info("Resuming existing benchmark run...")

        # PRESERVE EXISTING SETUP LOGIC (but skip job creation)
        self._setup_output_directory()
        self._discover_and_validate_engines()

        # Use git-stored patterns instead of generating new ones
        self._prepare_git_stored_patterns()

        # Still need corpus generation (this usually can't be pre-stored)
        self._prepare_corpus_files()

        # Update run status
        self.job_queue.conn.execute(
            "UPDATE benchmark_runs SET status = 'RUNNING' WHERE run_id = ?",
            (run_id,)
        )
        self.job_queue.conn.commit()

    def _create_jobs_from_combinations(self, run_id: str, combinations: List[Dict]) -> None:
        """Create database jobs from test matrix combinations."""
        logger.info(f"Creating {len(combinations)} jobs...")

        config_hash = self._compute_config_hash()

        for combo in combinations:
            # Convert combination to job
            job = BenchmarkJob.create_new(
                run_id=run_id,
                engine_name=combo['engine'],
                pattern_count=combo['pattern_count'],
                input_size=combo['input_size'],
                input_size_bytes=self._get_input_size_bytes(combo['input_size']),
                iteration=combo['iteration'],
                pattern_suite=combo.get('pattern_suite', 'default'),
                corpus_name=combo.get('corpus', 'default'),
                config_hash=config_hash
            )

            self.job_queue.enqueue_job(job)

        logger.info(f"Created {len(combinations)} jobs for execution")

    def _execute_job_queue(self, run_id: str) -> None:
        """Execute jobs from queue with controlled parallelism."""
        logger.info("Starting job queue execution...")

        with ThreadPoolExecutor(max_workers=self.parallel_engines) as executor:
            futures = {}

            while True:
                # Fill up to max_workers with jobs
                while len(futures) < self.parallel_engines:
                    job = self.job_queue.get_next_job(run_id)
                    if not job:
                        break  # No more jobs

                    # Check if job should be skipped based on engine-specific rules
                    if self._should_skip_job(job):
                        self._mark_job_as_skipped(job, run_id)
                        continue  # Get next job instead of executing this one

                    # Pass db_path to worker so it can create its own connection
                    future = executor.submit(self._execute_job_with_tracking, job, self.db_path)
                    futures[future] = job

                if not futures:
                    break  # All jobs completed

                # Calculate appropriate timeout based on largest input size
                max_timeout = self._calculate_job_timeout()

                # Wait for completed jobs with appropriate timeout
                try:
                    done_futures = list(as_completed(futures.keys(), timeout=max_timeout))

                    for future in done_futures:
                        job = futures.pop(future)

                        try:
                            result = future.result()
                            self.results.append(result)  # PRESERVE existing results collection

                            # Log progress (preserve existing format) - use main thread connection
                            progress = self.job_queue.get_run_progress(run_id)
                            completed = progress['COMPLETED']
                            total = progress['total']

                            logger.info(f"[{completed}/{total}] Completed: {job.engine_name} "
                                       f"({job.pattern_count} patterns, {job.input_size}, "
                                       f"iter {job.iteration})")

                        except Exception as e:
                            logger.error(f"Job {job.job_id} failed: {e}")

                except Exception as timeout_error:
                    # Handle timeout or other issues
                    remaining_count = len(futures)
                    logger.warning(f"Timeout waiting for jobs after {max_timeout}s: {timeout_error}")

                    if remaining_count > 0:
                        logger.error(f"{remaining_count} (of {remaining_count}) futures unfinished after {max_timeout}s timeout")

                        # Log which jobs were hanging
                        for future, job in futures.items():
                            logger.error(f"   Hanging job: {job.engine_name} - {job.pattern_count} patterns, {job.input_size}, iter {job.iteration}")

                        # Cancel remaining futures
                        for future in futures.keys():
                            future.cancel()
                        break

    def _execute_job_with_tracking(self, job: BenchmarkJob, db_path: Path) -> EngineResult:
        """Execute single job with status tracking - wraps existing logic."""
        # Create thread-local database connection to avoid SQLite threading issues
        thread_job_queue = JobQueue(db_path, str(self.output_dir), job.run_id)

        try:
            # Get engine instance with better error handling
            try:
                engine = self._get_engine_by_name(job.engine_name)
            except ValueError as engine_error:
                # Engine not available - mark as skipped
                logger.warning(f"Engine {job.engine_name} not available: {engine_error}")
                error_result = EngineResult(
                    engine_name=job.engine_name,
                    iteration=job.iteration,
                    status="skipped",
                    notes=f"Engine not available: {str(engine_error)}"
                )
                thread_job_queue.update_job_status(job.job_id, 'FAILED', error_result, str(engine_error))
                return error_result

            # PRESERVE EXISTING EXECUTION LOGIC - reconstruct combo for compatibility
            combination = {
                'engine': job.engine_name,
                'pattern_count': job.pattern_count,
                'input_size': job.input_size,
                'iteration': job.iteration,
                'pattern_suite': job.pattern_suite,
                'corpus': job.corpus_name
            }

            # Call existing method unchanged!
            result = self._execute_single_benchmark(engine, combination)

            # Map EngineResult status to database status for consistency
            engine_to_db_status = {
                "ok": "COMPLETED",
                "timeout": "TIMEOUT",
                "error": "FAILED",
                "skipped": "FAILED"
            }
            db_status = engine_to_db_status.get(result.status, "FAILED")

            # Enhanced validation for specific engines: java-native-unfair, rmatch, re2j
            # These engines are only considered completed if they terminate correctly AND report match counts
            if (db_status == "COMPLETED" and
                job.engine_name in ['java-native-unfair', 'rmatch', 're2j']):

                # Validate proper termination and match count reporting
                if (result.status != "ok" or
                    result.match_count is None or
                    not isinstance(result.match_count, int) or
                    result.match_count < 0):

                    logger.warning(f"Engine {job.engine_name} failed validation: "
                                 f"status={result.status}, match_count={result.match_count}")
                    db_status = "FAILED"

            # Update job with result using correct mapped status
            thread_job_queue.update_job_status(job.job_id, db_status, result)

            # Apply adaptive iteration logic for successful first iterations
            if db_status == 'COMPLETED' and job.iteration == 0:
                # Get configuration values
                optimization = self.config.get('optimization', {})
                adaptive_enabled = optimization.get('enable_adaptive_iterations', True)
                duration_threshold = optimization.get('adaptive_iteration_threshold_seconds', 100.0)

                if adaptive_enabled:
                    # Reload job to get updated duration
                    updated_job = thread_job_queue.get_job_by_id(job.job_id)
                    if updated_job:
                        skipped_count = thread_job_queue.apply_adaptive_iteration_logic(
                            updated_job, duration_threshold
                        )
                        if skipped_count > 0:
                            logger.info(f"Applied adaptive iteration logic: skipped {skipped_count} iterations "
                                       f"for {job.engine_name} (first iteration: {updated_job.duration_seconds:.1f}s)")

            return result

        except Exception as e:
            logger.error(f"Job {job.job_id} execution failed: {e}")
            # Create error result
            error_result = EngineResult(
                engine_name=job.engine_name,
                iteration=job.iteration,
                status="error",
                notes=f"Job execution failed: {str(e)}"
            )

            thread_job_queue.update_job_status(job.job_id, 'FAILED', error_result, str(e))
            return error_result

        finally:
            # Clean up thread-local connection
            thread_job_queue.close()

    def _finalize_run(self, run_id: str) -> None:
        """Finalize benchmark run with analysis."""
        logger.info("Finalizing benchmark run...")

        # PRESERVE EXISTING ANALYSIS LOGIC
        results = self._analyze_results()
        self._save_results(results)

        # Determine final status based on parameter coverage
        final_status, coverage_summary = self._determine_final_status(run_id)

        logger.info(f"Run finalized with status: {final_status}")
        logger.info(f"Parameter coverage: {coverage_summary}")

        # Update run completion with appropriate status
        self.job_queue.conn.execute("""
            UPDATE benchmark_runs
            SET status = ?, completed_at = ?, duration_seconds = ?, notes = ?
            WHERE run_id = ?
        """, (final_status, datetime.now(), time.time() - self.start_time, coverage_summary, run_id))
        self.job_queue.conn.commit()

    def _mark_run_failed(self, run_id: str, error_msg: str) -> None:
        """Mark run as failed with error message."""
        self.job_queue.conn.execute("""
            UPDATE benchmark_runs
            SET status = 'FAILED', completed_at = ?, notes = ?
            WHERE run_id = ?
        """, (datetime.now(), f"Failed: {error_msg}", run_id))
        self.job_queue.conn.commit()

    def _determine_final_status(self, run_id: str) -> tuple[str, str]:
        """
        Determine final run status based on parameter coverage.

        Returns:
            tuple: (status, coverage_summary)
            - status: 'COMPLETED', 'INCOMPLETE', or 'FAILED'
            - coverage_summary: Human-readable description of coverage
        """
        # Get all unique parameter combinations that were intended to run
        cursor = self.job_queue.conn.execute("""
            SELECT DISTINCT engine_name, pattern_count, input_size
            FROM benchmark_jobs
            WHERE run_id = ?
            ORDER BY engine_name, pattern_count, input_size
        """, (run_id,))

        all_combinations = cursor.fetchall()
        total_combinations = len(all_combinations)

        if total_combinations == 0:
            return 'FAILED', 'No jobs found'

        # Check which combinations have at least one successful result
        successful_combinations = []
        missing_combinations = []

        for combo in all_combinations:
            engine_name, pattern_count, input_size = combo['engine_name'], combo['pattern_count'], combo['input_size']

            # Check if this combination has at least one successful job
            cursor = self.job_queue.conn.execute("""
                SELECT COUNT(*) as count
                FROM benchmark_jobs
                WHERE run_id = ?
                  AND engine_name = ?
                  AND pattern_count = ?
                  AND input_size = ?
                  AND status = 'COMPLETED'
            """, (run_id, engine_name, pattern_count, input_size))

            successful_count = cursor.fetchone()['count']

            if successful_count > 0:
                successful_combinations.append(f"{engine_name}/{pattern_count}pats/{input_size}")
            else:
                missing_combinations.append(f"{engine_name}/{pattern_count}pats/{input_size}")

        # Determine status based on coverage
        successful_count = len(successful_combinations)

        if successful_count == total_combinations:
            status = 'COMPLETED'
            summary = f"All {total_combinations} parameter combinations have representative results"
        elif successful_count == 0:
            status = 'FAILED'
            summary = f"No successful results for any of the {total_combinations} parameter combinations"
        else:
            status = 'INCOMPLETE'
            summary = f"{successful_count}/{total_combinations} parameter combinations have results. Missing: {', '.join(missing_combinations[:5])}"
            if len(missing_combinations) > 5:
                summary += f" and {len(missing_combinations) - 5} more"

        return status, summary

    # Helper methods

    def _should_skip_job(self, job) -> bool:
        """
        Check if a job should be skipped based on engine-specific rules.

        Current rules:
        - java-native-unfair: Skip if pattern_count × corpus_size_mb > 100,000
        """
        # Only apply rule to java-native-unfair engine
        if job.engine_name != "java-native-unfair":
            return False

        # Convert input size to megabytes
        corpus_size_mb = self._parse_corpus_size_mb(job.input_size)

        # Calculate complexity factor
        complexity = job.pattern_count * corpus_size_mb

        # Skip if complexity exceeds threshold
        if complexity > 100000:
            logger.info(f"🚫 Skipping java-native-unfair job: {job.pattern_count} patterns × {corpus_size_mb}MB = {complexity} > 100,000 (complexity limit)")
            return True

        return False

    def _parse_corpus_size_mb(self, input_size: str) -> int:
        """Parse input size string to megabytes (e.g., '1GB' -> 1000, '100MB' -> 100)."""
        input_size = input_size.upper()
        if input_size.endswith('GB'):
            return int(input_size.replace('GB', '')) * 1000
        elif input_size.endswith('MB'):
            return int(input_size.replace('MB', ''))
        else:
            # Default to MB if no unit specified
            return int(input_size)

    def _mark_job_as_skipped(self, job, run_id: str):
        """Mark a job as completed with skipped status to preserve it in results."""
        from .database import JobQueue

        # Create thread-local database connection
        thread_job_queue = JobQueue(self.db_path, str(self.output_dir), run_id)

        try:
            # Mark job as completed with special status indicating it was skipped
            thread_job_queue.update_job_status(
                job_id=job.job_id,
                status="COMPLETED",
                result=None,
                error_message=f"SKIPPED_COMPLEXITY_LIMIT: {job.pattern_count} × {self._parse_corpus_size_mb(job.input_size)}MB > 100,000"
            )

            logger.info(f"✅ Marked {job.engine_name} job as skipped in database")

        except Exception as e:
            logger.error(f"Failed to mark job {job.job_id} as skipped: {e}")
        finally:
            thread_job_queue.close()

    def _calculate_job_timeout(self) -> float:
        """Calculate appropriate timeout respecting config timeout_per_job."""
        # Get configured timeout from config (default to 2 hours if not set)
        config_timeout = self.config.get('execution_plan', {}).get('timeout_per_job', 72000)

        # Use config timeout as the minimum - this ensures large workloads get adequate time
        max_timeout = config_timeout

        input_sizes = self.config.get('test_matrix', {}).get('input_sizes', ['1MB'])
        logger.info(f"Using job timeout: {max_timeout} seconds (from config) for input sizes: {input_sizes}")
        return max_timeout

    def _get_engine_by_name(self, engine_name: str):
        """Get engine instance by name."""
        engines = self._discover_and_validate_engines()
        for engine in engines:
            if engine.name == engine_name:
                return engine
        raise ValueError(f"Engine not found: {engine_name}")

    def _get_input_size_bytes(self, input_size: str) -> int:
        """Convert input size string to bytes."""
        size_map = {
            '1MB': 1024 * 1024,
            '10MB': 10 * 1024 * 1024,
            '100MB': 100 * 1024 * 1024,
            '500MB': 500 * 1024 * 1024,
            '1GB': 1024 * 1024 * 1024
        }
        return size_map.get(input_size, 1024 * 1024)

    def _compute_config_hash(self) -> str:
        """Compute hash of configuration for run deduplication."""
        config_str = json.dumps(self.config, sort_keys=True)
        return hashlib.md5(config_str.encode()).hexdigest()

    def _get_git_metadata(self) -> Dict[str, Any]:
        """Get git metadata for reproducibility."""
        try:
            git_meta = GitMetadata()
            metadata = git_meta.get_metadata()

            return {
                'commit_sha': metadata.get('commit', {}).get('sha'),
                'branch': metadata.get('branch', {}).get('current'),
                'is_dirty': not metadata.get('status', {}).get('is_clean', True)
            }
        except Exception as e:
            logger.warning(f"Failed to get git metadata: {e}")
            return {}

    def _prepare_git_stored_patterns(self) -> None:
        """Copy git-stored patterns instead of generating new ones."""
        logger.info("Preparing git-stored patterns...")

        matrix = self.config.get('test_matrix', {})
        pattern_counts = matrix.get('pattern_counts', [10, 100, 1000])

        data_dir = self.output_dir / "data"
        git_pattern_dir = Path(__file__).parent.parent / "benchmark_suites" / "log_mining"

        for count in pattern_counts:
            pattern_file = f"patterns_{count}.txt"
            metadata_file = f"patterns_{count}_metadata.json"

            git_pattern_path = git_pattern_dir / pattern_file
            git_metadata_path = git_pattern_dir / metadata_file

            target_pattern_path = data_dir / pattern_file
            target_metadata_path = data_dir / metadata_file

            # Copy git-stored pattern file if it exists
            if git_pattern_path.exists():
                import shutil
                shutil.copy2(git_pattern_path, target_pattern_path)
                logger.info(f"✅ Copied git-stored {pattern_file}")

                # Copy metadata if it exists
                if git_metadata_path.exists():
                    shutil.copy2(git_metadata_path, target_metadata_path)
                    logger.info(f"✅ Copied git-stored {metadata_file}")
                else:
                    logger.warning(f"⚠️  No git-stored {metadata_file}")
            else:
                logger.warning(f"⚠️  No git-stored {pattern_file}, falling back to generation")
                # Fallback to generation if git-stored file doesn't exist
                pattern_config = self.config.get('data_configuration', {}).get('pattern_generation', {})
                self._generate_pattern_file(count, pattern_config)

    def _prepare_corpus_files(self) -> None:
        """Generate corpus files (these are usually not pre-stored)."""
        logger.info("Preparing corpus files...")

        matrix = self.config.get('test_matrix', {})
        data_config = self.config.get('data_configuration', {})

        # Generate corpus files for each required size
        input_sizes = matrix.get('input_sizes', ['1MB'])
        corpus_config = data_config.get('corpus_generation', {})

        for size in input_sizes:
            self._generate_corpus_file(size, corpus_config)

    def close(self):
        """Clean up resources."""
        if self.job_queue:
            self.job_queue.close()