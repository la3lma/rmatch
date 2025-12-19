#!/usr/bin/env python3
"""
Generic benchmarking harness for regex engines (rmatch, java.util.regex, RE2J, Hyperscan, etc.).

The runner is intentionally lightweight: it uses only the Python standard library and executes
engines described in a JSON configuration file. External engines are executed via subprocess
commands that accept corpus and pattern paths as positional arguments.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import shutil
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Optional


@dataclass
class BenchmarkResult:
    engine: str
    iteration: int
    status: str
    wall_time_ms: float
    match_count: Optional[int]
    notes: str


class Engine:
    name: str
    description: str

    def __init__(self, name: str, description: str = "") -> None:
        self.name = name
        self.description = description

    def availability(self) -> Optional[str]:
        """Return None when available or a string reason when unavailable."""
        return None

    def run(self, patterns_path: Path, corpus_path: Path, iteration: int, output_dir: Path) -> BenchmarkResult:
        raise NotImplementedError


class PythonREEngine(Engine):
    def __init__(self, name: str, description: str = "") -> None:
        super().__init__(name, description)
        self._compiled_patterns: Optional[List[re.Pattern[str]]] = None

    def _load_patterns(self, patterns_path: Path) -> List[re.Pattern[str]]:
        if self._compiled_patterns is not None:
            return self._compiled_patterns
        compiled: List[re.Pattern[str]] = []
        for line in patterns_path.read_text().splitlines():
            striped = line.strip()
            if not striped:
                continue
            compiled.append(re.compile(striped))
        self._compiled_patterns = compiled
        return compiled

    def run(self, patterns_path: Path, corpus_path: Path, iteration: int, output_dir: Path) -> BenchmarkResult:  # noqa: ARG002
        patterns = self._load_patterns(patterns_path)
        corpus_lines = corpus_path.read_text().splitlines()
        start = time.perf_counter()
        match_count = 0
        for text in corpus_lines:
            for pattern in patterns:
                match_count += sum(1 for _ in pattern.finditer(text))
        elapsed_ms = (time.perf_counter() - start) * 1000
        return BenchmarkResult(
            engine=self.name,
            iteration=iteration,
            status="ok",
            wall_time_ms=elapsed_ms,
            match_count=match_count,
            notes="python.re baseline",
        )


class ExternalEngine(Engine):
    def __init__(
        self,
        name: str,
        command: List[str],
        description: str = "",
        requires: Optional[List[str]] = None,
        disabled: bool = False,
    ) -> None:
        super().__init__(name, description)
        self.command = command
        self.requires = requires or []
        self.disabled = disabled

    def availability(self) -> Optional[str]:
        if self.disabled:
            return "disabled in config"
        missing = [binary for binary in self.requires if shutil.which(binary) is None]
        if missing:
            return f"missing required binaries: {', '.join(missing)}"
        return None

    def _format_command(self, patterns_path: Path, corpus_path: Path, output_dir: Path) -> List[str]:
        replacements = {
            "patterns": str(patterns_path),
            "corpus": str(corpus_path),
            "output_dir": str(output_dir),
        }
        return [segment.format(**replacements) for segment in self.command]

    @staticmethod
    def _parse_match_count(output: str) -> Optional[int]:
        match = re.search(r"MATCHES=(\d+)", output)
        if match:
            return int(match.group(1))
        return None

    def run(self, patterns_path: Path, corpus_path: Path, iteration: int, output_dir: Path) -> BenchmarkResult:
        formatted = self._format_command(patterns_path, corpus_path, output_dir)
        start = time.perf_counter()
        proc = subprocess.run(
            formatted,
            capture_output=True,
            text=True,
            check=False,
        )
        elapsed_ms = (time.perf_counter() - start) * 1000
        combined_output = (proc.stdout or "") + (proc.stderr or "")
        match_count = self._parse_match_count(combined_output)
        status = "ok" if proc.returncode == 0 else f"exit={proc.returncode}"
        notes = " ".join(
            line.strip()
            for line in combined_output.splitlines()
            if line.strip()
        )
        if not notes:
            notes = "(no output)"
        return BenchmarkResult(
            engine=self.name,
            iteration=iteration,
            status=status,
            wall_time_ms=elapsed_ms,
            match_count=match_count,
            notes=notes,
        )


ENGINE_TYPES = {
    "python_re": PythonREEngine,
    "external": ExternalEngine,
}


def load_engines(config_path: Path) -> List[Engine]:
    config = json.loads(config_path.read_text())
    engines: List[Engine] = []
    for entry in config.get("engines", []):
        engine_type = entry.get("type")
        name = entry.get("name")
        description = entry.get("description", "")
        if not name or not engine_type:
            raise ValueError(f"Engine entry missing required fields: {entry}")
        engine_cls = ENGINE_TYPES.get(engine_type)
        if engine_cls is None:
            raise ValueError(f"Unsupported engine type '{engine_type}' for '{name}'")
        if engine_cls is PythonREEngine:
            engines.append(engine_cls(name=name, description=description))
        elif engine_cls is ExternalEngine:
            engines.append(
                engine_cls(
                    name=name,
                    description=description,
                    command=entry.get("command", []),
                    requires=entry.get("requires"),
                    disabled=entry.get("disabled", False),
                )
            )
    return engines


def write_results_csv(results: Iterable[BenchmarkResult], output_path: Path) -> None:
    fieldnames = ["engine", "iteration", "status", "wall_time_ms", "match_count", "notes"]
    with output_path.open("w", newline="") as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        for result in results:
            writer.writerow({
                "engine": result.engine,
                "iteration": result.iteration,
                "status": result.status,
                "wall_time_ms": f"{result.wall_time_ms:.3f}",
                "match_count": result.match_count if result.match_count is not None else "",
                "notes": result.notes,
            })


def write_results_json(results: Iterable[BenchmarkResult], output_path: Path) -> None:
    serializable = [
        {
            "engine": r.engine,
            "iteration": r.iteration,
            "status": r.status,
            "wall_time_ms": r.wall_time_ms,
            "match_count": r.match_count,
            "notes": r.notes,
        }
        for r in results
    ]
    output_path.write_text(json.dumps(serializable, indent=2))


def run_benchmarks(patterns: Path, corpus: Path, engines: List[Engine], iterations: int, output_dir: Path) -> List[BenchmarkResult]:
    output_dir.mkdir(parents=True, exist_ok=True)
    results: List[BenchmarkResult] = []
    for engine in engines:
        reason = engine.availability()
        if reason is not None:
            print(f"[SKIP] {engine.name}: {reason}")
            results.append(
                BenchmarkResult(
                    engine=engine.name,
                    iteration=0,
                    status="skipped",
                    wall_time_ms=0.0,
                    match_count=None,
                    notes=reason,
                )
            )
            continue
        for iteration in range(iterations):
            result = engine.run(patterns, corpus, iteration, output_dir)
            results.append(result)
            printable_count = result.match_count if result.match_count is not None else "?"
            print(
                f"[RUN ] {engine.name} iter={iteration} "
                f"time={result.wall_time_ms:.3f}ms matches={printable_count} status={result.status}"
            )
    return results


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Benchmark regex engines")
    parser.add_argument("--patterns", required=True, type=Path, help="Path to pattern file")
    parser.add_argument("--corpus", required=True, type=Path, help="Path to corpus file")
    parser.add_argument("--engines", required=True, type=Path, help="Path to engine configuration JSON")
    parser.add_argument("--output-dir", required=True, type=Path, help="Directory to write outputs")
    parser.add_argument("--iterations", type=int, default=3, help="Iterations to run per engine")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    engines = load_engines(args.engines)
    results = run_benchmarks(
        patterns=args.patterns,
        corpus=args.corpus,
        engines=engines,
        iterations=args.iterations,
        output_dir=args.output_dir,
    )
    write_results_csv(results, args.output_dir / "summary.csv")
    write_results_json(results, args.output_dir / "summary.json")


if __name__ == "__main__":
    main()
