"""
Database schema and initialization for benchmark job queue and results storage.
"""

import sqlite3
import logging
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)


def init_database(db_path: Path) -> sqlite3.Connection:
    """Initialize the benchmark database with complete schema."""
    db_path.parent.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row  # Enable column access by name

    # Enable foreign keys
    conn.execute("PRAGMA foreign_keys = ON")

    # Create all tables
    _create_system_profiles_table(conn)
    _create_benchmark_runs_table(conn)
    _create_benchmark_jobs_table(conn)
    _create_benchmark_statistics_table(conn)
    _create_benchmark_comparisons_table(conn)
    _create_test_data_files_table(conn)
    _create_system_baselines_table(conn)

    # Create indexes
    _create_indexes(conn)

    # Create views
    _create_views(conn)

    conn.commit()
    logger.info(f"Database initialized at {db_path}")

    return conn


def _create_system_profiles_table(conn: sqlite3.Connection):
    """Create system_profiles table for hardware/environment specs."""
    conn.execute("""
        CREATE TABLE IF NOT EXISTS system_profiles (
            profile_id TEXT PRIMARY KEY,           -- SHA256 hash of system fingerprint
            hostname TEXT NOT NULL,
            profile_name TEXT,                     -- User-friendly name (optional)

            -- CPU Information
            cpu_model TEXT NOT NULL,               -- "Apple M1 Pro", "Intel Core i7-12700K"
            cpu_architecture TEXT NOT NULL,        -- "arm64", "x86_64"
            cpu_physical_cores INTEGER NOT NULL,   -- Physical cores
            cpu_logical_cores INTEGER NOT NULL,    -- Logical cores (with hyperthreading)
            cpu_base_frequency_mhz INTEGER,        -- Base frequency
            cpu_max_frequency_mhz INTEGER,         -- Boost frequency
            cpu_l1_cache_kb INTEGER,               -- L1 cache size
            cpu_l2_cache_kb INTEGER,               -- L2 cache size
            cpu_l3_cache_mb INTEGER,               -- L3 cache size
            cpu_vendor TEXT,                       -- "Apple", "Intel", "AMD"
            cpu_flags TEXT,                        -- CPU feature flags (comma-separated)

            -- Memory Information
            memory_total_gb REAL NOT NULL,         -- Total physical memory
            memory_available_gb REAL NOT NULL,     -- Available at profile time
            memory_type TEXT,                      -- "DDR4", "DDR5", "LPDDR5"
            memory_speed_mhz INTEGER,              -- Memory speed
            swap_total_gb REAL,                    -- Swap space

            -- Storage Information
            storage_type TEXT,                     -- "SSD", "NVMe", "HDD", "Network"
            storage_available_gb REAL NOT NULL,    -- Available space
            storage_filesystem TEXT,               -- "APFS", "ext4", "NTFS"
            storage_mount_path TEXT,               -- Mount point for benchmark data

            -- Operating System
            os_name TEXT NOT NULL,                 -- "Darwin", "Linux", "Windows"
            os_version TEXT NOT NULL,              -- "25.1.0", "Ubuntu 22.04"
            os_release TEXT,                       -- Detailed OS release info
            kernel_version TEXT,                   -- Kernel version
            os_architecture TEXT,                  -- "arm64", "x86_64"

            -- Virtualization & Containers
            is_virtualized BOOLEAN DEFAULT FALSE,  -- Running in VM
            virtualization_type TEXT,              -- "Docker", "VirtualBox", "VMware", etc.
            container_runtime TEXT,                -- "docker", "podman", etc.
            container_image TEXT,                  -- Container image if applicable

            -- Runtime Environment
            python_version TEXT NOT NULL,          -- "3.11.5"
            python_implementation TEXT,            -- "CPython", "PyPy"
            python_compiler TEXT,                  -- Compiler details

            -- Key Dependencies (for reproducibility)
            regex_bench_version TEXT,              -- Our framework version
            key_dependencies_json TEXT,            -- JSON of critical package versions

            -- Environment Variables (filtered)
            env_variables_json TEXT,               -- Relevant env vars (PATH, JAVA_HOME, etc.)

            -- Performance Characteristics
            benchmark_baseline_score REAL,         -- Standard benchmark score
            thermal_throttling_detected BOOLEAN DEFAULT FALSE,

            -- Profile Metadata
            profiled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            profiled_by TEXT,                      -- Username who created profile
            notes TEXT,                            -- Additional notes

            -- Raw System Info (complete dump)
            raw_system_info_json TEXT              -- Complete system info for debugging
        )
    """)


