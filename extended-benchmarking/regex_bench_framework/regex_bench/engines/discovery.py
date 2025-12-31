"""
Engine discovery utilities.
"""

from pathlib import Path
from typing import List, Dict, Any
import json

from .base import Engine
from .external import ExternalEngine


class EngineDiscovery:
    """Engine discovery and validation utilities."""

    @staticmethod
    def discover_engines_in_directory(engines_dir: Path) -> List[Engine]:
        """Discover all engines in a directory."""
        engines = []

        if not engines_dir.exists():
            return engines

        for engine_dir in engines_dir.iterdir():
            if not engine_dir.is_dir():
                continue

            config_file = engine_dir / "engine.json"
            if not config_file.exists():
                continue

            try:
                with open(config_file, 'r') as f:
                    config = json.load(f)

                engine = EngineDiscovery._create_engine_from_config(
                    engine_dir.name, config, engine_dir
                )

                if engine:
                    engines.append(engine)

            except Exception as e:
                print(f"Warning: Failed to load engine {engine_dir.name}: {e}")

        return engines

    @staticmethod
    def _create_engine_from_config(name: str, config: Dict[str, Any], base_path: Path) -> Engine:
        """Create engine from configuration."""
        engine_type = config.get("type", "external")

        if engine_type == "external":
            return ExternalEngine(name, config, base_path)
        else:
            raise ValueError(f"Unsupported engine type: {engine_type}")