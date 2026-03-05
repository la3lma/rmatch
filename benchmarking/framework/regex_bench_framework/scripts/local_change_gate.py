#!/usr/bin/env python3
"""Local performance gate for fast idea validation.

Workflow:
1) Baseline on a known-good branch (typically main):
   - smoke correctness checks
   - moderate-load benchmark probe (10K patterns, 1MB/10MB)
2) Candidate run after a change:
   - same checks
   - fail if runtime regresses above threshold vs baseline
"""

from __future__ import annotations

import argparse
import json
import shutil
import sqlite3
import statistics
import subprocess
import sys
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


FRAMEWORK_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = FRAMEWORK_DIR.parents[2]
BASELINE_MANIFEST = FRAMEWORK_DIR / ".gate" / "local_gate_baseline.json"


@dataclass(frozen=True)
class RunSummary:
    run_dir: Path
    run_id: str
    engine: str
    pattern_count: int
    input_sizes: list[str]
    metric: str
    medians_ns: dict[str, float]


def _run(cmd: list[str], cwd: Path) -> None:
    print(f"+ ({cwd}) {' '.join(cmd)}")
    subprocess.run(cmd, cwd=str(cwd), check=True)


def _run_capture(cmd: list[str], cwd: Path) -> str:
    out = subprocess.check_output(cmd, cwd=str(cwd), text=True)
    return out.strip()


def _current_branch() -> str:
    return _run_capture(["git", "rev-parse", "--abbrev-ref", "HEAD"], REPO_ROOT)


def _resolve_config(config_value: str) -> Path:
    candidate = Path(config_value)
    if candidate.is_absolute():
        return candidate
    return FRAMEWORK_DIR / candidate


def _load_config(config_path: Path) -> tuple[int, list[str]]:
    cfg = json.loads(config_path.read_text(encoding="utf-8"))
    matrix = cfg.get("test_matrix", {})
    pattern_counts = matrix.get("pattern_counts", [])
    input_sizes = matrix.get("input_sizes", [])
    if len(pattern_counts) != 1:
        raise ValueError(
            f"Gate config must contain exactly one pattern count, got: {pattern_counts}"
        )
    if not input_sizes:
        raise ValueError("Gate config must contain at least one input size")
    return int(pattern_counts[0]), [str(x) for x in input_sizes]


def _run_smoke_checks() -> None:
    print("\n== Smoke checks (build + unit smoke + tiny corpus smoke) ==")
    _run(
        ["./mvnw", "-q", "-B", "-DskipTests", "-Dspotbugs.skip=true", "clean", "package"],
        REPO_ROOT,
    )
    _run(
        [
            "./mvnw",
            "-q",
            "-B",
            "-pl",
            "rmatch",
            "-Dtest=no.rmz.rmatch.ordinaryuse.OrdinaryUsageSmokeTest",
            "test",
        ],
        REPO_ROOT,
    )
    _run(
        [
            "./mvnw",
            "-q",
            "-B",
            "-pl",
            "rmatch-tester",
            "exec:java",
            "-Dexec.mainClass=no.rmz.rmatch.performancetests.BenchmarkTheWutheringHeightsCorpusWithMemory",
            "-Dexec.args=25",
        ],
        REPO_ROOT,
    )


def _rebuild_engine_for_probe(engine: str) -> None:
    """Rebuild engine artifacts from the currently checked-out branch."""
    print("\n== Rebuild engine artifacts for probe ==")
    if engine == "rmatch":
        # Ensure the snapshot consumed by benchmark engine build is fresh.
        _run(
            [
                "./mvnw",
                "-q",
                "-B",
                "-pl",
                "rmatch",
                "-am",
                "clean",
                "install",
                "-DskipTests",
                "-Dmaven.test.skip=true",
            ],
            REPO_ROOT,
        )

    engine_dir = FRAMEWORK_DIR / "engines" / engine
    build_script = engine_dir / "build.sh"
    build_dir = engine_dir / ".build"

    if build_dir.exists():
        shutil.rmtree(build_dir)

    if build_script.exists():
        _run(["bash", "build.sh"], engine_dir)
    else:
        print(f"No build script for engine '{engine}', skipping explicit engine rebuild.")


