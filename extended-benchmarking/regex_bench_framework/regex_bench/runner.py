"""
Main benchmark runner and orchestrator.

Coordinates execution across engines, manages test matrix,
and produces comprehensive results.
"""

import json
import time
import hashlib
import tempfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict
from pathlib import Path
from typing import Dict, List, Any, Optional, Tuple
import logging

from .engines import EngineManager, EngineResult
from .data import PatternSuite, CorpusManager
from .statistics import StatisticalAnalyzer
from .utils import SystemInfo, GitMetadata


logger = logging.getLogger(__name__)


class BenchmarkRunner:
    """Main benchmark orchestrator."""

    def __init__(self, config: Dict[str, Any], output_dir: Path, parallel_engines: int = 1):
        self.config = config
        self.output_dir = Path(output_dir)
        self.parallel_engines = parallel_engines

        # Initialize components
        self.engine_manager = EngineManager()
        self.pattern_suite = PatternSuite()
        self.corpus_manager = CorpusManager()
        self.analyzer = StatisticalAnalyzer()

        # Runtime state
        self.start_time = time.time()
        self.run_id = self._generate_run_id()
        self.results: List[EngineResult] = []

        # Setup logging
        self._setup_logging()

    def run(self) -> Dict[str, Any]:
        """Execute the complete benchmark suite."""
        logger.info(f"Starting benchmark run {self.run_id}")
        logger.info(f"Phase: {self.config.get('phase', 'unknown')}")

        try:
            # Phase 1: Setup and validation
            self._setup_output_directory()
            engines = self._discover_and_validate_engines()
            test_combinations = self._generate_test_matrix()

            # Phase 2: Data preparation
            self._prepare_test_data()

            # Phase 3: Execute benchmarks
            self._execute_benchmarks(engines, test_combinations)

            # Phase 4: Analysis and output
            results = self._analyze_results()
            self._save_results(results)

            logger.info(f"Benchmark completed successfully in {time.time() - self.start_time:.1f}s")
            return results

        except Exception as e:
            logger.error(f"Benchmark failed: {e}")
            self._save_error_state(str(e))
            raise

    def filter_engines(self, engine_names: List[str]) -> None:
        """Filter engines to run only specified ones."""
        self.config.setdefault('test_matrix', {})['engines'] = engine_names

    def _setup_output_directory(self) -> None:
        """Create and setup output directory structure."""
        self.output_dir.mkdir(parents=True, exist_ok=True)

        # Create subdirectories
        (self.output_dir / "raw_results").mkdir(exist_ok=True)
        (self.output_dir / "analysis").mkdir(exist_ok=True)
        (self.output_dir / "data").mkdir(exist_ok=True)
        (self.output_dir / "logs").mkdir(exist_ok=True)

        logger.info(f"Output directory: {self.output_dir}")

    def _setup_logging(self) -> None:
        """Setup logging for the benchmark run."""
        log_file = self.output_dir / "logs" / f"benchmark_{self.run_id}.log"
        log_file.parent.mkdir(parents=True, exist_ok=True)

        # Configure logging
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler(log_file),
                logging.StreamHandler()
            ]
        )

    def _discover_and_validate_engines(self) -> List[Any]:
        """Discover available engines and validate configuration."""
        logger.info("Discovering engines...")

        all_engines = self.engine_manager.discover_engines()
        requested_engines = self.config.get('test_matrix', {}).get('engines', [])

        available_engines = []
        unavailable_engines = []

        for engine in all_engines:
            if engine.name not in requested_engines:
                continue

            availability = engine.check_availability()
            if availability is None:
                available_engines.append(engine)
                logger.info(f"✓ {engine.name}: available")
            else:
                unavailable_engines.append((engine.name, availability))
                logger.warning(f"✗ {engine.name}: {availability}")

        if not available_engines:
            raise RuntimeError("No engines are available for benchmarking")

        if unavailable_engines:
            failure_mode = self.config.get('execution_plan', {}).get('failure_handling', {}).get('engine_unavailable', 'abort')
            if failure_mode == 'abort':
                unavailable_names = [name for name, _ in unavailable_engines]
                raise RuntimeError(f"Required engines unavailable: {unavailable_names}")

        logger.info(f"Available engines: {[e.name for e in available_engines]}")
        return available_engines

    def _generate_test_matrix(self) -> List[Dict[str, Any]]:
        """Generate all test combinations from the test matrix."""
        matrix = self.config.get('test_matrix', {})

        pattern_counts = matrix.get('pattern_counts', [100])
        input_sizes = matrix.get('input_sizes', ['1MB'])
        pattern_suites = matrix.get('pattern_suites', ['log_mining'])
        corpora = matrix.get('corpora', ['synthetic_controllable'])
        engines = matrix.get('engines', [])
        iterations = matrix.get('iterations_per_combination', 5)

        combinations = []
        for pattern_count in pattern_counts:
            for input_size in input_sizes:
                for pattern_suite in pattern_suites:
                    for corpus_type in corpora:
                        for engine in engines:
                            for iteration in range(iterations):
                                combinations.append({
                                    'pattern_count': pattern_count,
                                    'input_size': input_size,
                                    'pattern_suite': pattern_suite,
                                    'corpus_type': corpus_type,
                                    'engine': engine,
                                    'iteration': iteration
                                })

        logger.info(f"Generated {len(combinations)} test combinations")
        return combinations

    def _prepare_test_data(self) -> None:
        """Generate patterns and corpora for testing."""
        logger.info("Preparing test data...")

        matrix = self.config.get('test_matrix', {})
        data_config = self.config.get('data_configuration', {})

        # Generate pattern files for each required size
        pattern_counts = matrix.get('pattern_counts', [100])
        pattern_config = data_config.get('pattern_generation', {})

        for count in pattern_counts:
            self._generate_pattern_file(count, pattern_config)

        # Generate corpus files for each required size
        input_sizes = matrix.get('input_sizes', ['1MB'])
        corpus_config = data_config.get('corpus_generation', {})

        for size in input_sizes:
            self._generate_corpus_file(size, corpus_config)

        logger.info("Test data preparation complete")

    def _generate_pattern_file(self, count: int, config: Dict[str, Any]) -> Path:
        """Generate a pattern file with specified count."""
        suite_name = config.get('suite', 'log_mining')
        seed = config.get('seed', 42)

        output_file = self.output_dir / "data" / f"patterns_{count}.txt"
        metadata_file = self.output_dir / "data" / f"patterns_{count}_metadata.json"

        if output_file.exists() and metadata_file.exists():
            logger.info(f"Pattern file already exists: {output_file}")
            return output_file

        logger.info(f"Generating {count} patterns for suite '{suite_name}'")

        # Generate patterns
        suite_gen = PatternSuite(suite_name, seed=seed)
        result = suite_gen.generate(count)

        # Write pattern file
        with open(output_file, 'w') as f:
            for pattern in result['patterns']:
                f.write(f"{pattern}\n")

        # Write metadata
        with open(metadata_file, 'w') as f:
            json.dump(result, f, indent=2)

        logger.info(f"Generated pattern file: {output_file}")
        return output_file

    def _generate_corpus_file(self, size: str, config: Dict[str, Any]) -> Path:
        """Generate a corpus file of specified size."""
        corpus_type = config.get('type', 'synthetic_controllable')

        output_file = self.output_dir / "data" / f"corpus_{size}.txt"

        if output_file.exists():
            logger.info(f"Corpus file already exists: {output_file}")
            return output_file

        logger.info(f"Generating corpus file: {size}")

        # Generate corpus using corpus manager
        corpus_content = self.corpus_manager.generate_corpus(size, config)

        # Write corpus file
        with open(output_file, 'w') as f:
            f.write(corpus_content)

        logger.info(f"Generated corpus file: {output_file} ({output_file.stat().st_size} bytes)")
        return output_file

    def _execute_benchmarks(self, engines: List[Any], combinations: List[Dict[str, Any]]) -> None:
        """Execute all benchmark combinations."""
        logger.info(f"Executing benchmarks with {self.parallel_engines} parallel engines")

        # Group combinations by engine for better resource utilization
        engine_combinations = {}
        for combo in combinations:
            engine_name = combo['engine']
            if engine_name not in engine_combinations:
                engine_combinations[engine_name] = []
            engine_combinations[engine_name].append(combo)

        # Execute with controlled parallelism
        with ThreadPoolExecutor(max_workers=self.parallel_engines) as executor:
            future_to_combo = {}

            for engine in engines:
                if engine.name not in engine_combinations:
                    continue

                for combo in engine_combinations[engine.name]:
                    future = executor.submit(self._execute_single_benchmark, engine, combo)
                    future_to_combo[future] = combo

            # Collect results as they complete
            completed = 0
            total = len(future_to_combo)

            for future in as_completed(future_to_combo):
                combo = future_to_combo[future]
                completed += 1

                try:
                    result = future.result()
                    self.results.append(result)
                    logger.info(f"[{completed}/{total}] Completed: {combo['engine']} "
                              f"({combo['pattern_count']} patterns, {combo['input_size']}, "
                              f"iter {combo['iteration']})")

                except Exception as e:
                    logger.error(f"Failed combination {combo}: {e}")
                    # Create error result
                    error_result = EngineResult(
                        engine_name=combo['engine'],
                        iteration=combo['iteration'],
                        status="error",
                        notes=f"Execution failed: {str(e)}"
                    )
                    self.results.append(error_result)

        logger.info(f"Benchmark execution complete: {len(self.results)} results")

    def _execute_single_benchmark(self, engine: Any, combination: Dict[str, Any]) -> EngineResult:
        """Execute a single benchmark combination."""
        # Get input files
        patterns_file = self.output_dir / "data" / f"patterns_{combination['pattern_count']}.txt"
        corpus_file = self.output_dir / "data" / f"corpus_{combination['input_size']}.txt"

        # Create temporary output directory for this run
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)

            # Execute the engine
            result = engine.run(
                patterns_file=patterns_file,
                corpus_file=corpus_file,
                iteration=combination['iteration'],
                output_dir=temp_path
            )

            # Add combination metadata to result
            result.patterns_compiled = combination['pattern_count']
            result.corpus_size_bytes = corpus_file.stat().st_size if corpus_file.exists() else 0

            return result

    def _analyze_results(self) -> Dict[str, Any]:
        """Analyze benchmark results and compute statistics."""
        logger.info("Analyzing benchmark results...")

        # Check for failures and fail fast
        successful_runs = [r for r in self.results if r.status == 'ok']
        failed_runs = [r for r in self.results if r.status != 'ok']

        if not successful_runs:
            # All runs failed - this is a critical error
            error_details = []
            for result in failed_runs[:3]:  # Show first 3 failures
                error_details.append(f"  {result.engine_name}: {result.notes or result.status}")

            error_msg = f"All {len(failed_runs)} benchmark runs failed!\n" + "\n".join(error_details)
            if len(failed_runs) > 3:
                error_msg += f"\n  ... and {len(failed_runs) - 3} more failures"

            logger.error(error_msg)
            raise RuntimeError(error_msg)

        elif failed_runs:
            # Some runs failed - log warnings but continue
            logger.warning(f"{len(failed_runs)} of {len(self.results)} benchmark runs failed:")
            for result in failed_runs[:5]:  # Log first 5 failures
                logger.warning(f"  {result.engine_name}: {result.notes or result.status}")

        # Organize results by engine and test parameters
        analysis = self.analyzer.analyze_results(self.results, self.config)

        # Add metadata
        analysis['benchmark_metadata'] = {
            'run_id': self.run_id,
            'start_time': self.start_time,
            'duration_seconds': time.time() - self.start_time,
            'config': self.config,
            'system_info': SystemInfo().get_info(),
            'git_metadata': GitMetadata().get_metadata(),
            'framework_version': '2.0.0'
        }

        logger.info("Statistical analysis complete")
        return analysis

    def _save_results(self, analysis: Dict[str, Any]) -> None:
        """Save results and analysis to files."""
        logger.info("Saving results...")

        # Save raw results
        raw_results_file = self.output_dir / "raw_results" / "benchmark_results.json"
        with open(raw_results_file, 'w') as f:
            json.dump([asdict(result) for result in self.results], f, indent=2)

        # Save analysis
        analysis_file = self.output_dir / "analysis" / "statistical_analysis.json"
        with open(analysis_file, 'w') as f:
            json.dump(analysis, f, indent=2)

        # Save summary
        summary = self._generate_summary(analysis)
        summary_file = self.output_dir / "summary.json"
        with open(summary_file, 'w') as f:
            json.dump(summary, f, indent=2)

        logger.info(f"Results saved to {self.output_dir}")

    def _generate_summary(self, analysis: Dict[str, Any]) -> Dict[str, Any]:
        """Generate a high-level summary of results."""
        return {
            'run_id': self.run_id,
            'phase': self.config.get('phase', 'unknown'),
            'status': 'completed',
            'engines_tested': list(set(r.engine_name for r in self.results)),
            'total_combinations': len(self.results),
            'successful_runs': len([r for r in self.results if r.status == 'ok']),
            'failed_runs': len([r for r in self.results if r.status != 'ok']),
            'duration_seconds': time.time() - self.start_time,
            'output_directory': str(self.output_dir)
        }

    def _save_error_state(self, error: str) -> None:
        """Save error information for debugging."""
        error_file = self.output_dir / "error.json"
        with open(error_file, 'w') as f:
            json.dump({
                'run_id': self.run_id,
                'error': error,
                'timestamp': time.time(),
                'partial_results': len(self.results)
            }, f, indent=2)

    def _generate_run_id(self) -> str:
        """Generate a unique run identifier."""
        timestamp = time.strftime("%Y%m%d_%H%M%S")
        config_hash = hashlib.md5(
            json.dumps(self.config, sort_keys=True).encode()
        ).hexdigest()[:8]
        return f"{timestamp}_{config_hash}"