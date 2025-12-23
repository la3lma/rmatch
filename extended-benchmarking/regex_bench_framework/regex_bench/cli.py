"""
Command-line interface for the regex benchmarking framework.
"""

import click
import json
import sys
from datetime import datetime
from pathlib import Path
from typing import Optional
from rich.console import Console
from rich.table import Table
from rich.progress import Progress, SpinnerColumn, TextColumn

from . import __version__
from .runner import BenchmarkRunner
from .job_runner import JobBasedBenchmarkRunner
from .engines import EngineManager
from .statistics import StatisticalAnalyzer
from .data import CorpusManager, PatternSuite
from .database import JobQueue


console = Console()


@click.group()
@click.version_option(__version__)
@click.option('--verbose', '-v', is_flag=True, help='Enable verbose output')
@click.pass_context
def main(ctx, verbose):
    """Regex Benchmarking Framework - Scientific regex engine comparison."""
    ctx.ensure_object(dict)
    ctx.obj['verbose'] = verbose


@main.command()
@click.option('--output', '-o', type=click.Path(), help='Output directory for engine status')
def check_engines(output):
    """Check availability and status of all engines."""
    console.print("🔍 [bold blue]Checking Engine Availability[/bold blue]")

    engine_manager = EngineManager()
    engines = engine_manager.discover_engines()

    table = Table(title="Engine Status")
    table.add_column("Engine", style="cyan")
    table.add_column("Version", style="green")
    table.add_column("Status", justify="center")
    table.add_column("Notes", style="dim")

    results = {}
    for engine in engines:
        status_msg = engine.check_availability()
        if status_msg is None:
            status = "[green]✓ Available[/green]"
            results[engine.name] = "available"
        else:
            status = "[red]✗ Unavailable[/red]"
            results[engine.name] = f"unavailable: {status_msg}"

        table.add_row(
            engine.name,
            engine.get_version(),
            status,
            status_msg or ""
        )

    console.print(table)

    # Summary
    available = len([r for r in results.values() if r == "available"])
    total = len(results)
    console.print(f"\n📊 [bold]{available}/{total}[/bold] engines available")

    if output:
        output_path = Path(output)
        output_path.mkdir(parents=True, exist_ok=True)
        with open(output_path / "engine_status.json", 'w') as f:
            json.dump({
                "timestamp": "2024-12-19T12:30:00Z",  # TODO: actual timestamp
                "platform": "darwin_arm64",  # TODO: detect platform
                "engines": results
            }, f, indent=2)
        console.print(f"💾 Status saved to {output_path / 'engine_status.json'}")


@main.command()
@click.option('--config', '-c', required=True, type=click.Path(exists=True),
              help='Test matrix configuration file')
@click.option('--output', '-o', required=True, type=click.Path(),
              help='Output directory for results')
@click.option('--parallel', '-p', default=1, type=int,
              help='Number of parallel engine processes')
@click.option('--engines', help='Comma-separated list of engines to run')
@click.option('--dry-run', is_flag=True, help='Show what would be run without executing')
def run_phase(config, output, parallel, engines, dry_run):
    """Run a benchmark phase with the specified configuration."""
    console.print(f"🚀 [bold blue]Running Benchmark Phase[/bold blue]")
    console.print(f"📋 Config: {config}")
    console.print(f"📁 Output: {output}")

    # Load configuration
    with open(config, 'r') as f:
        config_data = json.load(f)

    if dry_run:
        console.print("[yellow]🔍 Dry run mode - showing planned execution[/yellow]")
        _show_dry_run(config_data, engines)
        return

    # Create output directory
    output_path = Path(output)
    output_path.mkdir(parents=True, exist_ok=True)

    # Initialize runner
    runner = BenchmarkRunner(
        config=config_data,
        output_dir=output_path,
        parallel_engines=parallel
    )

    # Filter engines if specified
    if engines:
        engine_list = [e.strip() for e in engines.split(',')]
        console.print(f"🎯 Running engines: {', '.join(engine_list)}")
        runner.filter_engines(engine_list)

    # Execute benchmarks
    try:
        with Progress(
            SpinnerColumn(),
            TextColumn("[progress.description]{task.description}"),
            console=console
        ) as progress:
            task = progress.add_task("Running benchmarks...", total=None)

            results = runner.run()

            progress.update(task, description="✓ Benchmarks complete")

        console.print(f"✅ [green]Benchmark phase completed successfully![/green]")
        console.print(f"📊 Results: {output_path}")

        # Quick summary
        _show_results_summary(results)

    except KeyboardInterrupt:
        console.print("[red]❌ Benchmark interrupted by user[/red]")
        sys.exit(1)
    except Exception as e:
        console.print(f"[red]❌ Benchmark failed: {e}[/red]")
        sys.exit(1)


@main.command()
@click.option('--engine', required=True, help='Engine name to run')
@click.option('--patterns', required=True, type=click.Path(exists=True),
              help='Pattern file')
@click.option('--corpus', required=True, type=click.Path(exists=True),
              help='Corpus file')
@click.option('--output', '-o', required=True, type=click.Path(),
              help='Output directory')
@click.option('--iterations', default=3, type=int, help='Number of iterations')
def run_single(engine, patterns, corpus, output, iterations):
    """Run a single engine benchmark."""
    console.print(f"🎯 [bold blue]Running Single Engine Benchmark[/bold blue]")
    console.print(f"⚙️  Engine: {engine}")
    console.print(f"📋 Patterns: {patterns}")
    console.print(f"📄 Corpus: {corpus}")

    # TODO: Implement single engine runner
    console.print("[yellow]⚠️  Single engine runner not yet implemented[/yellow]")


@main.command()
@click.option('--input', '-i', required=True, type=click.Path(exists=True),
              help='Benchmark results directory')
@click.option('--output', '-o', required=True, type=click.Path(),
              help='Report output directory')
@click.option('--format', default='html', type=click.Choice(['html', 'pdf', 'markdown']),
              help='Report format')
@click.option('--include-charts', is_flag=True, help='Include performance charts')
@click.option('--live', is_flag=True, help='Generate live progress report for running benchmarks')
def generate_report(input, output, format, include_charts, live):
    """Generate benchmark analysis report."""
    console.print(f"📊 [bold blue]Generating Benchmark Report[/bold blue]")
    console.print(f"📁 Input: {input}")

    try:
        # Handle live progress reporting for running benchmarks
        if live:
            _generate_live_progress_report(input, output, format)
            return
        # Read Run ID from summary for clear identification
        input_path = Path(input)
        summary_file = input_path / "summary.json"

        if summary_file.exists():
            with open(summary_file, 'r') as f:
                summary = json.load(f)
            run_id = summary.get('run_id', 'unknown')
            phase = summary.get('phase', 'unknown')
            console.print(f"🏃 [cyan]Run ID: {run_id}[/cyan] | [yellow]Phase: {phase}[/yellow]")
        else:
            console.print(f"⚠️  [yellow]No summary found - using directory analysis[/yellow]")

        console.print(f"📋 Format: {format}")

        from .reporting import ReportGenerator

        # Create output directory
        output_path = Path(output)
        output_path.mkdir(parents=True, exist_ok=True)

        # Generate report
        generator = ReportGenerator()
        report_file = generator.generate(
            input_dir=Path(input),
            output_dir=output_path,
            format=format,
            include_charts=include_charts
        )

        console.print(f"✅ [green]Report generated successfully![/green]")
        console.print(f"📄 Report file: {report_file}")

    except ImportError:
        console.print("[red]❌ Report generator dependencies not available[/red]")
    except Exception as e:
        console.print(f"[red]❌ Report generation failed: {e}[/red]")