def _run_probe(config_path: Path, label: str) -> Path:
    regex_bench = FRAMEWORK_DIR / ".venv" / "bin" / "regex-bench"
    if not regex_bench.exists():
        raise FileNotFoundError(
            f"Missing {regex_bench}. Run `make -C {FRAMEWORK_DIR} setup` first."
        )
    timestamp = datetime.now(UTC).strftime("%Y%m%d_%H%M%S")
    out_dir = FRAMEWORK_DIR / "results" / f"local_gate_{label}_{timestamp}"
    print(f"\n== Benchmark probe ({label}) ==")
    _run(
        [
            str(regex_bench),
            "run-phase",
            "--config",
            str(config_path),
            "--output",
            str(out_dir),
        ],
        FRAMEWORK_DIR,
    )
    return out_dir


def _load_summary(
    run_dir: Path,
    *,
    engine: str,
    metric: str,
    pattern_count: int,
    input_sizes: list[str],
) -> RunSummary:
    db_path = run_dir / "jobs.db"
    if not db_path.exists():
        raise FileNotFoundError(f"Expected database not found: {db_path}")

    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    try:
        row = conn.execute(
            "SELECT run_id FROM benchmark_runs ORDER BY created_at DESC LIMIT 1"
        ).fetchone()
        if row is None:
            raise RuntimeError(f"No benchmark_runs rows found in {db_path}")
        run_id = str(row["run_id"])

        medians: dict[str, float] = {}
        for input_size in input_sizes:
            rows = conn.execute(
                f"""
                SELECT status, {metric} AS metric_value
                FROM benchmark_jobs
                WHERE run_id = ?
                  AND engine_name = ?
                  AND pattern_count = ?
                  AND input_size = ?
                """,
                (run_id, engine, pattern_count, input_size),
            ).fetchall()

            if not rows:
                raise RuntimeError(
                    f"No jobs found for {engine}/{pattern_count}/{input_size} in {db_path}"
                )

            bad_statuses = {
                str(r["status"])
                for r in rows
                if str(r["status"]) in {"FAILED", "TIMEOUT", "CANCELLED"}
            }
            if bad_statuses:
                raise RuntimeError(
                    f"Non-success statuses for {engine}/{pattern_count}/{input_size}: {sorted(bad_statuses)}"
                )

            values = [
                int(r["metric_value"])
                for r in rows
                if str(r["status"]) == "COMPLETED" and r["metric_value"] is not None
            ]
            if not values:
                raise RuntimeError(
                    f"No completed metric values ({metric}) for {engine}/{pattern_count}/{input_size}"
                )

            medians[input_size] = float(statistics.median(values))

        return RunSummary(
            run_dir=run_dir,
            run_id=run_id,
            engine=engine,
            pattern_count=pattern_count,
            input_sizes=input_sizes,
            metric=metric,
            medians_ns=medians,
        )
    finally:
        conn.close()


def _save_baseline_manifest(summary: RunSummary, config_path: Path) -> None:
    BASELINE_MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    git_sha = _run_capture(["git", "rev-parse", "HEAD"], REPO_ROOT)
    git_branch = _run_capture(["git", "rev-parse", "--abbrev-ref", "HEAD"], REPO_ROOT)
    payload = {
        "version": 1,
        "created_at_utc": datetime.now(UTC).isoformat(),
        "git_sha": git_sha,
        "git_branch": git_branch,
        "run_dir": str(summary.run_dir),
        "run_id": summary.run_id,
        "engine": summary.engine,
        "pattern_count": summary.pattern_count,
        "input_sizes": summary.input_sizes,
        "metric": summary.metric,
        "medians_ns": summary.medians_ns,
        "config_path": str(config_path),
    }
    BASELINE_MANIFEST.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(f"\nSaved baseline manifest: {BASELINE_MANIFEST}")


