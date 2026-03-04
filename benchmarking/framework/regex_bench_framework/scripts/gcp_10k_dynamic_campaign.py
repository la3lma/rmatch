#!/usr/bin/env python3
"""Run a dynamic 10K benchmark campaign on GCP.

Flow:
1) Start baseline runs per size with engines: rmatch + re2j.
2) As soon as a size has at least one successful baseline measurement, start
   java-native-naive for that size using timeout = ceil(4x fastest baseline seconds).
3) Continuously sync partial/final outputs and generate workload comparison reports.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import shutil
import sqlite3
import subprocess
import sys
import time
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


REPO_DIR = Path(__file__).resolve().parents[1]
GCP_CONTROL = REPO_DIR / "scripts" / "gcp_benchmark_control.py"
WORKLOAD_REPORT = REPO_DIR / "scripts" / "generate_workload_engine_comparison.py"
RUNS_DIR = REPO_DIR / "gcp_runs"
RESULTS_DIR = REPO_DIR / "results"
REPORTS_DIR = REPO_DIR / "reports"
MATRIX_DIR = REPO_DIR / "test_matrix" / "campaign_10k_dynamic"

SIZES = ["1MB", "10MB", "100MB"]
FAST_ENGINES = ["rmatch", "re2j"]
JAVA_ENGINE = "java-native-naive"

BASELINE_TIMEOUTS = {
    "1MB": 1800,   # 30m
    "10MB": 3600,  # 60m
    "100MB": 7200, # 120m
}
STALL_TIMEOUT_SECONDS = 1200  # 20m without measurable progress

TERMINAL_STATUSES = {"completed", "failed", "cancelled", "stopped"}


def _iso_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _out(msg: str) -> None:
    print(msg, flush=True)


def _run(cmd: list[str], *, check: bool = True, capture: bool = True) -> subprocess.CompletedProcess[str]:
    cp = subprocess.run(
        cmd,
        check=False,
        cwd=str(REPO_DIR),
        text=True,
        capture_output=capture,
    )
    if check and cp.returncode != 0:
        stdout = (cp.stdout or "").strip()
        stderr = (cp.stderr or "").strip()
        raise RuntimeError(
            "Command failed "
            f"(exit={cp.returncode}): {' '.join(cmd)}\n"
            f"stdout:\n{stdout}\n"
            f"stderr:\n{stderr}"
        )
    return cp


def _py_cmd() -> str:
    venv_py = REPO_DIR / ".venv" / "bin" / "python"
    if venv_py.exists():
        return str(venv_py)
    return sys.executable


def _save_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)


def _load_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def _state_file(campaign_id: str) -> Path:
    return RUNS_DIR / f"campaign_{campaign_id}.json"


def _campaign_report_dir(campaign_id: str) -> Path:
    return REPORTS_DIR / f"campaign_{campaign_id}"


def _campaign_matrix_dir(campaign_id: str) -> Path:
    return MATRIX_DIR / campaign_id


def _extract(pattern: str, text: str) -> str | None:
    m = re.search(pattern, text, flags=re.MULTILINE)
    return m.group(1).strip() if m else None


def _latest_data_uri() -> str | None:
    payload = _load_json(RUNS_DIR / "latest_data.json")
    if not payload:
        return None
    uri = payload.get("data_uri")
    return str(uri) if uri else None


def _latest_image_uri() -> str | None:
    payload = _load_json(RUNS_DIR / "latest_image.json")
    if not payload:
        return None
    uri = payload.get("image_uri")
    return str(uri) if uri else None


def _build_common_config(
    *,
    phase: str,
    description: str,
    size: str,
    engines: list[str],
    iterations: int,
    warmup: int,
    timeout_per_job: int,
) -> dict[str, Any]:
    return {
        "phase": phase,
        "description": description,
        "test_matrix": {
            "pattern_counts": [10000],
            "input_sizes": [size],
            "pattern_suites": ["stable_patterns"],
            "corpora": ["synthetic_controllable"],
            "engines": engines,
            "iterations_per_combination": iterations,
            "warmup_iterations": warmup,
        },
        "data_configuration": {
            "pattern_generation": {
                "suite": "stable_patterns",
                "seed": 12345,
            },
            "corpus_generation": {
                "type": "synthetic_controllable",
                "match_density": "medium",
                "character_distribution": "log_like",
            },
        },
        "execution_plan": {
            "failure_handling": {
                "engine_unavailable": "abort",
                "correctness_mismatch": "record_with_warning",
                "performance_timeout": "record_partial_results",
            },
            "timeout_per_job": timeout_per_job,
            "stall_timeout_seconds": STALL_TIMEOUT_SECONDS,
            "resource_limits": {
                "max_memory_mb": 16384,
                "max_concurrent_jobs": 1,
            },
        },
        "optimization": {
            "enable_parallel_execution": False,
            "enable_adaptive_iterations": False,
            "result_caching": False,
            "corpus_pregeneration": True,
            "job_scheduling": "sequential",
        },
        "validation": {
            "cross_engine_correctness": True,
            "performance_regression_detection": True,
            "memory_usage_tracking": True,
        },
    }


def _write_baseline_configs(campaign_id: str, iterations: int, warmup: int) -> tuple[list[Path], Path]:
    matrix_dir = _campaign_matrix_dir(campaign_id)
    matrix_dir.mkdir(parents=True, exist_ok=True)

    config_paths: list[Path] = []
    for size in SIZES:
        cfg = _build_common_config(
            phase=f"campaign_10k_fastpair_{size.lower()}",
            description=f"10K baseline ({size}) with rmatch+re2j for java cutoff derivation",
            size=size,
            engines=FAST_ENGINES,
            iterations=iterations,
            warmup=warmup,
            timeout_per_job=BASELINE_TIMEOUTS[size],
        )
        path = matrix_dir / f"baseline_10k_{size}_fastpair.json"
        path.write_text(json.dumps(cfg, indent=2) + "\n", encoding="utf-8")
        config_paths.append(path)

    # Data-only bundle config (all three sizes, 10K patterns)
    bundle_cfg = _build_common_config(
        phase=f"campaign_10k_data_bundle_{campaign_id}",
        description="Data bundle definition for dynamic 10K campaign",
        size="1MB",
        engines=["rmatch"],
        iterations=1,
        warmup=0,
        timeout_per_job=300,
    )
    bundle_cfg["test_matrix"]["input_sizes"] = list(SIZES)
    bundle_cfg["test_matrix"]["engines"] = ["rmatch", "re2j", JAVA_ENGINE]
    data_cfg = matrix_dir / "campaign_10k_data_bundle.json"
    data_cfg.write_text(json.dumps(bundle_cfg, indent=2) + "\n", encoding="utf-8")

    return config_paths, data_cfg


def _write_java_config(
    campaign_id: str,
    *,
    size: str,
    timeout_seconds: int,
    iterations: int,
    warmup: int,
) -> Path:
    matrix_dir = _campaign_matrix_dir(campaign_id)
    matrix_dir.mkdir(parents=True, exist_ok=True)
    cfg = _build_common_config(
        phase=f"campaign_10k_java_{size.lower()}",
        description=f"10K java-native-naive ({size}) with timeout from dynamic baseline",
        size=size,
        engines=[JAVA_ENGINE],
        iterations=iterations,
        warmup=warmup,
        timeout_per_job=timeout_seconds,
    )
    path = matrix_dir / f"java_10k_{size}_timeout_{timeout_seconds}s.json"
    path.write_text(json.dumps(cfg, indent=2) + "\n", encoding="utf-8")
    return path


def _load_run_manifest(run_id: str) -> dict[str, Any]:
    path = RUNS_DIR / f"{run_id}.json"
    payload = _load_json(path)
    if not payload:
        raise RuntimeError(f"Run manifest missing or invalid: {path}")
    return payload


def _start_batch(
    *,
    config_paths: list[Path],
    zone: str,
    bucket: str,
    machine_type: str,
    image_uri: str | None,
    data_uri: str | None,
    batch_id: str,
) -> tuple[str, dict[str, Any]]:
    cmd = [
        _py_cmd(),
        str(GCP_CONTROL),
        "start-batch",
        "--zone",
        zone,
        "--bucket",
        bucket,
        "--machine-type",
        machine_type,
        "--configs",
        ",".join(str(p) for p in config_paths),
        "--batch-id",
        batch_id,
    ]
    if image_uri:
        cmd.extend(["--image-uri", image_uri])
    if data_uri:
        cmd.extend(["--data-uri", data_uri])

    cp = _run(cmd, check=True, capture=True)
    _out(cp.stdout)
    if cp.stderr:
        _out(cp.stderr)

    out_batch_id = _extract(r"Started batch:\s+([^\s]+)", cp.stdout) or batch_id
    batch_manifest_path = RUNS_DIR / f"{out_batch_id}.json"
    batch_manifest = _load_json(batch_manifest_path)
    if not batch_manifest:
        raise RuntimeError(f"Batch manifest not found: {batch_manifest_path}")
    return out_batch_id, batch_manifest


def _start_single_run(
    *,
    config_path: Path,
    zone: str,
    bucket: str,
    machine_type: str,
    image_uri: str | None,
    data_uri: str | None,
) -> dict[str, Any]:
    cmd = [
        _py_cmd(),
        str(GCP_CONTROL),
        "start",
        "--zone",
        zone,
        "--bucket",
        bucket,
        "--machine-type",
        machine_type,
        "--config",
        str(config_path),
    ]
    if image_uri:
        cmd.extend(["--image-uri", image_uri])
    if data_uri:
        cmd.extend(["--data-uri", data_uri])

    cp = _run(cmd, check=True, capture=True)
    _out(cp.stdout)
    if cp.stderr:
        _out(cp.stderr)
    run_id = _extract(r"Started run:\s+([^\s]+)", cp.stdout)
    if not run_id:
        raise RuntimeError("Could not parse run_id from start output.")
    return _load_run_manifest(run_id)


def _publish_data(
    *,
    zone: str,
    bucket: str,
    config_path: Path,
    dataset_id: str,
) -> str:
    cmd = [
        _py_cmd(),
        str(GCP_CONTROL),
        "publish-data",
        "--zone",
        zone,
        "--bucket",
        bucket,
        "--config",
        str(config_path),
        "--dataset-id",
        dataset_id,
    ]
    cp = _run(cmd, check=True, capture=True)
    _out(cp.stdout)
    if cp.stderr:
        _out(cp.stderr)
    data_uri = _extract(r"Data URI:\s+([^\s]+)", cp.stdout)
    if data_uri:
        return data_uri
    latest = _latest_data_uri()
    if not latest:
        raise RuntimeError("Could not resolve data URI after publish-data.")
    return latest


def _fetch_remote_state(run_root: str) -> dict[str, Any] | None:
    cp = _run(
        ["gcloud", "storage", "cat", f"{run_root}/state/state.json"],
        check=False,
        capture=True,
    )
    if cp.returncode != 0 or not cp.stdout.strip():
        return None
    try:
        return json.loads(cp.stdout)
    except Exception:
        return None


def _sync_run_output(run_root: str, local_result_dir: Path) -> None:
    local_result_dir.mkdir(parents=True, exist_ok=True)
    _run(
        ["gcloud", "storage", "rsync", "--recursive", f"{run_root}/output", str(local_result_dir)],
        check=False,
        capture=True,
    )
    meta_dir = local_result_dir / "_gcp_meta"
    meta_dir.mkdir(parents=True, exist_ok=True)
    _run(
        ["gcloud", "storage", "cp", f"{run_root}/state/state.json", str(meta_dir / "state.json")],
        check=False,
        capture=True,
    )
    _run(
        ["gcloud", "storage", "cp", f"{run_root}/logs/run.log", str(meta_dir / "run.log")],
        check=False,
        capture=True,
    )


def _load_rows_from_jobs_db(local_result_dir: Path) -> list[dict[str, Any]]:
    db_path = local_result_dir / "jobs.db"
    if not db_path.exists():
        return []
    try:
        conn = sqlite3.connect(str(db_path))
        conn.row_factory = sqlite3.Row
        rows = conn.execute(
            """
            SELECT
                engine_name,
                status,
                total_ns
            FROM benchmark_jobs
            """
        ).fetchall()
        conn.close()
    except Exception:
        return []

    out: list[dict[str, Any]] = []
    for row in rows:
        status_raw = str(row["status"] or "").upper()
        status = "ok" if status_raw == "COMPLETED" else status_raw.lower()
        out.append(
            {
                "engine_name": str(row["engine_name"] or ""),
                "status": status,
                "total_ns": float(row["total_ns"] or 0.0),
            }
        )
    return out


def _load_rows(local_result_dir: Path) -> list[dict[str, Any]]:
    final_path = local_result_dir / "raw_results" / "benchmark_results.json"
    if final_path.exists():
        try:
            rows = json.loads(final_path.read_text(encoding="utf-8"))
            if isinstance(rows, list):
                return rows
        except Exception:
            return []
    partial_path = local_result_dir / "raw_results" / "benchmark_results.partial.json"
    if partial_path.exists():
        try:
            payload = json.loads(partial_path.read_text(encoding="utf-8"))
            if isinstance(payload, dict):
                rows = payload.get("results", [])
                if isinstance(rows, list):
                    return rows
        except Exception:
            return []
    return _load_rows_from_jobs_db(local_result_dir)


def _fastest_baseline_seconds(local_result_dir: Path) -> tuple[str, float] | None:
    rows = _load_rows(local_result_dir)
    by_engine: dict[str, list[float]] = defaultdict(list)
    for row in rows:
        if not isinstance(row, dict):
            continue
        engine = str(row.get("engine_name", "")).strip()
        if engine not in FAST_ENGINES:
            continue
        if str(row.get("status", "")).lower() != "ok":
            continue
        total_ns = float(row.get("total_ns", 0.0))
        if total_ns > 0:
            by_engine[engine].append(total_ns / 1_000_000_000.0)

    if not by_engine:
        return None

    means = [(engine, sum(vals) / len(vals)) for engine, vals in by_engine.items() if vals]
    if not means:
        return None
    means.sort(key=lambda x: x[1])
    return means[0]


def _generate_report_for_run(local_result_dir: Path, campaign_id: str, run_id: str) -> None:
    if not local_result_dir.exists():
        return
    out_dir = _campaign_report_dir(campaign_id) / run_id
    out_dir.mkdir(parents=True, exist_ok=True)
    _run(
        [
            _py_cmd(),
            str(WORKLOAD_REPORT),
            "--input",
            str(local_result_dir),
            "--output",
            str(out_dir),
        ],
        check=False,
        capture=True,
    )


@dataclass
class RunMeta:
    run_id: str
    gcs_run_root: str
    config_name: str
    role: str
    size: str


def _as_run_meta(manifest: dict[str, Any], *, role: str, size: str) -> RunMeta:
    return RunMeta(
        run_id=str(manifest["run_id"]),
        gcs_run_root=str(manifest["gcs_run_root"]),
        config_name=str(manifest.get("config_name", "")),
        role=role,
        size=size,
    )


def _terminal(status: str | None) -> bool:
    if not status:
        return False
    return status.lower() in TERMINAL_STATUSES


def _results_dir_for_run(run_id: str) -> Path:
    return RESULTS_DIR / f"gcp_{run_id}"


def _stop_run_vm(run_id: str) -> None:
    cp = _run(
        [_py_cmd(), str(GCP_CONTROL), "stop", "--run-id", run_id],
        check=False,
        capture=True,
    )
    if cp.stdout.strip():
        _out(cp.stdout.strip())
    if cp.stderr.strip():
        _out(cp.stderr.strip())


def _default_campaign_id() -> str:
    return f"10k_dynamic_{datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S')}"


def run_campaign(args: argparse.Namespace) -> int:
    campaign_id = args.campaign_id or _default_campaign_id()
    state_path = _state_file(campaign_id)
    report_root = _campaign_report_dir(campaign_id)
    report_root.mkdir(parents=True, exist_ok=True)

    _out(f"Campaign ID: {campaign_id}")
    _out("Writing campaign configs...")
    baseline_cfgs, data_cfg = _write_baseline_configs(campaign_id, args.iterations, args.warmup)
    baseline_cfg_by_size = {size: path for size, path in zip(SIZES, baseline_cfgs)}

    existing_state = _load_json(state_path) or {}
    resuming = bool(existing_state)
    if resuming:
        _out(f"Resuming existing campaign state: {state_path}")

    image_uri = args.image_uri or str(existing_state.get("image_uri") or "") or _latest_image_uri()
    if not image_uri:
        raise RuntimeError("No image URI available. Publish image first or pass --image-uri.")
    _out(f"Image URI: {image_uri}")

    if args.data_uri:
        data_uri = args.data_uri
    elif str(existing_state.get("data_uri") or "").strip():
        data_uri = str(existing_state["data_uri"]).strip()
    else:
        dataset_id = f"{campaign_id}-data"
        _out(f"Publishing data bundle (dataset_id={dataset_id})...")
        data_uri = _publish_data(
            zone=args.zone,
            bucket=args.bucket,
            config_path=data_cfg,
            dataset_id=dataset_id,
        )
    _out(f"Data URI: {data_uri}")

    baseline_by_size: dict[str, RunMeta] = {}
    java_by_size: dict[str, RunMeta] = {}
    java_timeouts: dict[str, int] = {
        str(k): int(v)
        for k, v in dict(existing_state.get("java_timeouts_seconds", {})).items()
        if str(k) in SIZES
    }
    skipped_java: dict[str, str] = {
        str(k): str(v)
        for k, v in dict(existing_state.get("java_skipped", {})).items()
        if str(k) in SIZES
    }
    run_statuses: dict[str, dict[str, Any]] = {
        str(k): dict(v)
        for k, v in dict(existing_state.get("status_by_run", {})).items()
        if isinstance(v, dict)
    }
    stopped_runs: set[str] = set(str(x) for x in existing_state.get("stopped_runs", []))
    waiting_for_metrics: set[str] = set()

    for size in SIZES:
        b_raw = dict(existing_state.get("baseline_runs", {})).get(size)
        if isinstance(b_raw, dict) and b_raw.get("run_id") and b_raw.get("gcs_run_root"):
            baseline_by_size[size] = RunMeta(
                run_id=str(b_raw["run_id"]),
                gcs_run_root=str(b_raw["gcs_run_root"]),
                config_name=str(b_raw.get("config_name") or baseline_cfg_by_size[size].name),
                role=str(b_raw.get("role") or "baseline_fastpair"),
                size=size,
            )
        j_raw = dict(existing_state.get("java_runs", {})).get(size)
        if isinstance(j_raw, dict) and j_raw.get("run_id") and j_raw.get("gcs_run_root"):
            java_by_size[size] = RunMeta(
                run_id=str(j_raw["run_id"]),
                gcs_run_root=str(j_raw["gcs_run_root"]),
                config_name=str(j_raw.get("config_name") or ""),
                role=str(j_raw.get("role") or "java_naive_dynamic_cutoff"),
                size=size,
            )

    dynamic_max_lanes = int(existing_state.get("effective_max_lanes") or args.max_lanes)
    if dynamic_max_lanes <= 0:
        dynamic_max_lanes = int(args.max_lanes)
    if dynamic_max_lanes <= 0:
        dynamic_max_lanes = 1

    state: dict[str, Any] = dict(existing_state)
    state["campaign_id"] = campaign_id
    state["started_at"] = str(existing_state.get("started_at") or _iso_now())
    state["updated_at"] = _iso_now()
    state["zone"] = args.zone
    state["bucket"] = args.bucket
    state["machine_type"] = args.machine_type
    state["timeout_factor"] = args.timeout_factor
    state["poll_seconds"] = args.poll_seconds
    state["iterations"] = args.iterations
    state["warmup"] = args.warmup
    state["max_lanes"] = int(args.max_lanes)
    state["effective_max_lanes"] = int(dynamic_max_lanes)
    state["image_uri"] = image_uri
    state["data_uri"] = data_uri
    state["campaign_status"] = "running"
    state["notes"] = [
        "java cutoff per size = ceil(timeout_factor * fastest_mean_seconds_from_rmatch_or_re2j)",
        "scheduling mode: multi-lane (baseline and java per size with dependency)",
        f"max_lanes: {int(args.max_lanes)}",
        "baseline uses engines: rmatch,re2j",
        f"java engine: {JAVA_ENGINE}",
    ]

    def persist_state(status: str = "running") -> None:
        state["updated_at"] = _iso_now()
        state["campaign_status"] = status
        if status != "completed":
            state.pop("completed_at", None)
        state["baseline_runs"] = {s: baseline_by_size[s].__dict__ for s in sorted(baseline_by_size.keys())}
        state["java_runs"] = {s: java_by_size[s].__dict__ for s in sorted(java_by_size.keys())}
        state["java_timeouts_seconds"] = {s: int(java_timeouts[s]) for s in sorted(java_timeouts.keys())}
        state["java_skipped"] = {s: skipped_java[s] for s in sorted(skipped_java.keys())}
        state["status_by_run"] = run_statuses
        state["stopped_runs"] = sorted(stopped_runs)
        state["effective_max_lanes"] = int(dynamic_max_lanes)
        _save_json(state_path, state)

    def refresh_and_persist(run: RunMeta) -> dict[str, Any]:
        local_dir = _results_dir_for_run(run.run_id)
        _sync_run_output(run.gcs_run_root, local_dir)
        _generate_report_for_run(local_dir, campaign_id, run.run_id)
        remote_state = _fetch_remote_state(run.gcs_run_root) or {}
        previous = run_statuses.get(run.run_id, {})
        status = str(remote_state.get("status") or previous.get("status") or "unknown").lower()
        row = {
            "run_id": run.run_id,
            "role": run.role,
            "size": run.size,
            "status": status,
            "completed_jobs": int(remote_state.get("completed_jobs", previous.get("completed_jobs", 0)) or 0),
            "total_jobs": int(remote_state.get("total_jobs", previous.get("total_jobs", 0)) or 0),
            "remaining_seconds": int(remote_state.get("remaining_seconds", previous.get("remaining_seconds", -1)) or -1),
            "updated_at": remote_state.get("updated_at", previous.get("updated_at")),
            "memory_congested": bool(remote_state.get("memory_congested", previous.get("memory_congested", False))),
            "recommendation": str(remote_state.get("recommendation", previous.get("recommendation", "")) or ""),
        }
        run_statuses[run.run_id] = row
        persist_state("running")
        return row

    def run_status(meta: RunMeta | None) -> str:
        if not meta:
            return "missing"
        return str(run_statuses.get(meta.run_id, {}).get("status", "unknown")).lower()

    def active_run_count() -> int:
        count = 0
        for meta in list(baseline_by_size.values()) + list(java_by_size.values()):
            if not _terminal(run_status(meta)):
                count += 1
        return count

    def size_done(size: str) -> bool:
        baseline = baseline_by_size.get(size)
        if not baseline:
            return False
        if not _terminal(run_status(baseline)):
            return False
        if size in skipped_java:
            return True
        java = java_by_size.get(size)
        if not java:
            return False
        return _terminal(run_status(java))

    def maybe_stop_terminal_vm(meta: RunMeta) -> None:
        status = run_status(meta)
        if not _terminal(status):
            return
        if args.keep_terminal_vms:
            return
        if meta.run_id in stopped_runs:
            return
        _out(f"[{meta.size}] stopping terminal VM for run {meta.run_id} ({meta.role})")
        _stop_run_vm(meta.run_id)
        stopped_runs.add(meta.run_id)
        persist_state("running")

    def start_baseline(size: str) -> None:
        _out(f"[{size}] starting baseline run (rmatch + re2j)")
        manifest = _start_single_run(
            config_path=baseline_cfg_by_size[size],
            zone=args.zone,
            bucket=args.bucket,
            machine_type=args.machine_type,
            image_uri=image_uri,
            data_uri=data_uri,
        )
        baseline_by_size[size] = _as_run_meta(
            manifest,
            role="baseline_fastpair",
            size=size,
        )
        persist_state("running")

    def start_java(size: str, timeout_seconds: int) -> None:
        java_cfg = _write_java_config(
            campaign_id,
            size=size,
            timeout_seconds=timeout_seconds,
            iterations=args.iterations,
            warmup=args.warmup,
        )
        _out(f"[{size}] starting java run with timeout={timeout_seconds}s")
        manifest = _start_single_run(
            config_path=java_cfg,
            zone=args.zone,
            bucket=args.bucket,
            machine_type=args.machine_type,
            image_uri=image_uri,
            data_uri=data_uri,
        )
        java_by_size[size] = _as_run_meta(
            manifest,
            role="java_naive_dynamic_cutoff",
            size=size,
        )
        persist_state("running")

    def print_status_summary() -> None:
        parts: list[str] = []
        for size in SIZES:
            b = baseline_by_size.get(size)
            j = java_by_size.get(size)
            b_status = run_status(b)
            j_status = run_status(j) if j else ("skipped" if size in skipped_java else "pending")
            parts.append(f"{size}: baseline={b_status}, java={j_status}")
        _out(f"Status (lanes requested={int(args.max_lanes)}, effective={dynamic_max_lanes}) | " + " | ".join(parts))

    def is_quota_capacity_error(exc: Exception) -> bool:
        msg = str(exc).lower()
        return "cpus_all_regions" in msg or ("quota" in msg and "cpu" in msg)

    persist_state("running")
    _out(
        f"Starting dynamic campaign scheduler "
        f"(max_lanes={int(args.max_lanes)}, effective={dynamic_max_lanes})..."
    )

    while True:
        known_runs = list(baseline_by_size.values()) + list(java_by_size.values())
        for meta in known_runs:
            row = refresh_and_persist(meta)
            _out(
                f"[{meta.size}] {meta.role} {row['status']} "
                f"{row['completed_jobs']}/{row['total_jobs']} eta={row['remaining_seconds']}"
            )
            maybe_stop_terminal_vm(meta)

        for size in SIZES:
            baseline = baseline_by_size.get(size)
            if not baseline:
                continue
            b_status = run_status(baseline)
            if b_status in {"failed", "cancelled", "stopped"} and size not in skipped_java:
                skipped_java[size] = f"baseline status is {b_status}; java skipped"
                _out(f"[{size}] {skipped_java[size]}")
                persist_state("running")

        if all(size_done(size) for size in SIZES):
            state["completed_at"] = _iso_now()
            persist_state("completed")
            _out("Campaign completed.")
            return 0

        slots = max(0, int(dynamic_max_lanes) - active_run_count())

        for size in SIZES:
            if slots <= 0:
                break
            if size in baseline_by_size:
                continue
            try:
                start_baseline(size)
            except RuntimeError as exc:
                if is_quota_capacity_error(exc):
                    dynamic_max_lanes = max(1, active_run_count())
                    _out(
                        f"[{size}] start blocked by CPU quota; "
                        f"reducing effective lanes to {dynamic_max_lanes} and retrying later."
                    )
                    persist_state("running")
                    break
                raise
            slots -= 1

        for size in SIZES:
            if slots <= 0:
                break
            if size in java_by_size:
                continue
            baseline = baseline_by_size.get(size)
            if not baseline:
                continue
            b_status = run_status(baseline)
            if not _terminal(b_status):
                continue
            if b_status in {"failed", "cancelled", "stopped"}:
                continue

            fastest = _fastest_baseline_seconds(_results_dir_for_run(baseline.run_id))
            if not fastest:
                if size not in waiting_for_metrics:
                    waiting_for_metrics.add(size)
                    _out(f"[{size}] baseline is terminal; waiting for synced metrics to derive java timeout.")
                continue

            if size in waiting_for_metrics:
                waiting_for_metrics.remove(size)
            skipped_java.pop(size, None)
            fastest_engine, fastest_seconds = fastest
            timeout_seconds = max(120, int(math.ceil(args.timeout_factor * fastest_seconds)))
            java_timeouts[size] = timeout_seconds
            _out(
                f"[{size}] fastest baseline={fastest_engine} {fastest_seconds:.2f}s "
                f"-> java timeout={timeout_seconds}s"
            )
            try:
                start_java(size, timeout_seconds)
            except RuntimeError as exc:
                if is_quota_capacity_error(exc):
                    dynamic_max_lanes = max(1, active_run_count())
                    _out(
                        f"[{size}] java start blocked by CPU quota; "
                        f"reducing effective lanes to {dynamic_max_lanes} and retrying later."
                    )
                    persist_state("running")
                    break
                raise
            slots -= 1

        print_status_summary()
        persist_state("running")
        time.sleep(max(15, int(args.poll_seconds)))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run dynamic 10K GCP benchmark campaign.")
    parser.add_argument("--campaign-id", default=None, help="Campaign identifier (default: timestamped)")
    parser.add_argument("--zone", default="europe-north2-a", help="GCP zone")
    parser.add_argument("--bucket", default="run-cl-rmatch-bench", help="GCS bucket")
    parser.add_argument("--machine-type", default="n2-standard-8", help="VM machine type")
    parser.add_argument("--image-uri", default=None, help="Docker image URI (default: latest_image.json)")
    parser.add_argument("--data-uri", default=None, help="Data bundle URI (skip publish-data if provided)")
    parser.add_argument("--timeout-factor", type=float, default=4.0, help="Java cutoff multiplier vs fastest baseline")
    parser.add_argument("--poll-seconds", type=int, default=60, help="Polling interval seconds")
    parser.add_argument("--iterations", type=int, default=2, help="Iterations per combination")
    parser.add_argument("--warmup", type=int, default=1, help="Warmup iterations per combination")
    parser.add_argument("--max-lanes", type=int, default=3, help="Maximum concurrent run lanes (VMs)")
    parser.add_argument(
        "--keep-terminal-vms",
        action="store_true",
        help="Do not stop terminal VMs automatically (default: stop terminal VMs)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    return run_campaign(args)


if __name__ == "__main__":
    raise SystemExit(main())
