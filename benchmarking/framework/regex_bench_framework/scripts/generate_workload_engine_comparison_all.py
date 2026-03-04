#!/usr/bin/env python3
"""Generate workload-vs-engine reports across all runs in a results directory."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import html
import json
import math
import sqlite3
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Any


def _escape(text: Any) -> str:
    return html.escape(str(text), quote=True)


def _bytes_label(num_bytes: int) -> str:
    mib = num_bytes / (1024 * 1024)
    if mib < 1024:
        return f"{int(round(mib))}MB"
    gib = mib / 1024
    return f"{gib:.1f}GB"


def _parse_input_size_to_bytes(size: str) -> int:
    s = str(size).strip().upper()
    mult = 1
    if s.endswith("KB"):
        mult = 1024
        s = s[:-2]
    elif s.endswith("MB"):
        mult = 1024 * 1024
        s = s[:-2]
    elif s.endswith("GB"):
        mult = 1024 * 1024 * 1024
        s = s[:-2]
    try:
        return int(float(s) * mult)
    except Exception:
        return 0


def _safe_stdev(values: list[float]) -> float:
    if len(values) < 2:
        return 0.0
    return statistics.stdev(values)


def _to_int(value: Any, default: int = 0) -> int:
    if value is None:
        return default
    if isinstance(value, bool):
        return int(value)
    text = str(value).strip()
    if not text:
        return default
    try:
        return int(text)
    except Exception:
        try:
            return int(float(text))
        except Exception:
            return default


def _is_failure_status(status: str) -> bool:
    s = str(status or "").strip().lower()
    if not s:
        return False
    if s in {"ok", "completed", "queued", "running"}:
        return False
    return True


def _geomean(values: list[float]) -> float:
    positive = [v for v in values if v > 0]
    if not positive:
        return float("nan")
    return math.exp(sum(math.log(v) for v in positive) / len(positive))


def _load_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def _json_for_html(value: Any) -> str:
    """Serialize JSON so it can be safely embedded inside an HTML <script> node."""
    return json.dumps(value, separators=(",", ":"), ensure_ascii=True).replace("</", "<\\/")


def _find_nearest(path: Path, filename: str, max_up: int = 3) -> Path | None:
    cur = path
    for _ in range(max_up + 1):
        candidate = cur / filename
        if candidate.exists():
            return candidate
        if cur.parent == cur:
            break
        cur = cur.parent
    return None


def _load_run_metadata(run_dir: Path) -> dict[str, str]:
    manifest_path = _find_nearest(run_dir, "run_manifest.json", max_up=4)
    state_path = (
        _find_nearest(run_dir, "_gcp_meta/state.json", max_up=3)
        or _find_nearest(run_dir, "state.json", max_up=4)
    )
    manifest = _load_json(manifest_path) if manifest_path else {}
    state = _load_json(state_path) if state_path else {}

    machine_type = (
        str(state.get("machine_type") or manifest.get("machine_type") or "").strip()
    )
    architecture = (
        str(state.get("architecture") or manifest.get("architecture") or "").strip()
    )
    run_id = str(
        manifest.get("run_id")
        or state.get("run_id")
        or ""
    ).strip()

    if not run_id:
        name = run_dir.name
        if name.startswith("gcp_"):
            run_id = name.replace("gcp_", "", 1)
        else:
            run_id = name

    return {
        "run_id": run_id,
        "machine_type": machine_type or "unknown",
        "architecture": architecture or "unknown",
    }


def _rows_from_final(run_dir: Path) -> tuple[list[dict[str, Any]], str] | None:
    path = run_dir / "raw_results" / "benchmark_results.json"
    if not path.exists():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(data, list):
            return data, str(path)
    except Exception:
        return None
    return None


def _rows_from_partial(run_dir: Path) -> tuple[list[dict[str, Any]], str] | None:
    path = run_dir / "raw_results" / "benchmark_results.partial.json"
    if not path.exists():
        return None
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(payload, dict):
            rows = payload.get("results")
            if isinstance(rows, list):
                return rows, str(path)
    except Exception:
        return None
    return None


def _rows_from_jobs_db(run_dir: Path) -> tuple[list[dict[str, Any]], str] | None:
    path = run_dir / "jobs.db"
    if not path.exists():
        return None

    conn = sqlite3.connect(str(path))
    conn.row_factory = sqlite3.Row
    try:
        rows = conn.execute(
            """
            SELECT
                run_id,
                engine_name,
                iteration,
                status,
                compilation_ns,
                scanning_ns,
                total_ns,
                match_count,
                result_json,
                pattern_count,
                input_size,
                input_size_bytes,
                pattern_suite,
                corpus_name,
                error_message
            FROM benchmark_jobs
            """
        ).fetchall()
    except Exception:
        conn.close()
        return None
    conn.close()

    mapped: list[dict[str, Any]] = []
    for r in rows:
        status_raw = str(r["status"] or "").upper()
        status = "ok" if status_raw == "COMPLETED" else status_raw.lower()
        corpus_size_bytes = int(r["input_size_bytes"] or 0)
        if corpus_size_bytes <= 0:
            corpus_size_bytes = _parse_input_size_to_bytes(str(r["input_size"] or ""))
        match_checksum = None
        try:
            payload = json.loads(str(r["result_json"] or "{}"))
            checksum = payload.get("match_checksum")
            if checksum:
                match_checksum = str(checksum)
        except Exception:
            match_checksum = None
        mapped.append(
            {
                "run_id": str(r["run_id"] or "").strip(),
                "engine_name": r["engine_name"],
                "iteration": r["iteration"],
                "status": status,
                "compilation_ns": r["compilation_ns"] or 0,
                "scanning_ns": r["scanning_ns"] or 0,
                "total_ns": r["total_ns"] or 0,
                "match_count": r["match_count"] or 0,
                "match_checksum": match_checksum,
                "patterns_compiled": r["pattern_count"] or 0,
                "corpus_size_bytes": corpus_size_bytes,
                "pattern_suite": str(r["pattern_suite"] or "unknown"),
                "corpus_name": str(r["corpus_name"] or "unknown"),
                "error_message": str(r["error_message"] or ""),
            }
        )
    return mapped, str(path)


def _load_rows_for_run(run_dir: Path) -> tuple[list[dict[str, Any]], str] | None:
    for loader in (_rows_from_final, _rows_from_partial, _rows_from_jobs_db):
        out = loader(run_dir)
        if out and out[0]:
            return out
    return None


def _find_run_dirs(results_dir: Path) -> list[Path]:
    candidates: set[Path] = set()
    for p in results_dir.rglob("*"):
        if not p.is_dir():
            continue
        if (p / "raw_results" / "benchmark_results.json").exists():
            candidates.add(p)
            continue
        if (p / "raw_results" / "benchmark_results.partial.json").exists():
            candidates.add(p)
            continue
        if (p / "jobs.db").exists():
            candidates.add(p)
            continue
    return sorted(candidates)


def _build_rows(results_dir: Path) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    run_dirs = _find_run_dirs(results_dir)
    all_rows: list[dict[str, Any]] = []
    run_inventory: list[dict[str, Any]] = []

    for run_dir in run_dirs:
        loaded = _load_rows_for_run(run_dir)
        if not loaded:
            continue
        rows, source = loaded
        meta = _load_run_metadata(run_dir)
        run_id = meta["run_id"]
        machine_type = meta["machine_type"]
        architecture = meta["architecture"]
        cohort = f"{machine_type}|{architecture}"

        ok_count = 0
        engines: set[str] = set()
        for row in rows:
            status = str(row.get("status", "")).lower()
            engine = str(row.get("engine_name", "")).strip()
            if engine:
                engines.add(engine)
            if status == "ok":
                ok_count += 1

            patterns = int(row.get("patterns_compiled", 0) or 0)
            corpus_size_bytes = int(row.get("corpus_size_bytes", 0) or 0)
            if patterns <= 0 or corpus_size_bytes <= 0 or not engine:
                continue
            all_rows.append(
                {
                    "run_id": run_id,
                    "run_dir": str(run_dir),
                    "source": source,
                    "machine_type": machine_type,
                    "architecture": architecture,
                    "cohort": cohort,
                    "engine_name": engine,
                    "status": status,
                    "patterns_compiled": patterns,
                    "corpus_size_bytes": corpus_size_bytes,
                    "input_label": _bytes_label(corpus_size_bytes),
                    "compilation_ns": float(row.get("compilation_ns", 0) or 0),
                    "scanning_ns": float(row.get("scanning_ns", 0) or 0),
                    "total_ns": float(row.get("total_ns", 0) or 0),
                    "match_count": float(row.get("match_count", 0) or 0),
                    "match_checksum": row.get("match_checksum"),
                    "pattern_suite": str(row.get("pattern_suite") or "unknown"),
                    "corpus_name": str(row.get("corpus_name") or "unknown"),
                    "iteration": _to_int(row.get("iteration"), -1),
                    "error_message": str(
                        row.get("error_message")
                        or row.get("error")
                        or row.get("stderr")
                        or ""
                    ),
                }
            )

        run_inventory.append(
            {
                "run_id": run_id,
                "run_dir": str(run_dir),
                "source": source,
                "machine_type": machine_type,
                "architecture": architecture,
                "cohort": cohort,
                "rows_total": len(rows),
                "rows_ok": ok_count,
                "engines": ",".join(sorted(engines)),
            }
        )

    return all_rows, run_inventory


def _aggregate(rows: list[dict[str, Any]], include_cohort: bool) -> tuple[list[str], dict[tuple[Any, ...], dict[str, dict[str, float]]]]:
    grouped: dict[tuple[Any, ...], dict[str, list[dict[str, Any]]]] = defaultdict(lambda: defaultdict(list))
    engines: set[str] = set()

    for row in rows:
        if row["status"] != "ok":
            continue
        engine = row["engine_name"]
        engines.add(engine)
        if include_cohort:
            wk = (row["cohort"], row["patterns_compiled"], row["corpus_size_bytes"], row["input_label"])
        else:
            wk = (row["patterns_compiled"], row["corpus_size_bytes"], row["input_label"])
        grouped[wk][engine].append(row)

    out: dict[tuple[Any, ...], dict[str, dict[str, float]]] = defaultdict(dict)
    for workload, engine_map in grouped.items():
        for engine, vals in engine_map.items():
            total_ms = [v["total_ns"] / 1_000_000.0 for v in vals if v["total_ns"] > 0]
            scan_ms = [v["scanning_ns"] / 1_000_000.0 for v in vals if v["scanning_ns"] >= 0]
            comp_ms = [v["compilation_ns"] / 1_000_000.0 for v in vals if v["compilation_ns"] >= 0]
            matches = [v["match_count"] for v in vals]
            if not total_ms:
                continue
            out[workload][engine] = {
                "iterations": float(len(total_ms)),
                "mean_total_ms": statistics.fmean(total_ms),
                "median_total_ms": statistics.median(total_ms),
                "std_total_ms": _safe_stdev(total_ms),
                "mean_scan_ms": statistics.fmean(scan_ms) if scan_ms else 0.0,
                "mean_compile_ms": statistics.fmean(comp_ms) if comp_ms else 0.0,
                "mean_matches": statistics.fmean(matches) if matches else 0.0,
            }
    return sorted(engines), out


def _write_run_inventory_csv(output_dir: Path, inventory: list[dict[str, Any]]) -> Path:
    out = output_dir / "all_runs_inventory.csv"
    fields = [
        "run_id", "run_dir", "source", "machine_type", "architecture", "cohort",
        "rows_total", "rows_ok", "engines",
    ]
    with out.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        for row in inventory:
            w.writerow(row)
    return out


def _write_all_runs_workload_csv(output_dir: Path, rows: list[dict[str, Any]]) -> Path:
    out = output_dir / "all_runs_workload_engine.csv"
    grouped: dict[tuple[Any, ...], list[dict[str, Any]]] = defaultdict(list)
    for r in rows:
        if r["status"] != "ok":
            continue
        key = (
            r["run_id"], r["cohort"], r["machine_type"], r["architecture"],
            r["pattern_suite"], r["corpus_name"],
            r["patterns_compiled"], r["input_label"], r["corpus_size_bytes"], r["engine_name"],
        )
        grouped[key].append(r)

    fields = [
        "run_id", "cohort", "machine_type", "architecture",
        "pattern_suite", "corpus_name",
        "patterns", "input_label", "input_bytes", "engine", "samples",
        "mean_total_ms", "median_total_ms", "std_total_ms", "mean_scan_ms", "mean_compile_ms", "mean_matches",
    ]
    with out.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        for key in sorted(grouped.keys()):
            vals = grouped[key]
            total_ms = [v["total_ns"] / 1_000_000.0 for v in vals if v["total_ns"] > 0]
            if not total_ms:
                continue
            scan_ms = [v["scanning_ns"] / 1_000_000.0 for v in vals]
            comp_ms = [v["compilation_ns"] / 1_000_000.0 for v in vals]
            matches = [v["match_count"] for v in vals]
            w.writerow(
                {
                    "run_id": key[0],
                    "cohort": key[1],
                    "machine_type": key[2],
                    "architecture": key[3],
                    "pattern_suite": key[4],
                    "corpus_name": key[5],
                    "patterns": key[6],
                    "input_label": key[7],
                    "input_bytes": key[8],
                    "engine": key[9],
                    "samples": len(total_ms),
                    "mean_total_ms": f"{statistics.fmean(total_ms):.6f}",
                    "median_total_ms": f"{statistics.median(total_ms):.6f}",
                    "std_total_ms": f"{_safe_stdev(total_ms):.6f}",
                    "mean_scan_ms": f"{statistics.fmean(scan_ms):.6f}" if scan_ms else "0.000000",
                    "mean_compile_ms": f"{statistics.fmean(comp_ms):.6f}" if comp_ms else "0.000000",
                    "mean_matches": f"{statistics.fmean(matches):.2f}" if matches else "0.00",
                }
            )
    return out


def _write_failed_jobs_csv(output_dir: Path, rows: list[dict[str, Any]]) -> Path:
    out = output_dir / "all_runs_failed_jobs.csv"
    fields = [
        "run_id",
        "cohort",
        "machine_type",
        "architecture",
        "pattern_suite",
        "corpus_name",
        "patterns",
        "input_label",
        "input_bytes",
        "engine",
        "status",
        "iteration",
        "total_ms",
        "scan_ms",
        "compile_ms",
        "matches",
        "error_message",
        "run_dir",
        "source",
    ]
    with out.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        failed_rows = [
            r for r in rows
            if _is_failure_status(str(r.get("status", "")))
        ]
        for r in sorted(
            failed_rows,
            key=lambda x: (
                str(x.get("run_id", "")),
                int(x.get("patterns_compiled", 0) or 0),
                int(x.get("corpus_size_bytes", 0) or 0),
                str(x.get("engine_name", "")),
                str(x.get("status", "")),
                _to_int(x.get("iteration"), -1),
            ),
        ):
            w.writerow(
                {
                    "run_id": r.get("run_id", ""),
                    "cohort": r.get("cohort", ""),
                    "machine_type": r.get("machine_type", ""),
                    "architecture": r.get("architecture", ""),
                    "pattern_suite": r.get("pattern_suite", ""),
                    "corpus_name": r.get("corpus_name", ""),
                    "patterns": int(r.get("patterns_compiled", 0) or 0),
                    "input_label": r.get("input_label", ""),
                    "input_bytes": int(r.get("corpus_size_bytes", 0) or 0),
                    "engine": r.get("engine_name", ""),
                    "status": r.get("status", ""),
                    "iteration": _to_int(r.get("iteration"), -1),
                    "total_ms": (
                        f"{(float(r.get('total_ns', 0.0) or 0.0) / 1_000_000.0):.6f}"
                        if float(r.get("total_ns", 0.0) or 0.0) > 0
                        else ""
                    ),
                    "scan_ms": (
                        f"{(float(r.get('scanning_ns', 0.0) or 0.0) / 1_000_000.0):.6f}"
                        if float(r.get("scanning_ns", 0.0) or 0.0) > 0
                        else ""
                    ),
                    "compile_ms": (
                        f"{(float(r.get('compilation_ns', 0.0) or 0.0) / 1_000_000.0):.6f}"
                        if float(r.get("compilation_ns", 0.0) or 0.0) > 0
                        else ""
                    ),
                    "matches": int(float(r.get("match_count", 0.0) or 0.0)),
                    "error_message": str(r.get("error_message", "")),
                    "run_dir": r.get("run_dir", ""),
                    "source": r.get("source", ""),
                }
            )
    return out


def _count_map_text(values: list[float]) -> str:
    counts: dict[str, int] = {}
    for v in values:
        key = str(int(v))
        counts[key] = counts.get(key, 0) + 1
    return ", ".join(f"{k}:{counts[k]}" for k in sorted(counts.keys(), key=lambda x: int(x)))


def _checksum_map_text(values: list[str]) -> str:
    counts: dict[str, int] = {}
    for v in values:
        key = str(v)
        counts[key] = counts.get(key, 0) + 1
    return ", ".join(f"{k}:{counts[k]}" for k in sorted(counts.keys()))


def _compute_consistency_audit(rows: list[dict[str, Any]]) -> dict[str, Any]:
    minor_discrepancy_max_absolute = 10
    minor_discrepancy_max_relative = 0.001

    def _classify_delta(a: int, b: int) -> tuple[str, int, float]:
        delta = abs(int(a) - int(b))
        denom = max(abs(int(a)), abs(int(b)), 1)
        rel = float(delta) / float(denom)
        severity = "minor" if (delta <= minor_discrepancy_max_absolute or rel <= minor_discrepancy_max_relative) else "major"
        return severity, delta, rel

    def _classify_span(values: list[int]) -> tuple[str, int, float]:
        if not values:
            return "major", 0, 0.0
        lo = min(values)
        hi = max(values)
        return _classify_delta(lo, hi)

    ok_rows = [r for r in rows if r.get("status") == "ok"]
    same_engine_groups: dict[tuple[Any, ...], list[dict[str, Any]]] = defaultdict(list)
    for row in ok_rows:
        key = (
            row.get("engine_name"),
            row.get("pattern_suite", "unknown"),
            row.get("corpus_name", "unknown"),
            int(row.get("patterns_compiled", 0) or 0),
            int(row.get("corpus_size_bytes", 0) or 0),
        )
        same_engine_groups[key].append(row)

    same_engine_issues: list[dict[str, Any]] = []
    same_engine_checked = 0
    for key, vals in same_engine_groups.items():
        if len(vals) < 2:
            continue
        same_engine_checked += 1
        counts = [float(v.get("match_count", 0) or 0) for v in vals]
        checksums = [str(v.get("match_checksum")) for v in vals if v.get("match_checksum")]

        unique_counts = sorted(set(int(c) for c in counts))
        unique_checksums = sorted(set(checksums))
        if len(unique_counts) <= 1 and len(unique_checksums) <= 1:
            continue

        severity, delta_abs, delta_rel = _classify_span(unique_counts)
        run_ids = sorted({str(v.get("run_id", "")) for v in vals if v.get("run_id")})
        same_engine_issues.append(
            {
                "issue_type": "same_engine_inconsistency",
                "severity": severity,
                "engine_name": str(key[0]),
                "pattern_suite": str(key[1]),
                "corpus_name": str(key[2]),
                "patterns_compiled": int(key[3]),
                "corpus_size_bytes": int(key[4]),
                "input_label": _bytes_label(int(key[4])),
                "run_ids": run_ids,
                "run_count": len(run_ids),
                "samples": len(vals),
                "delta_abs": delta_abs,
                "delta_rel_pct": delta_rel * 100.0,
                "match_count_values": _count_map_text(counts),
                "checksum_values": _checksum_map_text(checksums) if checksums else "",
            }
        )

    strict_reference_engine = "rmatch"
    strict_java_engines = {
        "java-native-naive",
        "java-native-unfair",
        "java-native-optimized",
        "java-native",
    }
    cross_engine_issues: list[dict[str, Any]] = []
    cross_engine_pairs_checked = 0

    by_run_workload: dict[tuple[Any, ...], dict[str, list[dict[str, Any]]]] = defaultdict(lambda: defaultdict(list))
    for row in ok_rows:
        run_key = (
            str(row.get("run_id", "")),
            str(row.get("pattern_suite", "unknown")),
            str(row.get("corpus_name", "unknown")),
            int(row.get("patterns_compiled", 0) or 0),
            int(row.get("corpus_size_bytes", 0) or 0),
        )
        by_run_workload[run_key][str(row.get("engine_name", ""))].append(row)

    for run_key, engine_map in by_run_workload.items():
        if strict_reference_engine not in engine_map:
            continue

        java_present = sorted(
            e for e in engine_map.keys() if (e in strict_java_engines or e.startswith("java-native"))
        )
        if not java_present:
            continue

        ref_rows = engine_map[strict_reference_engine]
        ref_counts = sorted({int(float(v.get("match_count", 0) or 0)) for v in ref_rows})
        ref_checksums = sorted({str(v.get("match_checksum")) for v in ref_rows if v.get("match_checksum")})

        for java_engine in java_present:
            if java_engine == strict_reference_engine:
                continue
            cross_engine_pairs_checked += 1

            j_rows = engine_map[java_engine]
            j_counts = sorted({int(float(v.get("match_count", 0) or 0)) for v in j_rows})
            j_checksums = sorted({str(v.get("match_checksum")) for v in j_rows if v.get("match_checksum")})

            mismatch_reasons: list[str] = []
            mismatch_severity = "minor"
            delta_abs = 0
            delta_rel_pct = 0.0
            if len(ref_counts) != 1:
                mismatch_reasons.append(f"{strict_reference_engine} non-deterministic count set={ref_counts}")
                sev, d_abs, d_rel = _classify_span(ref_counts)
                mismatch_severity = "major" if sev == "major" else mismatch_severity
                delta_abs = max(delta_abs, d_abs)
                delta_rel_pct = max(delta_rel_pct, d_rel * 100.0)
            if len(j_counts) != 1:
                mismatch_reasons.append(f"{java_engine} non-deterministic count set={j_counts}")
                sev, d_abs, d_rel = _classify_span(j_counts)
                mismatch_severity = "major" if sev == "major" else mismatch_severity
                delta_abs = max(delta_abs, d_abs)
                delta_rel_pct = max(delta_rel_pct, d_rel * 100.0)
            if len(ref_counts) == 1 and len(j_counts) == 1 and ref_counts[0] != j_counts[0]:
                mismatch_reasons.append(
                    f"count mismatch {strict_reference_engine}={ref_counts[0]} vs {java_engine}={j_counts[0]}"
                )
                sev, d_abs, d_rel = _classify_delta(ref_counts[0], j_counts[0])
                mismatch_severity = "major" if sev == "major" else mismatch_severity
                delta_abs = max(delta_abs, d_abs)
                delta_rel_pct = max(delta_rel_pct, d_rel * 100.0)

            if len(ref_checksums) == 1 and len(j_checksums) == 1 and ref_checksums[0] != j_checksums[0]:
                mismatch_reasons.append(
                    f"checksum mismatch {strict_reference_engine}!={java_engine}"
                )
                mismatch_severity = "major"

            if not mismatch_reasons:
                continue

            run_id, pattern_suite, corpus_name, patterns_compiled, corpus_size_bytes = run_key
            cross_engine_issues.append(
                {
                    "issue_type": "cross_engine_mismatch",
                    "severity": mismatch_severity,
                    "run_id": run_id,
                    "pattern_suite": pattern_suite,
                    "corpus_name": corpus_name,
                    "patterns_compiled": patterns_compiled,
                    "corpus_size_bytes": corpus_size_bytes,
                    "input_label": _bytes_label(corpus_size_bytes),
                    "reference_engine": strict_reference_engine,
                    "compare_engine": java_engine,
                    "delta_abs": delta_abs,
                    "delta_rel_pct": delta_rel_pct,
                    "reference_counts": ",".join(str(v) for v in ref_counts),
                    "compare_counts": ",".join(str(v) for v in j_counts),
                    "reference_checksums": ",".join(ref_checksums),
                    "compare_checksums": ",".join(j_checksums),
                    "details": "; ".join(mismatch_reasons),
                }
            )

    same_minor = sum(1 for i in same_engine_issues if str(i.get("severity")) == "minor")
    same_major = sum(1 for i in same_engine_issues if str(i.get("severity")) == "major")
    cross_minor = sum(1 for i in cross_engine_issues if str(i.get("severity")) == "minor")
    cross_major = sum(1 for i in cross_engine_issues if str(i.get("severity")) == "major")

    return {
        "strict_reference_engine": strict_reference_engine,
        "strict_java_engines": sorted(strict_java_engines),
        "minor_discrepancy_max_absolute": minor_discrepancy_max_absolute,
        "minor_discrepancy_max_relative": minor_discrepancy_max_relative,
        "same_engine_groups_checked": same_engine_checked,
        "same_engine_issue_count": len(same_engine_issues),
        "same_engine_minor_issue_count": same_minor,
        "same_engine_major_issue_count": same_major,
        "same_engine_issues": same_engine_issues,
        "cross_engine_pairs_checked": cross_engine_pairs_checked,
        "cross_engine_issue_count": len(cross_engine_issues),
        "cross_engine_minor_issue_count": cross_minor,
        "cross_engine_major_issue_count": cross_major,
        "cross_engine_issues": cross_engine_issues,
    }


def _write_consistency_audit_csv(output_dir: Path, audit: dict[str, Any]) -> Path:
    out = output_dir / "correctness_consistency_audit.csv"
    fields = [
        "issue_type",
        "severity",
        "run_id",
        "engine_name",
        "reference_engine",
        "compare_engine",
        "pattern_suite",
        "corpus_name",
        "patterns_compiled",
        "input_label",
        "corpus_size_bytes",
        "delta_abs",
        "delta_rel_pct",
        "details",
        "match_count_values",
        "checksum_values",
        "reference_counts",
        "compare_counts",
        "reference_checksums",
        "compare_checksums",
        "run_ids",
        "samples",
    ]
    with out.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()

        for issue in audit.get("same_engine_issues", []):
            row = {
                "issue_type": issue.get("issue_type", "same_engine_inconsistency"),
                "severity": issue.get("severity", "major"),
                "run_id": "",
                "engine_name": issue.get("engine_name", ""),
                "reference_engine": "",
                "compare_engine": "",
                "pattern_suite": issue.get("pattern_suite", ""),
                "corpus_name": issue.get("corpus_name", ""),
                "patterns_compiled": issue.get("patterns_compiled", ""),
                "input_label": issue.get("input_label", ""),
                "corpus_size_bytes": issue.get("corpus_size_bytes", ""),
                "delta_abs": issue.get("delta_abs", ""),
                "delta_rel_pct": f"{float(issue.get('delta_rel_pct', 0.0)):.6f}",
                "details": "same engine produced multiple match outputs across runs",
                "match_count_values": issue.get("match_count_values", ""),
                "checksum_values": issue.get("checksum_values", ""),
                "reference_counts": "",
                "compare_counts": "",
                "reference_checksums": "",
                "compare_checksums": "",
                "run_ids": ",".join(issue.get("run_ids", [])),
                "samples": issue.get("samples", ""),
            }
            w.writerow(row)

        for issue in audit.get("cross_engine_issues", []):
            row = {
                "issue_type": issue.get("issue_type", "cross_engine_mismatch"),
                "severity": issue.get("severity", "major"),
                "run_id": issue.get("run_id", ""),
                "engine_name": "",
                "reference_engine": issue.get("reference_engine", ""),
                "compare_engine": issue.get("compare_engine", ""),
                "pattern_suite": issue.get("pattern_suite", ""),
                "corpus_name": issue.get("corpus_name", ""),
                "patterns_compiled": issue.get("patterns_compiled", ""),
                "input_label": issue.get("input_label", ""),
                "corpus_size_bytes": issue.get("corpus_size_bytes", ""),
                "delta_abs": issue.get("delta_abs", ""),
                "delta_rel_pct": f"{float(issue.get('delta_rel_pct', 0.0)):.6f}",
                "details": issue.get("details", ""),
                "match_count_values": "",
                "checksum_values": "",
                "reference_counts": issue.get("reference_counts", ""),
                "compare_counts": issue.get("compare_counts", ""),
                "reference_checksums": issue.get("reference_checksums", ""),
                "compare_checksums": issue.get("compare_checksums", ""),
                "run_ids": "",
                "samples": "",
            }
            w.writerow(row)

    return out


def _write_matrix_csv(
    output_dir: Path,
    filename: str,
    engines: list[str],
    aggregated: dict[tuple[Any, ...], dict[str, dict[str, float]]],
    include_cohort: bool,
) -> tuple[Path, dict[str, dict[str, float]]]:
    out = output_dir / filename
    wins = {e: 0 for e in engines}
    ratios = {e: [] for e in engines}

    fields = []
    if include_cohort:
        fields.extend(["cohort", "patterns", "input_label", "input_bytes", "winner_engine", "winner_total_ms"])
    else:
        fields.extend(["patterns", "input_label", "input_bytes", "winner_engine", "winner_total_ms"])
    fields.extend([f"{e}_total_ms" for e in engines])
    fields.extend([f"{e}_x_vs_winner" for e in engines])

    with out.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        for wk in sorted(aggregated.keys()):
            stats = aggregated[wk]
            if not stats:
                continue
            winner = min(stats.keys(), key=lambda e: stats[e]["mean_total_ms"])
            winner_ms = stats[winner]["mean_total_ms"]
            wins[winner] += 1
            row: dict[str, Any] = {}
            if include_cohort:
                row.update(
                    {
                        "cohort": wk[0],
                        "patterns": wk[1],
                        "input_bytes": wk[2],
                        "input_label": wk[3],
                        "winner_engine": winner,
                        "winner_total_ms": f"{winner_ms:.6f}",
                    }
                )
            else:
                row.update(
                    {
                        "patterns": wk[0],
                        "input_bytes": wk[1],
                        "input_label": wk[2],
                        "winner_engine": winner,
                        "winner_total_ms": f"{winner_ms:.6f}",
                    }
                )
            for e in engines:
                if e in stats:
                    row[f"{e}_total_ms"] = f"{stats[e]['mean_total_ms']:.6f}"
                    ratio = stats[e]["mean_total_ms"] / winner_ms if winner_ms > 0 else float("nan")
                    row[f"{e}_x_vs_winner"] = f"{ratio:.6f}" if math.isfinite(ratio) else ""
                    if math.isfinite(ratio):
                        ratios[e].append(ratio)
                else:
                    row[f"{e}_total_ms"] = ""
                    row[f"{e}_x_vs_winner"] = ""
            w.writerow(row)

    summary: dict[str, dict[str, float]] = {}
    for e in engines:
        vals = ratios[e]
        summary[e] = {
            "wins": float(wins[e]),
            "mean_x_vs_winner": statistics.fmean(vals) if vals else float("nan"),
            "geomean_x_vs_winner": _geomean(vals) if vals else float("nan"),
        }
    return out, summary


def _write_html(
    output_dir: Path,
    *,
    results_dir: Path,
    run_inventory: list[dict[str, Any]],
    consistency_audit: dict[str, Any],
    engines: list[str],
    overall_summary: dict[str, dict[str, float]],
    cohort_summary: dict[str, dict[str, float]],
    overall_agg: dict[tuple[Any, ...], dict[str, dict[str, float]]],
    cohort_agg: dict[tuple[Any, ...], dict[str, dict[str, float]]],
    totals: dict[str, int],
    model_artifacts: dict[str, bool] | None = None,
) -> Path:
    out = output_dir / "workload_engine_comparison_all.html"
    generated = dt.datetime.now(dt.timezone.utc).isoformat()

    palette = [
        "#1d4ed8", "#b45309", "#047857", "#7c3aed", "#be123c",
        "#0f766e", "#4338ca", "#a16207", "#334155", "#166534",
    ]
    engine_colors = {engine: palette[i % len(palette)] for i, engine in enumerate(engines)}

    trend_records: list[dict[str, Any]] = []
    for wk in sorted(overall_agg.keys()):
        patterns, input_bytes, input_label = wk
        for engine, stats in overall_agg[wk].items():
            trend_records.append(
                {
                    "cohort": "__overall__",
                    "cohort_label": "overall (mixed hardware)",
                    "patterns": int(patterns),
                    "input_bytes": int(input_bytes),
                    "input_label": str(input_label),
                    "engine": str(engine),
                    "mean_total_ms": float(stats["mean_total_ms"]),
                    "iterations": int(stats.get("iterations", 0)),
                }
            )

    for wk in sorted(cohort_agg.keys()):
        cohort, patterns, input_bytes, input_label = wk
        for engine, stats in cohort_agg[wk].items():
            trend_records.append(
                {
                    "cohort": str(cohort),
                    "cohort_label": str(cohort),
                    "patterns": int(patterns),
                    "input_bytes": int(input_bytes),
                    "input_label": str(input_label),
                    "engine": str(engine),
                    "mean_total_ms": float(stats["mean_total_ms"]),
                    "iterations": int(stats.get("iterations", 0)),
                }
            )

    trend_data_json = _json_for_html(trend_records)
    trend_colors_json = _json_for_html(engine_colors)

    def winner_of(stats: dict[str, dict[str, float]]) -> tuple[str, float]:
        winner = min(stats.keys(), key=lambda e: stats[e]["mean_total_ms"])
        return winner, float(stats[winner]["mean_total_ms"])

    def ratio_text(stats: dict[str, dict[str, float]], winner_ms: float) -> str:
        parts = []
        for e in engines:
            if e not in stats or winner_ms <= 0:
                continue
            ratio = stats[e]["mean_total_ms"] / winner_ms
            parts.append(f"{e}:{ratio:.2f}x")
        return " | ".join(parts)

    lines: list[str] = []
    lines.append("<!doctype html>")
    lines.append("<html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
    lines.append("<title>All-Runs Workload vs Engine</title>")
    lines.append("<style>")
    lines.append("body{font-family:Segoe UI,Tahoma,sans-serif;background:#f6f8fb;color:#1f2937;margin:0} .wrap{max-width:1450px;margin:0 auto;padding:18px}")
    lines.append(".card{background:#fff;border:1px solid #d1d5db;border-radius:10px;padding:14px;margin-bottom:14px} table{width:100%;border-collapse:collapse;font-size:.92rem}")
    lines.append("th,td{border-bottom:1px solid #d1d5db;padding:6px 8px;text-align:left;vertical-align:top} .num{text-align:right;font-variant-numeric:tabular-nums} .mono{font-family:ui-monospace,Menlo,monospace}")
    lines.append(".table-wrap{overflow:auto;max-height:70vh;border:1px solid #d1d5db;border-radius:8px} thead th{position:sticky;top:0;background:#f3f4f6;z-index:1}")
    lines.append(".cell{min-width:145px} .winner{font-weight:700} .small{font-size:.82rem;color:#4b5563} .ratio{font-size:.78rem;color:#374151}")
    lines.append(".status-success{color:#047857} .status-warning{color:#b45309} .status-error{color:#b91c1c}")
    lines.append(".rank-1{background:rgba(16,185,129,0.14)} .rank-2{background:rgba(245,158,11,0.14)} .rank-3{background:rgba(239,68,68,0.14)}")
    lines.append(".sortable-ratio{cursor:pointer;user-select:none} .sortable-ratio:hover{background:#e5e7eb} .sorted-ratio{background:#dbeafe !important}")
    lines.append(".controls{display:flex;flex-wrap:wrap;gap:10px;align-items:flex-end;margin-bottom:10px} .ctrl{display:flex;flex-direction:column;gap:4px;min-width:180px}")
    lines.append(".ctrl select{border:1px solid #9ca3af;border-radius:6px;padding:5px 8px;background:#fff;font-size:.92rem} .hint{font-size:.82rem;color:#4b5563;margin-top:6px}")
    lines.append(".engine-filter{display:flex;flex-wrap:wrap;gap:8px;margin:8px 0 10px 0} .engine-item{display:inline-flex;gap:6px;align-items:center;padding:3px 8px;border:1px solid #d1d5db;border-radius:999px;background:#f9fafb}")
    lines.append(".engine-dot{display:inline-block;width:10px;height:10px;border-radius:50%} .chart-wrap{border:1px solid #d1d5db;border-radius:8px;background:#fff;overflow:auto}")
    lines.append("#trend-svg{display:block;width:100%;height:auto;min-width:980px} .axis-label{font-size:12px;fill:#374151} .tick-label{font-size:11px;fill:#4b5563}")
    lines.append(".grid-line{stroke:#e5e7eb;stroke-width:1} .axis-line{stroke:#374151;stroke-width:1.5} .line-series{fill:none;stroke-width:2.4} .pt{stroke:#fff;stroke-width:1}")
    lines.append(".legend{display:flex;flex-wrap:wrap;gap:10px;margin:8px 0} .legend-item{display:flex;align-items:center;gap:6px;font-size:.85rem;color:#374151}")
    lines.append("#dominance-svg{display:block;width:100%;height:auto;min-width:980px} .dom-cell{stroke:#111827;stroke-width:1} .dom-cell.partial{stroke-dasharray:2 2;opacity:.62} .dom-note{font-size:.82rem;color:#4b5563}")
    lines.append(".dom-boundary{fill:none;stroke:#111827;stroke-width:1.4;stroke-dasharray:6 4;opacity:.78} .dom-boundary-label{font-size:10px;fill:#111827;paint-order:stroke;stroke:#ffffff;stroke-width:2}")
    lines.append(".dom-grid-line{stroke:#e5e7eb;stroke-width:1} .dom-axis-line{stroke:#374151;stroke-width:1.5} .dom-axis-label{font-size:12px;fill:#374151} .dom-tick-label{font-size:11px;fill:#4b5563}")
    lines.append("</style></head><body><div class=\"wrap\">")
    lines.append("<div class=\"card\" id=\"report-header-card\">")
    lines.append("<h1>All-Runs Workload vs Engine</h1>")
    lines.append(f"<p>Source root: <span class=\"mono\">{_escape(results_dir)}</span></p>")
    lines.append(f"<p>Generated (UTC): <span class=\"mono\">{_escape(generated)}</span></p>")
    lines.append(
        f"<p>Runs scanned: <b>{totals['runs']}</b> | Runs with data: <b>{totals['runs_with_data']}</b> | "
        f"Rows (ok): <b>{totals['rows_ok']}</b> | Rows (terminal non-ok): <b>{totals['rows_failed']}</b> | "
        f"Engines: <b>{_escape(', '.join(engines))}</b></p>"
    )
    lines.append(
        "<p><a href=\"all_runs_inventory.csv\">Run inventory CSV</a> | "
        "<a href=\"all_runs_workload_engine.csv\">All runs workload CSV</a> | "
        "<a href=\"all_runs_failed_jobs.csv\">Failed jobs CSV</a> | "
        "<a href=\"overall_workload_engine_matrix.csv\">Overall matrix CSV</a> | "
        "<a href=\"cohort_workload_engine_matrix.csv\">Cohort matrix CSV</a> | "
        "<a href=\"correctness_consistency_audit.csv\">Correctness audit CSV</a></p>"
    )
    lines.append(f"<p class=\"small\">Failure breakdown: <span class=\"mono\">{_escape(totals['rows_failed_breakdown'])}</span></p>")
    lines.append("<p id=\"auto-refresh-status\" class=\"small\">Auto-refresh: initializing...</p>")
    lines.append("</div>")

    tex_ready = bool((model_artifacts or {}).get("tex"))
    pdf_ready = bool((model_artifacts or {}).get("pdf"))
    lines.append("<div class=\"card\" id=\"modeling-paper-card\">")
    lines.append("<h2>Modeling Paper</h2>")
    lines.append("<p>Compact LaTeX note with fitted runtime model and static 2D dominance map.</p>")
    lines.append("<p><a href=\"modeling_dominance_note.tex\">LaTeX source</a> | <a href=\"modeling_dominance_note.pdf\">PDF</a></p>")
    lines.append(f"<p class=\"small\">Availability: tex={'yes' if tex_ready else 'no'} | pdf={'yes' if pdf_ready else 'no'}</p>")
    if not tex_ready:
        lines.append("<p class=\"small\">Generate with: <span class=\"mono\">make report-model-paper OUT=reports/workload_all_live</span></p>")
    lines.append("</div>")

    lines.append("<div class=\"card\" id=\"run-inventory-card\"><h2>Run Inventory</h2><table><thead><tr><th>run_id</th><th>cohort</th><th>machine_type</th><th>arch</th><th class=\"num\">rows_total</th><th class=\"num\">rows_ok</th><th>engines</th></tr></thead><tbody>")
    for r in sorted(run_inventory, key=lambda x: x["run_id"]):
        lines.append(
            f"<tr><td class=\"mono\">{_escape(r['run_id'])}</td><td class=\"mono\">{_escape(r['cohort'])}</td><td>{_escape(r['machine_type'])}</td><td>{_escape(r['architecture'])}</td><td class=\"num\">{r['rows_total']}</td><td class=\"num\">{r['rows_ok']}</td><td class=\"mono\">{_escape(r['engines'])}</td></tr>"
        )
    lines.append("</tbody></table></div>")

    same_engine_issue_count = int(consistency_audit.get("same_engine_issue_count", 0))
    cross_engine_issue_count = int(consistency_audit.get("cross_engine_issue_count", 0))
    same_engine_minor = int(consistency_audit.get("same_engine_minor_issue_count", 0))
    same_engine_major = int(consistency_audit.get("same_engine_major_issue_count", 0))
    cross_engine_minor = int(consistency_audit.get("cross_engine_minor_issue_count", 0))
    cross_engine_major = int(consistency_audit.get("cross_engine_major_issue_count", 0))
    same_engine_checked = int(consistency_audit.get("same_engine_groups_checked", 0))
    cross_engine_checked = int(consistency_audit.get("cross_engine_pairs_checked", 0))
    total_issues = same_engine_issue_count + cross_engine_issue_count
    total_major = same_engine_major + cross_engine_major
    total_minor = same_engine_minor + cross_engine_minor
    issue_class = "status-success" if total_issues == 0 else ("status-warning" if total_major == 0 else "status-error")
    lines.append("<div class=\"card\" id=\"consistency-audit-card\"><h2>Correctness Consistency Audit</h2>")
    lines.append(
        f"<p>Status: <b class=\"{issue_class}\">{'clean' if total_issues == 0 else 'issues found'}</b> | "
        f"same-engine groups checked: <b>{same_engine_checked}</b>, issues: <b>{same_engine_issue_count}</b> "
        f"(major={same_engine_major}, minor={same_engine_minor}) | "
        f"java-vs-rmatch pairs checked: <b>{cross_engine_checked}</b>, issues: <b>{cross_engine_issue_count}</b> "
        f"(major={cross_engine_major}, minor={cross_engine_minor})</p>"
    )
    lines.append(
        "<p class=\"small\">Policy: mismatches are recorded for analysis; runs are not invalidated. "
        f"Minor discrepancy threshold: abs≤{int(consistency_audit.get('minor_discrepancy_max_absolute', 10))} "
        f"or rel≤{float(consistency_audit.get('minor_discrepancy_max_relative', 0.001)):.6f}.</p>"
    )

    issue_rows: list[dict[str, Any]] = []
    for issue in consistency_audit.get("same_engine_issues", []):
        issue_rows.append(
            {
                "issue_type": "same_engine_inconsistency",
                "severity": issue.get("severity", "major"),
                "run_id": ",".join(issue.get("run_ids", [])),
                "engine": issue.get("engine_name", ""),
                "suite": issue.get("pattern_suite", ""),
                "corpus": issue.get("corpus_name", ""),
                "patterns": issue.get("patterns_compiled", ""),
                "input": issue.get("input_label", ""),
                "delta_abs": issue.get("delta_abs", ""),
                "delta_rel_pct": issue.get("delta_rel_pct", ""),
                "details": f"match_count={issue.get('match_count_values', '')}"
                + (f"; checksum={issue.get('checksum_values', '')}" if issue.get("checksum_values") else ""),
            }
        )
    for issue in consistency_audit.get("cross_engine_issues", []):
        issue_rows.append(
            {
                "issue_type": "java_vs_rmatch_mismatch",
                "severity": issue.get("severity", "major"),
                "run_id": issue.get("run_id", ""),
                "engine": f"{issue.get('reference_engine', '')} vs {issue.get('compare_engine', '')}",
                "suite": issue.get("pattern_suite", ""),
                "corpus": issue.get("corpus_name", ""),
                "patterns": issue.get("patterns_compiled", ""),
                "input": issue.get("input_label", ""),
                "delta_abs": issue.get("delta_abs", ""),
                "delta_rel_pct": issue.get("delta_rel_pct", ""),
                "details": issue.get("details", ""),
            }
        )

    if not issue_rows:
        lines.append("<p><b>No consistency issues detected in the scanned rows.</b></p>")
    else:
        lines.append("<div class=\"table-wrap\"><table><thead><tr><th>severity</th><th>type</th><th>run_id(s)</th><th>engine(s)</th><th>suite</th><th>corpus</th><th class=\"num\">patterns</th><th>input</th><th class=\"num\">delta_abs</th><th class=\"num\">delta_rel_%</th><th>details</th></tr></thead><tbody>")
        for item in issue_rows[:200]:
            sev = str(item.get("severity", "major")).lower()
            sev_class = "status-warning" if sev == "minor" else "status-error"
            delta_rel_raw = item.get("delta_rel_pct", "")
            delta_rel_text = ""
            if delta_rel_raw != "":
                try:
                    delta_rel_text = f"{float(delta_rel_raw):.6f}"
                except Exception:
                    delta_rel_text = str(delta_rel_raw)
            lines.append(
                "<tr>"
                f"<td class=\"mono {sev_class}\"><b>{_escape(sev)}</b></td>"
                f"<td class=\"mono\">{_escape(item['issue_type'])}</td>"
                f"<td class=\"mono\">{_escape(item['run_id'])}</td>"
                f"<td class=\"mono\">{_escape(item['engine'])}</td>"
                f"<td class=\"mono\">{_escape(item['suite'])}</td>"
                f"<td class=\"mono\">{_escape(item['corpus'])}</td>"
                f"<td class=\"num\">{_escape(item['patterns'])}</td>"
                f"<td class=\"mono\">{_escape(item['input'])}</td>"
                f"<td class=\"num\">{_escape(item.get('delta_abs', ''))}</td>"
                f"<td class=\"num\">{_escape(delta_rel_text)}</td>"
                f"<td>{_escape(item['details'])}</td>"
                "</tr>"
            )
        lines.append("</tbody></table></div>")
        if len(issue_rows) > 200:
            lines.append(f"<p class=\"small\">Showing 200 of {len(issue_rows)} issues. See correctness_consistency_audit.csv for full list.</p>")
    lines.append("</div>")

    def summary_block(title: str, summary: dict[str, dict[str, float]], block_id: str) -> None:
        lines.append(f"<div class=\"card\" id=\"{_escape(block_id)}\"><h2>{_escape(title)}</h2><table><thead><tr><th>engine</th><th class=\"num\">wins</th><th class=\"num\">mean_x_vs_winner</th><th class=\"num\">geomean_x_vs_winner</th></tr></thead><tbody>")
        for e in engines:
            s = summary.get(e, {})
            mean_x = s.get("mean_x_vs_winner", float("nan"))
            geo_x = s.get("geomean_x_vs_winner", float("nan"))
            lines.append(
                f"<tr><td class=\"mono\" style=\"color:{engine_colors.get(e, '#1f2937')}\">{_escape(e)}</td><td class=\"num\">{int(s.get('wins', 0))}</td><td class=\"num\">{(f'{mean_x:.3f}' if math.isfinite(mean_x) else '-')}</td><td class=\"num\">{(f'{geo_x:.3f}' if math.isfinite(geo_x) else '-')}</td></tr>"
            )
        lines.append("</tbody></table></div>")

    summary_block("Overall (Mixed Hardware, All Runs)", overall_summary, "overall-summary-card")
    summary_block("Apples-to-Apples by Cohort", cohort_summary, "cohort-summary-card")

    lines.append("<div class=\"card\" id=\"interactive-trend-card\"><h2>Interactive Trend Explorer</h2>")
    lines.append("<p>Pick cohort and x-axis dimension: either vary corpus size at fixed pattern count, or vary pattern count at fixed corpus size.</p>")
    lines.append("<div class=\"controls\">")
    lines.append("<label class=\"ctrl\"><span>Cohort</span><select id=\"trend-cohort\"></select></label>")
    lines.append("<label class=\"ctrl\" id=\"trend-x-dim-ctrl\"><span>X Dimension</span><select id=\"trend-x-dim\"><option value=\"corpus\">corpus size</option><option value=\"pattern\">pattern count</option></select></label>")
    lines.append("<label class=\"ctrl\" id=\"trend-pattern-ctrl\"><span>Pattern Count</span><select id=\"trend-pattern\"></select></label>")
    lines.append("<label class=\"ctrl\" id=\"trend-input-ctrl\"><span>Input Size</span><select id=\"trend-input\"></select></label>")
    lines.append("<label class=\"ctrl\"><span>Comparable Rows</span><select id=\"trend-compare\"><option value=\"two_plus\">at least 2 selected engines</option><option value=\"selected\">all selected engines</option><option value=\"all\">all available points</option></select></label>")
    lines.append("<label class=\"ctrl\"><span>Y Axis</span><select id=\"trend-y-scale\"><option value=\"linear\">linear</option><option value=\"log\">log10</option></select></label>")
    lines.append("<label class=\"ctrl\"><span>X Scale</span><select id=\"trend-x-scale\"><option value=\"linear\">linear</option><option value=\"log\">log10</option></select></label>")
    lines.append("</div>")
    lines.append("<div id=\"trend-engines\" class=\"engine-filter\"></div>")
    lines.append("<div id=\"trend-legend\" class=\"legend\"></div>")
    lines.append("<div class=\"chart-wrap\"><svg id=\"trend-svg\" viewBox=\"0 0 1100 480\" preserveAspectRatio=\"xMidYMid meet\" aria-label=\"runtime trend chart\"></svg></div>")
    lines.append("<p id=\"trend-summary\" class=\"hint\"></p>")
    lines.append("<div class=\"table-wrap\"><table id=\"trend-table\"></table></div>")
    lines.append("</div>")

    lines.append("<div class=\"card\" id=\"dominance-map-card\"><h2>2D Dominance Map</h2>")
    lines.append("<p>Each point is one observed workload scenario: x=corpus size, y=pattern count, color=fastest engine for that scenario.</p>")
    lines.append("<div class=\"controls\">")
    lines.append("<label class=\"ctrl\"><span>Cohort</span><select id=\"dom-cohort\"></select></label>")
    lines.append("<label class=\"ctrl\"><span>Comparable Rows</span><select id=\"dom-compare\"><option value=\"selected\">all selected engines</option><option value=\"two_plus\">at least 2 selected engines</option><option value=\"all\">all available points</option></select></label>")
    lines.append("<label class=\"ctrl\"><span>Model Boundaries</span><select id=\"dom-boundary\"><option value=\"on\">show equal-runtime lines</option><option value=\"off\">hide</option></select></label>")
    lines.append("<label class=\"ctrl\"><span>X Scale</span><select id=\"dom-x-scale\"><option value=\"log\">log10</option><option value=\"linear\">linear</option></select></label>")
    lines.append("<label class=\"ctrl\"><span>Y Scale</span><select id=\"dom-y-scale\"><option value=\"log\">log10</option><option value=\"linear\">linear</option></select></label>")
    lines.append("</div>")
    lines.append("<div id=\"dom-engines\" class=\"engine-filter\"></div>")
    lines.append("<div id=\"dom-legend\" class=\"legend\"></div>")
    lines.append("<div class=\"chart-wrap\"><svg id=\"dominance-svg\" viewBox=\"0 0 1100 560\" preserveAspectRatio=\"xMidYMid meet\" aria-label=\"2d dominance map\"></svg></div>")
    lines.append("<p id=\"dom-summary\" class=\"hint\"></p>")
    lines.append("<p class=\"dom-note\">Dashed border means that not all selected engines were present for that point. Dashed isolines show model-predicted equal-runtime boundaries.</p>")
    lines.append("</div>")

    # Scenario winner matrix (overall).
    overall_inputs = sorted({(wk[1], wk[2]) for wk in overall_agg.keys()})
    overall_patterns = sorted({wk[0] for wk in overall_agg.keys()})
    lines.append("<div class=\"card\" id=\"scenario-overall-card\"><h2>Scenario Winner Map (Overall)</h2>")
    lines.append("<p>Each cell is one load scenario (patterns x input length). It shows winner and absolute winner time.</p>")
    lines.append("<div class=\"table-wrap\"><table><thead><tr><th>patterns \\ input</th>")
    for _, label in overall_inputs:
        lines.append(f"<th>{_escape(label)}</th>")
    lines.append("</tr></thead><tbody>")
    for p in overall_patterns:
        lines.append(f"<tr><td class=\"num\"><b>{p}</b></td>")
        for ibytes, ilabel in overall_inputs:
            key = (p, ibytes, ilabel)
            stats = overall_agg.get(key)
            if not stats:
                lines.append("<td class=\"small\">-</td>")
                continue
            winner, wms = winner_of(stats)
            col = engine_colors.get(winner, "#111827")
            lines.append(
                "<td class=\"cell\">"
                f"<div class=\"winner\" style=\"color:{col}\">{_escape(winner)}</div>"
                f"<div class=\"small\">{wms:.2f} ms</div>"
                f"<div class=\"ratio\">{_escape(ratio_text(stats, wms))}</div>"
                "</td>"
            )
        lines.append("</tr>")
    lines.append("</tbody></table></div></div>")

    # Scenario winner matrices per cohort.
    cohorts = sorted({wk[0] for wk in cohort_agg.keys()})
    lines.append("<div id=\"scenario-cohort-container\">")
    for cohort in cohorts:
        cohort_keys = [wk for wk in cohort_agg.keys() if wk[0] == cohort]
        inputs = sorted({(wk[2], wk[3]) for wk in cohort_keys})
        patterns = sorted({wk[1] for wk in cohort_keys})
        lines.append(f"<div class=\"card\"><h2>Scenario Winner Map (Cohort: <span class=\"mono\">{_escape(cohort)}</span>)</h2>")
        lines.append("<div class=\"table-wrap\"><table><thead><tr><th>patterns \\ input</th>")
        for _, label in inputs:
            lines.append(f"<th>{_escape(label)}</th>")
        lines.append("</tr></thead><tbody>")
        for p in patterns:
            lines.append(f"<tr><td class=\"num\"><b>{p}</b></td>")
            for ibytes, ilabel in inputs:
                key = (cohort, p, ibytes, ilabel)
                stats = cohort_agg.get(key)
                if not stats:
                    lines.append("<td class=\"small\">-</td>")
                    continue
                winner, wms = winner_of(stats)
                col = engine_colors.get(winner, "#111827")
                lines.append(
                    "<td class=\"cell\">"
                    f"<div class=\"winner\" style=\"color:{col}\">{_escape(winner)}</div>"
                    f"<div class=\"small\">{wms:.2f} ms</div>"
                    f"<div class=\"ratio\">{_escape(ratio_text(stats, wms))}</div>"
                    "</td>"
                )
            lines.append("</tr>")
        lines.append("</tbody></table></div></div>")
    lines.append("</div>")

    # Full detailed scenario table (not rolled up).
    lines.append("<div class=\"card\" id=\"detailed-scenario-card\"><h2>Detailed Scenario Table (Non-Rolled-Up)</h2>")
    lines.append("<p>This is the explicit per-load comparison requested: one row per cohort + patterns + input length scenario.</p>")
    lines.append("<div class=\"table-wrap\"><table id=\"detailed-scenario-table\"><thead><tr><th>cohort</th><th class=\"num\">patterns</th><th>input</th><th>winner</th><th class=\"num\">winner_ms</th>")
    for e in engines:
        lines.append(f"<th class=\"num\">{_escape(e)}_ms</th>")
    for e in engines:
        lines.append(f"<th class=\"num sortable-ratio\" data-sort-role=\"ratio\" title=\"Sort ascending by this x-multiplier\">{_escape(e)}_x</th>")
    lines.append("</tr></thead><tbody>")
    for wk in sorted(cohort_agg.keys()):
        cohort, patterns, ibytes, ilabel = wk
        stats = cohort_agg[wk]
        winner, wms = winner_of(stats)
        ratio_by_engine: dict[str, float] = {}
        rank_by_engine: dict[str, int] = {}
        if wms > 0:
            for e in engines:
                if e in stats:
                    ratio_by_engine[e] = stats[e]["mean_total_ms"] / wms
            sorted_ratios = sorted(ratio_by_engine.items(), key=lambda item: item[1])
            last_ratio = None
            current_rank = 0
            for idx, (e, ratio) in enumerate(sorted_ratios, start=1):
                if last_ratio is None or abs(ratio - last_ratio) > 1e-12:
                    current_rank = idx
                    last_ratio = ratio
                rank_by_engine[e] = current_rank
        lines.append(
            f"<tr data-sort-patterns=\"{int(patterns)}\" data-sort-input-bytes=\"{int(ibytes)}\">"
        )
        lines.append(f"<td class=\"mono\">{_escape(cohort)}</td>")
        lines.append(f"<td class=\"num\">{patterns}</td>")
        lines.append(f"<td class=\"mono\">{_escape(ilabel)} ({ibytes})</td>")
        lines.append(f"<td class=\"mono winner\" style=\"color:{engine_colors.get(winner, '#111827')}\">{_escape(winner)}</td>")
        lines.append(f"<td class=\"num\">{wms:.3f}</td>")
        for e in engines:
            if e in stats:
                lines.append(f"<td class=\"num\">{stats[e]['mean_total_ms']:.3f}</td>")
            else:
                lines.append("<td class=\"num\">-</td>")
        for e in engines:
            if e in stats and e in ratio_by_engine:
                ratio = ratio_by_engine[e]
                rank = rank_by_engine.get(e, 99)
                rank_class = f" rank-{rank}" if 1 <= rank <= 3 else ""
                lines.append(f"<td class=\"num{rank_class}\">{ratio:.2f}x</td>")
            else:
                lines.append("<td class=\"num\">-</td>")
        lines.append("</tr>")
    lines.append("</tbody></table></div></div>")

    lines.append("<div class=\"card\"><p>Notes: 'Overall' mixes machine cohorts. For apples-to-apples claims, rely on cohort maps/tables.</p></div>")
    lines.append(f"<script type=\"application/json\" id=\"trend-data-json\">{trend_data_json}</script>")
    lines.append(f"<script type=\"application/json\" id=\"trend-colors-json\">{trend_colors_json}</script>")
    lines.append(
        """
