#!/usr/bin/env python3
"""
GCP benchmark run control utility.

Workflow:
1) publish-image  -> build/push Docker image to Artifact Registry
2) publish-data   -> upload pattern/corpus bundle to GCS
3) start          -> launch GCE VM that pulls image + data and runs benchmark
3b) start-batch   -> launch multiple VMs (one per config) for parallel runs
3c) resume        -> restart a stopped run VM and continue from synced state
4) status/watch   -> live progress + ETA
4b) status-batch/watch-batch -> aggregate progress + ETA across runs
5) cancel/stop    -> safe cancel or immediate stop
5b) cancel-batch/stop-batch -> cancel/stop all runs in a batch
6) cohort-report  -> comparable cohorts + pairwise engine coverage
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import itertools
import json
import secrets
import shlex
import subprocess
import sys
import tarfile
import tempfile
import time
from pathlib import Path
from typing import Any


SCRIPT_PATH = Path(__file__).resolve()
FRAMEWORK_DIR = SCRIPT_PATH.parent.parent
REPO_ROOT = FRAMEWORK_DIR.parent.parent
LOCAL_RUNS_DIR = FRAMEWORK_DIR / "gcp_runs"
LATEST_IMAGE_FILE = LOCAL_RUNS_DIR / "latest_image.json"
LATEST_DATA_FILE = LOCAL_RUNS_DIR / "latest_data.json"
BATCH_MANIFEST_PREFIX = "batch_"

TIER_ORDER = ["S", "M", "L", "XL"]
TIER_TO_MACHINE_TYPE = {
    "S": "n2-standard-8",
    "M": "n2-highmem-16",
    "L": "n2-highmem-32",
    "XL": "n2-highmem-64",
}
DEFAULT_REQUIRE_ENGINES = ["rmatch", "re2j", "java-native-naive"]


def _out(msg: str = "") -> None:
    print(msg, flush=True)


def _run(
    cmd: list[str],
    *,
    check: bool = True,
    capture_output: bool = False,
    text: bool = True,
) -> subprocess.CompletedProcess:
    return subprocess.run(
        cmd,
        check=check,
        capture_output=capture_output,
        text=text,
    )


def _run_stdout(cmd: list[str]) -> str:
    proc = _run(cmd, capture_output=True)
    return proc.stdout.strip()


def _eprint(msg: str) -> None:
    print(msg, file=sys.stderr, flush=True)


def _which(cmd: str) -> str | None:
    return _run_stdout(["bash", "-lc", f"command -v {shlex.quote(cmd)} || true"]) or None


def _require_cmd(cmd: str) -> None:
    if not _which(cmd):
        raise RuntimeError(f"Required command not found: {cmd}")


def _utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def _iso_utc(ts: dt.datetime | None = None) -> str:
    ts = ts or _utc_now()
    return ts.isoformat().replace("+00:00", "Z")


def _ensure_local_runs_dir() -> None:
    LOCAL_RUNS_DIR.mkdir(parents=True, exist_ok=True)


def _default_project() -> str:
    project = _run_stdout(["gcloud", "config", "get-value", "project"])
    if not project:
        raise RuntimeError("No active gcloud project. Set with: gcloud config set project <PROJECT>")
    return project


def _region_from_zone(zone: str) -> str:
    parts = zone.split("-")
    if len(parts) < 3:
        raise ValueError(f"Unexpected zone format: {zone}")
    return "-".join(parts[:-1])


def _bucket_exists(bucket: str) -> bool:
    proc = _run(["gcloud", "storage", "buckets", "describe", f"gs://{bucket}"], check=False, capture_output=True)
    return proc.returncode == 0


def _create_bucket(bucket: str, project: str, region: str) -> None:
    _run(
        [
            "gcloud",
            "storage",
            "buckets",
            "create",
            f"gs://{bucket}",
            "--project",
            project,
            "--location",
            region,
            "--uniform-bucket-level-access",
        ]
    )


def _artifact_repo_exists(project: str, region: str, repo: str) -> bool:
    proc = _run(
        [
            "gcloud",
            "artifacts",
            "repositories",
            "describe",
            repo,
            "--project",
            project,
            "--location",
            region,
            "--format=value(name)",
        ],
        check=False,
        capture_output=True,
    )
    return proc.returncode == 0


def _create_artifact_repo(project: str, region: str, repo: str) -> None:
    _run(
        [
            "gcloud",
            "artifacts",
            "repositories",
            "create",
            repo,
            "--project",
            project,
            "--location",
            region,
            "--repository-format=docker",
            "--description=Regex benchmark images",
        ]
    )


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def _save_latest(path: Path, payload: dict[str, Any]) -> None:
    _write_json(path, payload)


def _load_latest(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    return _read_json(path)


def _run_manifest_path(run_id: str) -> Path:
    return LOCAL_RUNS_DIR / f"{run_id}.json"


def _batch_manifest_path(batch_id: str) -> Path:
    return LOCAL_RUNS_DIR / f"{BATCH_MANIFEST_PREFIX}{batch_id}.json"


def _is_run_manifest(data: Any) -> bool:
    return (
        isinstance(data, dict)
        and isinstance(data.get("run_id"), str)
        and isinstance(data.get("instance_name"), str)
        and isinstance(data.get("gcs_run_root"), str)
    )


def _is_batch_manifest(data: Any) -> bool:
    return (
        isinstance(data, dict)
        and isinstance(data.get("batch_id"), str)
        and isinstance(data.get("runs"), list)
    )


def _latest_manifest(predicate) -> Path | None:
    if not LOCAL_RUNS_DIR.exists():
        return None
    manifests = sorted(LOCAL_RUNS_DIR.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    for manifest_path in manifests:
        try:
            if predicate(_read_json(manifest_path)):
                return manifest_path
        except Exception:
            continue
    return None


def _latest_run_manifest() -> Path | None:
    return _latest_manifest(_is_run_manifest)


def _latest_batch_manifest() -> Path | None:
    return _latest_manifest(_is_batch_manifest)


def _load_manifest(run_id: str | None) -> dict[str, Any]:
    if run_id:
        path = _run_manifest_path(run_id)
        if not path.exists():
            raise RuntimeError(f"Run manifest not found: {path}")
        data = _read_json(path)
        if not _is_run_manifest(data):
            raise RuntimeError(f"Not a run manifest: {path}")
        return data

    latest = _latest_run_manifest()
    if not latest:
        raise RuntimeError("No local run manifests found.")
    return _read_json(latest)


def _load_batch_manifest(batch_id: str | None) -> dict[str, Any]:
    if batch_id:
        path = _batch_manifest_path(batch_id)
        if not path.exists():
            raise RuntimeError(f"Batch manifest not found: {path}")
        data = _read_json(path)
        if not _is_batch_manifest(data):
            raise RuntimeError(f"Not a batch manifest: {path}")
        return data

    latest = _latest_batch_manifest()
    if not latest:
        raise RuntimeError("No local batch manifests found.")
    return _read_json(latest)


def _label_safe_run_id(run_id: str) -> str:
    safe = "".join(c if c.isalnum() or c in "-_" else "-" for c in run_id.lower())
    return safe[:63]


def _fetch_remote_json(uri: str) -> dict[str, Any] | None:
    proc = _run(["gcloud", "storage", "cat", uri], check=False, capture_output=True)
    if proc.returncode != 0:
        return None
    try:
        return json.loads(proc.stdout)
    except json.JSONDecodeError:
        return None


def _instance_status(project: str, zone: str, instance_name: str) -> str:
    proc = _run(
        [
            "gcloud",
            "compute",
            "instances",
            "describe",
            instance_name,
            "--project",
            project,
            "--zone",
            zone,
            "--format=value(status)",
        ],
        check=False,
        capture_output=True,
    )
    if proc.returncode != 0:
        return "UNKNOWN"
    return (proc.stdout or "").strip() or "UNKNOWN"


def _human_duration(seconds: int | float | None) -> str:
    if seconds is None or seconds < 0:
        return "unknown"
    seconds = int(seconds)
    h, rem = divmod(seconds, 3600)
    m, s = divmod(rem, 60)
    if h > 0:
        return f"{h}h {m}m {s}s"
    if m > 0:
        return f"{m}m {s}s"
    return f"{s}s"


def _extract_vcpu_count(machine_type: str) -> int:
    try:
        return int(machine_type.rsplit("-", 1)[-1])
    except Exception:
        return 0


def _normalize_tier(tier: str | None) -> str | None:
    if tier is None:
        return None
    t = str(tier).strip().upper()
    if t in TIER_ORDER:
        return t
    return None


def _infer_machine_tier(machine_type: str) -> str:
    for tier, mt in TIER_TO_MACHINE_TYPE.items():
        if machine_type == mt:
            return tier

    vcpu = _extract_vcpu_count(machine_type)
    m = machine_type.lower()
    if "highmem" in m:
        if vcpu >= 64:
            return "XL"
        if vcpu >= 32:
            return "L"
        return "M"
    if "standard" in m:
        if vcpu >= 32:
            return "L"
        if vcpu >= 16:
            return "M"
        return "S"
    if "highcpu" in m:
        return "S"
    return "S"


def _resolve_machine_tier(explicit_tier: str | None, machine_type: str) -> str:
    normalized = _normalize_tier(explicit_tier)
    if normalized:
        return normalized
    return _infer_machine_tier(machine_type)


def _next_machine_tier(current_tier: str) -> str | None:
    normalized = _normalize_tier(current_tier)
    if not normalized:
        return None
    idx = TIER_ORDER.index(normalized)
    if idx + 1 >= len(TIER_ORDER):
        return None
    return TIER_ORDER[idx + 1]


def _manifest_machine_tier(manifest: dict[str, Any]) -> str:
    tier = _normalize_tier(manifest.get("machine_tier"))
    if tier:
        return tier
    machine_type = str(manifest.get("machine_type", "")).strip()
    if machine_type:
        return _infer_machine_tier(machine_type)
    return "?"


def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _config_counts_and_sizes(config_path: Path) -> tuple[list[int], list[str]]:
    config = _read_json(config_path)
    matrix = config.get("test_matrix", {})
    counts = [int(x) for x in matrix.get("pattern_counts", [])]
    sizes = [str(x) for x in matrix.get("input_sizes", [])]
    if not counts or not sizes:
        raise RuntimeError("Config missing pattern_counts/input_sizes")
    return counts, sizes


def _prepare_data_bundle(config_path: Path, bundle_dir: Path) -> dict[str, Any]:
    counts, sizes = _config_counts_and_sizes(config_path)
    src_patterns = FRAMEWORK_DIR / "benchmark_suites" / "log_mining"
    src_corpora = FRAMEWORK_DIR / "benchmark_suites" / "corpora"

    bundle_dir.mkdir(parents=True, exist_ok=True)
    manifest: dict[str, Any] = {
        "generated_at": _iso_utc(),
        "config": str(config_path),
        "patterns": [],
        "corpora": [],
    }

    # Patterns
    patterns_10 = src_patterns / "patterns_10.txt"
    for count in counts:
        dst = bundle_dir / f"patterns_{count}.txt"
        src = src_patterns / f"patterns_{count}.txt"
        if src.exists():
            dst.write_text(src.read_text(encoding="utf-8"), encoding="utf-8")
            src_used = str(src)
        elif count <= 10 and patterns_10.exists():
            lines = patterns_10.read_text(encoding="utf-8").splitlines()
            dst.write_text("\n".join(lines[:count]) + "\n", encoding="utf-8")
            src_used = f"{patterns_10} (first {count} lines)"
        else:
            raise RuntimeError(f"No source pattern file for count={count}")

        meta_src = src_patterns / f"patterns_{count}_metadata.json"
        if meta_src.exists():
            (bundle_dir / f"patterns_{count}_metadata.json").write_text(
                meta_src.read_text(encoding="utf-8"),
                encoding="utf-8",
            )

        manifest["patterns"].append({"count": count, "file": dst.name, "source": src_used})

    # Corpora
    synthetic_1mb = src_corpora / "corpus_synthetic_1MB.txt"
    for size in sizes:
        dst = bundle_dir / f"corpus_{size}.txt"
        src = src_corpora / f"corpus_synthetic_{size}.txt"

        if src.exists():
            dst.write_text(src.read_text(encoding="utf-8"), encoding="utf-8")
            src_used = str(src)
        elif size.upper() == "100KB" and synthetic_1mb.exists():
            text = synthetic_1mb.read_text(encoding="utf-8")
            dst.write_text(text[:102400], encoding="utf-8")
            src_used = f"{synthetic_1mb} (first 100KB)"
        else:
            raise RuntimeError(f"No source corpus file for size={size}")

        manifest["corpora"].append({"size": size, "file": dst.name, "source": src_used})

    return manifest


def _make_startup_script(
    *,
    run_id: str,
    project: str,
    zone: str,
    bucket: str,
    machine_type: str,
    machine_tier: str,
    next_machine_tier: str,
    next_machine_type: str,
    image_uri: str,
    data_uri: str,
    auto_shutdown: bool,
) -> str:
    shutdown_flag = "true" if auto_shutdown else "false"
    return f"""#!/usr/bin/env bash
