"""
Engine management and discovery system.

Provides unified interface for all regex engines with:
- Automatic discovery and validation
- Standardized execution interface
- Platform compatibility checking
- Performance profiling
"""

from .base import Engine, EngineResult, EngineError
from .manager import EngineManager
from .discovery import EngineDiscovery
from .external import ExternalEngine

__all__ = [
    "Engine",
    "EngineResult",
    "EngineError",
    "EngineManager",
    "EngineDiscovery",
    "ExternalEngine"
]