<script>
(function () {
  const dataNode = document.getElementById("trend-data-json");
  const colorNode = document.getElementById("trend-colors-json");
  const cohortSel = document.getElementById("trend-cohort");
  const xDimSel = document.getElementById("trend-x-dim");
  const patternSel = document.getElementById("trend-pattern");
  const inputSel = document.getElementById("trend-input");
  const patternCtrl = document.getElementById("trend-pattern-ctrl");
  const inputCtrl = document.getElementById("trend-input-ctrl");
  const compareSel = document.getElementById("trend-compare");
  const yScaleSel = document.getElementById("trend-y-scale");
  const xScaleSel = document.getElementById("trend-x-scale");
  const enginesBox = document.getElementById("trend-engines");
  const legendBox = document.getElementById("trend-legend");
  const svg = document.getElementById("trend-svg");
  const summary = document.getElementById("trend-summary");
  const table = document.getElementById("trend-table");

  if (!dataNode || !cohortSel || !xDimSel || !patternSel || !inputSel || !svg || !summary || !table) {
    return;
  }

  let data = [];
  let colors = {};
  try {
    data = JSON.parse(dataNode.textContent || "[]");
  } catch (_) {
    data = [];
  }
  try {
    colors = JSON.parse((colorNode && colorNode.textContent) || "{}");
  } catch (_) {
    colors = {};
  }

  if (!Array.isArray(data) || data.length === 0) {
    summary.textContent = "No trend data available.";
    return;
  }

  function clear(node) {
    while (node.firstChild) {
      node.removeChild(node.firstChild);
    }
  }

  function toNum(v) {
    const n = Number(v);
    return Number.isFinite(n) ? n : NaN;
  }

  function bytesLabel(bytes) {
    if (bytes < 1024) {
      return String(Math.round(bytes)) + "B";
    }
    const kib = bytes / 1024;
    if (kib < 1024) {
      return Math.round(kib) + "KB";
    }
    const mib = kib / 1024;
    if (mib < 1024) {
      return Math.round(mib) + "MB";
    }
    return (mib / 1024).toFixed(1) + "GB";
  }

  function msLabel(ms) {
    if (!Number.isFinite(ms)) {
      return "-";
    }
    return ms >= 1000 ? (ms / 1000).toFixed(2) + "s" : ms.toFixed(2) + "ms";
  }

  function patternLabel(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) {
      return String(value);
    }
    if (n >= 1_000_000) {
      return (n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1) + "M";
    }
    if (n >= 1_000) {
      return (n / 1_000).toFixed(n % 1_000 === 0 ? 0 : 1) + "K";
    }
    return String(n);
  }

  function updateDimensionControls() {
    const dim = xDimSel.value;
    if (patternCtrl) {
      patternCtrl.style.display = dim === "corpus" ? "flex" : "none";
    }
    if (inputCtrl) {
      inputCtrl.style.display = dim === "pattern" ? "flex" : "none";
    }
  }

  function createSvg(tag, attrs) {
    const el = document.createElementNS("http://www.w3.org/2000/svg", tag);
    Object.keys(attrs).forEach((k) => el.setAttribute(k, String(attrs[k])));
    return el;
  }

  let cohortMap = new Map();
  let engineNames = [];
  const selectedEngines = new Map();

  function computeDomains(preferredCohort, preferredPattern, preferredInput, preferredXDim, preferredSelectedEngines) {
    cohortMap = new Map();
    data.forEach((row) => {
      const id = String(row.cohort || "");
      const label = String(row.cohort_label || id);
      if (!cohortMap.has(id)) {
        cohortMap.set(id, label);
      }
    });

    const cohortIds = Array.from(cohortMap.keys()).sort((a, b) => {
      if (a === "__overall__") return -1;
      if (b === "__overall__") return 1;
      return String(cohortMap.get(a)).localeCompare(String(cohortMap.get(b)));
    });

    clear(cohortSel);
    cohortIds.forEach((id) => {
      const opt = document.createElement("option");
      opt.value = id;
      opt.textContent = cohortMap.get(id) || id;
      cohortSel.appendChild(opt);
    });

    if (cohortIds.length) {
      if (preferredCohort && cohortIds.includes(preferredCohort)) {
        cohortSel.value = preferredCohort;
      } else {
        cohortSel.value = cohortIds[0];
      }
    }

    const priorSelected = new Set(Array.from(selectedEngines.entries()).filter((pair) => pair[1]).map((pair) => pair[0]));
    const preferredSelected = new Set(Array.isArray(preferredSelectedEngines) ? preferredSelectedEngines.map((v) => String(v)) : []);
    engineNames = Array.from(new Set(data.map((r) => String(r.engine || "")).filter(Boolean))).sort();
    selectedEngines.clear();
    engineNames.forEach((e) => {
      let selected = true;
      if (preferredSelected.size > 0) {
        selected = preferredSelected.has(e);
      } else if (priorSelected.size > 0) {
        selected = priorSelected.has(e);
      }
      selectedEngines.set(e, selected);
    });
    if (engineNames.length > 0 && !engineNames.some((e) => selectedEngines.get(e))) {
      selectedEngines.set(engineNames[0], true);
    }

    syncPatternOptions(preferredPattern);
    syncInputOptions(preferredInput);

    if (preferredXDim && Array.from(xDimSel.options).some((o) => o.value === preferredXDim)) {
      xDimSel.value = preferredXDim;
    }
    updateDimensionControls();
  }

  function renderEngineSelectors() {
    clear(enginesBox);
    engineNames.forEach((engine) => {
      const item = document.createElement("label");
      item.className = "engine-item";

      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = Boolean(selectedEngines.get(engine));
      cb.addEventListener("change", () => {
        selectedEngines.set(engine, cb.checked);
        render();
      });

      const dot = document.createElement("span");
      dot.className = "engine-dot";
      dot.style.background = colors[engine] || "#374151";

      const text = document.createElement("span");
      text.className = "mono";
      text.textContent = engine;

      item.appendChild(cb);
      item.appendChild(dot);
      item.appendChild(text);
      enginesBox.appendChild(item);
    });
  }

  function syncPatternOptions(preferredPattern) {
    const current = preferredPattern !== undefined && preferredPattern !== null ? String(preferredPattern) : patternSel.value;
    clear(patternSel);
    const cohort = cohortSel.value;
    const patterns = Array.from(
      new Set(
        data
          .filter((r) => String(r.cohort) === cohort)
          .map((r) => Number(r.patterns))
          .filter((v) => Number.isFinite(v))
      )
    ).sort((a, b) => a - b);
    patterns.forEach((p) => {
      const opt = document.createElement("option");
      opt.value = String(p);
      opt.textContent = String(p);
      patternSel.appendChild(opt);
    });
    if (patterns.length === 0) {
      return;
    }
    if (patterns.some((p) => String(p) === current)) {
      patternSel.value = current;
    } else {
      patternSel.value = String(patterns[0]);
    }
  }

  function syncInputOptions(preferredInput) {
    const current = preferredInput !== undefined && preferredInput !== null ? String(preferredInput) : inputSel.value;
    clear(inputSel);
    const cohort = cohortSel.value;
    const sizes = Array.from(
      new Set(
        data
          .filter((r) => String(r.cohort) === cohort)
          .map((r) => Number(r.input_bytes))
          .filter((v) => Number.isFinite(v) && v > 0)
      )
    ).sort((a, b) => a - b);
    sizes.forEach((bytes) => {
      const opt = document.createElement("option");
      opt.value = String(bytes);
      opt.textContent = bytesLabel(bytes) + " (" + bytes + ")";
      inputSel.appendChild(opt);
    });
    if (sizes.length === 0) {
      return;
    }
    if (sizes.some((v) => String(v) === current)) {
      inputSel.value = current;
    } else {
      inputSel.value = String(sizes[0]);
    }
  }

  function selectTicks(values, maxTicks) {
    if (values.length <= maxTicks) {
      return values.slice();
    }
    const stride = Math.ceil(values.length / maxTicks);
    return values.filter((_, idx) => idx % stride === 0 || idx === values.length - 1);
  }

  function solve3x3(a, b) {
    const m = [
      [a[0][0], a[0][1], a[0][2], b[0]],
      [a[1][0], a[1][1], a[1][2], b[1]],
      [a[2][0], a[2][1], a[2][2], b[2]],
    ];
    const n = 3;

    for (let col = 0; col < n; col += 1) {
      let pivot = col;
      let pivotAbs = Math.abs(m[col][col]);
      for (let r = col + 1; r < n; r += 1) {
        const v = Math.abs(m[r][col]);
        if (v > pivotAbs) {
          pivotAbs = v;
          pivot = r;
        }
      }
      if (!Number.isFinite(pivotAbs) || pivotAbs < 1e-12) {
        return null;
      }
      if (pivot !== col) {
        const tmp = m[col];
        m[col] = m[pivot];
        m[pivot] = tmp;
      }

      const denom = m[col][col];
      for (let c = col; c < n + 1; c += 1) {
        m[col][c] /= denom;
      }

      for (let r = 0; r < n; r += 1) {
        if (r === col) continue;
        const factor = m[r][col];
        if (Math.abs(factor) < 1e-18) continue;
        for (let c = col; c < n + 1; c += 1) {
          m[r][c] -= factor * m[col][c];
        }
      }
    }

    return [m[0][3], m[1][3], m[2][3]];
  }

  function fitModels(cohort, activeEngines) {
    const selected = new Set(activeEngines);
    const byEngine = new Map();

    data.forEach((r) => {
      if (String(r.cohort) !== cohort) {
        return;
      }
      const engine = String(r.engine || "");
      if (!selected.has(engine)) {
        return;
      }
      const patterns = Number(r.patterns);
      const inputBytes = Number(r.input_bytes);
      const ms = Number(r.mean_total_ms);
      if (!Number.isFinite(patterns) || !Number.isFinite(inputBytes) || !Number.isFinite(ms) || patterns <= 0 || inputBytes <= 0 || ms <= 0) {
        return;
      }
      if (!byEngine.has(engine)) {
        byEngine.set(engine, []);
      }
      byEngine.get(engine).push([Math.log(patterns), Math.log(inputBytes), Math.log(ms)]);
    });

    const models = new Map();
    byEngine.forEach((pts, engine) => {
      if (!Array.isArray(pts) || pts.length < 3) {
        return;
      }
      const n = pts.length;
      let sumX1 = 0;
      let sumX2 = 0;
      let sumY = 0;
      let sumX1X1 = 0;
      let sumX2X2 = 0;
      let sumX1X2 = 0;
      let sumX1Y = 0;
      let sumX2Y = 0;

      pts.forEach((row) => {
        const x1 = row[0];
        const x2 = row[1];
        const y = row[2];
        sumX1 += x1;
        sumX2 += x2;
        sumY += y;
        sumX1X1 += x1 * x1;
        sumX2X2 += x2 * x2;
        sumX1X2 += x1 * x2;
        sumX1Y += x1 * y;
        sumX2Y += x2 * y;
      });

      const a = [
        [n, sumX1, sumX2],
        [sumX1, sumX1X1, sumX1X2],
        [sumX2, sumX1X2, sumX2X2],
      ];
      const rhs = [sumY, sumX1Y, sumX2Y];
      const sol = solve3x3(a, rhs);
      if (!sol) {
        return;
      }
      models.set(engine, { b0: sol[0], b1: sol[1], b2: sol[2], n: n });
    });

    return models;
  }

  function buildBoundaryCurves(models, activeEngines, xMin, xMax, yMin, yMax, xLog) {
    const curves = [];
    const eps = 1e-9;
    if (xMin <= 0 || xMax <= 0 || yMin <= 0 || yMax <= 0) {
      return curves;
    }

    const lnXMin = Math.log(xMin);
    const lnXMax = Math.log(xMax);

    for (let i = 0; i < activeEngines.length; i += 1) {
      for (let j = i + 1; j < activeEngines.length; j += 1) {
        const e1 = activeEngines[i];
        const e2 = activeEngines[j];
        const m1 = models.get(e1);
        const m2 = models.get(e2);
        if (!m1 || !m2) {
          continue;
        }

        const d0 = m1.b0 - m2.b0;
        const d1 = m1.b1 - m2.b1;
        const d2 = m1.b2 - m2.b2;
        if (Math.abs(d1) < eps && Math.abs(d2) < eps) {
          continue;
        }

        const pair = e1 + " = " + e2;

        if (Math.abs(d1) < eps) {
          if (Math.abs(d2) < eps) {
            continue;
          }
          const lnX = -d0 / d2;
          const xVal = Math.exp(lnX);
          if (Number.isFinite(xVal) && xVal >= xMin && xVal <= xMax) {
            curves.push({
              pair: pair,
              segments: [[[xVal, yMin], [xVal, yMax]]],
            });
          }
          continue;
        }

        const sampleCount = xLog ? 180 : 140;
        const segments = [];
        let seg = [];
        for (let s = 0; s < sampleCount; s += 1) {
          const t = sampleCount <= 1 ? 0 : (s / (sampleCount - 1));
          const xVal = xLog ? Math.exp(lnXMin + t * (lnXMax - lnXMin)) : (xMin + t * (xMax - xMin));
          const lnY = -(d0 + d2 * Math.log(xVal)) / d1;
          const yVal = Math.exp(lnY);
          const valid = Number.isFinite(xVal) && Number.isFinite(yVal) && xVal >= xMin && xVal <= xMax && yVal >= yMin && yVal <= yMax;
          if (valid) {
            seg.push([xVal, yVal]);
          } else if (seg.length >= 2) {
            segments.push(seg);
            seg = [];
          } else {
            seg = [];
          }
        }
        if (seg.length >= 2) {
          segments.push(seg);
        }
        if (segments.length > 0) {
          curves.push({
            pair: pair,
            segments: segments,
          });
        }
      }
    }

    return curves;
  }

  function solve3x3(a, b) {
    const m = [
      [a[0][0], a[0][1], a[0][2], b[0]],
      [a[1][0], a[1][1], a[1][2], b[1]],
      [a[2][0], a[2][1], a[2][2], b[2]],
    ];
    const n = 3;

    for (let col = 0; col < n; col += 1) {
      let pivot = col;
      let pivotAbs = Math.abs(m[col][col]);
      for (let r = col + 1; r < n; r += 1) {
        const v = Math.abs(m[r][col]);
        if (v > pivotAbs) {
          pivotAbs = v;
          pivot = r;
        }
      }
      if (!Number.isFinite(pivotAbs) || pivotAbs < 1e-12) {
        return null;
      }
      if (pivot !== col) {
        const tmp = m[col];
        m[col] = m[pivot];
        m[pivot] = tmp;
      }

      const denom = m[col][col];
      for (let c = col; c < n + 1; c += 1) {
        m[col][c] /= denom;
      }

      for (let r = 0; r < n; r += 1) {
        if (r === col) continue;
        const factor = m[r][col];
        if (Math.abs(factor) < 1e-18) continue;
        for (let c = col; c < n + 1; c += 1) {
          m[r][c] -= factor * m[col][c];
        }
      }
    }

    return [m[0][3], m[1][3], m[2][3]];
  }

  function fitModels(cohort, activeEngines) {
    const selected = new Set(activeEngines);
    const byEngine = new Map();

    data.forEach((r) => {
      if (String(r.cohort) !== cohort) {
        return;
      }
      const engine = String(r.engine || "");
      if (!selected.has(engine)) {
        return;
      }
      const patterns = Number(r.patterns);
      const inputBytes = Number(r.input_bytes);
      const ms = Number(r.mean_total_ms);
      if (!Number.isFinite(patterns) || !Number.isFinite(inputBytes) || !Number.isFinite(ms) || patterns <= 0 || inputBytes <= 0 || ms <= 0) {
        return;
      }
      if (!byEngine.has(engine)) {
        byEngine.set(engine, []);
      }
      byEngine.get(engine).push([Math.log(patterns), Math.log(inputBytes), Math.log(ms)]);
    });

    const models = new Map();
    byEngine.forEach((pts, engine) => {
      if (!Array.isArray(pts) || pts.length < 3) {
        return;
      }
      const n = pts.length;
      let sumX1 = 0;
      let sumX2 = 0;
      let sumY = 0;
      let sumX1X1 = 0;
      let sumX2X2 = 0;
      let sumX1X2 = 0;
      let sumX1Y = 0;
      let sumX2Y = 0;

      pts.forEach((row) => {
        const x1 = row[0];
        const x2 = row[1];
        const y = row[2];
        sumX1 += x1;
        sumX2 += x2;
        sumY += y;
        sumX1X1 += x1 * x1;
        sumX2X2 += x2 * x2;
        sumX1X2 += x1 * x2;
        sumX1Y += x1 * y;
        sumX2Y += x2 * y;
      });

      const a = [
        [n, sumX1, sumX2],
        [sumX1, sumX1X1, sumX1X2],
        [sumX2, sumX1X2, sumX2X2],
      ];
      const rhs = [sumY, sumX1Y, sumX2Y];
      const sol = solve3x3(a, rhs);
      if (!sol) {
        return;
      }
      models.set(engine, { b0: sol[0], b1: sol[1], b2: sol[2], n: n });
    });

    return models;
  }

  function buildBoundaryCurves(models, activeEngines, xMin, xMax, yMin, yMax, xLog) {
    const curves = [];
    const eps = 1e-9;
    if (xMin <= 0 || xMax <= 0 || yMin <= 0 || yMax <= 0) {
      return curves;
    }

    const lnXMin = Math.log(xMin);
    const lnXMax = Math.log(xMax);

    for (let i = 0; i < activeEngines.length; i += 1) {
      for (let j = i + 1; j < activeEngines.length; j += 1) {
        const e1 = activeEngines[i];
        const e2 = activeEngines[j];
        const m1 = models.get(e1);
        const m2 = models.get(e2);
        if (!m1 || !m2) {
          continue;
        }

        const d0 = m1.b0 - m2.b0;
        const d1 = m1.b1 - m2.b1;
        const d2 = m1.b2 - m2.b2;
        if (Math.abs(d1) < eps && Math.abs(d2) < eps) {
          continue;
        }

        const pair = e1 + " = " + e2;

        if (Math.abs(d1) < eps) {
          if (Math.abs(d2) < eps) {
            continue;
          }
          const lnX = -d0 / d2;
          const xVal = Math.exp(lnX);
          if (Number.isFinite(xVal) && xVal >= xMin && xVal <= xMax) {
            curves.push({
              pair: pair,
              segments: [[[xVal, yMin], [xVal, yMax]]],
            });
          }
          continue;
        }

        const sampleCount = xLog ? 180 : 140;
        const segments = [];
        let seg = [];
        for (let s = 0; s < sampleCount; s += 1) {
          const t = sampleCount <= 1 ? 0 : (s / (sampleCount - 1));
          const xVal = xLog ? Math.exp(lnXMin + t * (lnXMax - lnXMin)) : (xMin + t * (xMax - xMin));
          const lnY = -(d0 + d2 * Math.log(xVal)) / d1;
          const yVal = Math.exp(lnY);
          const valid = Number.isFinite(xVal) && Number.isFinite(yVal) && xVal >= xMin && xVal <= xMax && yVal >= yMin && yVal <= yMax;
          if (valid) {
            seg.push([xVal, yVal]);
          } else if (seg.length >= 2) {
            segments.push(seg);
            seg = [];
          } else {
            seg = [];
          }
        }
        if (seg.length >= 2) {
          segments.push(seg);
        }
        if (segments.length > 0) {
          curves.push({
            pair: pair,
            segments: segments,
          });
        }
      }
    }

    return curves;
  }

  function drawChart(series, scenarios, xMode, yMode, xDim) {
    clear(svg);

    const points = [];
    series.forEach((s) => s.points.forEach((pt) => points.push(pt)));
    if (points.length === 0) {
      const msg = createSvg("text", { x: 30, y: 40, class: "axis-label" });
      msg.textContent = "No points available for this filter selection.";
      svg.appendChild(msg);
      return;
    }

    const width = 1100;
    const height = 480;
    const m = { left: 90, right: 30, top: 20, bottom: 76 };
    const w = width - m.left - m.right;
    const h = height - m.top - m.bottom;

    const xVals = scenarios.map((s) => s.x_value).filter((v) => Number.isFinite(v) && v > 0).sort((a, b) => a - b);
    const yVals = points.map((p) => p.ms).filter((v) => Number.isFinite(v) && v > 0);
    if (!xVals.length || !yVals.length) {
      const msg = createSvg("text", { x: 30, y: 40, class: "axis-label" });
      msg.textContent = "No finite values available.";
      svg.appendChild(msg);
      return;
    }

    let xMin = xVals[0];
    let xMax = xVals[xVals.length - 1];
    let yMin = Math.min.apply(null, yVals);
    let yMax = Math.max.apply(null, yVals);

    const xLog = xMode === "log" && xMin > 0 && xMax > xMin;
    const yLog = yMode === "log" && yMin > 0 && yMax > yMin;

    if (xMax <= xMin) {
      xMax = xMin * 1.05 + 1;
    }
    if (yMax <= yMin) {
      yMax = yMin * 1.05 + 1;
    }

    const lxMin = Math.log10(xMin);
    const lxMax = Math.log10(xMax);
    const lyMin = Math.log10(yMin);
    const lyMax = Math.log10(yMax);

    function xPos(v) {
      const t = xLog ? (Math.log10(v) - lxMin) / (lxMax - lxMin) : (v - xMin) / (xMax - xMin);
      return m.left + t * w;
    }

    function yPos(v) {
      const t = yLog ? (Math.log10(v) - lyMin) / (lyMax - lyMin) : (v - yMin) / (yMax - yMin);
      return m.top + (1 - t) * h;
    }

    const xTickVals = selectTicks(xVals, 10);
    xTickVals.forEach((tv) => {
      const x = xPos(tv);
      svg.appendChild(createSvg("line", { x1: x, y1: m.top, x2: x, y2: m.top + h, class: "grid-line" }));
      const t = createSvg("text", { x: x, y: m.top + h + 18, class: "tick-label", "text-anchor": "middle" });
      t.textContent = xDim === "pattern" ? patternLabel(tv) : bytesLabel(tv);
      svg.appendChild(t);
    });

    let yTickVals = [];
    if (yLog) {
      const start = Math.floor(Math.log10(yMin));
      const end = Math.ceil(Math.log10(yMax));
      for (let p = start; p <= end; p += 1) {
        const value = Math.pow(10, p);
        if (value >= yMin && value <= yMax) {
          yTickVals.push(value);
        }
      }
      if (!yTickVals.length) {
        yTickVals = [yMin, yMax];
      }
    } else {
      const count = 6;
      for (let i = 0; i < count; i += 1) {
        yTickVals.push(yMin + (i * (yMax - yMin)) / (count - 1));
      }
    }

    yTickVals.forEach((tv) => {
      const y = yPos(tv);
      svg.appendChild(createSvg("line", { x1: m.left, y1: y, x2: m.left + w, y2: y, class: "grid-line" }));
      const t = createSvg("text", { x: m.left - 10, y: y + 4, class: "tick-label", "text-anchor": "end" });
      t.textContent = msLabel(tv);
      svg.appendChild(t);
    });

    svg.appendChild(createSvg("line", { x1: m.left, y1: m.top + h, x2: m.left + w, y2: m.top + h, class: "axis-line" }));
    svg.appendChild(createSvg("line", { x1: m.left, y1: m.top, x2: m.left, y2: m.top + h, class: "axis-line" }));

    const xLabel = createSvg("text", { x: m.left + w / 2, y: height - 18, class: "axis-label", "text-anchor": "middle" });
    xLabel.textContent = xDim === "pattern" ? "Pattern count" : "Corpus size";
    svg.appendChild(xLabel);

    const yLabel = createSvg("text", { x: 22, y: m.top + h / 2, class: "axis-label", transform: "rotate(-90 22 " + (m.top + h / 2) + ")", "text-anchor": "middle" });
    yLabel.textContent = "Runtime (mean total ms)";
    svg.appendChild(yLabel);

    series.forEach((s) => {
      if (!s.points.length) {
        return;
      }
      const sorted = s.points.slice().sort((a, b) => a.x_value - b.x_value);
      const d = sorted
        .map((pt, idx) => (idx === 0 ? "M" : "L") + xPos(pt.x_value).toFixed(2) + " " + yPos(pt.ms).toFixed(2))
        .join(" ");
      if (sorted.length > 1) {
        svg.appendChild(createSvg("path", { d: d, class: "line-series", stroke: s.color }));
      }
      sorted.forEach((pt) => {
        const c = createSvg("circle", { cx: xPos(pt.x_value), cy: yPos(pt.ms), r: 4.2, class: "pt", fill: s.color });
        const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
        const xText = xDim === "pattern" ? (patternLabel(pt.x_value) + " patterns") : bytesLabel(pt.x_value);
        title.textContent = s.engine + " | " + xText + " | " + msLabel(pt.ms) + " | n=" + pt.iterations;
        c.appendChild(title);
        svg.appendChild(c);
      });
    });
  }

  function renderLegend(activeEngines) {
    clear(legendBox);
    activeEngines.forEach((engine) => {
      const item = document.createElement("div");
      item.className = "legend-item";
      const dot = document.createElement("span");
      dot.className = "engine-dot";
      dot.style.background = colors[engine] || "#374151";
      const txt = document.createElement("span");
      txt.className = "mono";
      txt.textContent = engine;
      item.appendChild(dot);
      item.appendChild(txt);
      legendBox.appendChild(item);
    });
  }

  function renderTable(scenarios, activeEngines, xDim) {
    clear(table);

    const thead = document.createElement("thead");
    const hr = document.createElement("tr");
    const firstCol = xDim === "pattern" ? "patterns" : "input";
    [firstCol, "winner", "winner_ms"].forEach((h) => {
      const th = document.createElement("th");
      th.textContent = h;
      if (h === "winner_ms") th.className = "num";
      hr.appendChild(th);
    });
    activeEngines.forEach((engine) => {
      const th = document.createElement("th");
      th.className = "num";
      th.textContent = engine + "_ms";
      hr.appendChild(th);
    });
    activeEngines.forEach((engine) => {
      const th = document.createElement("th");
      th.className = "num";
      th.textContent = engine + "_x";
      hr.appendChild(th);
    });
    thead.appendChild(hr);
    table.appendChild(thead);

    const tbody = document.createElement("tbody");
    scenarios.forEach((sc) => {
      const present = activeEngines.filter((e) => sc.byEngine.has(e));
      if (!present.length) return;
      let winner = present[0];
      let winnerMs = toNum(sc.byEngine.get(winner).mean_total_ms);
      present.forEach((e) => {
        const val = toNum(sc.byEngine.get(e).mean_total_ms);
        if (Number.isFinite(val) && val < winnerMs) {
          winner = e;
          winnerMs = val;
        }
      });

      const tr = document.createElement("tr");
      const cInput = document.createElement("td");
      cInput.className = "mono";
      if (xDim === "pattern") {
        cInput.textContent = patternLabel(sc.patterns);
      } else {
        cInput.textContent = (sc.input_label || bytesLabel(sc.input_bytes)) + " (" + sc.input_bytes + ")";
      }
      tr.appendChild(cInput);

      const cWinner = document.createElement("td");
      cWinner.className = "mono winner";
      cWinner.style.color = colors[winner] || "#111827";
      cWinner.textContent = winner;
      tr.appendChild(cWinner);

      const cWinnerMs = document.createElement("td");
      cWinnerMs.className = "num";
      cWinnerMs.textContent = Number.isFinite(winnerMs) ? winnerMs.toFixed(3) : "-";
      tr.appendChild(cWinnerMs);

      activeEngines.forEach((e) => {
        const td = document.createElement("td");
        td.className = "num";
        if (sc.byEngine.has(e)) {
          td.textContent = toNum(sc.byEngine.get(e).mean_total_ms).toFixed(3);
        } else {
          td.textContent = "-";
        }
        tr.appendChild(td);
      });

      activeEngines.forEach((e) => {
        const td = document.createElement("td");
        td.className = "num";
        if (sc.byEngine.has(e) && Number.isFinite(winnerMs) && winnerMs > 0) {
          const ratio = toNum(sc.byEngine.get(e).mean_total_ms) / winnerMs;
          td.textContent = Number.isFinite(ratio) ? ratio.toFixed(2) + "x" : "-";
        } else {
          td.textContent = "-";
        }
        tr.appendChild(td);
      });

      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
  }

  function render() {
    const cohort = cohortSel.value;
    const cohortLabel = cohortMap.get(cohort) || cohort;
    const xDim = xDimSel.value;
    const patterns = Number(patternSel.value);
    const inputBytes = Number(inputSel.value);
    const mode = compareSel.value;
    const yMode = yScaleSel.value;
    const xMode = xScaleSel.value;

    const activeEngines = engineNames.filter((e) => selectedEngines.get(e));
    if (!activeEngines.length) {
      summary.textContent = "Select at least one engine.";
      clear(svg);
      clear(table);
      clear(legendBox);
      return;
    }

    const scoped = data.filter((r) => {
      if (String(r.cohort) !== cohort) return false;
      if (!activeEngines.includes(String(r.engine))) return false;
      if (xDim === "pattern") {
        return Number(r.input_bytes) === inputBytes;
      }
      return Number(r.patterns) === patterns;
    });
    const scenarioMap = new Map();
    scoped.forEach((r) => {
      const keyValue = xDim === "pattern" ? Number(r.patterns) : Number(r.input_bytes);
      const k = String(keyValue);
      if (!scenarioMap.has(k)) {
        const scenario = { byEngine: new Map() };
        if (xDim === "pattern") {
          scenario.x_value = Number(r.patterns);
          scenario.patterns = Number(r.patterns);
          scenario.input_bytes = Number(r.input_bytes);
          scenario.input_label = String(r.input_label || bytesLabel(Number(r.input_bytes)));
        } else {
          scenario.x_value = Number(r.input_bytes);
          scenario.input_bytes = Number(r.input_bytes);
          scenario.input_label = String(r.input_label || bytesLabel(Number(r.input_bytes)));
          scenario.patterns = Number(r.patterns);
        }
        scenarioMap.set(k, scenario);
      }
      scenarioMap.get(k).byEngine.set(String(r.engine), r);
    });

    let scenarios = Array.from(scenarioMap.values()).sort((a, b) => a.x_value - b.x_value);
    scenarios = scenarios.filter((s) => {
      const present = activeEngines.filter((e) => s.byEngine.has(e)).length;
      if (mode === "selected") {
        return activeEngines.every((e) => s.byEngine.has(e));
      }
      if (mode === "two_plus") {
        return present >= Math.min(2, activeEngines.length);
      }
      return present >= 1;
    });

    const series = activeEngines.map((engine) => ({
      engine: engine,
      color: colors[engine] || "#374151",
      points: scenarios
        .filter((s) => s.byEngine.has(engine))
        .map((s) => {
          const row = s.byEngine.get(engine);
          return {
            x_value: Number(s.x_value),
            ms: Number(row.mean_total_ms),
            iterations: Number(row.iterations || 0),
          };
        })
        .filter((pt) => Number.isFinite(pt.x_value) && Number.isFinite(pt.ms) && pt.ms > 0),
    })).filter((s) => s.points.length > 0);

    drawChart(series, scenarios, xMode, yMode, xDim);
    renderLegend(series.map((s) => s.engine));
    renderTable(scenarios, activeEngines, xDim);

    const shownSizes = scenarios.length;
    const shownEngines = series.length;
    const modeLabel = mode === "selected" ? "all selected engines present" : (mode === "two_plus" ? "at least 2 selected engines" : "all available points");
    const xLabel = xDim === "pattern" ? "pattern count" : "corpus size";
    const fixedLabel = xDim === "pattern" ? ("input: " + (inputSel.options[inputSel.selectedIndex] ? inputSel.options[inputSel.selectedIndex].textContent : "-")) : ("patterns: " + patternLabel(patterns));
    summary.textContent =
      "Cohort: " + cohortLabel +
      " | x-axis: " + xLabel +
      " | " + fixedLabel +
      " | points shown: " + shownSizes +
      " | engines shown: " + shownEngines +
      " | filter: " + modeLabel +
      " | scales: x=" + xMode + ", y=" + yMode;
  }

  function getState() {
    return {
      cohort: cohortSel.value,
      xDim: xDimSel.value,
      pattern: patternSel.value,
      inputBytes: inputSel.value,
      compare: compareSel.value,
      yScale: yScaleSel.value,
      xScale: xScaleSel.value,
      selectedEngines: engineNames.filter((e) => selectedEngines.get(e)),
    };
  }

  function setData(newData, newColors) {
    if (!Array.isArray(newData)) {
      return false;
    }
    const prev = getState();
    data = newData;
    if (newColors && typeof newColors === "object") {
      colors = newColors;
    }

    computeDomains(prev.cohort, prev.pattern, prev.inputBytes, prev.xDim, prev.selectedEngines);
    renderEngineSelectors();

    if (prev.compare && Array.from(compareSel.options).some((o) => o.value === prev.compare)) {
      compareSel.value = prev.compare;
    }
    if (prev.yScale && Array.from(yScaleSel.options).some((o) => o.value === prev.yScale)) {
      yScaleSel.value = prev.yScale;
    }
    if (prev.xScale && Array.from(xScaleSel.options).some((o) => o.value === prev.xScale)) {
      xScaleSel.value = prev.xScale;
    }

    render();
    return true;
  }

  cohortSel.addEventListener("change", () => {
    syncPatternOptions();
    syncInputOptions();
    render();
  });
  xDimSel.addEventListener("change", () => {
    updateDimensionControls();
    render();
  });
  patternSel.addEventListener("change", render);
  inputSel.addEventListener("change", render);
  compareSel.addEventListener("change", render);
  yScaleSel.addEventListener("change", render);
  xScaleSel.addEventListener("change", render);

  computeDomains(undefined, undefined, undefined, undefined, undefined);
  renderEngineSelectors();
  render();

  window.__rbTrendReport = {
    getState: getState,
    setData: setData,
    render: render,
  };
})();
</script>
""".strip()
    )
    lines.append(
        """
<script>
(function () {
  const dataNode = document.getElementById("trend-data-json");
  const colorNode = document.getElementById("trend-colors-json");
  const cohortSel = document.getElementById("dom-cohort");
  const compareSel = document.getElementById("dom-compare");
  const boundarySel = document.getElementById("dom-boundary");
  const xScaleSel = document.getElementById("dom-x-scale");
  const yScaleSel = document.getElementById("dom-y-scale");
  const enginesBox = document.getElementById("dom-engines");
  const legendBox = document.getElementById("dom-legend");
  const svg = document.getElementById("dominance-svg");
  const summary = document.getElementById("dom-summary");

  if (!dataNode || !cohortSel || !compareSel || !boundarySel || !xScaleSel || !yScaleSel || !enginesBox || !legendBox || !svg || !summary) {
    return;
  }

  try {

  let data = [];
  let colors = {};
  try {
    data = JSON.parse(dataNode.textContent || "[]");
  } catch (_) {
    data = [];
  }
  try {
    colors = JSON.parse((colorNode && colorNode.textContent) || "{}");
  } catch (_) {
    colors = {};
  }

  if (!Array.isArray(data) || data.length === 0) {
    summary.textContent = "No dominance-map data available.";
    return;
  }

  function clear(node) {
    while (node.firstChild) {
      node.removeChild(node.firstChild);
    }
  }

  function toNum(v) {
    const n = Number(v);
    return Number.isFinite(n) ? n : NaN;
  }

  function bytesLabel(bytes) {
    const mib = bytes / (1024 * 1024);
    if (mib < 1024) {
      return Math.round(mib) + "MB";
    }
    return (mib / 1024).toFixed(1) + "GB";
  }

  function msLabel(ms) {
    if (!Number.isFinite(ms)) {
      return "-";
    }
    return ms >= 1000 ? (ms / 1000).toFixed(2) + "s" : ms.toFixed(2) + "ms";
  }

  function patternLabel(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) {
      return String(value);
    }
    if (n >= 1_000_000) {
      return (n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1) + "M";
    }
    if (n >= 1_000) {
      return (n / 1_000).toFixed(n % 1_000 === 0 ? 0 : 1) + "K";
    }
    return String(n);
  }

  function createSvg(tag, attrs) {
    const el = document.createElementNS("http://www.w3.org/2000/svg", tag);
    Object.keys(attrs).forEach((k) => el.setAttribute(k, String(attrs[k])));
    return el;
  }

  function selectTicks(values, maxTicks) {
    if (values.length <= maxTicks) {
      return values.slice();
    }
    const stride = Math.ceil(values.length / maxTicks);
    return values.filter((_, idx) => idx % stride === 0 || idx === values.length - 1);
  }

  function solve3x3(a, b) {
    const m = [
      [a[0][0], a[0][1], a[0][2], b[0]],
      [a[1][0], a[1][1], a[1][2], b[1]],
      [a[2][0], a[2][1], a[2][2], b[2]],
    ];
    const n = 3;

    for (let col = 0; col < n; col += 1) {
      let pivot = col;
      let pivotAbs = Math.abs(m[col][col]);
      for (let r = col + 1; r < n; r += 1) {
        const v = Math.abs(m[r][col]);
        if (v > pivotAbs) {
          pivotAbs = v;
          pivot = r;
        }
      }
      if (!Number.isFinite(pivotAbs) || pivotAbs < 1e-12) {
        return null;
      }
      if (pivot !== col) {
        const tmp = m[col];
        m[col] = m[pivot];
        m[pivot] = tmp;
      }

      const denom = m[col][col];
      for (let c = col; c < n + 1; c += 1) {
        m[col][c] /= denom;
      }

      for (let r = 0; r < n; r += 1) {
        if (r === col) continue;
        const factor = m[r][col];
        if (Math.abs(factor) < 1e-18) continue;
        for (let c = col; c < n + 1; c += 1) {
          m[r][c] -= factor * m[col][c];
        }
      }
    }

    return [m[0][3], m[1][3], m[2][3]];
  }

  function fitModels(cohort, activeEngines) {
    const selected = new Set(activeEngines);
    const byEngine = new Map();

    data.forEach((r) => {
      if (String(r.cohort) !== cohort) {
        return;
      }
      const engine = String(r.engine || "");
      if (!selected.has(engine)) {
        return;
      }
      const patterns = Number(r.patterns);
      const inputBytes = Number(r.input_bytes);
      const ms = Number(r.mean_total_ms);
      if (!Number.isFinite(patterns) || !Number.isFinite(inputBytes) || !Number.isFinite(ms) || patterns <= 0 || inputBytes <= 0 || ms <= 0) {
        return;
      }
      if (!byEngine.has(engine)) {
        byEngine.set(engine, []);
      }
      byEngine.get(engine).push([Math.log(patterns), Math.log(inputBytes), Math.log(ms)]);
    });

    const models = new Map();
    byEngine.forEach((pts, engine) => {
      if (!Array.isArray(pts) || pts.length < 3) {
        return;
      }
      const n = pts.length;
      let sumX1 = 0;
      let sumX2 = 0;
      let sumY = 0;
      let sumX1X1 = 0;
      let sumX2X2 = 0;
      let sumX1X2 = 0;
      let sumX1Y = 0;
      let sumX2Y = 0;

      pts.forEach((row) => {
        const x1 = row[0];
        const x2 = row[1];
        const y = row[2];
        sumX1 += x1;
        sumX2 += x2;
        sumY += y;
        sumX1X1 += x1 * x1;
        sumX2X2 += x2 * x2;
        sumX1X2 += x1 * x2;
        sumX1Y += x1 * y;
        sumX2Y += x2 * y;
      });

      const a = [
        [n, sumX1, sumX2],
        [sumX1, sumX1X1, sumX1X2],
        [sumX2, sumX1X2, sumX2X2],
      ];
      const rhs = [sumY, sumX1Y, sumX2Y];
      const sol = solve3x3(a, rhs);
      if (!sol) {
        return;
      }
      models.set(engine, { b0: sol[0], b1: sol[1], b2: sol[2], n: n });
    });

    return models;
  }

  function buildBoundaryCurves(models, activeEngines, xMin, xMax, yMin, yMax, xLog) {
    const curves = [];
    const eps = 1e-9;
    if (xMin <= 0 || xMax <= 0 || yMin <= 0 || yMax <= 0) {
      return curves;
    }

    const lnXMin = Math.log(xMin);
    const lnXMax = Math.log(xMax);

    for (let i = 0; i < activeEngines.length; i += 1) {
      for (let j = i + 1; j < activeEngines.length; j += 1) {
        const e1 = activeEngines[i];
        const e2 = activeEngines[j];
        const m1 = models.get(e1);
        const m2 = models.get(e2);
        if (!m1 || !m2) {
          continue;
        }

        const d0 = m1.b0 - m2.b0;
        const d1 = m1.b1 - m2.b1;
        const d2 = m1.b2 - m2.b2;
        if (Math.abs(d1) < eps && Math.abs(d2) < eps) {
          continue;
        }

        const pair = e1 + " = " + e2;

        if (Math.abs(d1) < eps) {
          if (Math.abs(d2) < eps) {
            continue;
          }
          const lnX = -d0 / d2;
          const xVal = Math.exp(lnX);
          if (Number.isFinite(xVal) && xVal >= xMin && xVal <= xMax) {
            curves.push({
              pair: pair,
              segments: [[[xVal, yMin], [xVal, yMax]]],
            });
          }
          continue;
        }

        const sampleCount = xLog ? 180 : 140;
        const segments = [];
        let seg = [];
        for (let s = 0; s < sampleCount; s += 1) {
          const t = sampleCount <= 1 ? 0 : (s / (sampleCount - 1));
          const xVal = xLog ? Math.exp(lnXMin + t * (lnXMax - lnXMin)) : (xMin + t * (xMax - xMin));
          const lnY = -(d0 + d2 * Math.log(xVal)) / d1;
          const yVal = Math.exp(lnY);
          const valid = Number.isFinite(xVal) && Number.isFinite(yVal) && xVal >= xMin && xVal <= xMax && yVal >= yMin && yVal <= yMax;
          if (valid) {
            seg.push([xVal, yVal]);
          } else if (seg.length >= 2) {
            segments.push(seg);
            seg = [];
          } else {
            seg = [];
          }
        }
        if (seg.length >= 2) {
          segments.push(seg);
        }
        if (segments.length > 0) {
          curves.push({
            pair: pair,
            segments: segments,
          });
        }
      }
    }

    return curves;
  }

  let cohortMap = new Map();
  let engineNames = [];
  const selectedEngines = new Map();

  function computeDomains(preferredCohort, preferredSelectedEngines) {
    cohortMap = new Map();
    data.forEach((row) => {
      const id = String(row.cohort || "");
      const label = String(row.cohort_label || id);
      if (!cohortMap.has(id)) {
        cohortMap.set(id, label);
      }
    });

    const cohortIds = Array.from(cohortMap.keys()).sort((a, b) => {
      if (a === "__overall__") return -1;
      if (b === "__overall__") return 1;
      return String(cohortMap.get(a)).localeCompare(String(cohortMap.get(b)));
    });

    clear(cohortSel);
    cohortIds.forEach((id) => {
      const opt = document.createElement("option");
      opt.value = id;
      opt.textContent = cohortMap.get(id) || id;
      cohortSel.appendChild(opt);
    });

    const priorSelected = new Set(Array.from(selectedEngines.entries()).filter((pair) => pair[1]).map((pair) => pair[0]));
    const preferredSelected = new Set(Array.isArray(preferredSelectedEngines) ? preferredSelectedEngines.map((v) => String(v)) : []);
    engineNames = Array.from(new Set(data.map((r) => String(r.engine || "")).filter(Boolean))).sort();
    selectedEngines.clear();
    engineNames.forEach((e) => {
      let selected = true;
      if (preferredSelected.size > 0) {
        selected = preferredSelected.has(e);
      } else if (priorSelected.size > 0) {
        selected = priorSelected.has(e);
      } else if (String(e).includes("unfair")) {
        selected = false;
      }
      selectedEngines.set(e, selected);
    });
    if (engineNames.length > 0 && !engineNames.some((e) => selectedEngines.get(e))) {
      selectedEngines.set(engineNames[0], true);
    }

    function recommendedCohort() {
      const active = engineNames.filter((e) => selectedEngines.get(e));
      const counts = new Map();
      data.forEach((r) => {
        const c = String(r.cohort || "");
        if (!c || c === "__overall__") {
          return;
        }
        const e = String(r.engine || "");
        if (!active.includes(e)) {
          return;
        }
        counts.set(c, (counts.get(c) || 0) + 1);
      });
      let best = "";
      let bestCount = -1;
      Array.from(counts.keys()).sort().forEach((c) => {
        const n = Number(counts.get(c) || 0);
        if (n > bestCount) {
          best = c;
          bestCount = n;
        }
      });
      if (best && cohortIds.includes(best)) {
        return best;
      }
      if (cohortIds.length && cohortIds[0] === "__overall__" && cohortIds.length > 1) {
        return cohortIds[1];
      }
      return cohortIds.length ? cohortIds[0] : "";
    }

    if (cohortIds.length) {
      if (preferredCohort && cohortIds.includes(preferredCohort)) {
        cohortSel.value = preferredCohort;
      } else {
        cohortSel.value = recommendedCohort();
      }
    }
  }

  function renderEngineSelectors() {
    clear(enginesBox);
    engineNames.forEach((engine) => {
      const item = document.createElement("label");
      item.className = "engine-item";

      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = Boolean(selectedEngines.get(engine));
      cb.addEventListener("change", () => {
        selectedEngines.set(engine, cb.checked);
        render();
      });

      const dot = document.createElement("span");
      dot.className = "engine-dot";
      dot.style.background = colors[engine] || "#374151";

      const text = document.createElement("span");
      text.className = "mono";
      text.textContent = engine;

      item.appendChild(cb);
      item.appendChild(dot);
      item.appendChild(text);
      enginesBox.appendChild(item);
    });
  }

  function buildScenarios() {
    const cohort = cohortSel.value;
    const activeEngines = engineNames.filter((e) => selectedEngines.get(e));
    if (!activeEngines.length) {
      return { error: "Select at least one engine.", scenarios: [], activeEngines: activeEngines, cohortLabel: cohortMap.get(cohort) || cohort };
    }

    const scoped = data.filter((r) => String(r.cohort) === cohort && activeEngines.includes(String(r.engine)));
    const scenarioMap = new Map();
    scoped.forEach((r) => {
      const patterns = Number(r.patterns);
      const inputBytes = Number(r.input_bytes);
      const ms = Number(r.mean_total_ms);
      if (!Number.isFinite(patterns) || !Number.isFinite(inputBytes) || !Number.isFinite(ms) || patterns <= 0 || inputBytes <= 0 || ms <= 0) {
        return;
      }
      const key = String(patterns) + "|" + String(inputBytes);
      if (!scenarioMap.has(key)) {
        scenarioMap.set(key, {
          patterns: patterns,
          input_bytes: inputBytes,
          input_label: String(r.input_label || bytesLabel(inputBytes)),
          byEngine: new Map(),
        });
      }
      scenarioMap.get(key).byEngine.set(String(r.engine), r);
    });

    let scenarios = Array.from(scenarioMap.values());
    const mode = compareSel.value;
    scenarios = scenarios.filter((s) => {
      const present = activeEngines.filter((e) => s.byEngine.has(e)).length;
      if (mode === "selected") {
        return activeEngines.every((e) => s.byEngine.has(e));
      }
      if (mode === "two_plus") {
        return present >= Math.min(2, activeEngines.length);
      }
      return present >= 1;
    });

    scenarios.forEach((s) => {
      const presentEngines = activeEngines.filter((e) => s.byEngine.has(e));
      let winner = presentEngines[0];
      let winnerMs = toNum(s.byEngine.get(winner).mean_total_ms);
      presentEngines.forEach((engine) => {
        const value = toNum(s.byEngine.get(engine).mean_total_ms);
        if (Number.isFinite(value) && value < winnerMs) {
          winner = engine;
          winnerMs = value;
        }
      });
      s.present = presentEngines.length;
      s.winner = winner;
      s.winner_ms = winnerMs;
      s.partial = s.present < activeEngines.length;
    });

    scenarios.sort((a, b) => {
      if (a.patterns !== b.patterns) return a.patterns - b.patterns;
      return a.input_bytes - b.input_bytes;
    });

    return {
      scenarios: scenarios,
      activeEngines: activeEngines,
      cohortLabel: cohortMap.get(cohort) || cohort,
      error: null,
    };
  }

  function drawMap(scenarios, activeEngines, modelBoundaries, showBoundaries) {
    clear(svg);

    if (!scenarios.length) {
      const msg = createSvg("text", { x: 30, y: 40, class: "dom-axis-label" });
      msg.textContent = "No points for this filter selection.";
      svg.appendChild(msg);
      return { boundaryCount: 0, modelCount: 0 };
    }

    const width = 1100;
    const height = 560;
    const m = { left: 95, right: 36, top: 24, bottom: 86 };
    const w = width - m.left - m.right;
    const h = height - m.top - m.bottom;

    const xVals = scenarios.map((s) => s.input_bytes).filter((v) => Number.isFinite(v) && v > 0).sort((a, b) => a - b);
    const yVals = scenarios.map((s) => s.patterns).filter((v) => Number.isFinite(v) && v > 0).sort((a, b) => a - b);
    if (!xVals.length || !yVals.length) {
      const msg = createSvg("text", { x: 30, y: 40, class: "dom-axis-label" });
      msg.textContent = "No finite points available.";
      svg.appendChild(msg);
      return { boundaryCount: 0, modelCount: 0 };
    }

    let xMin = xVals[0];
    let xMax = xVals[xVals.length - 1];
    let yMin = yVals[0];
    let yMax = yVals[yVals.length - 1];

    if (xMax <= xMin) {
      xMax = xMin * 1.05 + 1;
    }
    if (yMax <= yMin) {
      yMax = yMin * 1.05 + 1;
    }

    const xLog = xScaleSel.value === "log" && xMin > 0 && xMax > xMin;
    const yLog = yScaleSel.value === "log" && yMin > 0 && yMax > yMin;

    const lxMin = Math.log10(xMin);
    const lxMax = Math.log10(xMax);
    const lyMin = Math.log10(yMin);
    const lyMax = Math.log10(yMax);

    function xPos(v) {
      const t = xLog ? (Math.log10(v) - lxMin) / (lxMax - lxMin) : (v - xMin) / (xMax - xMin);
      return m.left + t * w;
    }

    function yPos(v) {
      const t = yLog ? (Math.log10(v) - lyMin) / (lyMax - lyMin) : (v - yMin) / (yMax - yMin);
      return m.top + (1 - t) * h;
    }

    const xTicks = selectTicks(Array.from(new Set(xVals)), 11);
    xTicks.forEach((tv) => {
      const x = xPos(tv);
      svg.appendChild(createSvg("line", { x1: x, y1: m.top, x2: x, y2: m.top + h, class: "dom-grid-line" }));
      const t = createSvg("text", { x: x, y: m.top + h + 19, class: "dom-tick-label", "text-anchor": "middle" });
      t.textContent = bytesLabel(tv);
      svg.appendChild(t);
    });

    const yTicks = selectTicks(Array.from(new Set(yVals)).sort((a, b) => a - b), 9);
    yTicks.forEach((tv) => {
      const y = yPos(tv);
      svg.appendChild(createSvg("line", { x1: m.left, y1: y, x2: m.left + w, y2: y, class: "dom-grid-line" }));
      const t = createSvg("text", { x: m.left - 10, y: y + 4, class: "dom-tick-label", "text-anchor": "end" });
      t.textContent = patternLabel(tv);
      svg.appendChild(t);
    });

    svg.appendChild(createSvg("line", { x1: m.left, y1: m.top + h, x2: m.left + w, y2: m.top + h, class: "dom-axis-line" }));
    svg.appendChild(createSvg("line", { x1: m.left, y1: m.top, x2: m.left, y2: m.top + h, class: "dom-axis-line" }));

    const xLabel = createSvg("text", { x: m.left + w / 2, y: height - 20, class: "dom-axis-label", "text-anchor": "middle" });
    xLabel.textContent = "Corpus size";
    svg.appendChild(xLabel);

    const yLabel = createSvg("text", { x: 24, y: m.top + h / 2, class: "dom-axis-label", transform: "rotate(-90 24 " + (m.top + h / 2) + ")", "text-anchor": "middle" });
    yLabel.textContent = "Pattern count";
    svg.appendChild(yLabel);

    let drawnBoundaries = 0;
    if (showBoundaries && Array.isArray(modelBoundaries) && modelBoundaries.length > 0) {
      modelBoundaries.forEach((curve) => {
        if (!curve || !Array.isArray(curve.segments)) {
          return;
        }
        curve.segments.forEach((segment) => {
          if (!Array.isArray(segment) || segment.length < 2) {
            return;
          }
          const d = segment
            .map((pt, idx) => (idx === 0 ? "M" : "L") + xPos(pt[0]).toFixed(2) + " " + yPos(pt[1]).toFixed(2))
            .join(" ");
          if (!d) {
            return;
          }
          const path = createSvg("path", { d: d, class: "dom-boundary" });
          const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
          title.textContent = "Model boundary: " + String(curve.pair || "");
          path.appendChild(title);
          svg.appendChild(path);
          drawnBoundaries += 1;
        });
      });
    }

    scenarios.forEach((s) => {
      const x = xPos(s.input_bytes);
      const y = yPos(s.patterns);
      const baseColor = colors[s.winner] || "#374151";
      const cell = createSvg("rect", {
        x: x - 7,
        y: y - 7,
        width: 14,
        height: 14,
        rx: 2,
        ry: 2,
        fill: baseColor,
        class: "dom-cell" + (s.partial ? " partial" : ""),
      });

      const ratioParts = activeEngines
        .filter((e) => s.byEngine.has(e))
        .map((e) => {
          const row = s.byEngine.get(e);
          const ratio = toNum(row.mean_total_ms) / s.winner_ms;
          const ms = toNum(row.mean_total_ms);
          const msTxt = Number.isFinite(ms) ? msLabel(ms) : "-";
          return e + ":" + msTxt + " (" + (Number.isFinite(ratio) ? ratio.toFixed(2) + "x" : "-") + ")";
        });
      const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
      title.textContent =
        "cohort=" + (cohortMap.get(cohortSel.value) || cohortSel.value) +
        " | " +
        "patterns=" + patternLabel(s.patterns) +
        " | input=" + (s.input_label || bytesLabel(s.input_bytes)) +
        " | winner=" + s.winner +
        " | winner_ms=" + msLabel(s.winner_ms) +
        " | " + ratioParts.join(" | ");
      cell.appendChild(title);
      svg.appendChild(cell);
    });

    return {
      boundaryCount: drawnBoundaries,
      modelCount: Array.isArray(modelBoundaries) ? modelBoundaries.length : 0,
    };
  }

  function renderLegend(activeEngines, scenarios, modelBoundaries, showBoundaries) {
    clear(legendBox);
    const wins = new Map();
    activeEngines.forEach((e) => wins.set(e, 0));
    scenarios.forEach((s) => {
      if (wins.has(s.winner)) {
        wins.set(s.winner, wins.get(s.winner) + 1);
      }
    });

    activeEngines.forEach((engine) => {
      const item = document.createElement("div");
      item.className = "legend-item";
      const dot = document.createElement("span");
      dot.className = "engine-dot";
      dot.style.background = colors[engine] || "#374151";
      const txt = document.createElement("span");
      txt.className = "mono";
      txt.textContent = engine + " (" + String(wins.get(engine) || 0) + ")";
      item.appendChild(dot);
      item.appendChild(txt);
      legendBox.appendChild(item);
    });

    if (showBoundaries && Array.isArray(modelBoundaries) && modelBoundaries.length > 0) {
      modelBoundaries.forEach((curve) => {
        const item = document.createElement("div");
        item.className = "legend-item";
        const line = document.createElement("span");
        line.style.display = "inline-block";
        line.style.width = "18px";
        line.style.borderTop = "2px dashed #111827";
        const txt = document.createElement("span");
        txt.className = "mono";
        txt.textContent = String(curve.pair || "");
        item.appendChild(line);
        item.appendChild(txt);
        legendBox.appendChild(item);
      });
    }
  }

  function render() {
    const result = buildScenarios();
    if (result.error) {
      summary.textContent = result.error;
      clear(svg);
      clear(legendBox);
      return;
    }

    const showBoundaries = boundarySel.value !== "off";
    const models = fitModels(cohortSel.value, result.activeEngines);
    const fittedEngines = result.activeEngines.filter((e) => models.has(e));

    const xVals = result.scenarios.map((s) => Number(s.input_bytes)).filter((v) => Number.isFinite(v) && v > 0);
    const yVals = result.scenarios.map((s) => Number(s.patterns)).filter((v) => Number.isFinite(v) && v > 0);
    let xMin = xVals.length ? Math.min.apply(null, xVals) : NaN;
    let xMax = xVals.length ? Math.max.apply(null, xVals) : NaN;
    let yMin = yVals.length ? Math.min.apply(null, yVals) : NaN;
    let yMax = yVals.length ? Math.max.apply(null, yVals) : NaN;
    if (Number.isFinite(xMin) && Number.isFinite(xMax) && xMax <= xMin) {
      xMax = xMin * 1.05 + 1;
    }
    if (Number.isFinite(yMin) && Number.isFinite(yMax) && yMax <= yMin) {
      yMax = yMin * 1.05 + 1;
    }

    let modelBoundaries = [];
    if (Number.isFinite(xMin) && Number.isFinite(xMax) && Number.isFinite(yMin) && Number.isFinite(yMax)) {
      modelBoundaries = buildBoundaryCurves(
        models,
        fittedEngines,
        xMin,
        xMax,
        yMin,
        yMax,
        xScaleSel.value === "log"
      );
    }

    const drawStats = drawMap(result.scenarios, result.activeEngines, modelBoundaries, showBoundaries);
    renderLegend(result.activeEngines, result.scenarios, modelBoundaries, showBoundaries);

    const partialCount = result.scenarios.filter((s) => s.partial).length;
    const mode = compareSel.value;
    const modeLabel = mode === "selected" ? "all selected engines present" : (mode === "two_plus" ? "at least 2 selected engines" : "all available points");
    const boundaryText = showBoundaries
      ? (" | model boundaries: " + drawStats.boundaryCount + " (pairs: " + drawStats.modelCount + ")")
      : " | model boundaries: hidden";
    summary.textContent =
      "Cohort: " + result.cohortLabel +
      " | points shown: " + result.scenarios.length +
      " | engines selected: " + result.activeEngines.length +
      " | model-fitted engines: " + fittedEngines.length +
      " | filter: " + modeLabel +
      " | partial points: " + partialCount +
      " | scales: x=" + xScaleSel.value + ", y=" + yScaleSel.value +
      boundaryText;
  }

  function getState() {
    return {
      cohort: cohortSel.value,
      compare: compareSel.value,
      boundaries: boundarySel.value,
      xScale: xScaleSel.value,
      yScale: yScaleSel.value,
      selectedEngines: engineNames.filter((e) => selectedEngines.get(e)),
    };
  }

  function setData(newData, newColors) {
    if (!Array.isArray(newData)) {
      return false;
    }
    const prev = getState();
    data = newData;
    if (newColors && typeof newColors === "object") {
      colors = newColors;
    }

    computeDomains(prev.cohort, prev.selectedEngines);
    renderEngineSelectors();

    if (prev.compare && Array.from(compareSel.options).some((o) => o.value === prev.compare)) {
      compareSel.value = prev.compare;
    }
    if (prev.boundaries && Array.from(boundarySel.options).some((o) => o.value === prev.boundaries)) {
      boundarySel.value = prev.boundaries;
    }
    if (prev.xScale && Array.from(xScaleSel.options).some((o) => o.value === prev.xScale)) {
      xScaleSel.value = prev.xScale;
    }
    if (prev.yScale && Array.from(yScaleSel.options).some((o) => o.value === prev.yScale)) {
      yScaleSel.value = prev.yScale;
    }

    render();
    return true;
  }

  cohortSel.addEventListener("change", render);
  compareSel.addEventListener("change", render);
  boundarySel.addEventListener("change", render);
  xScaleSel.addEventListener("change", render);
  yScaleSel.addEventListener("change", render);

  computeDomains(undefined, undefined);
  renderEngineSelectors();
  render();

  window.__rbDominanceMap = {
    getState: getState,
    setData: setData,
    render: render,
  };
  } catch (error) {
    const detail = String(error && error.message ? error.message : error);
    summary.textContent = "Dominance map error: " + detail;
    if (typeof console !== "undefined" && console && typeof console.error === "function") {
      console.error("Dominance map error", error);
    }
  }
})();
</script>
""".strip()
    )
    lines.append(
        """
