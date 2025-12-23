"""
System information collection for benchmark metadata.
"""

import platform
import psutil
import subprocess
from typing import Dict, Any, Optional


class SystemInfo:
    """Collect comprehensive system information."""

    def get_info(self) -> Dict[str, Any]:
        """Get complete system information."""
        return {
            'platform': self._get_platform_info(),
            'hardware': self._get_hardware_info(),
            'software': self._get_software_info(),
            'resources': self._get_resource_info()
        }

    def _get_platform_info(self) -> Dict[str, Any]:
        """Get platform and OS information."""
        return {
            'system': platform.system(),
            'release': platform.release(),
            'version': platform.version(),
            'machine': platform.machine(),
            'processor': platform.processor(),
            'architecture': platform.architecture(),
            'platform_string': platform.platform(),
            'node': platform.node()
        }

    def _get_hardware_info(self) -> Dict[str, Any]:
        """Get hardware information."""
        hardware = {}

        try:
            # CPU information
            hardware['cpu'] = {
                'logical_cores': psutil.cpu_count(logical=True),
                'physical_cores': psutil.cpu_count(logical=False),
                'max_frequency': psutil.cpu_freq().max if psutil.cpu_freq() else None,
                'current_frequency': psutil.cpu_freq().current if psutil.cpu_freq() else None
            }

            # Get detailed CPU info on different platforms
            cpu_model = self._get_cpu_model()
            if cpu_model:
                hardware['cpu']['model'] = cpu_model

            # Memory information
            memory = psutil.virtual_memory()
            hardware['memory'] = {
                'total_bytes': memory.total,
                'total_gb': round(memory.total / (1024**3), 2),
                'available_bytes': memory.available,
                'available_gb': round(memory.available / (1024**3), 2)
            }

            # Disk information for temp space
            disk = psutil.disk_usage('/')
            hardware['disk'] = {
                'total_bytes': disk.total,
                'total_gb': round(disk.total / (1024**3), 2),
                'free_bytes': disk.free,
                'free_gb': round(disk.free / (1024**3), 2)
            }

        except Exception as e:
            hardware['error'] = f"Failed to collect hardware info: {e}"

        return hardware

    def _get_software_info(self) -> Dict[str, Any]:
        """Get software and compiler information."""
        software = {}

        # Python information
        software['python'] = {
            'version': platform.python_version(),
            'implementation': platform.python_implementation(),
            'compiler': platform.python_compiler()
        }

        # Check for important tools
        tools = ['java', 'javac', 'gcc', 'g++', 'go', 'make']
        software['tools'] = {}

        for tool in tools:
            version = self._get_tool_version(tool)
            software['tools'][tool] = version

        return software

    def _get_resource_info(self) -> Dict[str, Any]:
        """Get current resource usage."""
        try:
            return {
                'cpu_percent': psutil.cpu_percent(interval=1),
                'memory_percent': psutil.virtual_memory().percent,
                'load_average': psutil.getloadavg() if hasattr(psutil, 'getloadavg') else None,
                'boot_time': psutil.boot_time()
            }
        except Exception as e:
            return {'error': f"Failed to get resource info: {e}"}

    def _get_cpu_model(self) -> Optional[str]:
        """Get detailed CPU model information."""
        try:
            system = platform.system()

            if system == 'Darwin':  # macOS
                result = subprocess.run(
                    ['sysctl', '-n', 'machdep.cpu.brand_string'],
                    capture_output=True, text=True, timeout=5
                )
                if result.returncode == 0:
                    return result.stdout.strip()

            elif system == 'Linux':
                with open('/proc/cpuinfo', 'r') as f:
                    for line in f:
                        if line.startswith('model name'):
                            return line.split(':', 1)[1].strip()

        except Exception:
            pass

        return None

    def _get_tool_version(self, tool: str) -> Optional[str]:
        """Get version of a development tool."""
        try:
            # Different tools have different version flags
            version_flags = {
                'java': ['-version'],
                'javac': ['-version'],
                'gcc': ['--version'],
                'g++': ['--version'],
                'go': ['version'],
                'make': ['--version']
            }

            flags = version_flags.get(tool, ['--version'])

            result = subprocess.run(
                [tool] + flags,
                capture_output=True,
                text=True,
                timeout=5
            )

            if result.returncode == 0:
                # Extract first line which usually contains version
                output = result.stdout or result.stderr
                first_line = output.split('\n')[0]
                return first_line.strip()

        except Exception:
            pass

        return None