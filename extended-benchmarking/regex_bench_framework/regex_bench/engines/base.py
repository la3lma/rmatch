"""
Base classes for regex engine integration.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Any
import time


@dataclass
class EngineResult:
    """Results from a single benchmark run."""
    engine_name: str
    iteration: int
    status: str  # "ok", "error", "timeout", "skipped"

    # Timing data (nanoseconds)
    compilation_ns: Optional[int] = None
    scanning_ns: Optional[int] = None
    total_ns: Optional[int] = None

    # Match data
    match_count: Optional[int] = None
    match_checksum: Optional[str] = None

    # Resource usage
    memory_peak_bytes: Optional[int] = None
    memory_compilation_bytes: Optional[int] = None
    cpu_user_ms: Optional[int] = None
    cpu_system_ms: Optional[int] = None

    # Metadata
    patterns_compiled: Optional[int] = None
    patterns_failed: Optional[int] = None
    corpus_size_bytes: Optional[int] = None
    notes: str = ""

    # Raw output for debugging
    raw_stdout: str = ""
    raw_stderr: str = ""


class EngineError(Exception):
    """Exception raised during engine execution."""
    def __init__(self, message: str, engine_name: str, exit_code: Optional[int] = None):
        super().__init__(message)
        self.engine_name = engine_name
        self.exit_code = exit_code


class Engine(ABC):
    """Abstract base class for regex engines."""

    def __init__(self, name: str, config: Dict[str, Any], base_path: Path):
        self.name = name
        self.config = config
        self.base_path = base_path
        self.metadata = self._load_metadata()

    @abstractmethod
    def check_availability(self) -> Optional[str]:
        """
        Check if engine is available and properly configured.

        Returns:
            None if available, or error message string if not available.
        """
        pass

    @abstractmethod
    def build(self, force: bool = False) -> bool:
        """
        Build/compile the engine if necessary.

        Args:
            force: Force rebuild even if already built

        Returns:
            True if build succeeded, False otherwise
        """
        pass

    @abstractmethod
    def run(self, patterns_file: Path, corpus_file: Path,
            iteration: int, output_dir: Path) -> EngineResult:
        """
        Execute the engine on given patterns and corpus.

        Args:
            patterns_file: Path to file containing patterns (one per line)
            corpus_file: Path to corpus file to search
            iteration: Iteration number for this run
            output_dir: Directory for temporary/output files

        Returns:
            EngineResult with timing, match, and resource data
        """
        pass

    def _load_metadata(self) -> Dict[str, Any]:
        """Load engine metadata from engine.json."""
        metadata_file = self.base_path / "engine.json"
        if metadata_file.exists():
            import json
            return json.loads(metadata_file.read_text())
        return {}

    def get_version(self) -> str:
        """Get engine version string."""
        return self.metadata.get("version", "unknown")

    def get_description(self) -> str:
        """Get engine description."""
        return self.metadata.get("description", "")

    def supports_feature(self, feature: str) -> bool:
        """Check if engine supports a specific regex feature."""
        characteristics = self.metadata.get("characteristics", {})
        return characteristics.get(feature, False)

    def get_platform_requirements(self) -> Dict[str, Any]:
        """Get platform requirements for this engine."""
        return self.metadata.get("platform_requirements", {})