set -euo pipefail

RUN_ID="{run_id}"
PROJECT="{project}"
ZONE="{zone}"
BUCKET="{bucket}"
MACHINE_TYPE="{machine_type}"
MACHINE_TIER="{machine_tier}"
NEXT_MACHINE_TIER="{next_machine_tier}"
NEXT_MACHINE_TYPE="{next_machine_type}"
IMAGE_URI="{image_uri}"
DATA_URI="{data_uri}"
AUTO_SHUTDOWN="{shutdown_flag}"
REGISTRY_HOST="$(echo "$IMAGE_URI" | cut -d/ -f1)"
ARCH="$(uname -m)"

RUN_ROOT="gs://$BUCKET/runs/$RUN_ID"
CONFIG_URI="$RUN_ROOT/input/config.json"

WORK_ROOT="/opt/rmatch-run"
STATE_DIR="$WORK_ROOT/state"
LOG_DIR="$WORK_ROOT/logs"
DATA_DIR="$WORK_ROOT/data_bundle"
RESULTS_DIR="$WORK_ROOT/results"
STATE_FILE="$STATE_DIR/state.json"
STATUS_FILE="$STATE_DIR/status.txt"
RUN_LOG="$LOG_DIR/run.log"
FINAL_MARKER="$WORK_ROOT/finalized"
CONTAINER_NAME="rmatch-run-$RUN_ID"

mkdir -p "$STATE_DIR" "$LOG_DIR" "$DATA_DIR" "$RESULTS_DIR"

START_EPOCH="$(date +%s)"
STATUS="bootstrapping"
COMPLETED=0
TOTAL=0
CANCEL_REQUESTED=0
CANCEL_EPOCH=0
BENCH_PID=""
MEM_TOTAL_MB=0
MEM_AVAILABLE_MB=0
SWAP_USED_MB=0
MEMORY_HEADROOM_PCT=-1
MEMORY_CONGESTED=0
OOM_SIGNS=0
CONGESTION_REASON=""
RECOMMENDATION=""
LAST_PROGRESS_EPOCH=0
LAST_PROGRESS_COUNT=0
NO_PROGRESS_SECONDS=0
STALL_THRESHOLD_SECONDS=900
EMA_RATE_JOBS_PER_SEC=0
ETA_MODEL="insufficient_progress"
ETA_CONFIDENCE="low"
BENCH_PROCESS_ALIVE=0
BENCH_CPU_PERCENT=0
BENCH_CPU_ACTIVE=0
CPU_ACTIVE_THRESHOLD_PCT=50
echo "$STATUS" > "$STATUS_FILE"

set_status() {{
  STATUS="$1"
  echo "$STATUS" > "$STATUS_FILE"
}}

collect_memory_stats() {{
  local mem_total_kb mem_avail_kb swap_total_kb swap_free_kb
  mem_total_kb="$(awk '/MemTotal:/ {{print $2}}' /proc/meminfo 2>/dev/null || echo 0)"
  mem_avail_kb="$(awk '/MemAvailable:/ {{print $2}}' /proc/meminfo 2>/dev/null || echo 0)"
  swap_total_kb="$(awk '/SwapTotal:/ {{print $2}}' /proc/meminfo 2>/dev/null || echo 0)"
  swap_free_kb="$(awk '/SwapFree:/ {{print $2}}' /proc/meminfo 2>/dev/null || echo 0)"

  MEM_TOTAL_MB="$(( mem_total_kb / 1024 ))"
  MEM_AVAILABLE_MB="$(( mem_avail_kb / 1024 ))"
  SWAP_USED_MB="$(( (swap_total_kb - swap_free_kb) / 1024 ))"
  if [[ "$MEM_TOTAL_MB" -gt 0 ]]; then
    MEMORY_HEADROOM_PCT="$(( MEM_AVAILABLE_MB * 100 / MEM_TOTAL_MB ))"
  else
    MEMORY_HEADROOM_PCT=-1
  fi
}}

detect_memory_congestion() {{
  collect_memory_stats

  MEMORY_CONGESTED=0
  OOM_SIGNS=0
  CONGESTION_REASON=""
  RECOMMENDATION=""

  if [[ "$MEM_TOTAL_MB" -gt 0 ]]; then
    local min_headroom_mb
    min_headroom_mb="$(( MEM_TOTAL_MB / 20 ))"  # 5% memory headroom
    if [[ "$min_headroom_mb" -lt 512 ]]; then
      min_headroom_mb=512
    fi
    if [[ "$MEM_AVAILABLE_MB" -le "$min_headroom_mb" ]]; then
      MEMORY_CONGESTED=1
      CONGESTION_REASON="low_memory_headroom"
    fi
  fi

  if [[ "$SWAP_USED_MB" -ge 1024 ]]; then
    MEMORY_CONGESTED=1
    if [[ -n "$CONGESTION_REASON" ]]; then
      CONGESTION_REASON="$CONGESTION_REASON+swap_pressure"
    else
      CONGESTION_REASON="swap_pressure"
    fi
  fi

  if [[ -f "$RUN_LOG" ]] && tail -n 2000 "$RUN_LOG" | grep -E -q '(OutOfMemoryError|Cannot allocate memory|Killed process|killed by OOM|ENOMEM|Java heap space|GC overhead limit exceeded)'; then
    OOM_SIGNS=1
    MEMORY_CONGESTED=1
    if [[ -n "$CONGESTION_REASON" ]]; then
      CONGESTION_REASON="$CONGESTION_REASON+oom_signals"
    else
      CONGESTION_REASON="oom_signals"
    fi
  fi

  if [[ "$MEMORY_CONGESTED" -eq 1 ]]; then
    if [[ -n "$NEXT_MACHINE_TIER" && -n "$NEXT_MACHINE_TYPE" ]]; then
      RECOMMENDATION="rerun_cohort_on_tier_${{NEXT_MACHINE_TIER}}_machine_${{NEXT_MACHINE_TYPE}}"
    else
      RECOMMENDATION="memory_congestion_detected_consider_sharding_or_custom_highmem"
    fi
  fi
}}

update_progress_rate() {{
  local now_epoch delta_jobs delta_time instant_rate
  now_epoch="$(date +%s)"

  if [[ "$LAST_PROGRESS_EPOCH" -eq 0 ]]; then
    LAST_PROGRESS_EPOCH="$now_epoch"
    LAST_PROGRESS_COUNT="$COMPLETED"
    NO_PROGRESS_SECONDS=0
    return
  fi

  # Progress counters may move backwards if state was restored/reset; re-baseline.
  if [[ "$COMPLETED" -lt "$LAST_PROGRESS_COUNT" ]]; then
    LAST_PROGRESS_COUNT="$COMPLETED"
    LAST_PROGRESS_EPOCH="$now_epoch"
    NO_PROGRESS_SECONDS=0
    return
  fi

  if [[ "$COMPLETED" -gt "$LAST_PROGRESS_COUNT" ]]; then
    delta_jobs="$(( COMPLETED - LAST_PROGRESS_COUNT ))"
    delta_time="$(( now_epoch - LAST_PROGRESS_EPOCH ))"

    if [[ "$delta_jobs" -gt 0 && "$delta_time" -gt 0 ]]; then
      instant_rate="$(awk -v jobs="$delta_jobs" -v secs="$delta_time" 'BEGIN {{ printf "%.8f", jobs/secs }}')"
      if awk -v r="$EMA_RATE_JOBS_PER_SEC" 'BEGIN {{ exit !(r > 0) }}'; then
        EMA_RATE_JOBS_PER_SEC="$(awk -v old="$EMA_RATE_JOBS_PER_SEC" -v cur="$instant_rate" 'BEGIN {{ printf "%.8f", (0.70 * old) + (0.30 * cur) }}')"
      else
        EMA_RATE_JOBS_PER_SEC="$instant_rate"
      fi
    fi

    LAST_PROGRESS_COUNT="$COMPLETED"
    LAST_PROGRESS_EPOCH="$now_epoch"
    NO_PROGRESS_SECONDS=0
    return
  fi

  NO_PROGRESS_SECONDS="$(( now_epoch - LAST_PROGRESS_EPOCH ))"
}}

