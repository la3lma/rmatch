"""
Reporting and visualization module for benchmark results.

Provides comprehensive reporting capabilities including:
- HTML reports with interactive charts
- Statistical analysis summaries
- Performance comparison tables
- Engine characteristic overviews
"""

from .generator import ReportGenerator
from .formatter import HTMLFormatter, MarkdownFormatter

__all__ = [
    "ReportGenerator",
    "HTMLFormatter",
    "MarkdownFormatter"
]