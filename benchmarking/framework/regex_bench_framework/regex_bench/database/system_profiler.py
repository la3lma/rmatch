"""
Enhanced system profiler for benchmark hardware/environment tracking.
Builds on the existing SystemInfo class with database integration.
"""

import json
import hashlib
import logging
import subprocess
import time
import importlib.metadata
import os
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Any

from ..utils.system_info import SystemInfo

logger = logging.getLogger(__name__)


class SystemProfiler:
    """
    Enhanced system profiler that collects comprehensive hardware and environment
    information for benchmark reproducibility and cross-system comparison.
    """

    def __init__(self):
        """Initialize profiler with existing SystemInfo."""
        self.system_info = SystemInfo()

    def collect_profile(self) -> Dict[str, Any]:
        """
        Collect complete system profile for database storage.
        Returns a dict compatible with the system_profiles database table.
        """
        logger.info("Collecting comprehensive system profile...")

        # Get basic system info from existing class
        basic_info = self.system_info.get_info()

        # Build enhanced profile
        profile = {
            # Basic identification
            'hostname': basic_info['platform']['node'],
            'profile_name': None,  # User can set this later

            # CPU information (enhanced)
            **self._enhance_cpu_info(basic_info),

            # Memory information (enhanced)
            **self._enhance_memory_info(basic_info),

            # Storage information
            **self._collect_storage_info(),

            # Operating system (enhanced)
            **self._enhance_os_info(basic_info),

            # Virtualization detection
            **self._detect_virtualization(),

            # Runtime environment (enhanced)
            **self._enhance_runtime_info(basic_info),

            # Performance baselines
            **self._collect_performance_baselines(),

            # Environment variables (filtered)
            **self._collect_env_variables(),

            # Profile metadata
            'profiled_at': datetime.now(),
            'profiled_by': os.getenv('USER', 'unknown'),
            'notes': f"Auto-generated profile at {datetime.now()}",

            # Raw system info (complete dump)
            'raw_system_info_json': json.dumps(basic_info, default=str, indent=2)
        }

        # Generate unique profile ID
        profile['profile_id'] = self._generate_profile_id(profile)

        # Add regex-bench version
        profile['regex_bench_version'] = self._get_package_version('regex-bench-framework')

        logger.info(f"System profile collected: {profile['profile_id'][:8]}...")
        logger.info(f"System: {profile['cpu_model']} | "
                   f"{profile['cpu_logical_cores']} cores | "
                   f"{profile['memory_total_gb']:.1f}GB RAM | "
                   f"{profile['os_name']} {profile['os_version']}")

        return profile

    def _enhance_cpu_info(self, basic_info: Dict) -> Dict:
        """Enhance CPU information with additional details."""
        hardware = basic_info.get('hardware', {})
        cpu = hardware.get('cpu', {})
        platform_info = basic_info.get('platform', {})

        cpu_info = {
            'cpu_model': cpu.get('model') or platform_info.get('processor') or 'Unknown',
            'cpu_architecture': platform_info.get('machine', 'unknown'),
            'cpu_physical_cores': cpu.get('physical_cores', 1),
            'cpu_logical_cores': cpu.get('logical_cores', 1),
            'cpu_vendor': None,  # Will be set platform-specifically
            'cpu_flags': None,
            'cpu_base_frequency_mhz': None,
            'cpu_max_frequency_mhz': cpu.get('max_frequency'),
            'cpu_l1_cache_kb': None,
            'cpu_l2_cache_kb': None,
            'cpu_l3_cache_mb': None
        }

        # Platform-specific CPU details
        try:
            system = platform_info.get('system')
            if system == 'Darwin':
                cpu_info.update(self._collect_macos_cpu_details())
            elif system == 'Linux':
                cpu_info.update(self._collect_linux_cpu_details())
        except Exception as e:
            logger.warning(f"Failed to collect platform-specific CPU details: {e}")

        return cpu_info

    def _enhance_memory_info(self, basic_info: Dict) -> Dict:
        """Enhance memory information."""
        hardware = basic_info.get('hardware', {})
        memory = hardware.get('memory', {})

        memory_info = {
            'memory_total_gb': memory.get('total_gb', 0.0),
            'memory_available_gb': memory.get('available_gb', 0.0),
            'memory_type': None,  # DDR4, DDR5, etc. - platform specific
            'memory_speed_mhz': None,
            'swap_total_gb': 0.0
        }

        # Try to get swap information
        try:
            import psutil
            swap = psutil.swap_memory()
            memory_info['swap_total_gb'] = round(swap.total / (1024**3), 2)
        except Exception as e:
            logger.warning(f"Failed to get swap info: {e}")

        return memory_info

    def _collect_storage_info(self) -> Dict:
        """Collect storage information for benchmark workspace."""
        storage_info = {
            'storage_type': None,
            'storage_available_gb': 0.0,
            'storage_filesystem': None,
            'storage_mount_path': str(Path.cwd())
        }

        try:
            import psutil
            disk_usage = psutil.disk_usage('.')
            storage_info['storage_available_gb'] = round(disk_usage.free / (1024**3), 2)

            # Try to determine storage type and filesystem
            system = os.uname().sysname
            if system == 'Darwin':
                storage_info.update(self._get_macos_storage_info())
            elif system == 'Linux':
                storage_info.update(self._get_linux_storage_info())

        except Exception as e:
            logger.warning(f"Failed to collect storage info: {e}")

        return storage_info

    def _enhance_os_info(self, basic_info: Dict) -> Dict:
        """Enhance operating system information."""
        platform_info = basic_info.get('platform', {})

        return {
            'os_name': platform_info.get('system', 'unknown'),
            'os_version': platform_info.get('release', 'unknown'),
            'os_release': platform_info.get('version', ''),
            'kernel_version': platform_info.get('version', ''),
            'os_architecture': platform_info.get('architecture', ['unknown'])[0]
        }

    def _detect_virtualization(self) -> Dict:
        """Detect virtualization and containerization."""
        virt_info = {
            'is_virtualized': False,
            'virtualization_type': None,
            'container_runtime': None,
            'container_image': None
        }

        # Check for Docker
        if Path('/.dockerenv').exists():
            virt_info.update({
                'is_virtualized': True,
                'virtualization_type': 'Docker',
                'container_runtime': 'docker',
                'container_image': os.environ.get('DOCKER_IMAGE', 'unknown')
            })

        # Check for other virtualization (Linux only)
        try:
            if os.uname().sysname == 'Linux':
                result = subprocess.run(['systemd-detect-virt'],
                                      capture_output=True, text=True, timeout=5)
                if result.returncode == 0:
                    virt_type = result.stdout.strip()
                    if virt_type != 'none':
                        virt_info.update({
                            'is_virtualized': True,
                            'virtualization_type': virt_type
                        })
        except Exception:
            pass

        return virt_info

    def _enhance_runtime_info(self, basic_info: Dict) -> Dict:
        """Enhance Python runtime and dependency information."""
        software = basic_info.get('software', {})
        python_info = software.get('python', {})

        runtime_info = {
            'python_version': python_info.get('version', 'unknown'),
            'python_implementation': python_info.get('implementation', 'unknown'),
            'python_compiler': python_info.get('compiler', 'unknown')
        }

        # Collect key dependencies
        key_packages = ['psutil', 'click', 'numpy', 'pandas', 'matplotlib', 'scipy']
        dependencies = {}

        for package in key_packages:
            version = self._get_package_version(package)
            dependencies[package] = version if version else 'not_installed'

        runtime_info['key_dependencies_json'] = json.dumps(dependencies, indent=2)

        return runtime_info

    def _collect_performance_baselines(self) -> Dict:
        """Run quick performance baselines for system health checking."""
        baselines = {}

        # CPU baseline: simple computation test
        try:
            start = time.perf_counter()
            result = sum(i * i for i in range(100000))
            duration = time.perf_counter() - start
            baselines['benchmark_baseline_score'] = round(duration * 1000, 2)  # milliseconds
        except Exception as e:
            logger.warning(f"Failed CPU baseline: {e}")
            baselines['benchmark_baseline_score'] = None

        # Memory allocation test
        try:
            start = time.perf_counter()
            data = [i for i in range(1000000)]
            duration = time.perf_counter() - start
            del data
            baselines['memory_baseline_ms'] = round(duration * 1000, 2)
        except Exception as e:
            logger.warning(f"Failed memory baseline: {e}")

        # Thermal throttling detection (placeholder)
        baselines['thermal_throttling_detected'] = False

        return baselines

    def _collect_env_variables(self) -> Dict:
        """Collect relevant environment variables."""
        relevant_vars = [
            'PATH', 'JAVA_HOME', 'PYTHONPATH', 'HOME', 'USER',
            'CC', 'CXX', 'MAKEFLAGS', 'CFLAGS', 'CXXFLAGS'
        ]

        env_vars = {}
        for var in relevant_vars:
            value = os.environ.get(var)
            if value:
                # Truncate very long values (like PATH)
                if len(value) > 1000:
                    env_vars[var] = value[:500] + '...[truncated]...' + value[-500:]
                else:
                    env_vars[var] = value

        return {'env_variables_json': json.dumps(env_vars, indent=2)}

    def _generate_profile_id(self, profile: Dict) -> str:
        """Generate unique profile ID based on hardware fingerprint."""
        # Create fingerprint from hardware-specific fields
        fingerprint_fields = [
            'hostname', 'cpu_model', 'cpu_architecture',
            'cpu_physical_cores', 'cpu_logical_cores',
            'memory_total_gb', 'os_name', 'os_version'
        ]

        fingerprint_data = {k: profile.get(k) for k in fingerprint_fields}
        fingerprint_json = json.dumps(fingerprint_data, sort_keys=True)

        return hashlib.sha256(fingerprint_json.encode()).hexdigest()

    # Platform-specific helper methods

    def _collect_macos_cpu_details(self) -> Dict:
        """Collect macOS-specific CPU information."""
        details = {}

        sysctl_mappings = {
            'cpu_vendor': 'machdep.cpu.vendor',
            'cpu_l1_cache_kb': 'hw.l1icachesize',
            'cpu_l2_cache_kb': 'hw.l2cachesize',
            'cpu_l3_cache_mb': 'hw.l3cachesize'
        }

        for key, sysctl_key in sysctl_mappings.items():
            try:
                result = subprocess.run(['sysctl', '-n', sysctl_key],
                                      capture_output=True, text=True, timeout=5)
                if result.returncode == 0:
                    value = result.stdout.strip()
                    if 'cache' in key and 'mb' in key:
                        details[key] = int(value) // (1024 * 1024) if value.isdigit() else None
                    elif 'cache' in key:
                        details[key] = int(value) // 1024 if value.isdigit() else None
                    else:
                        details[key] = value
            except Exception:
                pass

        return details

    def _collect_linux_cpu_details(self) -> Dict:
        """Collect Linux-specific CPU information."""
        details = {}

        try:
            with open('/proc/cpuinfo', 'r') as f:
                for line in f:
                    if ':' in line:
                        key, value = line.split(':', 1)
                        key = key.strip().lower()
                        value = value.strip()

                        if key == 'vendor_id':
                            details['cpu_vendor'] = value
                        elif key == 'flags':
                            details['cpu_flags'] = value
                            break  # Only need first occurrence
        except Exception:
            pass

        return details

    def _get_macos_storage_info(self) -> Dict:
        """Get macOS storage type information."""
        storage_info = {}

        try:
            result = subprocess.run(['diskutil', 'info', '/'],
                                  capture_output=True, text=True, timeout=10)
            if result.returncode == 0:
                output = result.stdout
                if 'Solid State' in output:
                    storage_info['storage_type'] = 'SSD'
                elif 'Rotational' in output:
                    storage_info['storage_type'] = 'HDD'

                # Extract filesystem
                for line in output.split('\n'):
                    if 'File System Personality' in line:
                        fs = line.split(':')[-1].strip()
                        storage_info['storage_filesystem'] = fs
                        break
        except Exception:
            pass

        return storage_info

    def _get_linux_storage_info(self) -> Dict:
        """Get Linux storage type information."""
        storage_info = {}

        try:
            # Try to determine if SSD or HDD
            result = subprocess.run(['lsblk', '-d', '-o', 'name,rota'],
                                  capture_output=True, text=True, timeout=5)
            if result.returncode == 0:
                lines = result.stdout.strip().split('\n')[1:]  # Skip header
                if lines and '0' in lines[0]:  # rotational = 0 means SSD
                    storage_info['storage_type'] = 'SSD'
                elif lines and '1' in lines[0]:
                    storage_info['storage_type'] = 'HDD'

            # Get filesystem type
            result = subprocess.run(['df', '-T', '.'],
                                  capture_output=True, text=True, timeout=5)
            if result.returncode == 0:
                lines = result.stdout.strip().split('\n')
                if len(lines) > 1:
                    fs_type = lines[1].split()[1]
                    storage_info['storage_filesystem'] = fs_type
        except Exception:
            pass

        return storage_info

    def _get_package_version(self, package_name: str) -> Optional[str]:
        """Get version of an installed package."""
        try:
            return importlib.metadata.version(package_name)
        except (importlib.metadata.PackageNotFoundError, Exception):
            return None