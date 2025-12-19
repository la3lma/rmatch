"""
Main report generator for benchmark results.
"""

import json
import time
from pathlib import Path
from typing import Dict, Any, Optional

from .formatter import HTMLFormatter, MarkdownFormatter


class ReportGenerator:
    """Generate comprehensive reports from benchmark results."""

    def __init__(self):
        self.formatters = {
            'html': HTMLFormatter(),
            'markdown': MarkdownFormatter()
        }

    def generate(
        self,
        input_dir: Path,
        output_dir: Path,
        format: str = 'html',
        include_charts: bool = False
    ) -> Path:
        """Generate a comprehensive benchmark report."""

        # Load benchmark data
        data = self._load_benchmark_data(input_dir)

        # Generate report
        formatter = self.formatters.get(format)
        if not formatter:
            raise ValueError(f"Unsupported format: {format}")

        report_file = formatter.generate_report(
            data=data,
            output_dir=output_dir,
            include_charts=include_charts
        )

        return report_file

    def _load_benchmark_data(self, input_dir: Path) -> Dict[str, Any]:
        """Load all benchmark data from results directory."""
        data = {
            'metadata': {},
            'raw_results': [],
            'analysis': {},
            'summary': {}
        }

        # Load summary
        summary_file = input_dir / "summary.json"
        if summary_file.exists():
            with open(summary_file, 'r') as f:
                data['summary'] = json.load(f)

        # Load raw results
        raw_results_file = input_dir / "raw_results" / "benchmark_results.json"
        if raw_results_file.exists():
            with open(raw_results_file, 'r') as f:
                data['raw_results'] = json.load(f)

        # Load statistical analysis
        analysis_file = input_dir / "analysis" / "statistical_analysis.json"
        if analysis_file.exists():
            with open(analysis_file, 'r') as f:
                data['analysis'] = json.load(f)

        # Extract metadata from analysis
        if 'benchmark_metadata' in data['analysis']:
            data['metadata'] = data['analysis']['benchmark_metadata']

        return data