def _create_benchmark_runs_table(conn: sqlite3.Connection):
    """Create benchmark_runs table for run-level tracking."""
    conn.execute("""
        CREATE TABLE IF NOT EXISTS benchmark_runs (
            run_id TEXT PRIMARY KEY,
            run_name TEXT,
            config_path TEXT NOT NULL,
            config_json TEXT NOT NULL,             -- Full config for validation
            output_directory TEXT,

            -- System Reference
            system_profile_id TEXT NOT NULL REFERENCES system_profiles(profile_id),

            -- Performance Context (captured at runtime)
            system_load_avg_1min REAL,             -- System load when benchmark started
            memory_usage_percent REAL,             -- Memory usage at start
            cpu_temperature_celsius REAL,          -- CPU temp (if available)
            background_processes_count INTEGER,    -- Number of running processes

            -- Git and environment tracking
            git_commit_sha TEXT,
            git_branch TEXT,
            git_is_dirty BOOLEAN DEFAULT FALSE,
            git_remote_url TEXT,

            -- Run lifecycle
            status TEXT NOT NULL DEFAULT 'PREPARING', -- PREPARING, RUNNING, COMPLETED, FAILED, CANCELLED
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            started_at TIMESTAMP NULL,
            completed_at TIMESTAMP NULL,
            duration_seconds REAL,

            -- Progress tracking
            total_jobs INTEGER DEFAULT 0,
            queued_jobs INTEGER DEFAULT 0,
            running_jobs INTEGER DEFAULT 0,
            completed_jobs INTEGER DEFAULT 0,
            failed_jobs INTEGER DEFAULT 0,

            -- Performance summary
            avg_throughput_mbps REAL,
            total_patterns_tested INTEGER,
            total_corpus_size_gb REAL,

            -- Notes and metadata
            notes TEXT,
            created_by TEXT,
            tags TEXT,

            -- Configuration hash for deduplication
            config_hash TEXT
        )
    """)


def _create_benchmark_jobs_table(conn: sqlite3.Connection):
    """Create benchmark_jobs table for individual test execution tracking."""
    conn.execute("""
        CREATE TABLE IF NOT EXISTS benchmark_jobs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            job_id TEXT UNIQUE NOT NULL,
            run_id TEXT NOT NULL REFERENCES benchmark_runs(run_id),

            -- Job configuration (normalized for queries)
            engine_name TEXT NOT NULL,
            pattern_count INTEGER NOT NULL,
            input_size TEXT NOT NULL,              -- "1MB", "10MB", "100MB"
            input_size_bytes INTEGER NOT NULL,     -- Actual size for calculations
            iteration INTEGER NOT NULL,
            pattern_suite TEXT NOT NULL,
            corpus_name TEXT NOT NULL,

            -- Job lifecycle
            status TEXT NOT NULL DEFAULT 'QUEUED', -- QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, TIMEOUT
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            started_at TIMESTAMP NULL,
            completed_at TIMESTAMP NULL,
            duration_seconds REAL,

            -- Execution tracking
            attempt_count INTEGER DEFAULT 0,
            timeout_seconds INTEGER DEFAULT 300,
            error_message TEXT NULL,

            -- Performance metrics (normalized for fast queries and aggregation)
            compilation_ns INTEGER,
            scanning_ns INTEGER,
            total_ns INTEGER,
            match_count INTEGER,
            patterns_compiled INTEGER,
            memory_peak_bytes INTEGER,
            memory_compilation_bytes INTEGER,
            cpu_user_ms INTEGER,
            cpu_system_ms INTEGER,

            -- Calculated metrics
            throughput_mbps REAL,                  -- (input_size_bytes / scanning_ns) * conversion
            matches_per_second REAL,              -- match_count / (scanning_ns / 1e9)

            -- Complete result data (JSON blob)
            result_json TEXT,                      -- Complete EngineResult as JSON
            raw_stdout TEXT,                       -- Engine output
            raw_stderr TEXT,                       -- Engine errors

            -- Configuration context
            config_hash TEXT NOT NULL,
            job_config_json TEXT                   -- Job-specific config subset
        )
    """)