estimate_remaining_seconds() {{
  local elapsed="$1"
  local remaining_jobs global_rate rem_recent rem_global rem_blended penalty

  ETA_MODEL="insufficient_progress"
  ETA_CONFIDENCE="low"
  remaining=-1

  if [[ "$TOTAL" -gt 0 && "$COMPLETED" -ge "$TOTAL" ]]; then
    remaining=0
    ETA_MODEL="completed"
    ETA_CONFIDENCE="high"
    return
  fi

  if [[ "$TOTAL" -le "$COMPLETED" || "$TOTAL" -le 0 ]]; then
    return
  fi

  remaining_jobs="$(( TOTAL - COMPLETED ))"
  global_rate="$(awk -v c="$COMPLETED" -v e="$elapsed" 'BEGIN {{ if (c > 0 && e > 0) printf "%.8f", c/e; else printf "0" }}')"

  rem_recent=-1
  if awk -v r="$EMA_RATE_JOBS_PER_SEC" 'BEGIN {{ exit !(r > 0) }}'; then
    rem_recent="$(awk -v jobs="$remaining_jobs" -v r="$EMA_RATE_JOBS_PER_SEC" 'BEGIN {{ printf "%.0f", jobs/r }}')"
  fi

  rem_global=-1
  if awk -v r="$global_rate" 'BEGIN {{ exit !(r > 0) }}'; then
    rem_global="$(awk -v jobs="$remaining_jobs" -v r="$global_rate" 'BEGIN {{ printf "%.0f", jobs/r }}')"
  fi

  if [[ "$rem_recent" -ge 0 && ( "$NO_PROGRESS_SECONDS" -le "$STALL_THRESHOLD_SECONDS" || "$BENCH_CPU_ACTIVE" -eq 1 ) ]]; then
    if [[ "$rem_global" -ge 0 ]]; then
      rem_blended="$(awk -v recent="$rem_recent" -v global="$rem_global" 'BEGIN {{ printf "%.0f", (0.75 * recent) + (0.25 * global) }}')"
      remaining="$rem_blended"
      ETA_MODEL="blended_recent_global"
    else
      remaining="$rem_recent"
      ETA_MODEL="recent_rate"
    fi
    if [[ "$BENCH_CPU_ACTIVE" -eq 1 && "$NO_PROGRESS_SECONDS" -gt "$STALL_THRESHOLD_SECONDS" ]]; then
      ETA_MODEL="${{ETA_MODEL}}_cpu_active"
      ETA_CONFIDENCE="low"
    elif [[ "$COMPLETED" -ge 10 ]]; then
      ETA_CONFIDENCE="medium"
    fi
    return
  fi

  if [[ "$rem_recent" -ge 0 ]]; then
    penalty="$(( (NO_PROGRESS_SECONDS - STALL_THRESHOLD_SECONDS) / 2 ))"
    if [[ "$penalty" -lt 0 ]]; then
      penalty=0
    fi
    remaining="$(( rem_recent + penalty ))"
    ETA_MODEL="recent_rate_stall_penalized"
    ETA_CONFIDENCE="low"
    return
  fi

  if [[ "$rem_global" -ge 0 ]]; then
    remaining="$rem_global"
    ETA_MODEL="global_average"
    ETA_CONFIDENCE="low"
  fi
}}

detect_benchmark_liveness() {{
  BENCH_PROCESS_ALIVE=0
  BENCH_CPU_PERCENT=0
  BENCH_CPU_ACTIVE=0

  local container_alive=0
  if docker ps --format '{{{{.Names}}}}' | grep -q "^$CONTAINER_NAME$"; then
    container_alive=1
    BENCH_PROCESS_ALIVE=1
  fi

  if [[ "$BENCH_PROCESS_ALIVE" -eq 0 && -n "$BENCH_PID" ]] && kill -0 "$BENCH_PID" >/dev/null 2>&1; then
    BENCH_PROCESS_ALIVE=1
  fi

  local bench_cpu_raw=""
  local bench_cpu_numeric=""

  if [[ "$container_alive" -eq 1 ]]; then
    bench_cpu_raw="$(docker stats --no-stream --format '{{{{.CPUPerc}}}}' "$CONTAINER_NAME" 2>/dev/null | head -1 || true)"
  elif [[ -n "$BENCH_PID" ]] && kill -0 "$BENCH_PID" >/dev/null 2>&1; then
    bench_cpu_raw="$(ps -p "$BENCH_PID" -o %cpu= 2>/dev/null | head -1 || true)"
  fi

  bench_cpu_numeric="$(echo "$bench_cpu_raw" | tr -d ' %' | tr ',' '.' | sed -E 's/[^0-9.]//g')"
  if [[ -n "$bench_cpu_numeric" ]] && awk -v v="$bench_cpu_numeric" 'BEGIN {{ exit !(v+0 >= 0) }}'; then
    BENCH_CPU_PERCENT="$(awk -v v="$bench_cpu_numeric" 'BEGIN {{ printf "%.2f", v+0 }}')"
    if awk -v cpu="$BENCH_CPU_PERCENT" -v threshold="$CPU_ACTIVE_THRESHOLD_PCT" 'BEGIN {{ exit !(cpu >= threshold) }}'; then
      BENCH_CPU_ACTIVE=1
    fi
  fi
}}

write_state() {{
  local now elapsed remaining
  if [[ -f "$STATUS_FILE" ]]; then
    STATUS="$(cat "$STATUS_FILE")"
  fi
  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  elapsed="$(( $(date +%s) - START_EPOCH ))"
  remaining=-1

  detect_benchmark_liveness
  update_progress_rate
  estimate_remaining_seconds "$elapsed"
  detect_memory_congestion

  cat > "$STATE_FILE" <<EOF
{{
  "run_id": "$RUN_ID",
  "status": "$STATUS",
  "project": "$PROJECT",
  "zone": "$ZONE",
  "machine_type": "$MACHINE_TYPE",
  "machine_tier": "$MACHINE_TIER",
  "next_machine_tier": "$NEXT_MACHINE_TIER",
  "next_machine_type": "$NEXT_MACHINE_TYPE",
  "architecture": "$ARCH",
  "instance_name": "$(hostname)",
  "image_uri": "$IMAGE_URI",
  "data_uri": "$DATA_URI",
  "completed_jobs": $COMPLETED,
  "total_jobs": $TOTAL,
  "elapsed_seconds": $elapsed,
  "remaining_seconds": $remaining,
  "eta_model": "$ETA_MODEL",
  "eta_confidence": "$ETA_CONFIDENCE",
  "no_progress_seconds": $NO_PROGRESS_SECONDS,
  "stall_threshold_seconds": $STALL_THRESHOLD_SECONDS,
  "ema_jobs_per_second": $EMA_RATE_JOBS_PER_SEC,
  "benchmark_process_alive": $BENCH_PROCESS_ALIVE,
  "benchmark_cpu_percent": $BENCH_CPU_PERCENT,
  "benchmark_cpu_active": $BENCH_CPU_ACTIVE,
  "cpu_active_threshold_pct": $CPU_ACTIVE_THRESHOLD_PCT,
  "memory_total_mb": $MEM_TOTAL_MB,
  "memory_available_mb": $MEM_AVAILABLE_MB,
  "swap_used_mb": $SWAP_USED_MB,
  "memory_headroom_percent": $MEMORY_HEADROOM_PCT,
  "memory_congested": $MEMORY_CONGESTED,
  "oom_signals": $OOM_SIGNS,
  "congestion_reason": "$CONGESTION_REASON",
  "recommendation": "$RECOMMENDATION",
  "cancel_requested": $CANCEL_REQUESTED,
  "updated_at": "$now",
  "results_dir": "$RESULTS_DIR",
  "gcs_run_root": "$RUN_ROOT"
}}
EOF
}}

sync_state() {{
  gcloud storage cp --quiet "$STATE_FILE" "$RUN_ROOT/state/state.json" >/dev/null 2>&1 || true
}}

sync_jobs_db() {{
  local db_path="$RESULTS_DIR/jobs.db"
  local snapshot_tmp="$STATE_DIR/jobs.db.snapshot.tmp"
  local snapshot_path="$STATE_DIR/jobs.db.snapshot"

  if [[ ! -f "$db_path" ]]; then
    return
  fi

  # Use SQLite backup API for a consistent snapshot while writers are active.
  python3 - "$db_path" "$snapshot_tmp" <<'PY'
import sqlite3
import sys
from pathlib import Path

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
dst.parent.mkdir(parents=True, exist_ok=True)

try:
    src_conn = sqlite3.connect(str(src))
    dst_conn = sqlite3.connect(str(dst))
    with dst_conn:
        src_conn.backup(dst_conn)
    src_conn.close()
    dst_conn.close()
except Exception:
    # Best-effort sync path; failures are handled by caller.
    pass
PY

  if [[ -f "$snapshot_tmp" ]]; then
    mv -f "$snapshot_tmp" "$snapshot_path"
    gcloud storage cp --quiet "$snapshot_path" "$RUN_ROOT/output/jobs.db" >/dev/null 2>&1 || true
  fi
}}

sync_partial_results() {{
  if [[ ! -d "$RESULTS_DIR" ]]; then
    return
  fi

  if [[ -f "$RUN_LOG" ]]; then
    gcloud storage cp --quiet "$RUN_LOG" "$RUN_ROOT/logs/run.log" >/dev/null 2>&1 || true
  fi

  if [[ -f "$RESULTS_DIR/engine_status.json" ]]; then
    gcloud storage cp --quiet "$RESULTS_DIR/engine_status.json" "$RUN_ROOT/output/engine_status.json" >/dev/null 2>&1 || true
  fi

  if [[ -f "$RESULTS_DIR/summary.partial.json" ]]; then
    gcloud storage cp --quiet "$RESULTS_DIR/summary.partial.json" "$RUN_ROOT/output/summary.partial.json" >/dev/null 2>&1 || true
  fi

  if [[ -f "$RESULTS_DIR/raw_results/benchmark_results.partial.json" ]]; then
    gcloud storage cp --quiet \
      "$RESULTS_DIR/raw_results/benchmark_results.partial.json" \
      "$RUN_ROOT/output/raw_results/benchmark_results.partial.json" >/dev/null 2>&1 || true
  fi

  sync_jobs_db

  if ls "$RESULTS_DIR"/logs/transaction_log_*.jsonl >/dev/null 2>&1; then
    gcloud storage cp --quiet "$RESULTS_DIR"/logs/transaction_log_*.jsonl "$RUN_ROOT/output/logs/" >/dev/null 2>&1 || true
  fi
}}

parse_progress_from_db() {{
  local db_path="$RESULTS_DIR/jobs.db"
  if [[ ! -f "$db_path" ]]; then
    return
  fi

  local progress
  progress="$(python3 - "$db_path" <<'PY'
import sqlite3
import sys

db_path = sys.argv[1]
try:
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    total = cur.execute("SELECT COUNT(*) FROM benchmark_jobs").fetchone()[0] or 0
    completed = cur.execute(
        "SELECT COUNT(*) FROM benchmark_jobs "
        "WHERE status IN ('COMPLETED','FAILED','TIMEOUT','CANCELLED','SKIPPED_LOWVARIANCE')"
    ).fetchone()[0] or 0
    conn.close()
    print(f"{{completed}} {{total}}")
except Exception:
    print("")
PY
)"

  if [[ -n "$progress" ]]; then
    local db_completed db_total
    db_completed="$(echo "$progress" | awk '{{print $1}}')"
    db_total="$(echo "$progress" | awk '{{print $2}}')"
    if [[ "$db_total" =~ ^[0-9]+$ ]]; then
      COMPLETED="$db_completed"
      TOTAL="$db_total"
    fi
  fi
}}

parse_progress_from_log() {{
  if [[ ! -f "$RUN_LOG" ]]; then
    return
  fi
  local line
  line="$(grep -E '\\[[0-9]+/[0-9]+\\] Completed:' "$RUN_LOG" | tail -1 || true)"
  if [[ -n "$line" ]]; then
    COMPLETED="$(echo "$line" | sed -E 's/.*\\[([0-9]+)\\/([0-9]+)\\].*/\\1/')"
    TOTAL="$(echo "$line" | sed -E 's/.*\\[([0-9]+)\\/([0-9]+)\\].*/\\2/')"
  fi
}}