@main.command()
@click.option('--baseline', required=True, type=click.Path(exists=True),
              help='Baseline benchmark results')
@click.option('--comparison', required=True, type=click.Path(exists=True),
              help='Comparison benchmark results')
@click.option('--output', '-o', required=True, type=click.Path(),
              help='Comparison output directory')
@click.option('--format', default='html', type=click.Choice(['html', 'markdown']),
              help='Report format')
def compare_runs(baseline, comparison, output, format):
    """Compare two benchmark runs."""
    console.print(f"⚖️  [bold blue]Comparing Benchmark Runs[/bold blue]")
    console.print(f"📊 Baseline: {baseline}")
    console.print(f"📊 Comparison: {comparison}")

    # TODO: Implement comparison tool
    console.print("[yellow]⚠️  Comparison tool not yet implemented[/yellow]")


@main.command()
@click.option('--results-dir', '-r', default='results', type=click.Path(exists=True),
              help='Results directory containing multiple benchmark runs')
@click.option('--output', '-o', required=True, type=click.Path(),
              help='Report output directory')
@click.option('--include-charts', is_flag=True, default=True,
              help='Include interactive performance charts')
def comprehensive_report(results_dir, output, include_charts):
    """Generate comprehensive multi-run analysis report with charts."""
    console.print(f"🎯 [bold blue]Generating Comprehensive Multi-Run Report[/bold blue]")
    console.print(f"📁 Results Directory: {results_dir}")
    console.print(f"📊 Output: {output}")

    try:
        from .reporting.multi_run_generator import MultiRunReportGenerator

        # Create output directory
        output_path = Path(output)
        output_path.mkdir(parents=True, exist_ok=True)

        # Generate comprehensive report
        generator = MultiRunReportGenerator()
        report_file = generator.generate_comprehensive_report(
            results_dir=Path(results_dir),
            output_dir=output_path
        )

        console.print(f"✅ [bold green]Comprehensive report generated:[/bold green] {report_file}")
        console.print(f"📊 [blue]Open in browser:[/blue] file://{report_file.absolute()}")

    except ImportError as e:
        console.print(f"[red]❌ Missing dependencies for chart generation: {e}[/red]")
        console.print("[yellow]💡 Install with: pip install plotly pandas[/yellow]")
    except Exception as e:
        console.print(f"[red]❌ Failed to generate comprehensive report: {e}[/red]")
        raise click.ClickException(str(e))


@main.command()
@click.option('--suite', required=True,
              type=click.Choice(['log_mining', 'security_signatures', 'pathological', 'real_world']),
              help='Pattern suite to generate')
@click.option('--sizes', required=True, help='Comma-separated pattern counts (e.g., 10,100,1000)')
@click.option('--output', '-o', required=True, type=click.Path(),
              help='Output directory for patterns')
@click.option('--seed', default=42, type=int, help='Random seed for reproducibility')
def generate_patterns(suite, sizes, output, seed):
    """Generate pattern suites for benchmarking."""
    console.print(f"🎲 [bold blue]Generating Pattern Suite[/bold blue]")
    console.print(f"📋 Suite: {suite}")
    console.print(f"📊 Sizes: {sizes}")

    size_list = [int(s.strip()) for s in sizes.split(',')]
    output_path = Path(output)
    output_path.mkdir(parents=True, exist_ok=True)

    pattern_suite = PatternSuite(suite, seed=seed)

    for size in size_list:
        console.print(f"  Generating {size} patterns...")
        patterns = pattern_suite.generate(size)

        output_file = output_path / f"patterns_{size}.txt"
        with open(output_file, 'w') as f:
            for pattern in patterns['patterns']:
                f.write(f"{pattern}\n")

        # Save metadata
        metadata_file = output_path / f"patterns_{size}_metadata.json"
        with open(metadata_file, 'w') as f:
            json.dump(patterns, f, indent=2)

        console.print(f"    ✓ {output_file}")
        console.print(f"    ✓ {metadata_file}")

    console.print(f"✅ [green]Pattern generation complete![/green]")


@main.command()
@click.option('--sizes', required=True, help='Comma-separated sizes (e.g., 1MB,10MB,100MB)')
@click.option('--types', required=True,
              help='Comma-separated types (e.g., synthetic,logs,natural_language)')
@click.option('--output', '-o', required=True, type=click.Path(),
              help='Output directory for corpora')
def setup_corpora(sizes, types, output):
    """Setup test corpora for benchmarking."""
    from pathlib import Path
    from .data.corpus import CorpusManager
    import time

    console.print(f"📚 [bold blue]Setting Up Test Corpora[/bold blue]")

    # Parse input parameters
    size_list = [s.strip() for s in sizes.split(',')]
    type_list = [t.strip() for t in types.split(',')]

    # Validate corpus types
    valid_types = ['synthetic', 'logs', 'natural_language']
    for corpus_type in type_list:
        if corpus_type not in valid_types:
            console.print(f"[red]❌ Error: Invalid corpus type '{corpus_type}'. Valid types: {', '.join(valid_types)}[/red]")
            return

    # Create output directory
    output_dir = Path(output)
    output_dir.mkdir(parents=True, exist_ok=True)

    console.print(f"📊 Sizes: {', '.join(size_list)}")
    console.print(f"🏷️  Types: {', '.join(type_list)}")
    console.print(f"📂 Output directory: {output_dir}")
    console.print("")

    # Initialize corpus manager
    corpus_manager = CorpusManager()

    # Generate corpora
    total_files = len(size_list) * len(type_list)
    completed = 0

    console.print(f"[bold green]🚀 Generating {total_files} corpus files...[/bold green]")

    start_time = time.time()

    try:
        generated_files = corpus_manager.generate_corpus_files(size_list, type_list, output_dir)

        for key, filepath in generated_files.items():
            completed += 1
            file_size = filepath.stat().st_size
            console.print(f"  ✅ [{completed}/{total_files}] {filepath.name} ({_format_bytes(file_size)})")

        elapsed_time = time.time() - start_time
        console.print("")
        console.print(f"[bold green]🎉 Successfully generated {total_files} corpus files in {elapsed_time:.1f}s[/bold green]")

        # Show summary
        console.print("\n[bold blue]📋 Summary:[/bold blue]")
        total_size = sum(f.stat().st_size for f in generated_files.values())
        console.print(f"  📁 Files created: {total_files}")
        console.print(f"  💾 Total size: {_format_bytes(total_size)}")
        console.print(f"  📂 Location: {output_dir}")

        # Show usage example
        console.print(f"\n[bold blue]💡 Usage Example:[/bold blue]")
        console.print(f"  regex-bench benchmark --corpus-dir {output_dir} --patterns patterns.txt")

    except Exception as e:
        console.print(f"[red]❌ Error generating corpora: {str(e)}[/red]")
        return

def _format_bytes(bytes_count: int) -> str:
    """Format bytes into human readable format."""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if bytes_count < 1024:
            return f"{bytes_count:.1f}{unit}"
        bytes_count /= 1024
    return f"{bytes_count:.1f}TB"


@main.command()
@click.option('--config', required=True, type=click.Path(exists=True),
              help='Correctness test configuration')
@click.option('--output', '-o', required=True, type=click.Path(),
              help='Output directory for validation results')