def _create_benchmark_statistics_table(conn: sqlite3.Connection):
    """Create benchmark_statistics table for pre-computed statistical analysis."""
    conn.execute("""
        CREATE TABLE IF NOT EXISTS benchmark_statistics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            run_id TEXT NOT NULL REFERENCES benchmark_runs(run_id),

            -- Grouping dimensions
            engine_name TEXT NOT NULL,
            pattern_count INTEGER NOT NULL,
            input_size TEXT NOT NULL,
            metric_name TEXT NOT NULL,             -- "scanning_ns", "throughput_mbps", etc.

            -- Statistical measures
            sample_count INTEGER NOT NULL,
            mean_value REAL,
            median_value REAL,
            std_dev REAL,
            min_value REAL,
            max_value REAL,
            p95_value REAL,                        -- 95th percentile
            p99_value REAL,                        -- 99th percentile

            -- Confidence intervals
            ci_lower_95 REAL,                      -- 95% confidence interval
            ci_upper_95 REAL,

            -- Data quality indicators
            has_outliers BOOLEAN DEFAULT FALSE,
            outlier_count INTEGER DEFAULT 0,
            coefficient_of_variation REAL,        -- std_dev / mean

            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

            UNIQUE(run_id, engine_name, pattern_count, input_size, metric_name)
        )
    """)