<script>
(function () {
  const state = { colIndex: null };

  function toRatio(value) {
    const text = String(value || "").trim();
    if (!text || text === "-") {
      return Number.POSITIVE_INFINITY;
    }
    const num = Number(text.replace(/x$/i, ""));
    return Number.isFinite(num) ? num : Number.POSITIVE_INFINITY;
  }

  function toPatterns(row) {
    if (!row) {
      return Number.POSITIVE_INFINITY;
    }
    const raw = row.dataset ? row.dataset.sortPatterns : "";
    const n = Number(raw);
    return Number.isFinite(n) ? n : Number.POSITIVE_INFINITY;
  }

  function toInputBytes(row) {
    if (!row) {
      return Number.POSITIVE_INFINITY;
    }
    const raw = row.dataset ? row.dataset.sortInputBytes : "";
    const n = Number(raw);
    return Number.isFinite(n) ? n : Number.POSITIVE_INFINITY;
  }

  function markSortedHeader(table, colIndex) {
    const headers = Array.from(table.querySelectorAll("thead th"));
    headers.forEach((h) => h.classList.remove("sorted-ratio"));
    const target = headers[colIndex];
    if (target && target.classList.contains("sortable-ratio")) {
      target.classList.add("sorted-ratio");
    }
  }

  function sortByColumn(colIndex) {
    const table = document.getElementById("detailed-scenario-table");
    if (!table) {
      return false;
    }
    const tbody = table.tBodies && table.tBodies[0];
    if (!tbody) {
      return false;
    }

    const rows = Array.from(tbody.rows);
    rows.forEach((row, idx) => {
      if (!row.dataset.sortBaseIndex) {
        row.dataset.sortBaseIndex = String(idx);
      }
    });

    rows.sort((a, b) => {
      const aRatio = toRatio(a.cells[colIndex] ? a.cells[colIndex].textContent : "");
      const bRatio = toRatio(b.cells[colIndex] ? b.cells[colIndex].textContent : "");
      if (aRatio !== bRatio) {
        return aRatio - bRatio;
      }

      const aPatterns = toPatterns(a);
      const bPatterns = toPatterns(b);
      if (aPatterns !== bPatterns) {
        return aPatterns - bPatterns;
      }

      const aInput = toInputBytes(a);
      const bInput = toInputBytes(b);
      if (aInput !== bInput) {
        return aInput - bInput;
      }

      const ai = Number(a.dataset.sortBaseIndex || 0);
      const bi = Number(b.dataset.sortBaseIndex || 0);
      return ai - bi;
    });

    rows.forEach((row) => tbody.appendChild(row));
    markSortedHeader(table, colIndex);
    state.colIndex = colIndex;
    return true;
  }

  function handleClick(event) {
    const th = event.target.closest("#detailed-scenario-table thead th.sortable-ratio");
    if (!th) {
      return;
    }
    const headers = Array.from(th.parentElement.children);
    const colIndex = headers.indexOf(th);
    if (colIndex < 0) {
      return;
    }
    sortByColumn(colIndex);
  }

  function reapply() {
    if (state.colIndex === null) {
      return;
    }
    sortByColumn(state.colIndex);
  }

  document.addEventListener("click", handleClick);

  window.__rbDetailedSort = {
    sortByColumn: sortByColumn,
    reapply: reapply,
    getColIndex: function () { return state.colIndex; },
  };
})();
</script>
""".strip()
    )
    lines.append(
        """