@click.option('--reference-engine', default='java-native',
              help='Reference engine for correctness comparison')
def validate_correctness(config, output, reference_engine):
    """Validate correctness across engines."""
    console.print(f"✅ [bold blue]Validating Engine Correctness[/bold blue]")
    console.print(f"🎯 Reference: {reference_engine}")

    # TODO: Implement correctness validation
    console.print("[yellow]⚠️  Correctness validation not yet implemented[/yellow]")


@main.command()
@click.option('--input', '-i', required=True, type=click.Path(exists=True),
              help='Benchmark results directory')
def show_stats(input):
    """Show statistics from benchmark results."""
    console.print(f"📈 [bold blue]Benchmark Statistics[/bold blue]")

    try:
        results_dir = Path(input)

        # Load summary data
        summary_file = results_dir / "summary.json"
        analysis_file = results_dir / "analysis" / "statistical_analysis.json"

        if not summary_file.exists():
            console.print(f"[red]❌ Summary file not found: {summary_file}[/red]")
            return

        with open(summary_file, 'r') as f:
            summary = json.load(f)

        # Display run overview
        _display_run_overview(summary)

        if analysis_file.exists():
            with open(analysis_file, 'r') as f:
                analysis = json.load(f)
            _display_performance_stats(analysis)
        else:
            console.print("[yellow]⚠️  Detailed analysis not available[/yellow]")

    except Exception as e:
        console.print(f"[red]❌ Error loading statistics: {e}[/red]")


def _show_dry_run(config_data: dict, engines: Optional[str]) -> None:
    """Show what would be executed in dry run mode."""
    phase = config_data.get('phase', 'unknown')
    console.print(f"\n📋 [bold]Phase {phase} Configuration[/bold]")

    matrix = config_data.get('test_matrix', {})

    table = Table(title="Planned Execution")
    table.add_column("Parameter", style="cyan")
    table.add_column("Values", style="green")

    table.add_row("Pattern Counts", str(matrix.get('pattern_counts', [])))
    table.add_row("Input Sizes", str(matrix.get('input_sizes', [])))
    table.add_row("Pattern Suites", str(matrix.get('pattern_suites', [])))
    table.add_row("Corpora", str(matrix.get('corpora', [])))
    table.add_row("Engines", str(matrix.get('engines', [])))
    table.add_row("Repetitions", str(matrix.get('repetitions', 'unknown')))

    console.print(table)

    if engines:
        console.print(f"\n🎯 [yellow]Filtered to engines: {engines}[/yellow]")


def _show_results_summary(results: dict) -> None:
    """Show a quick summary of benchmark results."""
    console.print(f"\n📊 [bold]Results Summary[/bold]")

    # TODO: Parse actual results and show meaningful summary
    console.print("✓ Benchmark data saved")
    console.print("✓ Statistical analysis complete")
    console.print("✓ Ready for report generation")


# Job-based benchmark commands for lifecycle management

@main.command('job-start')
@click.option('--config', '-c', required=True, type=click.Path(exists=True),
              help='Test matrix configuration file')
@click.option('--output', '-o', required=True, type=click.Path(),
              help='Output directory for results')
@click.option('--parallel', '-p', default=1, type=int,
              help='Number of parallel engine processes')
@click.option('--engines', help='Comma-separated list of engines to run')
def job_start(config, output, parallel, engines):
    """Start a new benchmark run with job queue."""
    console.print("🚀 [bold blue]Starting New Job-Based Benchmark Run[/bold blue]")

    try:
        # Load configuration
        with open(config, 'r') as f:
            config_data = json.load(f)

        # Set config path for tracking
        config_data['config_path'] = str(Path(config).absolute())

        # Create output directory
        output_path = Path(output)
        output_path.mkdir(parents=True, exist_ok=True)

        # Filter engines if specified
        if engines:
            engine_list = [e.strip() for e in engines.split(',')]
            config_data.setdefault('test_matrix', {})['engines'] = engine_list
            console.print(f"🎯 Filtered to engines: {engines}")

        # Create job-based runner
        runner = JobBasedBenchmarkRunner(
            config=config_data,
            output_dir=output_path,
            parallel_engines=parallel
        )

        # Execute with job queue
        run_id = runner.run_with_jobs()

        console.print(f"✅ [green]Benchmark run completed: {run_id}[/green]")
        console.print(f"📁 Results saved to: {output_path}")
        console.print(f"💾 Database: {runner.db_path}")

        runner.close()

    except Exception as e:
        console.print(f"❌ [red]Benchmark failed: {e}[/red]")
        sys.exit(1)


@main.command('job-continue')
@click.option('--run-id', help='Specific run ID to continue (optional)')
@click.option('--output', '-o', type=click.Path(), help='Output directory (for auto-detection)')
def job_continue(run_id, output):
    """Continue an interrupted benchmark run."""
    console.print("🔄 [bold blue]Continuing Interrupted Benchmark Run[/bold blue]")

    try:
        # Find database path
        if output:
            db_path = Path(output) / "jobs.db"
        else:
            # Look in current directory
            db_path = Path.cwd() / "jobs.db"

        if not db_path.exists():
            console.print(f"❌ [red]No job database found at {db_path}[/red]")
            console.print("💡 Specify output directory with --output or run from benchmark directory")
            sys.exit(1)

        # Initialize job queue
        queue = JobQueue(db_path)

        # Find run to continue
        if not run_id:
            run_id = queue.get_latest_incomplete_run()
            if not run_id:
                console.print("❌ [red]No interrupted runs found[/red]")
                sys.exit(1)
            console.print(f"🔍 Found interrupted run: {run_id}")

        # Get run configuration
        cursor = queue.conn.execute(
            "SELECT config_json, output_directory FROM benchmark_runs WHERE run_id = ?",
            (run_id,)
        )
        run_data = cursor.fetchone()

        if not run_data:
            console.print(f"❌ [red]Run not found: {run_id}[/red]")
            sys.exit(1)

        config_data = json.loads(run_data['config_json'])
        output_dir = Path(run_data['output_directory']) if run_data['output_directory'] else Path(output or '.')

        # Create runner and continue
        runner = JobBasedBenchmarkRunner(
            config=config_data,
            output_dir=output_dir,
            db_path=db_path
        )

        # Continue execution
        completed_run_id = runner.run_with_jobs()

        console.print(f"✅ [green]Benchmark run completed: {completed_run_id}[/green]")
        console.print(f"📁 Results saved to: {output_dir}")

        runner.close()
        queue.close()

    except Exception as e:
        console.print(f"❌ [red]Failed to continue run: {e}[/red]")
        sys.exit(1)


@main.command('job-status')
@click.option('--run-id', help='Show status for specific run ID')
@click.option('--run-id-only', is_flag=True, help='Output only the latest run ID')
@click.option('--latest-run-id', is_flag=True, help='Output only the latest run ID')
@click.option('--quiet', is_flag=True, help='Minimal output')
@click.option('--output', '-o', type=click.Path(), help='Output directory (for database location)')
def job_status(run_id, run_id_only, latest_run_id, quiet, output):
    """Show status of benchmark runs and jobs."""

    # Handle simple output modes first
    if latest_run_id or run_id_only:
        try:
            # Find database
            if output:
                db_path = Path(output) / "jobs.db"
            else:
                db_path = Path.cwd() / "jobs.db"

            if not db_path.exists() and not quiet:
                console.print(f"❌ No job database found at {db_path}")
                return

            queue = JobQueue(db_path)
            latest_id = queue.get_latest_run_id()

            if latest_id:
                console.print(latest_id)
            elif not quiet:
                console.print("No runs found")

            queue.close()
            return

        except Exception as e:
            if not quiet:
                console.print(f"Error: {e}")
            return

    # Full status display
    if not quiet:
        console.print("📊 [bold blue]Benchmark Job Status[/bold blue]")

    try:
        # Find database
        if output:
            db_path = Path(output) / "jobs.db"
        else:
            db_path = Path.cwd() / "jobs.db"

        if not db_path.exists():
            console.print(f"❌ [red]No job database found at {db_path}[/red]")
            console.print("💡 Specify output directory with --output or run from benchmark directory")
            return

        queue = JobQueue(db_path)

        if run_id:
            _show_run_status(queue, run_id)
        else:
            _show_all_runs_status(queue)

        queue.close()

    except Exception as e:
        console.print(f"❌ [red]Error getting status: {e}[/red]")