def _load_baseline_manifest() -> dict[str, Any]:
    if not BASELINE_MANIFEST.exists():
        raise FileNotFoundError(
            f"Baseline manifest not found: {BASELINE_MANIFEST}. "
            "Run baseline mode first."
        )
    return json.loads(BASELINE_MANIFEST.read_text(encoding="utf-8"))


def _print_comparison(
    baseline: RunSummary, candidate: RunSummary, max_slowdown: float
) -> tuple[bool, list[str]]:
    print("\n== Regression check ==")
    print(
        f"Threshold: candidate <= {max_slowdown:.2f}x baseline "
        f"(metric: {candidate.metric}, engine: {candidate.engine})"
    )
    print("")
    print(
        f"{'Input':<8} {'Baseline (ms)':>14} {'Candidate (ms)':>15} "
        f"{'Ratio':>8} {'Result':>8}"
    )
    print("-" * 62)

    failures: list[str] = []
    all_ok = True

    for input_size in candidate.input_sizes:
        b = baseline.medians_ns[input_size]
        c = candidate.medians_ns[input_size]
        ratio = c / b if b > 0 else float("inf")
        ok = ratio <= max_slowdown
        all_ok = all_ok and ok
        result = "PASS" if ok else "FAIL"
        print(
            f"{input_size:<8} {b/1e6:>14.3f} {c/1e6:>15.3f} "
            f"{ratio:>8.3f} {result:>8}"
        )
        if not ok:
            failures.append(
                f"{input_size}: {ratio:.3f}x exceeds {max_slowdown:.2f}x threshold"
            )

    return all_ok, failures


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Local change gate for correctness + performance.")
    sub = parser.add_subparsers(dest="mode", required=True)

    def add_common(p: argparse.ArgumentParser) -> None:
        p.add_argument(
            "--config",
            default="test_matrix/stable_10k_moderate_rmatch.json",
            help="Benchmark framework config path (relative to framework dir unless absolute).",
        )
        p.add_argument("--engine", default="rmatch", help="Engine to gate on.")
        p.add_argument(
            "--metric",
            default="scanning_ns",
            choices=["scanning_ns", "total_ns"],
            help="Runtime metric used for regression check.",
        )
        p.add_argument(
            "--skip-smoke",
            action="store_true",
            help="Skip smoke correctness checks (not recommended).",
        )
        p.add_argument(
            "--baseline-branch",
            default="main",
            help="Branch name used as regression baseline (default: main).",
        )
        p.add_argument(
            "--skip-rebuild",
            action="store_true",
            help="Skip explicit engine rebuild before benchmark probe.",
        )

    baseline = sub.add_parser("baseline", help="Create/refresh baseline run and save manifest.")
    add_common(baseline)

    candidate = sub.add_parser("candidate", help="Run candidate gate against saved baseline.")
    add_common(candidate)
    candidate.add_argument(
        "--baseline-dir",
        default="",
        help="Override baseline run directory (otherwise use saved manifest).",
    )
    candidate.add_argument(
        "--max-slowdown",
        type=float,
        default=1.10,
        help="Maximum allowed slowdown ratio (candidate/baseline).",
    )

    return parser.parse_args()


