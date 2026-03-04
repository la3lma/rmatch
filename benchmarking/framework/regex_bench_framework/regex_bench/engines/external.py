"""
External engine implementation for subprocess-based engines.
"""

import subprocess
import shutil
import re
import time
import os
import signal
from pathlib import Path
from typing import Dict, Any, Optional, List

# Conditional psutil import to handle architecture issues
try:
    import psutil
    PSUTIL_AVAILABLE = True
except ImportError as e:
    PSUTIL_AVAILABLE = False
    print(f"Warning: psutil not available ({e}). Memory monitoring disabled.")

from .base import Engine, EngineResult, EngineError


class ExternalEngine(Engine):
    """Engine that executes via external subprocess."""

    def __init__(self, name: str, config: Dict[str, Any], base_path: Path):
        super().__init__(name, config, base_path)
        self.command_template = self.metadata.get("command", [])
        self.requires = self.metadata.get("requires", [])
        self.build_command = self.metadata.get("build_command", [])
        self.output_format = self.metadata.get("output_format", {})

    def check_availability(self) -> Optional[str]:
        """Check if external engine is available."""
        # Check if disabled
        if self.metadata.get("disabled", False):
            return f"disabled: {self.metadata.get('disabled_reason', 'in config')}"

        # Check platform requirements
        platform_reqs = self.get_platform_requirements()
        if platform_reqs:
            platform_error = self._check_platform_requirements(platform_reqs)
            if platform_error:
                return platform_error

        # Check required binaries
        missing = []
        for binary in self.requires:
            if shutil.which(binary) is None:
                missing.append(binary)

        if missing:
            return f"missing required binaries: {', '.join(missing)}"

        # Check if build is needed
        if self.build_command and not self._is_built():
            return "not built - run build command first"

        return None

    def build(self, force: bool = False) -> bool:
        """Build the external engine."""
        if not self.build_command:
            return True  # No build step required

        if not force and self._is_built():
            return True  # Already built

        try:
            # Execute build command
            result = subprocess.run(
                self.build_command,
                cwd=self.base_path,
                capture_output=True,
                text=True,
                timeout=300  # 5 minute timeout
            )

            if result.returncode == 0:
                print(f"✓ Built {self.name} successfully")
                return True
            else:
                print(f"✗ Build failed for {self.name}:")
                print(f"  stdout: {result.stdout}")
                print(f"  stderr: {result.stderr}")
                return False

        except subprocess.TimeoutExpired:
            print(f"✗ Build timeout for {self.name}")
            return False
        except Exception as e:
            print(f"✗ Build error for {self.name}: {e}")
            return False

    def run(self, patterns_file: Path, corpus_file: Path,
            iteration: int, output_dir: Path) -> EngineResult:
        """Execute the external engine."""

        # Special handling for rmatch using file-based communication to avoid subprocess hanging
        if self.name == "rmatch":
            return self._run_rmatch_file_based(patterns_file, corpus_file, iteration, output_dir)

        # Standard pipe-based execution for other engines
        return self._run_pipe_based(patterns_file, corpus_file, iteration, output_dir)

    def _get_hard_timeout_seconds(self) -> float:
        """Get per-job hard timeout from config."""
        return float(self.config.get('execution_plan', {}).get('timeout_per_job', 72000))

    def _get_stall_timeout_seconds(self, hard_timeout_seconds: float) -> float:
        """
        Get per-job stall timeout from config.

        Stall timeout can be disabled by setting <= 0.
        It is bounded below hard timeout to avoid conflicting cutoffs.
        """
        raw = self.config.get('execution_plan', {}).get('stall_timeout_seconds', 1200)
        try:
            stall_timeout = float(raw)
        except (TypeError, ValueError):
            stall_timeout = 1200.0

        if stall_timeout <= 0:
            return 0.0

        # Keep a small margin for the hard timeout watchdog.
        return min(stall_timeout, max(1.0, hard_timeout_seconds - 30.0))

    def _get_process_cpu_seconds(self, psutil_process) -> Optional[float]:
        """Return cumulative CPU seconds for a process, if available."""
        if not PSUTIL_AVAILABLE or psutil_process is None:
            return None
        try:
            cpu_times = psutil_process.cpu_times()
            return float(cpu_times.user + cpu_times.system)
        except (psutil.NoSuchProcess, psutil.AccessDenied, AttributeError, ValueError):
            return None

    def _terminate_process_tree(self, process: subprocess.Popen, grace_seconds: float = 2.0) -> None:
        """Terminate process tree started in a separate session/process-group."""
        if process.poll() is not None:
            return

        pgid = None
        try:
            pgid = os.getpgid(process.pid)
        except Exception:
            pgid = None

        try:
            if pgid is not None:
                os.killpg(pgid, signal.SIGTERM)
            else:
                process.terminate()
        except ProcessLookupError:
            return
        except Exception:
            try:
                process.terminate()
            except Exception:
                pass

        deadline = time.monotonic() + max(0.0, grace_seconds)
        while time.monotonic() < deadline:
            if process.poll() is not None:
                return
            time.sleep(0.1)

        try:
            if pgid is not None:
                os.killpg(pgid, signal.SIGKILL)
            else:
                process.kill()
        except ProcessLookupError:
            return
        except Exception:
            try:
                process.kill()
            except Exception:
                pass

    def _collect_process_output(self, process: subprocess.Popen, timeout_seconds: float = 2.0) -> tuple[str, str]:
        """Collect stdout/stderr if available after process termination."""
        if process.stdout is None and process.stderr is None:
            return "", ""
        try:
            stdout, stderr = process.communicate(timeout=timeout_seconds)
            return stdout or "", stderr or ""
        except Exception:
            return "", ""

    def _run_rmatch_file_based(self, patterns_file: Path, corpus_file: Path,
                              iteration: int, output_dir: Path) -> EngineResult:
        """Execute rmatch using robust file-based completion detection with defensive safeguards."""

        # Prepare result
        result = EngineResult(
            engine_name=self.name,
            iteration=iteration,
            status="ok",
            corpus_size_bytes=corpus_file.stat().st_size if corpus_file.exists() else 0
        )

        # Calculate workload-aware settings for large rmatch jobs
        pattern_count = 0
        if patterns_file.exists():
            with open(patterns_file, 'r') as f:
                pattern_count = len([line.strip() for line in f if line.strip()])

        corpus_size_mb = corpus_file.stat().st_size / (1024 * 1024) if corpus_file.exists() else 0

        # Workload-aware grace period: scale with complexity to prevent data loss
        if pattern_count >= 10000:
            grace_period = 30.0  # 30 seconds for 10K+ patterns
        elif pattern_count >= 1000 and corpus_size_mb >= 1000:  # 1GB+
            grace_period = 20.0  # 20 seconds for 1K patterns + large corpus
        elif pattern_count >= 1000:
            grace_period = 15.0  # 15 seconds for 1K+ patterns
        else:
            grace_period = 5.0   # 5 seconds for smaller workloads

        try:
            # Create temporary output files
            import uuid

            temp_id = str(uuid.uuid4())[:8]
            stdout_file = output_dir / f"rmatch_stdout_{temp_id}.txt"
            stderr_file = output_dir / f"rmatch_stderr_{temp_id}.txt"

            # Use file-based runner
            command = [
                "bash", "file_runner.sh",
                str(patterns_file.resolve()),
                str(corpus_file.resolve()),
                str(stdout_file.resolve()),
                str(stderr_file.resolve())
            ]

            start_time = time.time_ns()

            # Start process without pipes
            process = subprocess.Popen(
                command,
                cwd=self.base_path,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                start_new_session=True
            )

            # Monitor process with psutil if available
            psutil_process = None
            if PSUTIL_AVAILABLE:
                try:
                    psutil_process = psutil.Process(process.pid)
                except Exception:
                    psutil_process = None

            # Polling-based completion detection
            # rmatch finishes but doesn't signal termination properly
            poll_interval = 0.1  # 100ms
            max_wait_time = self._get_hard_timeout_seconds()
            stall_timeout = self._get_stall_timeout_seconds(max_wait_time)
            start_monotonic = time.monotonic()
            last_progress_at = start_monotonic
            last_cpu_seconds = self._get_process_cpu_seconds(psutil_process)
            last_stdout_size = 0
            last_stderr_size = 0

            completion_detected = False

            while (time.monotonic() - start_monotonic) < max_wait_time:
                # Check if process is still alive
                poll_result = process.poll()
                if poll_result is not None:
                    # Process terminated normally
                    completion_detected = True
                    break

                # Check for output files indicating completion
                if stdout_file.exists() and stderr_file.exists():
                    try:
                        stdout_content = stdout_file.read_text()
                        stderr_content = stderr_file.read_text()

                        # Check for rmatch completion indicators
                        has_results = (
                            "COMPILATION_NS=" in stdout_content and
                            "ELAPSED_NS=" in stdout_content and
                            "MATCHES=" in stdout_content and
                            "PATTERNS_COMPILED=" in stdout_content
                        )

                        has_completion_msg = "Benchmark completed:" in stderr_content

                        if has_results and has_completion_msg:
                            # rmatch appears finished, but wait for internal engines to complete
                            # Use workload-aware grace period to prevent data loss
                            grace_start = time.time()

                            while time.time() - grace_start < grace_period:
                                # Check if process terminated naturally
                                if process.poll() is not None:
                                    completion_detected = True
                                    break
                                time.sleep(0.1)

                            if completion_detected:
                                break

                            # Grace period expired, force termination
                            self._terminate_process_tree(process, grace_seconds=0.5)
                            completion_detected = True
                            break

                    except (FileNotFoundError, PermissionError):
                        # Files not ready yet
                        pass

                now = time.monotonic()
                made_progress = False

                # CPU progress indicates active compute.
                current_cpu_seconds = self._get_process_cpu_seconds(psutil_process)
                if current_cpu_seconds is not None:
                    if last_cpu_seconds is None or current_cpu_seconds > (last_cpu_seconds + 0.05):
                        made_progress = True
                    last_cpu_seconds = current_cpu_seconds

                # Output-file growth also indicates progress.
                stdout_size = 0
                stderr_size = 0
                try:
                    if stdout_file.exists():
                        stdout_size = stdout_file.stat().st_size
                    if stderr_file.exists():
                        stderr_size = stderr_file.stat().st_size
                except OSError:
                    pass

                if stdout_size > last_stdout_size or stderr_size > last_stderr_size:
                    made_progress = True
                last_stdout_size = max(last_stdout_size, stdout_size)
                last_stderr_size = max(last_stderr_size, stderr_size)

                if made_progress:
                    last_progress_at = now
                elif stall_timeout > 0 and (now - last_progress_at) >= stall_timeout:
                    self._terminate_process_tree(process)
                    result.status = "timeout"
                    result.notes = (
                        f"Process stall timeout ({stall_timeout:.0f} seconds without CPU/output progress, "
                        f"hard timeout {max_wait_time:.0f} seconds)"
                    )
                    return result

                # Sleep before next poll
                time.sleep(poll_interval)

            end_time = time.time_ns()
            result.total_ns = end_time - start_time

            if not completion_detected:
                # Timeout - kill process and report
                self._terminate_process_tree(process)
                result.status = "timeout"
                result.notes = f"Process timeout after {max_wait_time} seconds"
                return result

            # Robust output capture with defensive safeguards
            stdout, stderr = self._read_rmatch_output_defensively(
                stdout_file, stderr_file, pattern_count, corpus_size_mb
            )

            # Record output
            result.raw_stdout = stdout
            result.raw_stderr = stderr

            # Verify we captured meaningful results before cleanup
            has_meaningful_output = (
                "MATCHES=" in stdout and
                "ELAPSED_NS=" in stdout and
                "PATTERNS_COMPILED=" in stdout
            )

            if not has_meaningful_output and completion_detected:
                # Data loss detected - attempt pipe-based fallback
                result.notes = f"File-based capture failed for {pattern_count} patterns × {corpus_size_mb:.1f}MB, attempting fallback"
                try:
                    # Clean up failed files first
                    if stdout_file.exists():
                        stdout_file.unlink()
                    if stderr_file.exists():
                        stderr_file.unlink()

                    # Fallback to pipe-based execution (if process still alive, kill it first)
                    try:
                        if process.poll() is None:
                            process.kill()
                            time.sleep(1.0)
                    except:
                        pass

                    return self._run_pipe_based(patterns_file, corpus_file, iteration, output_dir)
                except Exception as fallback_error:
                    result.status = "error"
                    result.notes += f"; fallback failed: {str(fallback_error)}"
                    return result

            # Parse output
            self._parse_output(stdout, stderr, result)

            # Only cleanup files after successful parsing
            try:
                if stdout_file.exists():
                    stdout_file.unlink()
                if stderr_file.exists():
                    stderr_file.unlink()
            except Exception as cleanup_error:
                # Don't fail the job due to cleanup issues
                pass

            # Get memory usage (approximate) if psutil available
            if psutil_process and PSUTIL_AVAILABLE:
                try:
                    memory_info = psutil_process.memory_info()
                    result.memory_peak_bytes = memory_info.rss
                except (psutil.NoSuchProcess, psutil.AccessDenied):
                    pass

            return result

        except Exception as e:
            result.status = "error"
            result.notes = f"Execution error: {str(e)}"
            return result

    def _read_rmatch_output_defensively(self, stdout_file: Path, stderr_file: Path,
                                      pattern_count: int, corpus_size_mb: float) -> tuple:
        """Defensively read rmatch output files with retries and verification."""
        max_attempts = 5
        base_delay = 0.5  # Base delay between attempts

        # Calculate expected file stabilization time based on workload
        if pattern_count >= 10000:
            stabilization_delay = 3.0  # Large workloads need more time
        elif pattern_count >= 1000 and corpus_size_mb >= 1000:
            stabilization_delay = 2.0  # Medium-large workloads
        else:
            stabilization_delay = 1.0  # Smaller workloads

        for attempt in range(max_attempts):
            try:
                # Wait for file stabilization on first attempt
                if attempt == 0:
                    time.sleep(stabilization_delay)

                # Check if files exist
                if not stdout_file.exists() or not stderr_file.exists():
                    if attempt < max_attempts - 1:
                        time.sleep(base_delay * (attempt + 1))
                        continue
                    else:
                        return "", ""  # Return empty if files never appear

                # Check file sizes to ensure they're not empty or still being written
                stdout_size = stdout_file.stat().st_size
                stderr_size = stderr_file.stat().st_size

                # Wait a bit and check if file sizes are stable (not still growing)
                time.sleep(0.2)
                stdout_size_after = stdout_file.stat().st_size if stdout_file.exists() else 0
                stderr_size_after = stderr_file.stat().st_size if stderr_file.exists() else 0

                # If files are still growing, wait and retry
                if stdout_size != stdout_size_after or stderr_size != stderr_size_after:
                    if attempt < max_attempts - 1:
                        time.sleep(base_delay * (attempt + 1))
                        continue

                # Read file contents
                stdout_content = stdout_file.read_text() if stdout_size > 0 else ""
                stderr_content = stderr_file.read_text() if stderr_size > 0 else ""

                # Verify we got meaningful content from rmatch
                if stdout_content and ("ELAPSED_NS=" in stdout_content or "MATCHES=" in stdout_content):
                    return stdout_content, stderr_content

                # If content seems incomplete, retry (unless last attempt)
                if attempt < max_attempts - 1:
                    time.sleep(base_delay * (attempt + 1))
                    continue
                else:
                    # Last attempt - return what we have
                    return stdout_content, stderr_content

            except (FileNotFoundError, PermissionError, OSError) as e:
                # File operation failed, retry if possible
                if attempt < max_attempts - 1:
                    time.sleep(base_delay * (attempt + 1))
                    continue
                else:
                    # Final attempt failed - return empty
                    return "", ""

        # Should never reach here, but defensive fallback
        return "", ""

    def _run_pipe_based(self, patterns_file: Path, corpus_file: Path,
                       iteration: int, output_dir: Path) -> EngineResult:
        """Standard pipe-based execution for non-rmatch engines."""

        # Format command
        command = self._format_command(patterns_file, corpus_file, output_dir)

        # Prepare result
        result = EngineResult(
            engine_name=self.name,
            iteration=iteration,
            status="ok",
            corpus_size_bytes=corpus_file.stat().st_size if corpus_file.exists() else 0
        )

        try:
            # Calculate dynamic timeout based on pattern count and corpus size
            pattern_count = 0
            if patterns_file.exists():
                with open(patterns_file, 'r') as f:
                    pattern_count = len([line.strip() for line in f if line.strip()])

            corpus_size_mb = corpus_file.stat().st_size / (1024 * 1024) if corpus_file.exists() else 0

            # Use configured timeout from execution plan - critical for rmatch performance measurement
            # Default to 20 hours if not configured, matching job timeout logic
            timeout_seconds = self._get_hard_timeout_seconds()
            stall_timeout = self._get_stall_timeout_seconds(timeout_seconds)

            # This accommodates large workloads like 10K patterns × 1GB corpus
            # No artificial scaling - just use a generous timeout for all workloads

            # Start process with resource monitoring
            start_time = time.time_ns()

            process = subprocess.Popen(
                command,
                cwd=self.base_path,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                stdin=subprocess.DEVNULL,  # Fix Java subprocess hanging issue
                text=True,
                start_new_session=True,  # Start in new process group for better cleanup
                preexec_fn=None  # Explicitly disable preexec_fn for compatibility
            )

            # Monitor process if psutil available
            psutil_process = None
            if PSUTIL_AVAILABLE:
                try:
                    psutil_process = psutil.Process(process.pid)
                except Exception:
                    psutil_process = None
            peak_memory = 0

            # Watch process in short communicate windows so we can detect stalls.
            start_monotonic = time.monotonic()
            last_progress_at = start_monotonic
            last_cpu_seconds = self._get_process_cpu_seconds(psutil_process)
            poll_timeout = 5.0
            if stall_timeout > 0:
                poll_timeout = min(5.0, max(1.0, stall_timeout / 6.0))

            stdout = ""
            stderr = ""
            while True:
                elapsed_seconds = time.monotonic() - start_monotonic
                remaining_hard_timeout = timeout_seconds - elapsed_seconds
                if remaining_hard_timeout <= 0:
                    self._terminate_process_tree(process)
                    stdout, stderr = self._collect_process_output(process)
                    result.raw_stdout = stdout
                    result.raw_stderr = stderr
                    result.status = "timeout"
                    result.notes = (
                        f"Process timeout ({timeout_seconds:.0f} seconds, "
                        f"for {pattern_count} patterns x {corpus_size_mb:.1f}MB corpus)"
                    )
                    return result

                try:
                    stdout, stderr = process.communicate(timeout=min(poll_timeout, remaining_hard_timeout))
                    break
                except subprocess.TimeoutExpired:
                    now = time.monotonic()
                    current_cpu_seconds = self._get_process_cpu_seconds(psutil_process)

                    if current_cpu_seconds is None:
                        # Without CPU telemetry we cannot safely enforce stall cutoffs.
                        last_progress_at = now
                        continue

                    if last_cpu_seconds is None or current_cpu_seconds > (last_cpu_seconds + 0.05):
                        last_progress_at = now
                    elif stall_timeout > 0 and (now - last_progress_at) >= stall_timeout:
                        self._terminate_process_tree(process)
                        stdout, stderr = self._collect_process_output(process)
                        result.raw_stdout = stdout
                        result.raw_stderr = stderr
                        result.status = "timeout"
                        result.notes = (
                            f"Process stall timeout ({stall_timeout:.0f} seconds without CPU progress, "
                            f"hard timeout {timeout_seconds:.0f} seconds, "
                            f"for {pattern_count} patterns x {corpus_size_mb:.1f}MB corpus)"
                        )
                        return result

                    last_cpu_seconds = current_cpu_seconds

            end_time = time.time_ns()
            result.total_ns = end_time - start_time

            # Record output
            result.raw_stdout = stdout
            result.raw_stderr = stderr

            # Check exit code
            if process.returncode != 0:
                result.status = f"exit={process.returncode}"
                result.notes = f"Process exited with code {process.returncode}"
                if stderr:
                    result.notes += f": {stderr[:200]}"
                return result

            # Parse output
            self._parse_output(stdout, stderr, result)

            # Get memory usage (approximate) if psutil available
            if psutil_process and PSUTIL_AVAILABLE:
                try:
                    memory_info = psutil_process.memory_info()
                    result.memory_peak_bytes = memory_info.rss
                except (psutil.NoSuchProcess, psutil.AccessDenied):
                    pass

            return result

        except subprocess.TimeoutExpired:
            self._terminate_process_tree(process)
            result.status = "timeout"
            result.notes = (
                f"Process timeout ({timeout_seconds:.0f} seconds, "
                f"for {pattern_count} patterns x {corpus_size_mb:.1f}MB corpus)"
            )
            return result

        except Exception as e:
            result.status = "error"
            result.notes = f"Execution error: {str(e)}"
            return result

    def _format_command(self, patterns_file: Path, corpus_file: Path, output_dir: Path) -> List[str]:
        """Format command template with actual paths."""
        replacements = {
            "patterns": str(patterns_file.resolve()),
            "corpus": str(corpus_file.resolve()),
            "output_dir": str(output_dir.resolve())
        }

        command = []
        for segment in self.command_template:
            formatted = segment.format(**replacements)
            command.append(formatted)

        return command

    def _parse_output(self, stdout: str, stderr: str, result: EngineResult) -> None:
        """Parse engine output for metrics."""
        combined_output = stdout + "\n" + stderr

        # Parse match count
        if "match_pattern" in self.output_format:
            pattern = self.output_format["match_pattern"]
            match = re.search(pattern, combined_output)
            if match:
                result.match_count = int(match.group(1))

        # Parse optional order-independent match checksum/fingerprint.
        checksum_pattern = self.output_format.get("checksum_pattern", r"MATCH_CHECKSUM=([0-9a-fA-F_\-:]+)")
        checksum_match = re.search(checksum_pattern, combined_output)
        if checksum_match:
            result.match_checksum = str(checksum_match.group(1))

        # Parse timing
        if "time_pattern" in self.output_format:
            pattern = self.output_format["time_pattern"]
            match = re.search(pattern, combined_output)
            if match:
                result.scanning_ns = int(match.group(1))

        # Parse compilation time
        if "compilation_pattern" in self.output_format:
            pattern = self.output_format["compilation_pattern"]
            match = re.search(pattern, combined_output)
            if match:
                result.compilation_ns = int(match.group(1))

        # Parse memory usage
        if "memory_pattern" in self.output_format:
            pattern = self.output_format["memory_pattern"]
            match = re.search(pattern, combined_output)
            if match:
                try:
                    value = float(match.group(1))
                    # Convert MB to bytes if pattern indicates MB
                    if "MB" in pattern:
                        result.memory_compilation_bytes = int(value * 1024 * 1024)
                    else:
                        result.memory_compilation_bytes = int(value)
                except ValueError:
                    pass

        # Parse patterns compiled count
        pattern_compiled_regex = self.output_format.get("patterns_compiled_pattern", r"PATTERNS_COMPILED=(\d+)")
        pattern_compiled_match = re.search(pattern_compiled_regex, combined_output)
        if pattern_compiled_match:
            result.patterns_compiled = int(pattern_compiled_match.group(1))

        pattern_failed_regex = self.output_format.get("patterns_failed_pattern", r"PATTERNS_FAILED=(\d+)")
        pattern_failed_match = re.search(pattern_failed_regex, combined_output)
        if pattern_failed_match:
            result.patterns_failed = int(pattern_failed_match.group(1))

        # Extract notes from output
        notes_lines = []
        for line in combined_output.split('\n'):
            line = line.strip()
            if line and not any(keyword in line for keyword in
                              ['MATCHES=', 'ELAPSED_NS=', 'COMPILATION_NS=', 'MEMORY_']):
                notes_lines.append(line)

        if notes_lines:
            result.notes = " ".join(notes_lines[:3])  # First 3 non-metric lines

    def _is_built(self) -> bool:
        """Check if engine appears to be built."""
        # Look for common build artifacts
        build_dir = self.base_path / ".build"
        if not build_dir.exists():
            return False

        # Check for executable files in build directory
        for item in build_dir.iterdir():
            if item.is_file() and os.access(item, os.X_OK):
                return True

        return False

    def _check_platform_requirements(self, requirements: Dict[str, Any]) -> Optional[str]:
        """Check if platform requirements are met."""
        import platform

        # Check architecture
        required_archs = requirements.get("architecture", [])
        if required_archs:
            current_arch = platform.machine().lower()
            if not any(arch in current_arch for arch in required_archs):
                return f"unsupported architecture {current_arch}, requires one of: {required_archs}"

        # Check OS
        required_os = requirements.get("os", [])
        if required_os:
            current_os = platform.system().lower()
            if current_os not in required_os:
                return f"unsupported OS {current_os}, requires one of: {required_os}"

        # Check CPU features (basic check)
        required_features = requirements.get("cpu_features", [])
        if required_features:
            # This is a simplified check - real feature detection would be more complex
            if "sse4_2" in required_features and platform.machine() not in ["x86_64", "AMD64"]:
                return f"requires CPU features: {required_features}"

        return None