@main.command('job-reset')
@click.option('--confirm', is_flag=True, help='Skip confirmation prompt')
@click.option('--output', '-o', type=click.Path(), help='Output directory (for database location)')
def job_reset(confirm, output):
    """Reset job queue (clear all jobs and runs)."""

    if not confirm:
        if not click.confirm("⚠️  This will clear all queued and failed jobs. Continue?"):
            console.print("Reset cancelled.")
            return

    try:
        # Find database
        if output:
            db_path = Path(output) / "jobs.db"
        else:
            db_path = Path.cwd() / "jobs.db"

        if not db_path.exists():
            console.print(f"❌ [red]No job database found at {db_path}[/red]")
            return

        queue = JobQueue(db_path)

        # Clear jobs and runs
        queue.conn.execute("DELETE FROM benchmark_jobs")
        queue.conn.execute("DELETE FROM benchmark_runs")
        queue.conn.commit()

        console.print("✅ [green]Job queue reset completed[/green]")

        queue.close()

    except Exception as e:
        console.print(f"❌ [red]Reset failed: {e}[/red]")


@main.command('job-retry')
@click.option('--output', '-o', type=click.Path(), help='Output directory (for database location)')
@click.option('--run-id', help='Retry jobs for specific run ID (defaults to latest)')
@click.option('--status', multiple=True, default=['TIMEOUT', 'FAILED'],
              help='Job statuses to retry (default: TIMEOUT, FAILED)')
@click.option('--confirm', is_flag=True, help='Skip confirmation prompt')
def job_retry(output, run_id, status, confirm):
    """Retry failed or timed-out jobs from a previous run."""

    try:
        # Find database
        if output:
            db_path = Path(output) / "jobs.db"
        else:
            db_path = Path.cwd() / "jobs.db"

        if not db_path.exists():
            console.print(f"❌ [red]No job database found at {db_path}[/red]")
            console.print("💡 Specify output directory with --output or run from benchmark directory")
            return

        queue = JobQueue(db_path)

        # Find run to retry jobs for
        if not run_id:
            run_id = queue.get_latest_run_id()
            if not run_id:
                console.print("❌ [red]No runs found[/red]")
                queue.close()
                return

        # Check if run exists
        cursor = queue.conn.execute(
            "SELECT run_id, status FROM benchmark_runs WHERE run_id = ?",
            (run_id,)
        )
        run_data = cursor.fetchone()

        if not run_data:
            console.print(f"❌ [red]Run not found: {run_id}[/red]")
            queue.close()
            return

        # Find jobs with specified statuses
        status_placeholders = ','.join(['?' for _ in status])
        cursor = queue.conn.execute(
            f"""SELECT job_id, engine_name, pattern_count, input_size, status, notes
                FROM benchmark_jobs
                WHERE run_id = ? AND status IN ({status_placeholders})
                ORDER BY job_id""",
            [run_id] + list(status)
        )

        jobs_to_retry = cursor.fetchall()

        if not jobs_to_retry:
            console.print(f"✅ [green]No jobs found with status {', '.join(status)} for run {run_id}[/green]")
            queue.close()
            return

        # Show what will be retried
        console.print(f"🔄 [yellow]Found {len(jobs_to_retry)} jobs to retry for run {run_id}:[/yellow]")
        console.print()

        table = Table(title=f"Jobs to Retry (Run {run_id})")
        table.add_column("Job ID", style="cyan")
        table.add_column("Engine", style="green")
        table.add_column("Patterns", style="blue")
        table.add_column("Corpus Size", style="magenta")
        table.add_column("Status", style="red")
        table.add_column("Notes", style="dim", max_width=30)

        for job in jobs_to_retry:
            table.add_row(
                str(job['job_id']),
                job['engine_name'],
                str(job['pattern_count']),
                job['input_size'],
                job['status'],
                (job['notes'] or '')[:30] + ('...' if job['notes'] and len(job['notes']) > 30 else '')
            )

        console.print(table)
        console.print()

        # Confirm retry
        if not confirm:
            if not click.confirm(f"⚠️  Reset {len(jobs_to_retry)} jobs to PENDING status for retry?"):
                console.print("Retry cancelled.")
                queue.close()
                return

        # Reset jobs to PENDING status
        job_ids = [job['job_id'] for job in jobs_to_retry]
        job_ids_placeholders = ','.join(['?' for _ in job_ids])

        queue.conn.execute(
            f"""UPDATE benchmark_jobs
                SET status = 'PENDING',
                    started_at = NULL,
                    completed_at = NULL,
                    duration_seconds = NULL,
                    notes = NULL,
                    match_count = NULL
                WHERE job_id IN ({job_ids_placeholders})""",
            job_ids
        )

        queue.conn.commit()

        console.print(f"✅ [green]Successfully reset {len(jobs_to_retry)} jobs to PENDING status[/green]")
        console.print(f"🔄 [blue]Use 'regex-bench job-continue --output {db_path.parent}' to retry these jobs[/blue]")

        queue.close()

    except Exception as e:
        console.print(f"❌ [red]Retry failed: {e}[/red]")


def _generate_live_progress_report(results_dir: str, output_dir: str, format_type: str):
    """Generate live progress report for running benchmarks."""
    from .database.job_queue import JobQueue
    from datetime import datetime, timezone
    import json
    import sqlite3

    console.print(f"🔄 [cyan]Generating Live Progress Report[/cyan]")

    try:
        # Find database file
        input_path = Path(results_dir)
        db_file = input_path / "jobs.db"

        if not db_file.exists():
            console.print(f"❌ [red]No job database found at {db_file}[/red]")
            return

        # Connect to database and get job status
        queue = JobQueue(db_file)

        # Get current run info
        cursor = queue.conn.execute("""
            SELECT run_id, status, created_at, started_at
            FROM benchmark_runs
            ORDER BY created_at DESC
            LIMIT 1
        """)

        current_run = cursor.fetchone()
        if not current_run:
            console.print("❌ [red]No benchmark runs found[/red]")
            return

        run_id = current_run['run_id']
        console.print(f"🏃 [cyan]Run ID: {run_id}[/cyan]")

        # Get job progress breakdown
        progress = queue.get_run_progress(run_id)

        # Get detailed job information
        cursor = queue.conn.execute("""
            SELECT job_id, engine_name, pattern_count, input_size, iteration,
                   status, created_at, started_at, completed_at, duration_seconds, match_count
            FROM benchmark_jobs
            WHERE run_id = ?
            ORDER BY created_at ASC
        """, (run_id,))

        jobs = cursor.fetchall()

        # Generate HTML report
        _generate_live_html_report(current_run, progress, jobs, output_dir)

        queue.close()

        console.print(f"✅ [green]Live progress report generated successfully![/green]")
        console.print(f"📄 Report: {Path(output_dir) / 'live_progress.html'}")

    except Exception as e:
        console.print(f"❌ [red]Live report generation failed: {e}[/red]")
        import traceback
        traceback.print_exc()