<script>
(function () {
  const INTERVAL_MS = 10000;
  let inFlight = false;

  function setStatus(message, isError) {
    const el = document.getElementById("auto-refresh-status");
    if (!el) return;
    el.textContent = "Auto-refresh: " + message;
    el.style.color = isError ? "#b91c1c" : "#4b5563";
  }

  function replaceById(id, nextDoc) {
    const current = document.getElementById(id);
    const incoming = nextDoc.getElementById(id);
    if (!current || !incoming) {
      return false;
    }
    if (current.isEqualNode(incoming)) {
      return false;
    }
    current.replaceWith(document.importNode(incoming, true));
    return true;
  }

  function parseJsonNode(node, fallback) {
    if (!node) return fallback;
    try {
      return JSON.parse(node.textContent || "");
    } catch (_) {
      return fallback;
    }
  }

  async function refreshOnce() {
    if (inFlight) return;
    inFlight = true;
    try {
      const base = window.location.href.split("#")[0];
      const sep = base.includes("?") ? "&" : "?";
      const url = base + sep + "__refresh_ts=" + Date.now();
      const response = await fetch(url, { cache: "no-store", credentials: "same-origin" });
      if (!response.ok) {
        throw new Error("HTTP " + response.status);
      }

      const html = await response.text();
      const parsed = new DOMParser().parseFromString(html, "text/html");

      let changed = false;
      [
        "report-header-card",
        "modeling-paper-card",
        "run-inventory-card",
        "consistency-audit-card",
        "overall-summary-card",
        "cohort-summary-card",
        "scenario-overall-card",
        "scenario-cohort-container",
        "detailed-scenario-card",
      ].forEach((id) => {
        if (replaceById(id, parsed)) {
          changed = true;
        }
      });

      const currentDataNode = document.getElementById("trend-data-json");
      const incomingDataNode = parsed.getElementById("trend-data-json");
      const currentColorsNode = document.getElementById("trend-colors-json");
      const incomingColorsNode = parsed.getElementById("trend-colors-json");
      let trendChanged = false;

      if (currentDataNode && incomingDataNode && currentDataNode.textContent !== incomingDataNode.textContent) {
        currentDataNode.textContent = incomingDataNode.textContent || "[]";
        trendChanged = true;
        changed = true;
      }
      if (currentColorsNode && incomingColorsNode && currentColorsNode.textContent !== incomingColorsNode.textContent) {
        currentColorsNode.textContent = incomingColorsNode.textContent || "{}";
        trendChanged = true;
        changed = true;
      }

      if (trendChanged) {
        const newData = parseJsonNode(document.getElementById("trend-data-json"), []);
        const newColors = parseJsonNode(document.getElementById("trend-colors-json"), {});
        if (window.__rbTrendReport && typeof window.__rbTrendReport.setData === "function") {
          window.__rbTrendReport.setData(newData, newColors);
        }
        if (window.__rbDominanceMap && typeof window.__rbDominanceMap.setData === "function") {
          window.__rbDominanceMap.setData(newData, newColors);
        }
      }
      if (window.__rbDetailedSort && typeof window.__rbDetailedSort.reapply === "function") {
        window.__rbDetailedSort.reapply();
      }

      const stamp = new Date().toLocaleTimeString();
      setStatus("active (10s) | " + (changed ? "updated" : "no changes") + " | " + stamp, false);
    } catch (error) {
      const proto = window.location.protocol;
      const hint = proto === "file:"
        ? "smooth updates need HTTP serving (file:// blocks fetch in many browsers)"
        : String(error && error.message ? error.message : error);
      setStatus("paused | " + hint, true);
    } finally {
      inFlight = false;
    }
  }

  setStatus("active (10s) | waiting for first poll...", false);
  window.setInterval(refreshOnce, INTERVAL_MS);
  window.setTimeout(refreshOnce, INTERVAL_MS);
})();
</script>
""".strip()
    )
    lines.append("</div></body></html>")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return out


def generate(results_dir: Path, output_dir: Path) -> dict[str, str]:
    all_rows, run_inventory = _build_rows(results_dir)
    if not run_inventory:
        raise RuntimeError(f"No run artifacts found under {results_dir}")

    output_dir.mkdir(parents=True, exist_ok=True)

    inventory_csv = _write_run_inventory_csv(output_dir, run_inventory)
    all_runs_csv = _write_all_runs_workload_csv(output_dir, all_rows)
    failed_jobs_csv = _write_failed_jobs_csv(output_dir, all_rows)
    consistency_audit = _compute_consistency_audit(all_rows)
    consistency_audit_csv = _write_consistency_audit_csv(output_dir, consistency_audit)

    overall_engines, overall_agg = _aggregate(all_rows, include_cohort=False)
    cohort_engines, cohort_agg = _aggregate(all_rows, include_cohort=True)
    engines = sorted(set(overall_engines).union(cohort_engines))
    if not engines:
        raise RuntimeError("No successful rows found to compare.")

    overall_matrix, overall_summary = _write_matrix_csv(
        output_dir,
        "overall_workload_engine_matrix.csv",
        engines,
        overall_agg,
        include_cohort=False,
    )
    cohort_matrix, cohort_summary = _write_matrix_csv(
        output_dir,
        "cohort_workload_engine_matrix.csv",
        engines,
        cohort_agg,
        include_cohort=True,
    )

    failed_counts: dict[str, int] = defaultdict(int)
    for row in all_rows:
        status = str(row.get("status", "")).lower()
        if _is_failure_status(status):
            failed_counts[status] += 1
    failed_breakdown = ", ".join(
        f"{status}:{count}" for status, count in sorted(failed_counts.items())
    ) if failed_counts else "none"

    totals = {
        "runs": len(_find_run_dirs(results_dir)),
        "runs_with_data": len(run_inventory),
        "rows_ok": sum(1 for r in all_rows if r["status"] == "ok"),
        "rows_failed": sum(1 for r in all_rows if _is_failure_status(str(r.get("status", "")))),
        "rows_failed_breakdown": failed_breakdown,
    }
    model_artifacts = {
        "tex": (output_dir / "modeling_dominance_note.tex").is_file(),
        "pdf": (output_dir / "modeling_dominance_note.pdf").is_file(),
    }
    html_file = _write_html(
        output_dir,
        results_dir=results_dir,
        run_inventory=run_inventory,
        consistency_audit=consistency_audit,
        engines=engines,
        overall_summary=overall_summary,
        cohort_summary=cohort_summary,
        overall_agg=overall_agg,
        cohort_agg=cohort_agg,
        totals=totals,
        model_artifacts=model_artifacts,
    )

    return {
        "html": str(html_file),
        "inventory_csv": str(inventory_csv),
        "all_runs_csv": str(all_runs_csv),
        "failed_jobs_csv": str(failed_jobs_csv),
        "consistency_audit_csv": str(consistency_audit_csv),
        "overall_matrix": str(overall_matrix),
        "cohort_matrix": str(cohort_matrix),
        "runs_with_data": str(len(run_inventory)),
        "engines": ",".join(engines),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate all-runs workload-vs-engine comparison report.")
    parser.add_argument("--results-dir", required=True, help="Root results directory containing multiple runs")
    parser.add_argument("--output", required=True, help="Output directory for report files")
    args = parser.parse_args()

    result = generate(Path(args.results_dir), Path(args.output))
    print(f"Generated all-runs workload report.")
    print(f"Runs with data: {result['runs_with_data']}")
    print(f"Engines: {result['engines']}")
    print(f"HTML: {result['html']}")
    print(f"Run inventory CSV: {result['inventory_csv']}")
    print(f"All runs workload CSV: {result['all_runs_csv']}")
    print(f"Failed jobs CSV: {result['failed_jobs_csv']}")
    print(f"Correctness audit CSV: {result['consistency_audit_csv']}")
    print(f"Overall matrix CSV: {result['overall_matrix']}")
    print(f"Cohort matrix CSV: {result['cohort_matrix']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