check_cancel_request() {{
  if gcloud storage ls "$RUN_ROOT/control/cancel_requested" >/dev/null 2>&1; then
    if [[ "$CANCEL_REQUESTED" -eq 0 ]]; then
      CANCEL_REQUESTED=1
      CANCEL_EPOCH="$(date +%s)"
      set_status "cancelling"
      if docker ps --format '{{{{.Names}}}}' | grep -q "^$CONTAINER_NAME$"; then
        docker stop -t 30 "$CONTAINER_NAME" >/dev/null 2>&1 || true
      fi
      if [[ -n "$BENCH_PID" ]] && kill -0 "$BENCH_PID" >/dev/null 2>&1; then
        kill -INT "$BENCH_PID" >/dev/null 2>&1 || true
      fi
    fi
  fi
}}

monitor_loop() {{
  while true; do
    parse_progress_from_db
    parse_progress_from_log
    check_cancel_request

    if [[ "$CANCEL_REQUESTED" -eq 1 && "$CANCEL_EPOCH" -gt 0 ]]; then
      local since_cancel
      since_cancel="$(( $(date +%s) - CANCEL_EPOCH ))"
      if [[ "$since_cancel" -ge 300 ]]; then
        if docker ps --format '{{{{.Names}}}}' | grep -q "^$CONTAINER_NAME$"; then
          docker kill "$CONTAINER_NAME" >/dev/null 2>&1 || true
        fi
      fi
    fi

    write_state
    sync_state
    sync_partial_results

    if [[ -f "$FINAL_MARKER" ]]; then
      break
    fi
    sleep 15
  done
}}

generate_engine_outcomes() {{
  local results_json="$RESULTS_DIR/raw_results/benchmark_results.json"
  local outcomes_json="$RESULTS_DIR/engine_outcomes.json"
  if [[ ! -f "$results_json" ]]; then
    return
  fi

  python3 - "$results_json" "$outcomes_json" <<'PY'
import json
import itertools
import sys
from collections import defaultdict
from pathlib import Path

results_path = Path(sys.argv[1])
outcomes_path = Path(sys.argv[2])

raw = json.loads(results_path.read_text(encoding="utf-8"))
engine_stats = defaultdict(lambda: {{"ok": 0, "failed": 0, "total": 0}})

for row in raw:
    if not isinstance(row, dict):
        continue
    engine = str(row.get("engine_name", "unknown"))
    status = str(row.get("status", "")).lower()
    engine_stats[engine]["total"] += 1
    if status == "ok":
        engine_stats[engine]["ok"] += 1
    else:
        engine_stats[engine]["failed"] += 1

successful = sorted([e for e, st in engine_stats.items() if st["ok"] > 0])
pairwise = [list(pair) for pair in itertools.combinations(successful, 2)]

out = {{
    "engines": dict(engine_stats),
    "successful_engines": successful,
    "pairwise_successful_comparisons": pairwise,
}}
outcomes_path.write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
PY
}}

cleanup_and_exit() {{
  local exit_code="$1"
  if [[ "$exit_code" -eq 0 ]]; then
    set_status "completed"
  elif [[ "$CANCEL_REQUESTED" -eq 1 ]]; then
    set_status "cancelled"
  else
    set_status "failed"
  fi

  parse_progress_from_db
  parse_progress_from_log
  generate_engine_outcomes
  write_state
  sync_state

  gcloud storage cp --quiet "$RUN_LOG" "$RUN_ROOT/logs/run.log" >/dev/null 2>&1 || true
  if [[ -d "$RESULTS_DIR" ]]; then
    gcloud storage rsync --recursive "$RESULTS_DIR" "$RUN_ROOT/output" >/dev/null 2>&1 || true
  fi

  touch "$FINAL_MARKER"
  write_state
  sync_state

  if [[ "$AUTO_SHUTDOWN" == "true" ]]; then
    shutdown -h now || true
  fi
}}

monitor_loop &

write_state
sync_state

set_status "installing_dependencies"
write_state
sync_state

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends ca-certificates curl gnupg apt-transport-https docker.io
systemctl enable --now docker

install -d -m 0755 /usr/share/keyrings
curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg | gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg
echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" > /etc/apt/sources.list.d/google-cloud-sdk.list
apt-get update
apt-get install -y google-cloud-cli

set_status "downloading_artifacts"
write_state
sync_state

gcloud storage cp "$CONFIG_URI" "$WORK_ROOT/config.json"
gcloud storage cp "$DATA_URI" "$WORK_ROOT/data_bundle.tar.gz"
tar -xzf "$WORK_ROOT/data_bundle.tar.gz" -C "$DATA_DIR"

set_status "restoring_previous_state"
write_state
sync_state

# Restore previously synced output to resume interrupted long runs.
if gcloud storage ls "$RUN_ROOT/output/**" >/dev/null 2>&1; then
  gcloud storage rsync --recursive "$RUN_ROOT/output" "$RESULTS_DIR" >/dev/null 2>&1 || true
fi

gcloud auth configure-docker "$REGISTRY_HOST" --quiet
docker pull "$IMAGE_URI"

set_status "running_benchmark"
write_state
sync_state

if gcloud storage ls "$RUN_ROOT/output/summary.json" >/dev/null 2>&1; then
  set_status "completed"
  write_state
  sync_state
  touch "$FINAL_MARKER"
  if [[ "$AUTO_SHUTDOWN" == "true" ]]; then
    shutdown -h now || true
  fi
  exit 0
fi

set +e
docker run --rm --name "$CONTAINER_NAME" \\
  -v "$WORK_ROOT:/work" \\
  "$IMAGE_URI" \\
  /bin/bash -lc "
    set -euo pipefail
    cd /workspace/rmatch/benchmarking/framework/regex_bench_framework
    mkdir -p /work/results/data
    cp -n /work/data_bundle/patterns_*.txt /work/results/data/ 2>/dev/null || true
    cp -n /work/data_bundle/patterns_*_metadata.json /work/results/data/ 2>/dev/null || true
    cp -n /work/data_bundle/corpus_*.txt /work/results/data/ 2>/dev/null || true
    .venv/bin/regex-bench check-engines --output /work/results
    .venv/bin/regex-bench job-start --config /work/config.json --output /work/results --parallel 1
  " > "$RUN_LOG" 2>&1 &
BENCH_PID="$!"
wait "$BENCH_PID"
BENCH_EXIT="$?"
set -e