def _format_timestamp(timestamp_str):
    """Format timestamp string for consistent display."""
    if not timestamp_str:
        return 'Not set'

    try:
        # Parse the timestamp string from database
        if isinstance(timestamp_str, str):
            # Handle various timestamp formats
            if 'T' in timestamp_str:
                # ISO format with T separator
                dt = datetime.fromisoformat(timestamp_str.replace('Z', '+00:00'))
            else:
                # Standard format
                dt = datetime.fromisoformat(timestamp_str)
        else:
            # Already a datetime object
            dt = timestamp_str

        # Format as local time for display
        return dt.strftime('%Y-%m-%d %H:%M:%S')
    except (ValueError, TypeError) as e:
        # Fallback to original string if parsing fails
        return str(timestamp_str)


def _generate_live_html_report(run_info, progress, jobs, output_dir: str):
    """Generate HTML live progress report."""
    from datetime import datetime, timezone

    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    # Calculate summary statistics
    total_jobs = len(jobs)
    completed_jobs = len([j for j in jobs if j['status'] == 'COMPLETED'])
    running_jobs = len([j for j in jobs if j['status'] == 'RUNNING'])
    queued_jobs = len([j for j in jobs if j['status'] == 'QUEUED'])
    failed_jobs = len([j for j in jobs if j['status'] in ['FAILED', 'TIMEOUT']])

    # Calculate progress percentage
    progress_percent = (completed_jobs / total_jobs * 100) if total_jobs > 0 else 0

    # Estimate completion time
    if completed_jobs > 0 and run_info['started_at']:
        try:
            start_time = datetime.fromisoformat(run_info['started_at'])
            elapsed_minutes = (datetime.now() - start_time).total_seconds() / 60
            avg_time_per_job = elapsed_minutes / completed_jobs
            remaining_jobs = total_jobs - completed_jobs
            eta_minutes = remaining_jobs * avg_time_per_job
            eta_text = f"~{eta_minutes:.0f} minutes"
        except:
            eta_text = "Calculating..."
    else:
        eta_text = "Calculating..."

    html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Live Benchmark Progress - {run_info['run_id']}</title>
    <meta http-equiv="refresh" content="30">
    <style>
        body {{ font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }}
        .container {{ max-width: 1200px; margin: 0 auto; }}
        .header {{ background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
        .progress-section {{ background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
        .jobs-section {{ background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}

        .progress-bar {{ width: 100%; height: 30px; background: #e0e0e0; border-radius: 15px; overflow: hidden; margin: 10px 0; }}
        .progress-fill {{ height: 100%; background: linear-gradient(90deg, #4CAF50, #45a049); transition: width 0.3s ease; }}
        .progress-text {{ text-align: center; margin-top: 10px; font-weight: bold; }}

        .stats-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin: 20px 0; }}
        .stat-card {{ background: #f8f9fa; padding: 15px; border-radius: 8px; text-align: center; }}
        .stat-number {{ font-size: 24px; font-weight: bold; margin-bottom: 5px; }}
        .stat-label {{ color: #666; font-size: 14px; }}

        .completed {{ color: #4CAF50; }}
        .running {{ color: #2196F3; }}
        .queued {{ color: #FF9800; }}
        .failed {{ color: #f44336; }}

        table {{ width: 100%; border-collapse: collapse; margin-top: 20px; }}
        th, td {{ padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }}
        th {{ background-color: #f2f2f2; font-weight: bold; }}
        tr:hover {{ background-color: #f5f5f5; }}

        .status {{ padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; text-transform: uppercase; }}
        .status.completed {{ background: #d4edda; color: #155724; }}
        .status.running {{ background: #cce7ff; color: #004085; }}
        .status.queued {{ background: #fff3cd; color: #856404; }}
        .status.failed {{ background: #f8d7da; color: #721c24; }}
        .status.timeout {{ background: #f8d7da; color: #721c24; }}
        .status.skipped_lowvariance {{ background: #4db6ac; color: white; border-radius: 12px; padding: 6px 12px; font-size: 11px; }}

        .auto-refresh {{ text-align: center; margin: 20px 0; color: #666; font-size: 14px; }}
        .timestamp {{ color: #888; font-size: 12px; }}

        .system-info {{ background: #e3f2fd; padding: 15px; border-radius: 8px; margin: 10px 0; }}
        .system-info h3 {{ margin-top: 0; color: #1565c0; }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🚀 Live Benchmark Progress</h1>
            <h2>Run ID: {run_info['run_id']}</h2>
            <p class="timestamp">Last updated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')} | Auto-refresh: 30s</p>

            <div class="system-info">
                <h3>📋 Run Information</h3>
                <p><strong>Status:</strong> {run_info['status']}</p>
                <p><strong>Created:</strong> {_format_timestamp(run_info['created_at'])}</p>
                <p><strong>Started:</strong> {_format_timestamp(run_info['started_at'])}</p>
            </div>
        </div>

        <div class="progress-section">
            <h3>📊 Overall Progress</h3>
            <div class="progress-bar">
                <div class="progress-fill" style="width: {progress_percent:.1f}%"></div>
            </div>
            <div class="progress-text">{completed_jobs}/{total_jobs} Jobs Completed ({progress_percent:.1f}%)</div>
            <p><strong>Estimated Time Remaining:</strong> {eta_text}</p>

            <div class="stats-grid">
                <div class="stat-card">
                    <div class="stat-number completed">{completed_jobs}</div>
                    <div class="stat-label">Completed</div>
                </div>
                <div class="stat-card">
                    <div class="stat-number running">{running_jobs}</div>
                    <div class="stat-label">Running</div>
                </div>
                <div class="stat-card">
                    <div class="stat-number queued">{queued_jobs}</div>
                    <div class="stat-label">Queued</div>
                </div>
                <div class="stat-card">
                    <div class="stat-number failed">{failed_jobs}</div>
                    <div class="stat-label">Failed</div>
                </div>
            </div>
        </div>

        <div class="jobs-section">
            <h3>📋 Job Details</h3>
            <table>
                <thead>
                    <tr>
                        <th>#</th>
                        <th>Engine</th>
                        <th>Status</th>
                        <th>Patterns</th>
                        <th>Corpus Size</th>
                        <th>Iteration</th>
                        <th>Duration</th>
                        <th>Throughput</th>
                        <th>Total Throughput</th>
                        <th>Matches Found</th>
                        <th>Started</th>
                    </tr>
                </thead>
                <tbody>"""

    for i, job in enumerate(jobs):
        status_class = job['status'].lower() if job['status'] else 'unknown'
        # Display friendly status text
        status_display = "skipped" if job['status'] == 'SKIPPED_LOWVARIANCE' else job['status']
        duration = f"{job['duration_seconds']:.1f}s" if job['duration_seconds'] else "-"
        started = job['started_at'][:19] if job['started_at'] else "Not started"

        # Parse corpus size from input_size (e.g., "1MB", "500MB", "1GB")
        input_size = job['input_size'] or ""
        corpus_mb = 0
        if 'MB' in input_size:
            corpus_mb = float(input_size.replace('MB', ''))
        elif 'GB' in input_size:
            corpus_mb = float(input_size.replace('GB', '')) * 1024
        elif 'KB' in input_size:
            corpus_mb = float(input_size.replace('KB', '')) / 1024

        # Calculate throughput (MB/sec)
        throughput = "-"
        if job['duration_seconds'] and job['duration_seconds'] > 0 and corpus_mb > 0:
            mb_per_sec = corpus_mb / job['duration_seconds']
            throughput = f"{mb_per_sec:.2f} MB/sec"

        # Calculate total pattern throughput (patterns × MB/sec)
        total_throughput = "-"
        if job['duration_seconds'] and job['duration_seconds'] > 0 and job['pattern_count'] and corpus_mb > 0:
            total_throughput_value = (job['pattern_count'] * corpus_mb) / job['duration_seconds']
            if total_throughput_value >= 1000:
                total_throughput = f"{total_throughput_value/1000:.1f}K p⋅MB/s"
            else:
                total_throughput = f"{total_throughput_value:.1f} p⋅MB/s"

        # Format matches found
        matches_found = "-"
        if job['match_count'] is not None:
            matches_found = f"{job['match_count']:,}"

        html += f"""
                    <tr>
                        <td>{i + 1}</td>
                        <td>{job['engine_name']}</td>
                        <td><span class="status {status_class}">{status_display}</span></td>
                        <td>{job['pattern_count']}</td>
                        <td>{job['input_size']}</td>
                        <td>{job['iteration']}</td>
                        <td>{duration}</td>
                        <td>{throughput}</td>
                        <td>{total_throughput}</td>
                        <td>{matches_found}</td>
                        <td class="timestamp">{started}</td>
                    </tr>"""

    html += """
                </tbody>
            </table>
        </div>

        <div class="auto-refresh">
            <p>🔄 This page automatically refreshes every 30 seconds to show the latest progress.</p>
            <p>💡 You can also manually refresh to get immediate updates.</p>
        </div>
    </div>
</body>
</html>"""

    # Write the HTML file
    report_file = output_path / "live_progress.html"
    with open(report_file, 'w') as f:
        f.write(html)


def _show_run_status(queue: JobQueue, run_id: str):
    """Show detailed status for a specific run."""
    # Get run info
    cursor = queue.conn.execute("""
        SELECT r.*, s.hostname, s.cpu_model, s.memory_total_gb
        FROM benchmark_runs r
        JOIN system_profiles s ON r.system_profile_id = s.profile_id
        WHERE r.run_id = ?
    """, (run_id,))

    run_info = cursor.fetchone()
    if not run_info:
        console.print(f"❌ Run not found: {run_id}")
        return

    # Show run details
    console.print(f"\n🏃 [bold]Run: {run_id}[/bold]")
    console.print(f"Status: {run_info['status']}")
    console.print(f"Created: {_format_timestamp(run_info['created_at'])}")
    console.print(f"System: {run_info['cpu_model']} ({run_info['memory_total_gb']:.1f}GB)")

    if run_info['git_commit_sha']:
        console.print(f"Git: {run_info['git_commit_sha'][:8]} ({run_info['git_branch']})")

    # Get job progress
    progress = queue.get_run_progress(run_id)

    table = Table(title="Job Status")
    table.add_column("Status", style="cyan")
    table.add_column("Count", justify="right", style="bold")

    for status, count in progress.items():
        if status != 'total' and count > 0:
            table.add_row(status, str(count))

    table.add_row("", "")  # Separator
    table.add_row("TOTAL", str(progress['total']), style="bold")

    console.print(table)

    # Show completion percentage
    if progress['total'] > 0:
        completed_pct = (progress['COMPLETED'] / progress['total']) * 100
        console.print(f"Progress: {completed_pct:.1f}% ({progress['COMPLETED']}/{progress['total']})")


def _show_all_runs_status(queue: JobQueue):
    """Show status of all benchmark runs."""
    cursor = queue.conn.execute("""
        SELECT run_id, status, created_at, completed_at,
               total_jobs, completed_jobs, failed_jobs
        FROM benchmark_runs
        ORDER BY created_at DESC
        LIMIT 10
    """)

    runs = cursor.fetchall()

    if not runs:
        console.print("No benchmark runs found.")
        return

    table = Table(title="Recent Benchmark Runs")
    table.add_column("Run ID", style="cyan")
    table.add_column("Status", justify="center")
    table.add_column("Progress", justify="center")
    table.add_column("Created", style="dim")

    for run in runs:
        progress_text = ""
        if run['total_jobs']:
            completed = run['completed_jobs'] or 0
            failed = run['failed_jobs'] or 0
            total = run['total_jobs']
            progress_text = f"{completed}/{total}"
            if failed > 0:
                progress_text += f" ({failed} failed)"

        # Format run ID (show first 8 chars)
        short_run_id = run['run_id']
        if len(short_run_id) > 20:
            short_run_id = short_run_id[:8] + "..."

        table.add_row(
            short_run_id,
            run['status'],
            progress_text,
            run['created_at'][:16] if run['created_at'] else ""
        )

    console.print(table)


def _display_run_overview(summary: dict) -> None:
    """Display high-level run overview statistics."""
    console.print(f"\n🏃 [bold]Run Overview[/bold]")

    overview_table = Table(title="Benchmark Summary")
    overview_table.add_column("Metric", style="cyan")
    overview_table.add_column("Value", style="green", justify="right")

    overview_table.add_row("Run ID", str(summary.get('run_id', 'unknown')))
    overview_table.add_row("Phase", str(summary.get('phase', 'unknown')))
    overview_table.add_row("Status", f"[green]{summary.get('status', 'unknown')}[/green]")

    engines = summary.get('engines_tested', [])
    overview_table.add_row("Engines Tested", ', '.join(engines) if engines else 'None')

    total_combinations = summary.get('total_combinations', 0)
    successful_runs = summary.get('successful_runs', 0)
    failed_runs = summary.get('failed_runs', 0)

    overview_table.add_row("Total Combinations", str(total_combinations))
    overview_table.add_row("Successful Runs", f"[green]{str(successful_runs)}[/green]")

    if failed_runs > 0:
        overview_table.add_row("Failed Runs", f"[red]{str(failed_runs)}[/red]")

    # Calculate success rate
    if total_combinations > 0:
        success_rate = (successful_runs / total_combinations) * 100
        overview_table.add_row("Success Rate", f"{success_rate:.1f}%")

    duration = summary.get('duration_seconds', 0)
    overview_table.add_row("Duration", f"{duration:.1f}s ({duration/60:.1f}m)")

    console.print(overview_table)


def _display_performance_stats(analysis: dict) -> None:
    """Display detailed performance statistics."""
    console.print(f"\n⚡ [bold]Performance Statistics[/bold]")

    grouped_stats = analysis.get('grouped_statistics', {})

    if not grouped_stats:
        console.print("[yellow]No performance data available[/yellow]")
        return

    # Create engine comparison table
    perf_table = Table(title="Engine Performance Comparison")
    perf_table.add_column("Engine", style="cyan")
    perf_table.add_column("Pattern Count", justify="right")
    perf_table.add_column("Corpus Size", justify="right")
    perf_table.add_column("Avg Throughput", justify="right", style="green")
    perf_table.add_column("Avg Scanning Time", justify="right")
    perf_table.add_column("Compilation Time", justify="right")
    perf_table.add_column("Matches Found", justify="right")

    # Sort by engine name and pattern count for consistent display
    sorted_stats = sorted(grouped_stats.items())

    for group_key, stats in sorted_stats:
        engine_name = stats.get('engine_name', 'unknown')
        pattern_count = stats.get('patterns_compiled', 0)
        corpus_size = stats.get('corpus_size_bytes', 0)

        # Format corpus size
        corpus_mb = corpus_size / (1024 * 1024)
        if corpus_mb >= 1000:
            corpus_size_str = f"{corpus_mb/1024:.1f}GB"
        else:
            corpus_size_str = f"{corpus_mb:.1f}MB"

        # Get throughput data
        throughput_stats = stats.get('throughput', {})
        if 'mean' in throughput_stats:
            avg_throughput = f"{throughput_stats['mean']:.1f} MB/s"
        else:
            avg_throughput = "N/A"

        # Get scanning time data (convert from ns to ms)
        scanning_stats = stats.get('scanning', {})
        if 'mean' in scanning_stats:
            avg_scanning_ms = scanning_stats['mean'] / 1_000_000  # ns to ms
            avg_scanning_str = f"{avg_scanning_ms:.1f}ms"
        else:
            avg_scanning_str = "N/A"

        # Get compilation time data (convert from ns to ms)
        compilation_stats = stats.get('compilation', {})
        if 'mean' in compilation_stats:
            avg_compilation_ms = compilation_stats['mean'] / 1_000_000  # ns to ms
            avg_compilation_str = f"{avg_compilation_ms:.1f}ms"
        else:
            avg_compilation_str = "N/A"

        # Get match count
        matches_stats = stats.get('matches', {})
        if 'mean' in matches_stats:
            matches_str = f"{int(matches_stats['mean']):,}"
        else:
            matches_str = "N/A"

        perf_table.add_row(
            engine_name,
            str(pattern_count),
            corpus_size_str,
            avg_throughput,
            avg_scanning_str,
            avg_compilation_str,
            matches_str
        )

    console.print(perf_table)

    # Display top performers
    _display_top_performers(grouped_stats)


def _display_top_performers(grouped_stats: dict) -> None:
    """Display top performing engines by different metrics."""
    console.print(f"\n🏆 [bold]Top Performers[/bold]")

    # Collect engines with throughput data
    engines_with_throughput = []
    engines_with_scanning = []

    for group_key, stats in grouped_stats.items():
        engine_name = stats.get('engine_name', 'unknown')
        throughput_stats = stats.get('throughput', {})
        scanning_stats = stats.get('scanning', {})

        if 'mean' in throughput_stats:
            engines_with_throughput.append((engine_name, throughput_stats['mean'], group_key))

        if 'mean' in scanning_stats:
            engines_with_scanning.append((engine_name, scanning_stats['mean'], group_key))

    # Show fastest throughput
    if engines_with_throughput:
        fastest_throughput = max(engines_with_throughput, key=lambda x: x[1])
        console.print(f"🚀 Highest Throughput: [green]{fastest_throughput[0]}[/green] - {fastest_throughput[1]:.1f} MB/s")

    # Show fastest scanning
    if engines_with_scanning:
        fastest_scanning = min(engines_with_scanning, key=lambda x: x[1])
        scanning_ms = fastest_scanning[1] / 1_000_000  # ns to ms
        console.print(f"⚡ Fastest Scanning: [green]{fastest_scanning[0]}[/green] - {scanning_ms:.1f}ms")


# Process management commands

@main.command('process-status')
@click.option('--output', '-o', type=click.Path(), help='Output directory (for database location)')
@click.option('--cleanup', is_flag=True, help='Clean up orphaned processes')
@click.option('--monitor', is_flag=True, help='Monitor current processes')
def process_status(output, cleanup, monitor):
    """Show status of benchmark processes and cleanup orphans."""
    console.print("🔍 [bold blue]Benchmark Process Status[/bold blue]")

    try:
        # Find database
        if output:
            db_path = Path(output) / "jobs.db"
        else:
            db_path = Path.cwd() / "jobs.db"

        if not db_path.exists():
            console.print(f"[red]❌ No job database found at {db_path}[/red]")
            console.print("💡 Run from benchmark directory or specify --output")
            return

        # Import here to avoid circular imports
        from .database.process_tracking import ProcessTracker, add_process_tracking_schema
        import sqlite3

        conn = sqlite3.connect(str(db_path))
        conn.row_factory = sqlite3.Row

        # Ensure process tracking schema exists
        add_process_tracking_schema(conn)

        tracker = ProcessTracker(conn)

        if monitor:
            # Monitor and update process status
            console.print("📊 Monitoring processes...")
            status_counts = tracker.monitor_processes()
            console.print(f"Updated: {status_counts}")

        if cleanup:
            # Clean up orphaned processes
            console.print("🧹 Cleaning up orphaned processes...")
            cleanup_results = tracker.cleanup_orphaned_processes()

            if cleanup_results['killed'] > 0:
                console.print(f"✅ [green]Killed {cleanup_results['killed']} orphaned processes[/green]")
            if cleanup_results['failed'] > 0:
                console.print(f"❌ [red]Failed to kill {cleanup_results['failed']} processes[/red]")
            if cleanup_results['skipped'] > 0:
                console.print(f"⏭️ [yellow]Skipped {cleanup_results['skipped']} already-dead processes[/yellow]")

        # Show process statistics
        stats = tracker.get_process_stats()

        # Process status table
        if stats:
            status_table = Table(title="Process Status Summary")
            status_table.add_column("Status", style="cyan")
            status_table.add_column("Count", justify="right", style="green")
            status_table.add_column("Avg Duration (s)", justify="right")
            status_table.add_column("Peak CPU %", justify="right")
            status_table.add_column("Peak Memory (MB)", justify="right")

            for status, data in stats.items():
                if status == 'system_benchmark_processes' or status == 'system_process_pids':
                    continue

                status_table.add_row(
                    status,
                    str(data['count']),
                    f"{data['avg_duration']:.1f}" if data['avg_duration'] else "N/A",
                    f"{data['max_cpu']:.1f}" if data['max_cpu'] else "N/A",
                    f"{data['max_memory']:.1f}" if data['max_memory'] else "N/A"
                )

            console.print(status_table)

        # Show current system processes
        if stats.get('system_benchmark_processes', 0) > 0:
            console.print(f"\n🖥️  Current System: {stats['system_benchmark_processes']} benchmark processes running")
            console.print(f"PIDs: {', '.join(map(str, stats.get('system_process_pids', [])))}")
        else:
            console.print(f"\n✅ [green]No active benchmark processes found on system[/green]")

        # Show orphaned processes if any
        orphaned = tracker.detect_orphaned_processes()
        if orphaned:
            console.print(f"\n⚠️  [yellow]Found {len(orphaned)} orphaned processes:[/yellow]")
            orphan_table = Table()
            orphan_table.add_column("PID", style="red")
            orphan_table.add_column("Engine", style="cyan")
            orphan_table.add_column("Job Status", style="yellow")
            orphan_table.add_column("Since Monitor", justify="right")

            for proc in orphaned[:10]:  # Show first 10
                orphan_table.add_row(
                    str(proc['pid']),
                    proc['engine_name'],
                    proc.get('job_status', 'unknown'),
                    f"{proc.get('seconds_since_monitor', 0)}s"
                )

            console.print(orphan_table)
            console.print(f"💡 Use --cleanup to remove orphaned processes")

        conn.close()

    except Exception as e:
        console.print(f"[red]❌ Error checking process status: {e}[/red]")


@main.command()
@click.option('--output-dir', '-o', type=click.Path(), required=True, help='Output directory containing the benchmark run')
@click.option('--dry-run', is_flag=True, help='Show what would be cleaned without actually doing it')
def cleanup_resume(output_dir, dry_run):
    """Clean up invalid benchmark results and resume the run."""
    console.print("🧹 [bold blue]Cleaning Invalid Benchmark Results and Resuming Run[/bold blue]")

    output_path = Path(output_dir)
    if not output_path.exists():
        console.print(f"❌ Output directory not found: {output_path}")
        return

    # Connect to database
    db_file = output_path / "jobs.db"
    if not db_file.exists():
        console.print(f"❌ Database not found: {db_file}")
        return

    try:
        from .database.job_queue import JobQueue
        queue = JobQueue(db_file)

        # Get the run ID from the output directory
        run_id = _cleanup_and_resume_run(queue, output_path, dry_run)

        if not dry_run and run_id:
            console.print(f"\n✅ [bold green]Run cleanup completed. Resuming run: {run_id}[/bold green]")
            console.print(f"💡 Use 'make bench-continue' or 'regex-bench job-continue --output-dir {output_dir}' to continue execution")

        queue.close()

    except Exception as e:
        console.print(f"[red]❌ Error during cleanup and resume: {e}[/red]")


def _cleanup_and_resume_run(queue: JobQueue, output_path: Path, dry_run: bool = False) -> Optional[str]:
    """Clean invalid results and prepare run for resumption."""
    import uuid
    from .job_runner import JobBasedBenchmarkRunner

    # Get the most recent run ID from this output directory
    cursor = queue.conn.execute("""
        SELECT run_id, status, config_json FROM benchmark_runs
        ORDER BY created_at DESC LIMIT 1
    """)
    run_info = cursor.fetchone()

    if not run_info:
        console.print("❌ No benchmark run found in database")
        return None

    run_id = run_info['run_id']
    current_status = run_info['status']
    config_json = run_info['config_json']

    console.print(f"📋 Found run: {run_id} (status: {current_status})")

    # Step 1: Identify invalid jobs
    invalid_jobs = _identify_invalid_jobs(queue, run_id)

    if not invalid_jobs:
        console.print("✅ No invalid jobs found - all results appear valid")
        return run_id

    # Show what will be cleaned
    console.print(f"\n🚨 Found {len(invalid_jobs)} invalid jobs:")
    cleanup_table = Table()
    cleanup_table.add_column("Engine", style="cyan")
    cleanup_table.add_column("Patterns", justify="right")
    cleanup_table.add_column("Corpus", justify="right")
    cleanup_table.add_column("Iteration", justify="right")
    cleanup_table.add_column("Issue", style="red")
    cleanup_table.add_column("Duration", justify="right")

    for job in invalid_jobs[:15]:  # Show first 15
        issue = "Missing match_count" if job['match_count'] is None else "Suspiciously fast"
        duration = f"{job['duration_seconds']:.3f}s" if job['duration_seconds'] else "N/A"
        cleanup_table.add_row(
            job['engine_name'],
            str(job['pattern_count']),
            job['input_size'],
            str(job['iteration']),
            issue,
            duration
        )

    if len(invalid_jobs) > 15:
        cleanup_table.add_row("...", f"({len(invalid_jobs) - 15} more)", "", "", "", "")

    console.print(cleanup_table)

    if dry_run:
        console.print(f"\n🔍 [bold yellow]DRY RUN - Would clean {len(invalid_jobs)} invalid jobs[/bold yellow]")
        return run_id

    # Step 2: Clean up invalid jobs
    console.print(f"\n🗑️  Cleaning up {len(invalid_jobs)} invalid jobs...")

    job_ids_to_delete = [job['job_id'] for job in invalid_jobs]

    # Delete invalid jobs in batches
    for i in range(0, len(job_ids_to_delete), 100):
        batch = job_ids_to_delete[i:i+100]
        placeholders = ','.join('?' * len(batch))
        queue.conn.execute(f"""
            DELETE FROM benchmark_jobs
            WHERE job_id IN ({placeholders})
        """, batch)

    queue.conn.commit()
    console.print(f"✅ Deleted {len(invalid_jobs)} invalid job records")

    # Step 3: Update run status back to RUNNING
    queue.conn.execute("""
        UPDATE benchmark_runs
        SET status = 'RUNNING', completed_at = NULL
        WHERE run_id = ?
    """, (run_id,))
    queue.conn.commit()

    console.print(f"✅ Reset run {run_id} status to RUNNING")

    # Step 4: Determine what jobs need to be re-queued
    if config_json:
        config = json.loads(config_json)
        _requeue_missing_jobs(queue, run_id, config, output_path)
    else:
        console.print("⚠️  No config found - cannot re-queue missing jobs automatically")

    return run_id


def _identify_invalid_jobs(queue: JobQueue, run_id: str) -> list:
    """Identify jobs that should be considered invalid."""
    # Critical engines that MUST have match_count when completed successfully
    critical_engines = ['rmatch', 're2j', 'java-native-unfair']

    cursor = queue.conn.execute("""
        SELECT job_id, engine_name, pattern_count, input_size, iteration,
               status, duration_seconds, match_count
        FROM benchmark_jobs
        WHERE run_id = ?
          AND engine_name IN ({})
          AND status = 'COMPLETED'
          AND (
              match_count IS NULL
              OR (pattern_count >= 1000 AND duration_seconds < 1.0)
              OR (pattern_count >= 10000 AND duration_seconds < 10.0)
          )
        ORDER BY engine_name, pattern_count, input_size, iteration
    """.format(','.join('?' * len(critical_engines))), [run_id] + critical_engines)

    return [dict(row) for row in cursor.fetchall()]


def _requeue_missing_jobs(queue: JobQueue, run_id: str, config: dict, output_path: Path):
    """Re-queue jobs that are missing after cleanup."""
    from .job_runner import JobBasedBenchmarkRunner

    # Get existing jobs to avoid duplicates
    cursor = queue.conn.execute("""
        SELECT DISTINCT engine_name, pattern_count, input_size, iteration
        FROM benchmark_jobs
        WHERE run_id = ? AND status IN ('COMPLETED', 'RUNNING', 'QUEUED')
    """, (run_id,))

    existing_jobs = set()
    for row in cursor:
        job_key = (row['engine_name'], row['pattern_count'], row['input_size'], row['iteration'])
        existing_jobs.add(job_key)

    console.print(f"📝 Found {len(existing_jobs)} existing valid jobs")

    # Generate the full job matrix and queue missing ones
    runner = JobBasedBenchmarkRunner(config, output_path)

    # Temporarily monkey patch to capture job generation without execution
    original_enqueue = queue.enqueue_job
    queued_count = 0

    def counting_enqueue(job):
        nonlocal queued_count
        job_key = (job.engine_name, job.pattern_count, job.input_size, job.iteration)
        if job_key not in existing_jobs:
            original_enqueue(job)
            queued_count += 1
        return job.job_id

    queue.enqueue_job = counting_enqueue

    # Generate and queue missing jobs
    try:
        runner._generate_jobs(queue, run_id)
        console.print(f"✅ Re-queued {queued_count} missing jobs")
    except Exception as e:
        console.print(f"⚠️  Error re-queueing jobs: {e}")
    finally:
        queue.enqueue_job = original_enqueue


if __name__ == '__main__':
    main()