def main() -> int:
    args = _parse_args()
    config_path = _resolve_config(args.config)
    if not config_path.exists():
        print(f"Config file not found: {config_path}", file=sys.stderr)
        return 2

    try:
        pattern_count, input_sizes = _load_config(config_path)
    except Exception as exc:
        print(f"Invalid gate config: {exc}", file=sys.stderr)
        return 2

    if not args.skip_smoke:
        _run_smoke_checks()
    else:
        print("Skipping smoke checks (--skip-smoke)")
    if not args.skip_rebuild:
        _rebuild_engine_for_probe(args.engine)
    else:
        print("Skipping engine rebuild (--skip-rebuild)")

    if args.mode == "baseline":
        current_branch = _current_branch()
        if current_branch != args.baseline_branch:
            print(
                f"Refusing baseline capture from branch '{current_branch}'. "
                f"Expected baseline branch '{args.baseline_branch}'.",
                file=sys.stderr,
            )
            return 2
        run_dir = _run_probe(config_path, label="baseline")
        summary = _load_summary(
            run_dir,
            engine=args.engine,
            metric=args.metric,
            pattern_count=pattern_count,
            input_sizes=input_sizes,
        )
        _save_baseline_manifest(summary, config_path)
        print("\nBaseline ready.")
        print(
            "Git ref : "
            f"{_run_capture(['git', 'rev-parse', '--abbrev-ref', 'HEAD'], REPO_ROOT)}"
            f" @ {_run_capture(['git', 'rev-parse', '--short', 'HEAD'], REPO_ROOT)}"
        )
        print(f"Run dir: {summary.run_dir}")
        print(f"Run id : {summary.run_id}")
        for size in input_sizes:
            print(f"  {size}: {summary.medians_ns[size] / 1e6:.3f} ms")
        return 0

    # candidate mode
    baseline_dir_arg = str(args.baseline_dir).strip()
    if baseline_dir_arg:
        baseline_dir = Path(baseline_dir_arg)
        if not baseline_dir.is_absolute():
            baseline_dir = FRAMEWORK_DIR / baseline_dir
        baseline_metric = args.metric
        baseline_engine = args.engine
        baseline_ref = "(external baseline dir; git ref unknown)"
    else:
        manifest = _load_baseline_manifest()
        baseline_dir = Path(str(manifest["run_dir"]))
        baseline_metric = str(manifest.get("metric", args.metric))
        baseline_engine = str(manifest.get("engine", args.engine))
        baseline_branch = str(manifest.get("git_branch", "unknown"))
        baseline_sha = str(manifest.get("git_sha", "unknown"))
        if baseline_branch != args.baseline_branch:
            print(
                f"Saved baseline is from branch '{baseline_branch}', expected '{args.baseline_branch}'. "
                f"Re-run baseline on '{args.baseline_branch}'.",
                file=sys.stderr,
            )
            return 2
        baseline_ref = f"{baseline_branch} @ {baseline_sha[:12]}"

    if baseline_metric != args.metric:
        print(
            f"Metric mismatch: baseline uses {baseline_metric}, candidate requested {args.metric}",
            file=sys.stderr,
        )
        return 2
    if baseline_engine != args.engine:
        print(
            f"Engine mismatch: baseline uses {baseline_engine}, candidate requested {args.engine}",
            file=sys.stderr,
        )
        return 2
    if not baseline_dir.exists():
        print(f"Baseline run directory not found: {baseline_dir}", file=sys.stderr)
        return 2

    baseline = _load_summary(
        baseline_dir,
        engine=args.engine,
        metric=args.metric,
        pattern_count=pattern_count,
        input_sizes=input_sizes,
    )
    candidate_branch = _run_capture(["git", "rev-parse", "--abbrev-ref", "HEAD"], REPO_ROOT)
    candidate_sha = _run_capture(["git", "rev-parse", "HEAD"], REPO_ROOT)
    print("\nComparing branches/commits:")
    print(f"  baseline : {baseline_ref}")
    print(f"  candidate: {candidate_branch} @ {candidate_sha[:12]}")

    candidate_dir = _run_probe(config_path, label="candidate")
    candidate = _load_summary(
        candidate_dir,
        engine=args.engine,
        metric=args.metric,
        pattern_count=pattern_count,
        input_sizes=input_sizes,
    )

    ok, failures = _print_comparison(baseline, candidate, max_slowdown=args.max_slowdown)
    print("")
    print(f"Baseline run : {baseline.run_dir} ({baseline.run_id})")
    print(f"Candidate run: {candidate.run_dir} ({candidate.run_id})")

    if not ok:
        print("\nGate FAILED:")
        for failure in failures:
            print(f"  - {failure}")
        return 3

    print("\nGate PASSED: no regression beyond threshold.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
