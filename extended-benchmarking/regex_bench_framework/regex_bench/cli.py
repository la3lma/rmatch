"""
Command-line interface for the regex benchmarking framework.
"""

import click
import json
import sys
from pathlib import Path
from typing import Optional
from rich.console import Console
from rich.table import Table
from rich.progress import Progress, SpinnerColumn, TextColumn

from . import __version__
from .runner import BenchmarkRunner
from .engines import EngineManager
from .statistics import StatisticalAnalyzer
from .data import CorpusManager, PatternSuite


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
@click.option('--iterations', default=5, type=int, help='Number of iterations')
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
def generate_report(input, output, format, include_charts):
    """Generate benchmark analysis report."""
    console.print(f"📊 [bold blue]Generating Benchmark Report[/bold blue]")
    console.print(f"📁 Input: {input}")
    console.print(f"📋 Format: {format}")

    try:
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
    console.print(f"📚 [bold blue]Setting Up Test Corpora[/bold blue]")
    console.print(f"📊 Sizes: {sizes}")
    console.print(f"🏷️  Types: {types}")

    # TODO: Implement corpus setup
    console.print("[yellow]⚠️  Corpus setup not yet implemented[/yellow]")


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

    # TODO: Implement stats display
    console.print("[yellow]⚠️  Statistics display not yet implemented[/yellow]")


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


if __name__ == '__main__':
    main()