cleanup_and_exit "$BENCH_EXIT"
"""


def cmd_publish_image(args: argparse.Namespace) -> int:
    _require_cmd("gcloud")
    project = args.project or _default_project()
    region = args.region
    repo = args.repo
    image_name = args.image_name
    tag = args.tag or _utc_now().strftime("%Y%m%d-%H%M%S")

    if not _artifact_repo_exists(project, region, repo):
        _out(f"Creating Artifact Registry repo {repo} in {region} ...")
        _create_artifact_repo(project, region, repo)

    image_uri = f"{region}-docker.pkg.dev/{project}/{repo}/{image_name}:{tag}"
    dockerfile = FRAMEWORK_DIR / "Dockerfile"

    _out(f"Building and pushing image: {image_uri}")
    with tempfile.TemporaryDirectory(prefix="rmatch_cloudbuild_") as td:
        cfg = Path(td) / "cloudbuild.yaml"
        cfg.write_text(
            "\n".join(
                [
                    "steps:",
                    "- name: gcr.io/cloud-builders/docker",
                    "  args:",
                    f"  - build",
                    f"  - -f",
                    f"  - {dockerfile.relative_to(REPO_ROOT)}",
                    f"  - -t",
                    f"  - {image_uri}",
                    "  - .",
                    "images:",
                    f"- {image_uri}",
                    "",
                ]
            ),
            encoding="utf-8",
        )

        _run(
            [
                "gcloud",
                "builds",
                "submit",
                str(REPO_ROOT),
                "--project",
                project,
                "--config",
                str(cfg),
            ]
        )

    _ensure_local_runs_dir()
    latest = {
        "image_uri": image_uri,
        "project": project,
        "region": region,
        "repo": repo,
        "image_name": image_name,
        "tag": tag,
        "published_at": _iso_utc(),
    }
    _save_latest(LATEST_IMAGE_FILE, latest)

    _out("")
    _out(f"Published image: {image_uri}")
    _out(f"Saved: {LATEST_IMAGE_FILE}")
    return 0


def cmd_publish_data(args: argparse.Namespace) -> int:
    _require_cmd("gcloud")
    project = args.project or _default_project()
    zone = args.zone
    region = _region_from_zone(zone)
    bucket = args.bucket or f"{project}-rmatch-bench"
    config_path = Path(args.config).resolve()
    if not config_path.exists():
        raise RuntimeError(f"Config file not found: {config_path}")

    dataset_id = args.dataset_id or _utc_now().strftime("%Y%m%d-%H%M%S")
    dataset_root = f"gs://{bucket}/datasets/{dataset_id}"

    if not _bucket_exists(bucket):
        _out(f"Creating bucket gs://{bucket} in {region} ...")
        _create_bucket(bucket, project, region)

    with tempfile.TemporaryDirectory(prefix=f"rmatch_data_{dataset_id}_") as td:
        tmp = Path(td)
        bundle_dir = tmp / "data_bundle"
        manifest = _prepare_data_bundle(config_path, bundle_dir)

        tar_path = tmp / "data_bundle.tar.gz"
        with tarfile.open(tar_path, "w:gz") as tf:
            for p in sorted(bundle_dir.glob("*")):
                tf.add(p, arcname=p.name)

        manifest["bundle_sha256"] = _sha256_file(tar_path)
        manifest["dataset_id"] = dataset_id
        manifest["project"] = project
        manifest["bucket"] = bucket
        manifest["dataset_root"] = dataset_root

        manifest_path = tmp / "dataset_manifest.json"
        manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

        _out(f"Uploading data bundle to {dataset_root} ...")
        _run(["gcloud", "storage", "cp", str(tar_path), f"{dataset_root}/data_bundle.tar.gz"])
        _run(["gcloud", "storage", "cp", str(manifest_path), f"{dataset_root}/dataset_manifest.json"])

    _ensure_local_runs_dir()
    latest = {
        "dataset_id": dataset_id,
        "dataset_root": dataset_root,
        "data_uri": f"{dataset_root}/data_bundle.tar.gz",
        "manifest_uri": f"{dataset_root}/dataset_manifest.json",
        "config": str(config_path),
        "published_at": _iso_utc(),
    }
    _save_latest(LATEST_DATA_FILE, latest)

    _out("")
    _out(f"Published dataset: {dataset_id}")
    _out(f"Data URI: {latest['data_uri']}")
    _out(f"Saved: {LATEST_DATA_FILE}")
    return 0


def _resolve_start_context(args: argparse.Namespace) -> dict[str, str]:
    project = args.project or _default_project()
    zone = args.zone
    region = _region_from_zone(zone)
    bucket = args.bucket or f"{project}-rmatch-bench"

    image_uri = args.image_uri
    if not image_uri:
        latest_img = _load_latest(LATEST_IMAGE_FILE)
        if not latest_img:
            raise RuntimeError("No --image-uri provided and no latest image found. Run publish-image first.")
        image_uri = str(latest_img["image_uri"])

    data_uri = args.data_uri
    if not data_uri:
        latest_data = _load_latest(LATEST_DATA_FILE)
        if not latest_data:
            raise RuntimeError("No --data-uri provided and no latest dataset found. Run publish-data first.")
        data_uri = str(latest_data["data_uri"])

    if not _bucket_exists(bucket):
        _out(f"Creating bucket gs://{bucket} in {region} ...")
        _create_bucket(bucket, project, region)

    return {
        "project": project,
        "zone": zone,
        "region": region,
        "bucket": bucket,
        "image_uri": image_uri,
        "data_uri": data_uri,
    }


def _start_single_run(
    *,
    project: str,
    zone: str,
    region: str,
    bucket: str,
    config_path: Path,
    image_uri: str,
    data_uri: str,
    machine_type: str,
    machine_tier: str,
    disk_gb: int,
    instance_prefix: str,
    spot: bool,
    auto_shutdown: bool,
    batch_id: str | None = None,
    batch_index: int | None = None,
    batch_size: int | None = None,
) -> dict[str, Any]:
    if not config_path.exists():
        raise RuntimeError(f"Config file not found: {config_path}")

    _ensure_local_runs_dir()

    run_id = f"{_utc_now().strftime('%Y%m%d_%H%M%S')}_{secrets.token_hex(3)}"
    instance_name = f"{instance_prefix}-{run_id.lower().replace('_', '-')}"
    run_root = f"gs://{bucket}/runs/{run_id}"
    next_machine_tier = _next_machine_tier(machine_tier) or ""
    next_machine_type = TIER_TO_MACHINE_TYPE.get(next_machine_tier, "") if next_machine_tier else ""

    with tempfile.TemporaryDirectory(prefix=f"rmatch_{run_id}_") as td:
        tmp = Path(td)
        config_copy = tmp / "config.json"
        startup_script_path = tmp / "startup.sh"

        config_copy.write_text(config_path.read_text(encoding="utf-8"), encoding="utf-8")
        _out("Uploading run config to GCS ...")
        _run(["gcloud", "storage", "cp", str(config_copy), f"{run_root}/input/config.json"])

        startup_script_path.write_text(
            _make_startup_script(
                run_id=run_id,
                project=project,
                zone=zone,
                bucket=bucket,
                machine_type=machine_type,
                machine_tier=machine_tier,
                next_machine_tier=next_machine_tier,
                next_machine_type=next_machine_type,
                image_uri=image_uri,
                data_uri=data_uri,
                auto_shutdown=auto_shutdown,
            ),
            encoding="utf-8",
        )

        create_cmd = [
            "gcloud",
            "compute",
            "instances",
            "create",
            instance_name,
            "--project",
            project,
            "--zone",
            zone,
            "--machine-type",
            machine_type,
            "--image-family",
            "ubuntu-2404-lts-amd64",
            "--image-project",
            "ubuntu-os-cloud",
            "--boot-disk-size",
            f"{disk_gb}GB",
            "--scopes",
            "https://www.googleapis.com/auth/cloud-platform",
            "--metadata-from-file",
            f"startup-script={startup_script_path}",
            "--labels",
            f"app=rmatch-bench,run_id={_label_safe_run_id(run_id)}",
        ]

        if spot:
            create_cmd.extend(
                [
                    "--provisioning-model",
                    "SPOT",
                    "--instance-termination-action",
                    "STOP",
                ]
            )

        _out("Creating GCE instance ...")
        _run(create_cmd)

    manifest: dict[str, Any] = {
        "run_id": run_id,
        "created_at": _iso_utc(),
        "project": project,
        "zone": zone,
        "region": region,
        "bucket": bucket,
        "gcs_run_root": run_root,
        "instance_name": instance_name,
        "machine_type": machine_type,
        "machine_tier": machine_tier,
        "next_machine_tier": next_machine_tier,
        "next_machine_type": next_machine_type,
        "spot": bool(spot),
        "config_path": str(config_path),
        "config_name": config_path.name,
        "image_uri": image_uri,
        "data_uri": data_uri,
        "auto_shutdown": auto_shutdown,
        "execution_mode": "docker_image_plus_data_bundle_job_queue_resume",
    }
    if batch_id:
        manifest["batch_id"] = batch_id
        manifest["batch_index"] = batch_index
        manifest["batch_size"] = batch_size

    manifest_path = _run_manifest_path(run_id)
    _write_json(manifest_path, manifest)
    _run(["gcloud", "storage", "cp", str(manifest_path), f"{run_root}/meta/run_manifest.json"])
    manifest["local_manifest_path"] = str(manifest_path)
    return manifest


def _print_started_run(manifest: dict[str, Any]) -> None:
    _out("")
    _out(f"Started run: {manifest['run_id']}")
    _out(f"Instance: {manifest['instance_name']} ({manifest['zone']})")
    _out(f"Image: {manifest.get('image_uri', 'n/a')}")
    _out(f"Data:  {manifest.get('data_uri', 'n/a')}")
    _out(f"GCS root: {manifest['gcs_run_root']}")
    _out(f"Local manifest: {manifest.get('local_manifest_path', _run_manifest_path(manifest['run_id']))}")
    _out("")
    _out("Next commands:")
    _out(f"  {SCRIPT_PATH} status --run-id {manifest['run_id']}")
    _out(f"  {SCRIPT_PATH} watch --run-id {manifest['run_id']}")
    _out(f"  {SCRIPT_PATH} cancel --run-id {manifest['run_id']}")


def _run_status_snapshot(manifest: dict[str, Any]) -> dict[str, Any]:
    run_root = manifest["gcs_run_root"]
    state = _fetch_remote_json(f"{run_root}/state/state.json")
    vm_status = _instance_status(manifest["project"], manifest["zone"], manifest["instance_name"])

    status = str((state or {}).get("status", "unknown")).lower()
    if not state and vm_status.upper() in {"TERMINATED", "STOPPED", "STOPPING"}:
        status = "stopped"

    completed = int((state or {}).get("completed_jobs", 0) or 0)
    total = int((state or {}).get("total_jobs", 0) or 0)
    elapsed = (state or {}).get("elapsed_seconds")
    remaining = (state or {}).get("remaining_seconds")
    updated_at = (state or {}).get("updated_at")
    cancel_requested = bool((state or {}).get("cancel_requested", False))
    memory_total_mb = int((state or {}).get("memory_total_mb", 0) or 0)
    memory_available_mb = int((state or {}).get("memory_available_mb", 0) or 0)
    swap_used_mb = int((state or {}).get("swap_used_mb", 0) or 0)
    memory_headroom_percent = int((state or {}).get("memory_headroom_percent", -1) or -1)
    memory_congested = bool((state or {}).get("memory_congested", False))
    oom_signals = bool((state or {}).get("oom_signals", False))
    congestion_reason = str((state or {}).get("congestion_reason", "")).strip()
    recommendation = str((state or {}).get("recommendation", "")).strip()
    architecture = str((state or {}).get("architecture", manifest.get("architecture", ""))).strip()
    eta_model = str((state or {}).get("eta_model", "legacy_global_average")).strip() or "legacy_global_average"
    eta_confidence = str((state or {}).get("eta_confidence", "unknown")).strip() or "unknown"
    no_progress_seconds = int((state or {}).get("no_progress_seconds", -1) or -1)
    stall_threshold_seconds = int((state or {}).get("stall_threshold_seconds", -1) or -1)
    ema_jobs_per_second = float((state or {}).get("ema_jobs_per_second", 0.0) or 0.0)
    benchmark_process_alive_raw = (state or {}).get("benchmark_process_alive", None)
    benchmark_process_alive = None if benchmark_process_alive_raw is None else bool(benchmark_process_alive_raw)
    benchmark_cpu_percent = float((state or {}).get("benchmark_cpu_percent", 0.0) or 0.0)
    benchmark_cpu_active_raw = (state or {}).get("benchmark_cpu_active", None)
    benchmark_cpu_active = None if benchmark_cpu_active_raw is None else bool(benchmark_cpu_active_raw)
    cpu_active_threshold_pct = float((state or {}).get("cpu_active_threshold_pct", 0.0) or 0.0)

    pct = (completed / total) * 100.0 if total > 0 else 0.0
    return {
        "manifest": manifest,
        "state": state,
        "vm_status": vm_status,
        "status": status,
        "completed_jobs": completed,
        "total_jobs": total,
        "elapsed_seconds": elapsed,
        "remaining_seconds": remaining,
        "updated_at": updated_at,
        "cancel_requested": cancel_requested,
        "progress_pct": pct,
        "memory_total_mb": memory_total_mb,
        "memory_available_mb": memory_available_mb,
        "swap_used_mb": swap_used_mb,
        "memory_headroom_percent": memory_headroom_percent,
        "memory_congested": memory_congested,
        "oom_signals": oom_signals,
        "congestion_reason": congestion_reason,
        "recommendation": recommendation,
        "architecture": architecture,
        "eta_model": eta_model,
        "eta_confidence": eta_confidence,
        "no_progress_seconds": no_progress_seconds,
        "stall_threshold_seconds": stall_threshold_seconds,
        "ema_jobs_per_second": ema_jobs_per_second,
        "benchmark_process_alive": benchmark_process_alive,
        "benchmark_cpu_percent": benchmark_cpu_percent,
        "benchmark_cpu_active": benchmark_cpu_active,
        "cpu_active_threshold_pct": cpu_active_threshold_pct,
    }


def _print_run_status(snapshot: dict[str, Any]) -> None:
    manifest = snapshot["manifest"]
    machine_tier = _manifest_machine_tier(manifest)
    _out(f"Run ID:       {manifest['run_id']}")
    _out(f"Project/Zone: {manifest['project']} / {manifest['zone']}")
    _out(f"Instance:     {manifest['instance_name']} ({snapshot['vm_status']})")
    _out(f"Tier/Type:    {machine_tier} / {manifest.get('machine_type', '?')}")
    if snapshot.get("architecture"):
        _out(f"Architecture: {snapshot.get('architecture')}")
    _out(f"Image:        {manifest.get('image_uri', 'n/a')}")
    _out(f"Data:         {manifest.get('data_uri', 'n/a')}")
    _out(f"GCS Root:     {manifest['gcs_run_root']}")

    if not snapshot["state"]:
        _out("State:        (not yet published)")
        return

    _out(f"Run Status:   {snapshot['status']}")
    if snapshot["total_jobs"] > 0:
        _out(
            f"Progress:     {snapshot['completed_jobs']}/{snapshot['total_jobs']} "
            f"({snapshot['progress_pct']:.1f}%)"
        )
    else:
        _out(f"Progress:     {snapshot['completed_jobs']}/? (estimating total...)")
    _out(f"Elapsed:      {_human_duration(snapshot['elapsed_seconds'])}")
    eta_suffix = ""
    cpu_active = snapshot.get("benchmark_cpu_active")
    if snapshot.get("no_progress_seconds", -1) >= 0 and snapshot.get("stall_threshold_seconds", -1) > 0:
        if snapshot["no_progress_seconds"] > snapshot["stall_threshold_seconds"]:
            if cpu_active:
                eta_suffix = f" (compute active; no completed job for {_human_duration(snapshot['no_progress_seconds'])})"
            else:
                eta_suffix = f" (low confidence; no progress for {_human_duration(snapshot['no_progress_seconds'])})"
        elif snapshot.get("eta_confidence") == "low":
            eta_suffix = " (low confidence)"
    _out(f"ETA:          {_human_duration(snapshot['remaining_seconds'])}{eta_suffix}")
    _out(f"ETA Model:    {snapshot.get('eta_model', 'unknown')} ({snapshot.get('eta_confidence', 'unknown')})")
    if snapshot.get("ema_jobs_per_second", 0.0) > 0:
        _out(f"Rate (EMA):   {snapshot['ema_jobs_per_second']:.6f} jobs/s")
    if snapshot.get("no_progress_seconds", -1) >= 0:
        _out(f"No Progress:  {_human_duration(snapshot['no_progress_seconds'])}")
    proc_alive = snapshot.get("benchmark_process_alive")
    if proc_alive is None:
        _out("Proc Alive:   unknown")
    else:
        _out(f"Proc Alive:   {'yes' if proc_alive else 'no'}")
    if cpu_active is None:
        _out("Bench CPU:    unknown")
    else:
        threshold = snapshot.get("cpu_active_threshold_pct", 0.0)
        _out(
            f"Bench CPU:    {snapshot.get('benchmark_cpu_percent', 0.0):.1f}% "
            f"({'active' if cpu_active else 'idle'}, threshold={threshold:.0f}%)"
        )
    if snapshot["memory_total_mb"] > 0:
        _out(
            "Memory:       "
            + f"{snapshot['memory_available_mb']}/{snapshot['memory_total_mb']} MB avail "
            + f"({snapshot['memory_headroom_percent']}% headroom), swap {snapshot['swap_used_mb']} MB"
        )
    _out(f"Mem Pressure: {'yes' if snapshot['memory_congested'] else 'no'}")
    _out(f"OOM Signals:  {'yes' if snapshot['oom_signals'] else 'no'}")
    if snapshot["congestion_reason"]:
        _out(f"Reason:       {snapshot['congestion_reason']}")
    if snapshot["recommendation"]:
        _out(f"Recommend:    {snapshot['recommendation']}")
    _out(f"Cancel Req:   {snapshot['cancel_requested']}")
    _out(f"Updated:      {snapshot['updated_at'] or 'unknown'}")


def _terminal_status(status: str) -> bool:
    return status in {"completed", "failed", "cancelled"}


def _snapshot_terminal(snapshot: dict[str, Any]) -> bool:
    return _terminal_status(str(snapshot.get("status", "")).lower()) or str(snapshot.get("status", "")).lower() == "stopped"


def _stop_instance(manifest: dict[str, Any]) -> None:
    _run(
        [
            "gcloud",
            "compute",
            "instances",
            "stop",
            manifest["instance_name"],
            "--project",
            manifest["project"],
            "--zone",
            manifest["zone"],
            "--quiet",
        ]
    )


def _start_instance(manifest: dict[str, Any]) -> None:
    _run(
        [
            "gcloud",
            "compute",
            "instances",
            "start",
            manifest["instance_name"],
            "--project",
            manifest["project"],
            "--zone",
            manifest["zone"],
            "--quiet",
        ]
    )


def _resolve_batch_run_manifests(batch_manifest: dict[str, Any]) -> tuple[list[dict[str, Any]], list[str]]:
    manifests: list[dict[str, Any]] = []
    missing: list[str] = []
    for entry in batch_manifest.get("runs", []):
        run_id = str(entry.get("run_id", "")).strip()
        if not run_id:
            continue
        try:
            manifests.append(_load_manifest(run_id))
            continue
        except Exception:
            pass

        if _is_run_manifest(entry):
            manifests.append(entry)
        else:
            missing.append(run_id)
    return manifests, missing


def _print_batch_status(batch_manifest: dict[str, Any], snapshots: list[dict[str, Any]], missing: list[str]) -> None:
    batch_tier = _manifest_machine_tier(batch_manifest)
    _out(f"Batch ID:     {batch_manifest['batch_id']}")
    _out(f"Created:      {batch_manifest.get('created_at', 'unknown')}")
    _out(f"Project/Zone: {batch_manifest.get('project', '?')} / {batch_manifest.get('zone', '?')}")
    _out("Tier/Type:    " + f"{batch_tier} / {batch_manifest.get('machine_type', '?')}")
    _out(f"Runs:         {len(snapshots)}")
    if missing:
        _out(f"Missing local manifests: {', '.join(missing)}")

    if not snapshots:
        return

    _out("")
    _out("Run ID                  Status       Progress         ETA        VM Status   MemP  Config")
    _out("-" * 98)
    for snapshot in snapshots:
        manifest = snapshot["manifest"]
        if snapshot["total_jobs"] > 0:
            progress = f"{snapshot['completed_jobs']}/{snapshot['total_jobs']} ({snapshot['progress_pct']:.1f}%)"
        else:
            progress = f"{snapshot['completed_jobs']}/?"
        _out(
            f"{manifest['run_id']:<22} "
            f"{snapshot['status']:<12} "
            f"{progress:<16} "
            f"{_human_duration(snapshot['remaining_seconds']):<10} "
            f"{snapshot['vm_status']:<11} "
            f"{('Y' if snapshot['memory_congested'] else 'N'):<5} "
            f"{manifest.get('config_name', '?')}"
        )

    status_counts: dict[str, int] = {}
    for snapshot in snapshots:
        key = snapshot["status"]
        status_counts[key] = status_counts.get(key, 0) + 1

    completed_sum = sum(s["completed_jobs"] for s in snapshots)
    known_total_sum = sum(s["total_jobs"] for s in snapshots if s["total_jobs"] > 0)
    eta_candidates = [
        int(s["remaining_seconds"])
        for s in snapshots
        if isinstance(s["remaining_seconds"], (int, float)) and int(s["remaining_seconds"]) >= 0
    ]
    batch_eta = max(eta_candidates) if eta_candidates else None
    terminal_runs = sum(1 for s in snapshots if _snapshot_terminal(s))
    memory_pressure_runs = sum(1 for s in snapshots if s["memory_congested"])
    oom_runs = sum(1 for s in snapshots if s["oom_signals"])

    _out("")
    if known_total_sum > 0:
        _out(f"Aggregate Progress: {completed_sum}/{known_total_sum} ({(completed_sum/known_total_sum)*100.0:.1f}%)")
    else:
        _out(f"Aggregate Progress: {completed_sum}/? (waiting for totals)")
    _out(f"Batch ETA:          {_human_duration(batch_eta)}")
    _out(f"Terminal Runs:      {terminal_runs}/{len(snapshots)}")
    _out(f"Memory Pressure:    {memory_pressure_runs}/{len(snapshots)}")
    _out(f"OOM Signals:        {oom_runs}/{len(snapshots)}")
    _out("Status Counts:      " + ", ".join(f"{k}={v}" for k, v in sorted(status_counts.items())))

    recommendations = sorted({s["recommendation"] for s in snapshots if s.get("recommendation")})
    if recommendations:
        _out("Recommendations:")
        for recommendation in recommendations:
            _out(f"  - {recommendation}")


def _parse_batch_configs(args: argparse.Namespace) -> list[Path]:
    raw_paths: list[str] = []
    if getattr(args, "configs", None):
        raw_paths.extend(p.strip() for p in str(args.configs).split(",") if p.strip())

    configs_file = getattr(args, "configs_file", None)
    if configs_file:
        file_path = Path(configs_file).expanduser().resolve()
        if not file_path.exists():
            raise RuntimeError(f"Batch config list file not found: {file_path}")
        for line in file_path.read_text(encoding="utf-8").splitlines():
            item = line.strip()
            if item and not item.startswith("#"):
                raw_paths.append(item)

    if not raw_paths:
        raise RuntimeError("No configs provided. Use --configs and/or --configs-file.")

    seen: set[str] = set()
    paths: list[Path] = []
    for raw in raw_paths:
        path = Path(raw).expanduser().resolve()
        if not path.exists():
            raise RuntimeError(f"Config file not found: {path}")
        key = str(path)
        if key in seen:
            continue
        seen.add(key)
        paths.append(path)
    return paths


def cmd_start(args: argparse.Namespace) -> int:
    _require_cmd("gcloud")
    context = _resolve_start_context(args)
    config_path = Path(args.config).resolve()
    machine_tier = _resolve_machine_tier(getattr(args, "machine_tier", None), args.machine_type)

    manifest = _start_single_run(
        project=context["project"],
        zone=context["zone"],
        region=context["region"],
        bucket=context["bucket"],
        config_path=config_path,
        image_uri=context["image_uri"],
        data_uri=context["data_uri"],
        machine_type=args.machine_type,
        machine_tier=machine_tier,
        disk_gb=args.disk_gb,
        instance_prefix=args.instance_prefix,
        spot=bool(args.spot),
        auto_shutdown=not args.no_auto_shutdown,
    )
    _print_started_run(manifest)
    return 0


def cmd_resume(args: argparse.Namespace) -> int:
    manifest = _load_manifest(args.run_id)
    vm_status = _instance_status(manifest["project"], manifest["zone"], manifest["instance_name"]).upper()

    if vm_status in {"RUNNING", "PROVISIONING", "STAGING"}:
        _out(f"Run {manifest['run_id']} is already active ({vm_status}).")
        snapshot = _run_status_snapshot(manifest)
        _print_run_status(snapshot)
        return 0

    if vm_status in {"TERMINATED", "STOPPED", "STOPPING"}:
        _out(f"Starting VM for run {manifest['run_id']} ({manifest['instance_name']}) ...")
        _start_instance(manifest)
        _out("VM start requested.")
        _out(f"Follow progress with: {SCRIPT_PATH} watch --run-id {manifest['run_id']}")
        return 0

    raise RuntimeError(
        "Cannot resume run because VM is not in a startable state. "
        f"run_id={manifest['run_id']} vm_status={vm_status}"
    )


def cmd_start_batch(args: argparse.Namespace) -> int:
    _require_cmd("gcloud")
    context = _resolve_start_context(args)
    config_paths = _parse_batch_configs(args)
    batch_id = args.batch_id or f"{_utc_now().strftime('%Y%m%d_%H%M%S')}_{secrets.token_hex(2)}"
    machine_tier = _resolve_machine_tier(getattr(args, "machine_tier", None), args.machine_type)

    _out(f"Starting batch {batch_id} with {len(config_paths)} run(s)...")
    runs: list[dict[str, Any]] = []
    for index, config_path in enumerate(config_paths, start=1):
        _out(f"[{index}/{len(config_paths)}] Config: {config_path.name}")
        run_manifest = _start_single_run(
            project=context["project"],
            zone=context["zone"],
            region=context["region"],
            bucket=context["bucket"],
            config_path=config_path,
            image_uri=context["image_uri"],
            data_uri=context["data_uri"],
            machine_type=args.machine_type,
            machine_tier=machine_tier,
            disk_gb=args.disk_gb,
            instance_prefix=args.instance_prefix,
            spot=bool(args.spot),
            auto_shutdown=not args.no_auto_shutdown,
            batch_id=batch_id,
            batch_index=index,
            batch_size=len(config_paths),
        )
        runs.append(run_manifest)
        _out(f"  -> Run {run_manifest['run_id']} on {run_manifest['instance_name']}")

    batch_manifest: dict[str, Any] = {
        "batch_id": batch_id,
        "created_at": _iso_utc(),
        "project": context["project"],
        "zone": context["zone"],
        "region": context["region"],
        "bucket": context["bucket"],
        "machine_type": args.machine_type,
        "machine_tier": machine_tier,
        "next_machine_tier": _next_machine_tier(machine_tier) or "",
        "next_machine_type": TIER_TO_MACHINE_TYPE.get(_next_machine_tier(machine_tier) or "", ""),
        "spot": bool(args.spot),
        "auto_shutdown": not args.no_auto_shutdown,
        "image_uri": context["image_uri"],
        "data_uri": context["data_uri"],
        "run_count": len(runs),
        "runs": runs,
    }
    batch_path = _batch_manifest_path(batch_id)
    _write_json(batch_path, batch_manifest)
    _run(
        [
            "gcloud",
            "storage",
            "cp",
            str(batch_path),
            f"gs://{context['bucket']}/batches/{batch_id}/batch_manifest.json",
        ]
    )

    _out("")
    _out(f"Started batch: {batch_id}")
    _out(f"Local batch manifest: {batch_path}")
    _out("Next commands:")
    _out(f"  {SCRIPT_PATH} status-batch --batch-id {batch_id}")
    _out(f"  {SCRIPT_PATH} watch-batch --batch-id {batch_id}")
    _out(f"  {SCRIPT_PATH} cancel-batch --batch-id {batch_id}")
    return 0


def cmd_status(args: argparse.Namespace) -> int:
    manifest = _load_manifest(args.run_id)
    snapshot = _run_status_snapshot(manifest)
    _print_run_status(snapshot)
    return 0


def cmd_status_batch(args: argparse.Namespace) -> int:
    batch_manifest = _load_batch_manifest(args.batch_id)
    manifests, missing = _resolve_batch_run_manifests(batch_manifest)
    snapshots = [_run_status_snapshot(m) for m in manifests]
    _print_batch_status(batch_manifest, snapshots, missing)
    return 0


def cmd_watch(args: argparse.Namespace) -> int:
    while True:
        _out("")
        _out("=" * 72)
        manifest = _load_manifest(args.run_id)
        snapshot = _run_status_snapshot(manifest)
        _print_run_status(snapshot)
        if _snapshot_terminal(snapshot):
            _out("Terminal status reached.")
            return 0
        time.sleep(args.interval)


def cmd_watch_batch(args: argparse.Namespace) -> int:
    while True:
        _out("")
        _out("=" * 72)
        batch_manifest = _load_batch_manifest(args.batch_id)
        manifests, missing = _resolve_batch_run_manifests(batch_manifest)
        snapshots = [_run_status_snapshot(m) for m in manifests]
        _print_batch_status(batch_manifest, snapshots, missing)
        if not snapshots:
            _out("No runs found in batch manifest.")
            return 1
        if snapshots and all(_snapshot_terminal(s) for s in snapshots):
            _out("All runs reached terminal status.")
            return 0
        time.sleep(args.interval)


def _write_cancel_marker(run_root: str) -> None:
    with tempfile.NamedTemporaryFile("w", delete=False, encoding="utf-8") as tf:
        tf.write(json.dumps({"requested_at": _iso_utc()}) + "\n")
        temp_path = Path(tf.name)
    try:
        _run(["gcloud", "storage", "cp", str(temp_path), f"{run_root}/control/cancel_requested"])
    finally:
        temp_path.unlink(missing_ok=True)


def cmd_cancel(args: argparse.Namespace) -> int:
    manifest = _load_manifest(args.run_id)
    _out(f"Requesting graceful cancel for run {manifest['run_id']} ...")
    _write_cancel_marker(manifest["gcs_run_root"])
    _out("Cancel marker written.")

    if args.wait_seconds <= 0:
        return 0

    started = time.time()
    while True:
        snapshot = _run_status_snapshot(manifest)
        if _snapshot_terminal(snapshot):
            _out(f"Run reached terminal status: {snapshot['status']}")
            return 0

        if time.time() - started >= args.wait_seconds:
            if args.force_stop:
                _out("Grace period exceeded; force-stopping VM.")
                _stop_instance(manifest)
                return 0
            _out("Grace period exceeded; leaving VM running.")
            return 1
        time.sleep(10)


def cmd_cancel_batch(args: argparse.Namespace) -> int:
    batch_manifest = _load_batch_manifest(args.batch_id)
    manifests, missing = _resolve_batch_run_manifests(batch_manifest)
    if missing:
        _out(f"Skipping {len(missing)} run(s) with missing manifests: {', '.join(missing)}")

    for manifest in manifests:
        _out(f"Requesting graceful cancel for run {manifest['run_id']} ...")
        _write_cancel_marker(manifest["gcs_run_root"])

    if args.wait_seconds <= 0:
        return 0

    started = time.time()
    while True:
        snapshots = [_run_status_snapshot(m) for m in manifests]
        pending = [s for s in snapshots if not _snapshot_terminal(s)]
        if not pending:
            _out("All runs reached terminal status.")
            return 0

        if time.time() - started >= args.wait_seconds:
            if args.force_stop:
                _out("Grace period exceeded; force-stopping remaining VMs.")
                for snapshot in pending:
                    _stop_instance(snapshot["manifest"])
                return 0
            _out("Grace period exceeded; leaving remaining VMs running.")
            return 1
        time.sleep(10)


def cmd_stop(args: argparse.Namespace) -> int:
    manifest = _load_manifest(args.run_id)
    _stop_instance(manifest)
    _out(f"Stopped VM for run {manifest['run_id']}: {manifest['instance_name']}")
    return 0


def cmd_stop_batch(args: argparse.Namespace) -> int:
    batch_manifest = _load_batch_manifest(args.batch_id)
    manifests, missing = _resolve_batch_run_manifests(batch_manifest)
    if missing:
        _out(f"Skipping {len(missing)} run(s) with missing manifests: {', '.join(missing)}")
    for manifest in manifests:
        _stop_instance(manifest)
        _out(f"Stopped VM for run {manifest['run_id']}: {manifest['instance_name']}")
    return 0


def cmd_list(_: argparse.Namespace) -> int:
    _ensure_local_runs_dir()
    manifests = sorted(LOCAL_RUNS_DIR.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not manifests:
        _out("No local GCP run manifests found.")
        return 0
    shown = 0
    for path in manifests:
        try:
            data = _read_json(path)
            if not _is_run_manifest(data):
                continue
            _out(
                f"{data.get('run_id','?')}  "
                f"{data.get('project','?')}  "
                f"{data.get('zone','?')}  "
                f"{data.get('instance_name','?')}"
            )
            shown += 1
        except Exception:
            _out(f"{path.name}  (unreadable)")
    if shown == 0:
        _out("No local GCP run manifests found.")
    return 0


def cmd_list_batches(_: argparse.Namespace) -> int:
    _ensure_local_runs_dir()
    manifests = sorted(
        LOCAL_RUNS_DIR.glob(f"{BATCH_MANIFEST_PREFIX}*.json"),
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )
    if not manifests:
        _out("No local GCP batch manifests found.")
        return 0
    shown = 0
    for path in manifests:
        try:
            data = _read_json(path)
            if not _is_batch_manifest(data):
                continue
            _out(
                f"{data.get('batch_id','?')}  "
                f"runs={data.get('run_count', len(data.get('runs', [])))}  "
                f"{data.get('project','?')}  "
                f"{data.get('zone','?')}"
            )
            shown += 1
        except Exception:
            _out(f"{path.name}  (unreadable)")
    if shown == 0:
        _out("No local GCP batch manifests found.")
    return 0


def _parse_engine_list(raw: str | None) -> list[str]:
    if not raw:
        return list(DEFAULT_REQUIRE_ENGINES)
    engines = [x.strip() for x in str(raw).split(",") if x.strip()]
    return engines or list(DEFAULT_REQUIRE_ENGINES)


def _fetch_engine_outcomes(run_root: str) -> dict[str, Any] | None:
    return _fetch_remote_json(f"{run_root}/output/engine_outcomes.json")


def _cohort_key(manifest: dict[str, Any], snapshot: dict[str, Any]) -> str:
    config_name = manifest.get("config_name", "?")
    data_uri = manifest.get("data_uri", "?")
    image_uri = manifest.get("image_uri", "?")
    machine_tier = _manifest_machine_tier(manifest)
    machine_type = manifest.get("machine_type", "?")
    arch = snapshot.get("architecture") or "unknown"
    return (
        f"config={config_name} | tier={machine_tier} | type={machine_type} | arch={arch} | "
        f"data={data_uri} | image={image_uri}"
    )


def cmd_cohort_report(args: argparse.Namespace) -> int:
    required_engines = _parse_engine_list(args.require_engines)
    if len(required_engines) < 2:
        raise RuntimeError("Need at least two engines in --require-engines for pairwise comparability.")

    if args.batch_id:
        batch_manifest = _load_batch_manifest(args.batch_id)
        manifests, missing = _resolve_batch_run_manifests(batch_manifest)
        if missing:
            _out(f"Skipping {len(missing)} run(s) with missing manifests: {', '.join(missing)}")
    else:
        manifests = []
        for path in sorted(LOCAL_RUNS_DIR.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True):
            try:
                data = _read_json(path)
                if _is_run_manifest(data):
                    manifests.append(data)
            except Exception:
                continue

    if args.tier:
        tier_filter = _normalize_tier(args.tier)
        if not tier_filter:
            raise RuntimeError(f"Invalid tier filter: {args.tier}")
        manifests = [m for m in manifests if _manifest_machine_tier(m) == tier_filter]

    if not manifests:
        _out("No runs found for cohort report.")
        return 1

    cohort_groups: dict[str, list[dict[str, Any]]] = {}
    pairwise_all: dict[tuple[str, str], int] = {}
    cohort_results: list[dict[str, Any]] = []

    for manifest in manifests:
        snapshot = _run_status_snapshot(manifest)
        outcomes = _fetch_engine_outcomes(manifest["gcs_run_root"]) or {}
        successful_engines = set(outcomes.get("successful_engines", []))
        if not successful_engines:
            # Fallback when outcomes file is unavailable.
            if snapshot.get("status") == "completed":
                successful_engines = set(required_engines)
            else:
                successful_engines = set()

        required_successful = sorted(successful_engines.intersection(required_engines))
        pairwise_present = sorted(list(itertools.combinations(required_successful, 2)))
        for pair in pairwise_present:
            pairwise_all[pair] = pairwise_all.get(pair, 0) + 1

        key = _cohort_key(manifest, snapshot)
        row = {
            "run_id": manifest["run_id"],
            "status": snapshot["status"],
            "machine_tier": _manifest_machine_tier(manifest),
            "machine_type": manifest.get("machine_type"),
            "config_name": manifest.get("config_name"),
            "required_successful_engines": required_successful,
            "pairwise_present": ["<->".join(pair) for pair in pairwise_present],
            "memory_congested": snapshot.get("memory_congested", False),
            "oom_signals": snapshot.get("oom_signals", False),
            "recommendation": snapshot.get("recommendation", ""),
        }
        cohort_groups.setdefault(key, []).append(row)
        cohort_results.append({"cohort": key, **row})

    for cohort, rows in cohort_groups.items():
        _out("")
        _out("=" * 120)
        _out(f"Cohort: {cohort}")
        _out(f"Runs:   {len(rows)}")
        _out("-" * 120)
        _out("Run ID                  Status       Engines Successful                    Pairwise")
        _out("-" * 120)
        for row in rows:
            engines_display = ",".join(row["required_successful_engines"]) or "-"
            pairwise_display = ",".join(row["pairwise_present"]) or "-"
            _out(f"{row['run_id']:<22} {row['status']:<12} {engines_display:<35} {pairwise_display}")
            if row["memory_congested"] or row["oom_signals"]:
                _out(
                    "  memory: "
                    + f"congested={row['memory_congested']} oom={row['oom_signals']} "
                    + f"recommendation={row['recommendation'] or '-'}"
                )

        pairwise_counts: dict[str, int] = {}
        for row in rows:
            for pair in row["pairwise_present"]:
                pairwise_counts[pair] = pairwise_counts.get(pair, 0) + 1
        _out("Pairwise Coverage in Cohort:")
        if pairwise_counts:
            for pair_name in sorted(pairwise_counts):
                _out(f"  {pair_name}: {pairwise_counts[pair_name]} run(s)")
        else:
            _out("  none")

    _out("")
    _out("Global Pairwise Coverage:")
    for pair in itertools.combinations(sorted(required_engines), 2):
        count = pairwise_all.get(tuple(pair), 0)
        _out(f"  {pair[0]}<->{pair[1]}: {count} run(s)")

    if args.json_out:
        out_path = Path(args.json_out).expanduser().resolve()
        out_payload = {
            "generated_at": _iso_utc(),
            "required_engines": required_engines,
            "cohorts": cohort_results,
            "global_pairwise_counts": {f"{a}<->{b}": c for (a, b), c in sorted(pairwise_all.items())},
        }
        _write_json(out_path, out_payload)
        _out(f"Saved JSON report: {out_path}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="GCP control for regex benchmark runs")
    sub = parser.add_subparsers(dest="command", required=True)

    p_pub_img = sub.add_parser("publish-image", help="Build/push Docker image to Artifact Registry")
    p_pub_img.add_argument("--project", default=None, help="GCP project (default: active gcloud project)")
    p_pub_img.add_argument("--region", default="europe-north2", help="Artifact Registry region")
    p_pub_img.add_argument("--repo", default="rmatch-bench", help="Artifact Registry repository")
    p_pub_img.add_argument("--image-name", default="regex-bench", help="Image name")
    p_pub_img.add_argument("--tag", default=None, help="Image tag (default: UTC timestamp)")
    p_pub_img.set_defaults(func=cmd_publish_image)

    p_pub_data = sub.add_parser("publish-data", help="Upload pattern/corpus bundle to GCS")
    p_pub_data.add_argument(
        "--config",
        default=str(FRAMEWORK_DIR / "test_matrix" / "canonical_baseline_v1.json"),
        help="Config JSON used to derive required pattern counts and corpus sizes",
    )
    p_pub_data.add_argument("--project", default=None, help="GCP project (default: active gcloud project)")
    p_pub_data.add_argument("--zone", default="europe-north2-a", help="Zone used to infer bucket region")
    p_pub_data.add_argument("--bucket", default=None, help="GCS bucket (default: <project>-rmatch-bench)")
    p_pub_data.add_argument("--dataset-id", default=None, help="Dataset ID (default: UTC timestamp)")
    p_pub_data.set_defaults(func=cmd_publish_data)

    p_start = sub.add_parser("start", help="Start a benchmark run on GCE using published image + data")
    p_start.add_argument(
        "--config",
        default=str(FRAMEWORK_DIR / "test_matrix" / "canonical_baseline_v1.json"),
        help="Path to benchmark config JSON",
    )
    p_start.add_argument("--project", default=None, help="GCP project (default: active gcloud project)")
    p_start.add_argument("--zone", default="europe-north2-a", help="GCE zone")
    p_start.add_argument("--bucket", default=None, help="GCS bucket (default: <project>-rmatch-bench)")
    p_start.add_argument("--image-uri", default=None, help="Artifact Registry image URI (default: latest published)")
    p_start.add_argument("--data-uri", default=None, help="GCS data bundle URI (default: latest published)")
    p_start.add_argument("--machine-type", default="n2-standard-8", help="GCE machine type")
    p_start.add_argument("--machine-tier", default=None, choices=TIER_ORDER, help="Comparability tier label override")
    p_start.add_argument("--disk-gb", type=int, default=120, help="Boot disk size in GB")
    p_start.add_argument("--instance-prefix", default="rmatch-bench", help="Instance name prefix")
    p_start.add_argument("--spot", action="store_true", help="Use Spot VM")
    p_start.add_argument("--no-auto-shutdown", action="store_true", help="Leave VM running after run finishes")
    p_start.set_defaults(func=cmd_start)

    p_resume = sub.add_parser("resume", help="Resume a stopped/terminated run VM")
    p_resume.add_argument("--run-id", default=None, help="Run ID (default: latest local run)")
    p_resume.set_defaults(func=cmd_resume)

    p_start_batch = sub.add_parser("start-batch", help="Start multiple benchmark runs in parallel (one VM per config)")
    p_start_batch.add_argument(
        "--configs",
        default="",
        help="Comma-separated config JSON paths",
    )
    p_start_batch.add_argument(
        "--configs-file",
        default=None,
        help="File with one config JSON path per line (supports comments with #)",
    )
    p_start_batch.add_argument("--batch-id", default=None, help="Batch ID (default: UTC timestamp + random suffix)")
    p_start_batch.add_argument("--project", default=None, help="GCP project (default: active gcloud project)")
    p_start_batch.add_argument("--zone", default="europe-north2-a", help="GCE zone")
    p_start_batch.add_argument("--bucket", default=None, help="GCS bucket (default: <project>-rmatch-bench)")
    p_start_batch.add_argument("--image-uri", default=None, help="Artifact Registry image URI (default: latest published)")
    p_start_batch.add_argument("--data-uri", default=None, help="GCS data bundle URI (default: latest published)")
    p_start_batch.add_argument("--machine-type", default="n2-standard-8", help="GCE machine type")
    p_start_batch.add_argument("--machine-tier", default=None, choices=TIER_ORDER, help="Comparability tier label override")
    p_start_batch.add_argument("--disk-gb", type=int, default=120, help="Boot disk size in GB")
    p_start_batch.add_argument("--instance-prefix", default="rmatch-bench", help="Instance name prefix")
    p_start_batch.add_argument("--spot", action="store_true", help="Use Spot VM")
    p_start_batch.add_argument("--no-auto-shutdown", action="store_true", help="Leave VMs running after runs finish")
    p_start_batch.set_defaults(func=cmd_start_batch)

    p_status = sub.add_parser("status", help="Show status for a run")
    p_status.add_argument("--run-id", default=None, help="Run ID (default: latest local run)")
    p_status.set_defaults(func=cmd_status)

    p_status_batch = sub.add_parser("status-batch", help="Show aggregate status for a batch")
    p_status_batch.add_argument("--batch-id", default=None, help="Batch ID (default: latest local batch)")
    p_status_batch.set_defaults(func=cmd_status_batch)

    p_watch = sub.add_parser("watch", help="Continuously watch run status")
    p_watch.add_argument("--run-id", default=None, help="Run ID (default: latest local run)")
    p_watch.add_argument("--interval", type=int, default=20, help="Polling interval seconds")
    p_watch.set_defaults(func=cmd_watch)

    p_watch_batch = sub.add_parser("watch-batch", help="Continuously watch batch status")
    p_watch_batch.add_argument("--batch-id", default=None, help="Batch ID (default: latest local batch)")
    p_watch_batch.add_argument("--interval", type=int, default=20, help="Polling interval seconds")
    p_watch_batch.set_defaults(func=cmd_watch_batch)

    p_cancel = sub.add_parser("cancel", help="Request safe cancellation")
    p_cancel.add_argument("--run-id", default=None, help="Run ID (default: latest local run)")
    p_cancel.add_argument("--wait-seconds", type=int, default=300, help="Grace period before optional force-stop")
    p_cancel.add_argument("--force-stop", action="store_true", help="Force-stop VM if grace wait is exceeded")
    p_cancel.set_defaults(func=cmd_cancel)

    p_cancel_batch = sub.add_parser("cancel-batch", help="Request safe cancellation for all runs in a batch")
    p_cancel_batch.add_argument("--batch-id", default=None, help="Batch ID (default: latest local batch)")
    p_cancel_batch.add_argument("--wait-seconds", type=int, default=300, help="Grace period before optional force-stop")
    p_cancel_batch.add_argument("--force-stop", action="store_true", help="Force-stop VMs if grace wait is exceeded")
    p_cancel_batch.set_defaults(func=cmd_cancel_batch)

    p_stop = sub.add_parser("stop", help="Immediately stop VM for a run")
    p_stop.add_argument("--run-id", default=None, help="Run ID (default: latest local run)")
    p_stop.set_defaults(func=cmd_stop)

    p_stop_batch = sub.add_parser("stop-batch", help="Immediately stop VMs for all runs in a batch")
    p_stop_batch.add_argument("--batch-id", default=None, help="Batch ID (default: latest local batch)")
    p_stop_batch.set_defaults(func=cmd_stop_batch)

    p_list = sub.add_parser("list", help="List local run manifests")
    p_list.set_defaults(func=cmd_list)

    p_list_batches = sub.add_parser("list-batches", help="List local batch manifests")
    p_list_batches.set_defaults(func=cmd_list_batches)

    p_cohort_report = sub.add_parser("cohort-report", help="Show comparable cohorts and pairwise engine coverage")
    p_cohort_report.add_argument("--batch-id", default=None, help="Batch ID (default: all local runs)")
    p_cohort_report.add_argument("--tier", default=None, choices=TIER_ORDER, help="Filter by machine tier")
    p_cohort_report.add_argument(
        "--require-engines",
        default=",".join(DEFAULT_REQUIRE_ENGINES),
        help="Comma-separated engines required for pairwise comparability",
    )
    p_cohort_report.add_argument("--json-out", default=None, help="Optional output JSON path")
    p_cohort_report.set_defaults(func=cmd_cohort_report)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        return args.func(args)
    except Exception as exc:
        _eprint(f"ERROR: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
