"""
External engine implementation for subprocess-based engines.
"""

import subprocess
import shutil
import re
import time
import psutil
import os
from pathlib import Path
from typing import Dict, Any, Optional, List

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

    def _run_rmatch_file_based(self, patterns_file: Path, corpus_file: Path,
                              iteration: int, output_dir: Path) -> EngineResult:
        """Execute rmatch using polling-based completion detection to bypass subprocess hanging."""

        # Prepare result
        result = EngineResult(
            engine_name=self.name,
            iteration=iteration,
            status="ok",
            corpus_size_bytes=corpus_file.stat().st_size if corpus_file.exists() else 0
        )

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

            # Monitor process with psutil
            psutil_process = psutil.Process(process.pid)

            # Polling-based completion detection
            # rmatch finishes but doesn't signal termination properly
            poll_interval = 0.1  # 100ms
            max_wait_time = 30.0  # 30 seconds
            elapsed = 0.0

            completion_detected = False

            while elapsed < max_wait_time:
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
                            # rmatch finished! Force process termination
                            try:
                                process.terminate()
                                # Give it a moment to terminate gracefully
                                time.sleep(0.1)
                                if process.poll() is None:
                                    process.kill()
                            except:
                                pass
                            completion_detected = True
                            break

                    except (FileNotFoundError, PermissionError):
                        # Files not ready yet
                        pass

                # Sleep before next poll
                time.sleep(poll_interval)
                elapsed += poll_interval

            end_time = time.time_ns()
            result.total_ns = end_time - start_time

            if not completion_detected:
                # Timeout - kill process and report
                try:
                    process.kill()
                except:
                    pass
                result.status = "timeout"
                result.notes = f"Process timeout after {max_wait_time} seconds"
                return result

            # Read output from files
            stdout = ""
            stderr = ""

            if stdout_file.exists():
                stdout = stdout_file.read_text()
                stdout_file.unlink()  # Clean up

            if stderr_file.exists():
                stderr = stderr_file.read_text()
                stderr_file.unlink()  # Clean up

            # Record output
            result.raw_stdout = stdout
            result.raw_stderr = stderr

            # Parse output
            self._parse_output(stdout, stderr, result)

            # Get memory usage (approximate)
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

            # Monitor process
            psutil_process = psutil.Process(process.pid)
            peak_memory = 0

            # Wait for completion with aggressive timeout for hanging processes
            # rmatch should complete in ~400ms, so 30 seconds is generous but catches hangs
            stdout, stderr = process.communicate(timeout=30)  # 30 second timeout to catch hanging Java processes

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

            # Get memory usage (approximate)
            try:
                memory_info = psutil_process.memory_info()
                result.memory_peak_bytes = memory_info.rss
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                pass

            return result

        except subprocess.TimeoutExpired:
            process.kill()
            result.status = "timeout"
            result.notes = "Process timeout (30 seconds)"
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
        pattern_compiled_match = re.search(r"PATTERNS_COMPILED=(\d+)", combined_output)
        if pattern_compiled_match:
            result.patterns_compiled = int(pattern_compiled_match.group(1))

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