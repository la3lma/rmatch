#!/usr/bin/env python3
"""
Generate a GCP run status snapshot report (Markdown).

This is intended as a companion artifact for `make report`, especially when
some runs were terminated/failed and only partial benchmark outputs exist.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


def _run(cmd: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, check=False)


def _run_stdout(cmd: list[str]) -> str:
    proc = _run(cmd)
    if proc.returncode != 0:
        return ""
    return proc.stdout.strip()


def _default_project() -> str:
    project = _run_stdout(["gcloud", "config", "get-value", "project"])
    return project


def _list_run_ids(bucket: str) -> list[str]:
    proc = _run(["gcloud", "storage", "ls", f"gs://{bucket}/runs/"])
    if proc.returncode != 0:
        return []
    run_ids: list[str] = []
    for line in proc.stdout.splitlines():
        item = line.strip().rstrip("/")
        if not item:
            continue
        run_ids.append(item.rsplit("/", 1)[-1])
    return sorted(set(run_ids))


def _read_json_uri(uri: str) -> dict[str, Any] | None:
    proc = _run(["gcloud", "storage", "cat", uri])
    if proc.returncode != 0 or not proc.stdout.strip():
        return None
    try:
        return json.loads(proc.stdout)
    except json.JSONDecodeError:
        return None


def _uri_exists(uri: str) -> bool:
    proc = _run(["gcloud", "storage", "ls", uri])
    return proc.returncode == 0 and bool(proc.stdout.strip())


def _has_output(bucket: str, run_id: str) -> bool:
    proc = _run(["gcloud", "storage", "ls", f"gs://{bucket}/runs/{run_id}/output/**"])
    return proc.returncode == 0 and bool(proc.stdout.strip())


def _list_instances(project: str) -> dict[str, dict[str, str]]:
    proc = _run(
        [
            "gcloud",
            "compute",
            "instances",
            "list",
            "--project",
            project,
            "--filter",
            "labels.app=rmatch-bench",
            "--format",
            "json(name,status,zone,labels,machineType)",
        ]
    )
    if proc.returncode != 0:
        return {}

    try:
        rows = json.loads(proc.stdout)
    except json.JSONDecodeError:
        return {}

    out: dict[str, dict[str, str]] = {}
    for row in rows:
        labels = row.get("labels") or {}
        run_id = str(labels.get("run_id", "")).strip()
        if not run_id:
            continue
        out[run_id] = {
            "instance_name": str(row.get("name", "")),
            "vm_status": str(row.get("status", "UNKNOWN")),
            "zone": str(row.get("zone", "")).rsplit("/", 1)[-1],
            "machine_type": str(row.get("machineType", "")).rsplit("/", 1)[-1],
        }
    return out


def _md_table(rows: list[dict[str, Any]]) -> str:
    lines = [
        "| Run ID | VM Status | Run Status | Progress | Output | Jobs DB | Txn Log | Log | Updated (UTC) |",
        "|---|---|---|---:|---|---|---|---|---|",
    ]
    for r in rows:
        progress = f"{r.get('completed_jobs', '?')}/{r.get('total_jobs', '?')}"
        lines.append(
            f"| `{r.get('run_id','?')}` | {r.get('vm_status','?')} | {r.get('run_status','?')} | "
            f"{progress} | {r.get('has_output','no')} | {r.get('has_jobs_db','no')} | "
            f"{r.get('has_tx_log','no')} | {r.get('has_log','no')} | "
            f"{r.get('updated_at','?')} |"
        )
    if len(rows) == 0:
        lines.append("| _none_ | - | - | - | - | - | - | - | - |")
    return "\n".join(lines)


def _status_counts(rows: list[dict[str, Any]]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for row in rows:
        key = str(row.get("run_status", "unknown"))
        counts[key] = counts.get(key, 0) + 1
    return counts


def build_report(project: str, bucket: str) -> str:
    now = dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")
    instances = _list_instances(project)
    run_ids = _list_run_ids(bucket)

    rows: list[dict[str, Any]] = []
    for run_id in run_ids:
        state = _read_json_uri(f"gs://{bucket}/runs/{run_id}/state/state.json") or {}
        vm = instances.get(run_id, {})

        row = {
            "run_id": run_id,
            "vm_status": vm.get("vm_status", "DELETED_OR_UNKNOWN"),
            "run_status": state.get("status", "missing_state"),
            "completed_jobs": state.get("completed_jobs", "?"),
            "total_jobs": state.get("total_jobs", "?"),
            "updated_at": state.get("updated_at", "?"),
            "has_output": "yes" if _has_output(bucket, run_id) else "no",
            "has_jobs_db": "yes" if _uri_exists(f"gs://{bucket}/runs/{run_id}/output/jobs.db") else "no",
            "has_tx_log": "yes" if _uri_exists(f"gs://{bucket}/runs/{run_id}/output/logs/transaction_log_*.jsonl") else "no",
            "has_log": "yes" if _uri_exists(f"gs://{bucket}/runs/{run_id}/logs/run.log") else "no",
        }
        rows.append(row)

    terminated = [r for r in rows if r.get("vm_status") == "TERMINATED"]
    with_output = [r for r in rows if r.get("has_output") == "yes"]
    counts = _status_counts(rows)

    counts_line = ", ".join(f"{k}={v}" for k, v in sorted(counts.items())) or "none"

    return "\n".join(
        [
            "# GCP Run Status Snapshot",
            "",
            f"- Generated (UTC): `{now}`",
            f"- Project: `{project}`",
            f"- Bucket: `{bucket}`",
            f"- Runs discovered in GCS: `{len(rows)}`",
            f"- VM terminated runs: `{len(terminated)}`",
            f"- Runs with output artifacts: `{len(with_output)}`",
            f"- Run status counts: `{counts_line}`",
            "",
            "## Terminated VM Runs",
            "",
            _md_table(terminated),
            "",
            "## All Runs",
            "",
            _md_table(rows),
            "",
            "## Notes",
            "",
            "- `missing_state` means no `state/state.json` was found for that run ID.",
            "- `DELETED_OR_UNKNOWN` means no matching live VM was found by label.",
            "- This report is an artifact snapshot and can be used even if a later run fails.",
            "",
        ]
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate GCP run status snapshot report")
    parser.add_argument("--project", default=None, help="GCP project (default: active gcloud project)")
    parser.add_argument("--bucket", required=True, help="GCS bucket holding runs/")
    parser.add_argument("--output", required=True, help="Output markdown file path")
    args = parser.parse_args()

    project = args.project or _default_project()
    if not project:
        print("ERROR: no GCP project set. Pass --project or run `gcloud config set project ...`", file=sys.stderr)
        return 1

    report = build_report(project=project, bucket=args.bucket)
    out_path = Path(args.output).expanduser().resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(report, encoding="utf-8")
    print(f"Wrote status snapshot: {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