def _create_benchmark_comparisons_table(conn: sqlite3.Connection):
    """Create benchmark_comparisons table for engine performance rankings."""
    conn.execute("""
        CREATE TABLE IF NOT EXISTS benchmark_comparisons (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            run_id TEXT NOT NULL REFERENCES benchmark_runs(run_id),

            -- Comparison context
            pattern_count INTEGER NOT NULL,
            input_size TEXT NOT NULL,
            metric_name TEXT NOT NULL,

            -- Engine rankings
            engine_name TEXT NOT NULL,
            rank_position INTEGER NOT NULL,       -- 1 = fastest, 2 = second, etc.
            metric_value REAL NOT NULL,

            -- Relative performance
            relative_to_fastest REAL,             -- How much slower than #1
            percent_of_fastest REAL,              -- Percentage of fastest speed

            -- Statistical significance
            is_significantly_different BOOLEAN DEFAULT FALSE,
            p_value REAL,                          -- Statistical significance

            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)


def _create_test_data_files_table(conn: sqlite3.Connection):
    """Create test_data_files table for tracking pattern/corpus files."""
    conn.execute("""
        CREATE TABLE IF NOT EXISTS test_data_files (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            run_id TEXT NOT NULL REFERENCES benchmark_runs(run_id),

            file_type TEXT NOT NULL,               -- "patterns", "corpus"
            file_name TEXT NOT NULL,               -- "patterns_1000.txt"
            file_path TEXT NOT NULL,               -- Full path
            file_size_bytes INTEGER NOT NULL,
            file_hash TEXT NOT NULL,               -- SHA256 of file content

            -- Metadata
            pattern_count INTEGER,                 -- For pattern files
            corpus_size_bytes INTEGER,            -- For corpus files
            generation_method TEXT,               -- How file was created
            metadata_json TEXT,                    -- Complete metadata

            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

            UNIQUE(run_id, file_type, file_name)
        )
    """)


def _create_system_baselines_table(conn: sqlite3.Connection):
    """Create system_baselines table for performance baseline tracking."""
    conn.execute("""
        CREATE TABLE IF NOT EXISTS system_baselines (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            system_profile_id TEXT NOT NULL REFERENCES system_profiles(profile_id),

            baseline_type TEXT NOT NULL,           -- "cpu", "memory", "disk_io"
            metric_name TEXT NOT NULL,             -- "single_core_score", "read_mbps", etc.
            metric_value REAL NOT NULL,
            measurement_unit TEXT,                 -- "points", "MB/s", "ms"

            -- Benchmark context
            benchmark_tool TEXT,                   -- Tool used for baseline
            benchmark_version TEXT,
            benchmark_duration_seconds REAL,

            measured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

            UNIQUE(system_profile_id, baseline_type, metric_name)
        )
    """)


def _create_indexes(conn: sqlite3.Connection):
    """Create performance indexes."""
    indexes = [
        "CREATE INDEX IF NOT EXISTS idx_jobs_run_engine_status ON benchmark_jobs(run_id, engine_name, status)",
        "CREATE INDEX IF NOT EXISTS idx_jobs_performance ON benchmark_jobs(engine_name, pattern_count, input_size, throughput_mbps)",
        "CREATE INDEX IF NOT EXISTS idx_jobs_status_time ON benchmark_jobs(status, created_at)",
        "CREATE INDEX IF NOT EXISTS idx_runs_git_time ON benchmark_runs(git_commit_sha, created_at)",
        "CREATE INDEX IF NOT EXISTS idx_runs_status_time ON benchmark_runs(status, created_at)",
        "CREATE INDEX IF NOT EXISTS idx_statistics_lookup ON benchmark_statistics(run_id, engine_name, metric_name)",
        "CREATE INDEX IF NOT EXISTS idx_system_profiles_hostname ON system_profiles(hostname, profiled_at)",
        "CREATE INDEX IF NOT EXISTS idx_jobs_timestamps ON benchmark_jobs(started_at, completed_at)"
    ]

    for index_sql in indexes:
        conn.execute(index_sql)


def _create_views(conn: sqlite3.Connection):
    """Create useful views for common queries."""

    # Latest completed runs view
    conn.execute("""
        CREATE VIEW IF NOT EXISTS latest_completed_runs AS
        SELECT * FROM benchmark_runs
        WHERE status = 'COMPLETED'
        ORDER BY completed_at DESC
    """)

    # Run summary view
    conn.execute("""
        CREATE VIEW IF NOT EXISTS run_summary AS
        SELECT
            r.run_id,
            r.run_name,
            r.git_commit_sha,
            r.git_branch,
            r.created_at,
            r.duration_seconds,
            r.total_jobs,
            r.completed_jobs,
            r.failed_jobs,
            ROUND(100.0 * r.completed_jobs / NULLIF(r.total_jobs, 0), 2) as completion_percentage,
            COUNT(DISTINCT j.engine_name) as engine_count,
            ROUND(AVG(j.throughput_mbps), 2) as avg_throughput
        FROM benchmark_runs r
        LEFT JOIN benchmark_jobs j ON r.run_id = j.run_id AND j.status = 'COMPLETED'
        GROUP BY r.run_id
    """)

    # Engine performance trends view
    conn.execute("""
        CREATE VIEW IF NOT EXISTS engine_performance_trends AS
        SELECT
            j.engine_name,
            j.pattern_count,
            j.input_size,
            r.git_commit_sha,
            r.created_at as run_date,
            AVG(j.throughput_mbps) as avg_throughput,
            AVG(j.scanning_ns / 1000000.0) as avg_scanning_ms,
            COUNT(*) as sample_count
        FROM benchmark_jobs j
        JOIN benchmark_runs r ON j.run_id = r.run_id
        WHERE j.status = 'COMPLETED' AND j.throughput_mbps IS NOT NULL
        GROUP BY j.engine_name, j.pattern_count, j.input_size, r.git_commit_sha, r.created_at
        ORDER BY j.engine_name, j.pattern_count, j.input_size, r.created_at
    """)


def get_schema_version(conn: sqlite3.Connection) -> Optional[str]:
    """Get current schema version."""
    try:
        cursor = conn.execute("SELECT value FROM metadata WHERE key = 'schema_version'")
        row = cursor.fetchone()
        return row[0] if row else None
    except sqlite3.OperationalError:
        return None


def set_schema_version(conn: sqlite3.Connection, version: str):
    """Set schema version."""
    conn.execute("""
        CREATE TABLE IF NOT EXISTS metadata (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    conn.execute(
        "INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)",
        ("schema_version", version)
    )
    conn.commit()