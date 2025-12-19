"""
Engine discovery and management.
"""

import json
from pathlib import Path
from typing import List, Dict, Any

from .base import Engine
from .external import ExternalEngine


class EngineManager:
    """Discover and manage regex engines."""

    def __init__(self, engines_dir: Path = None):
        if engines_dir is None:
            # Default to engines directory relative to this file
            engines_dir = Path(__file__).parent.parent.parent / "engines"

        self.engines_dir = Path(engines_dir)

    def discover_engines(self) -> List[Engine]:
        """Discover all available engines."""
        engines = []

        if not self.engines_dir.exists():
            return engines

        # Look for engine directories
        for engine_dir in self.engines_dir.iterdir():
            if not engine_dir.is_dir():
                continue

            # Check for engine.json configuration
            config_file = engine_dir / "engine.json"
            if not config_file.exists():
                continue

            try:
                # Load engine configuration
                with open(config_file, 'r') as f:
                    config = json.load(f)

                # Create engine instance
                engine = self._create_engine(engine_dir.name, config, engine_dir)
                if engine:
                    engines.append(engine)

            except Exception as e:
                print(f"Warning: Failed to load engine {engine_dir.name}: {e}")

        return engines

    def _create_engine(self, name: str, config: Dict[str, Any], base_path: Path) -> Engine:
        """Create an engine instance from configuration."""
        engine_type = config.get("type", "external")

        if engine_type == "external":
            return ExternalEngine(name, config, base_path)
        else:
            # For future: other engine types (builtin, python, etc.)
            raise ValueError(f"Unsupported engine type: {engine_type}")

    def get_engine(self, name: str) -> Engine:
        """Get a specific engine by name."""
        engines = self.discover_engines()

        for engine in engines:
            if engine.name == name:
                return engine

        raise ValueError(f"Engine not found: {name}")

    def build_all_engines(self, force: bool = False) -> Dict[str, bool]:
        """Build all available engines."""
        engines = self.discover_engines()
        results = {}

        for engine in engines:
            try:
                success = engine.build(force=force)
                results[engine.name] = success
                if success:
                    print(f"✓ Built {engine.name}")
                else:
                    print(f"✗ Failed to build {engine.name}")
            except Exception as e:
                results[engine.name] = False
                print(f"✗ Error building {engine.name}: {e}")

        return results

    def check_all_engines(self) -> Dict[str, str]:
        """Check availability of all engines."""
        engines = self.discover_engines()
        results = {}

        for engine in engines:
            availability = engine.check_availability()
            if availability is None:
                results[engine.name] = "available"
            else:
                results[engine.name] = f"unavailable: {availability}"

        return results