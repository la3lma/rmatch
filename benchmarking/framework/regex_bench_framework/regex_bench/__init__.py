"""
Regex Benchmarking Framework 2.0

A scientific, comprehensive framework for comparing regex engine performance
across realistic workloads and pattern sets.
"""

__version__ = "2.0.0"
__author__ = "Regex Benchmarking Project"

from .runner import BenchmarkRunner
from .engines import EngineManager, Engine
from .statistics import StatisticalAnalyzer
from .data import CorpusManager, PatternSuite

__all__ = [
    "BenchmarkRunner",
    "EngineManager",
    "Engine",
    "StatisticalAnalyzer",
    "CorpusManager",
    "PatternSuite"
]