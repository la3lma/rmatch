#!/usr/bin/env python3
"""Generate workload-level engine comparison reports from benchmark results."""

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
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class WorkloadKey:
    patterns: int
    input_bytes: int
    input_label: str


def _escape(text: Any) -> str:
    return html.escape(str(text), quote=True)


def _bytes_label(num_bytes: int) -> str:
    mib = num_bytes / (1024 * 1024)
    if mib < 1024:
        rounded = int(round(mib))
        return f"{rounded}MB"
    gib = mib / 1024
    return f"{gib:.1f}GB"


def _load_corpus_label_map(run_dir: Path) -> dict[int, str]:
    data_dir = run_dir / "data"
    if not data_dir.exists():
        return {}
    mapping: dict[int, str] = {}
    for corpus_path in sorted(data_dir.glob("corpus_*.txt")):
        size = corpus_path.stat().st_size
        label = corpus_path.stem.replace("corpus_", "")
        mapping[size] = label
    return mapping


def _safe_stdev(values: list[float]) -> float:
    if len(values) < 2:
        return 0.0
    return statistics.stdev(values)


def _geomean(values: list[float]) -> float:
    positive = [v for v in values if v > 0]
    if not positive:
        return float("nan")
    return math.exp(sum(math.log(v) for v in positive) / len(positive))


def _ratio_class(value: float) -> str:
    if not math.isfinite(value):
        return "ratio-na"
    if value <= 1.05:
        return "ratio-best"
    if value <= 2.0:
        return "ratio-good"
    if value <= 5.0:
        return "ratio-mid"
    return "ratio-slow"


def _build_rows(raw_rows: list[dict[str, Any]], corpus_labels: dict[int, str]) -> tuple[list[str], dict[WorkloadKey, dict[str, dict[str, float]]]]:
    grouped: dict[tuple[int, int, str, str], list[dict[str, Any]]] = defaultdict(list)
    engines: set[str] = set()

    for row in raw_rows:
        if str(row.get("status", "")).lower() != "ok":
            continue
        engine = str(row.get("engine_name", "")).strip()
        if not engine:
            continue
        patterns = int(row.get("patterns_compiled", 0))
        input_bytes = int(row.get("corpus_size_bytes", 0))
        input_label = corpus_labels.get(input_bytes, _bytes_label(input_bytes))
        key = (patterns, input_bytes, input_label, engine)
        grouped[key].append(row)
        engines.add(engine)

    by_workload: dict[WorkloadKey, dict[str, dict[str, float]]] = defaultdict(dict)
    for (patterns, input_bytes, input_label, engine), rows in grouped.items():
        total_ms = [float(r.get("total_ns", 0)) / 1_000_000.0 for r in rows]
        scan_ms = [float(r.get("scanning_ns", 0)) / 1_000_000.0 for r in rows]
        compile_ms = [float(r.get("compilation_ns", 0)) / 1_000_000.0 for r in rows]
        matches = [float(r.get("match_count", 0)) for r in rows]

        mean_total_ms = statistics.fmean(total_ms)
        mean_scan_ms = statistics.fmean(scan_ms)
        mean_compile_ms = statistics.fmean(compile_ms)
        mean_matches = statistics.fmean(matches)

        throughput_mb_s = float("nan")
        total_seconds = mean_total_ms / 1000.0
        if total_seconds > 0:
            throughput_mb_s = (input_bytes / (1024 * 1024)) / total_seconds

        wk = WorkloadKey(patterns=patterns, input_bytes=input_bytes, input_label=input_label)
        by_workload[wk][engine] = {
            "iterations": float(len(rows)),
            "mean_total_ms": mean_total_ms,
            "median_total_ms": statistics.median(total_ms),
            "std_total_ms": _safe_stdev(total_ms),
            "mean_scan_ms": mean_scan_ms,
            "mean_compile_ms": mean_compile_ms,
            "mean_matches": mean_matches,
            "throughput_mb_s": throughput_mb_s,
        }

    return sorted(engines), by_workload


def _write_long_csv(output_dir: Path, by_workload: dict[WorkloadKey, dict[str, dict[str, float]]]) -> Path:
    out_path = output_dir / "workload_engine_comparison.csv"
    fieldnames = [
        "patterns",
        "input_label",
        "input_bytes",
        "engine",
        "iterations",
        "mean_total_ms",
        "median_total_ms",
        "std_total_ms",
        "mean_scan_ms",
        "mean_compile_ms",
        "mean_matches",
        "throughput_mb_s",
    ]
    with out_path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=fieldnames)
        writer.writeheader()
        for workload in sorted(by_workload.keys(), key=lambda w: (w.patterns, w.input_bytes)):
            engine_map = by_workload[workload]
            for engine in sorted(engine_map.keys()):
                stats = engine_map[engine]
                writer.writerow(
                    {
                        "patterns": workload.patterns,
                        "input_label": workload.input_label,
                        "input_bytes": workload.input_bytes,
                        "engine": engine,
                        "iterations": int(stats["iterations"]),
                        "mean_total_ms": f"{stats['mean_total_ms']:.6f}",
                        "median_total_ms": f"{stats['median_total_ms']:.6f}",
                        "std_total_ms": f"{stats['std_total_ms']:.6f}",
                        "mean_scan_ms": f"{stats['mean_scan_ms']:.6f}",
                        "mean_compile_ms": f"{stats['mean_compile_ms']:.6f}",
                        "mean_matches": f"{stats['mean_matches']:.2f}",
                        "throughput_mb_s": (
                            f"{stats['throughput_mb_s']:.6f}"
                            if math.isfinite(stats["throughput_mb_s"])
                            else ""
                        ),
                    }
                )
    return out_path


def _write_matrix_csv(output_dir: Path, engines: list[str], by_workload: dict[WorkloadKey, dict[str, dict[str, float]]]) -> tuple[Path, dict[str, Any]]:
    out_path = output_dir / "workload_engine_matrix.csv"
    headers = [
        "patterns",
        "input_label",
        "input_bytes",
        "winner_engine",
        "winner_total_ms",
    ]
    for engine in engines:
        headers.append(f"{engine}_total_ms")
    for engine in engines:
        headers.append(f"{engine}_x_vs_winner")

    wins = {engine: 0 for engine in engines}
    norm_factors: dict[str, list[float]] = {engine: [] for engine in engines}

    with out_path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=headers)
        writer.writeheader()

        for workload in sorted(by_workload.keys(), key=lambda w: (w.patterns, w.input_bytes)):
            row: dict[str, Any] = {
                "patterns": workload.patterns,
                "input_label": workload.input_label,
                "input_bytes": workload.input_bytes,
            }
            stats = by_workload[workload]
            winner_engine = min(stats.keys(), key=lambda e: stats[e]["mean_total_ms"])
            winner_total_ms = stats[winner_engine]["mean_total_ms"]
            wins[winner_engine] += 1

            row["winner_engine"] = winner_engine
            row["winner_total_ms"] = f"{winner_total_ms:.6f}"

            for engine in engines:
                if engine in stats:
                    engine_ms = stats[engine]["mean_total_ms"]
                    row[f"{engine}_total_ms"] = f"{engine_ms:.6f}"
                else:
                    row[f"{engine}_total_ms"] = ""

            for engine in engines:
                if engine in stats and winner_total_ms > 0:
                    factor = stats[engine]["mean_total_ms"] / winner_total_ms
                    row[f"{engine}_x_vs_winner"] = f"{factor:.6f}"
                    norm_factors[engine].append(factor)
                else:
                    row[f"{engine}_x_vs_winner"] = ""

            writer.writerow(row)

    normalized_summary: dict[str, Any] = {}
    for engine in engines:
        vals = norm_factors[engine]
        if vals:
            normalized_summary[engine] = {
                "wins": wins[engine],
                "mean_x_vs_winner": statistics.fmean(vals),
                "geomean_x_vs_winner": _geomean(vals),
            }
        else:
            normalized_summary[engine] = {
                "wins": wins[engine],
                "mean_x_vs_winner": float("nan"),
                "geomean_x_vs_winner": float("nan"),
            }

    return out_path, normalized_summary


def _write_markdown(
    output_dir: Path,
    run_dir: Path,
    engines: list[str],
    by_workload: dict[WorkloadKey, dict[str, dict[str, float]]],
    normalized_summary: dict[str, Any],
) -> Path:
    out_path = output_dir / "workload_engine_comparison.md"
    generated = dt.datetime.now(dt.timezone.utc).isoformat()

    lines: list[str] = []
    lines.append("# Workload vs Engine Comparison")
    lines.append("")
    lines.append(f"- Source run: `{run_dir}`")
    lines.append(f"- Generated (UTC): `{generated}`")
    lines.append(f"- Engines: `{', '.join(engines)}`")
    lines.append(f"- Workloads: `{len(by_workload)}`")
    lines.append("")

    lines.append("## Workload Winners (mean total ms)")
    lines.append("")
    header = ["patterns", "input", "winner", "winner_ms"] + [f"{e}_ms" for e in engines] + [f"{e}_x" for e in engines]
    lines.append("| " + " | ".join(header) + " |")
    lines.append("|" + "|".join(["---"] * len(header)) + "|")

    for workload in sorted(by_workload.keys(), key=lambda w: (w.patterns, w.input_bytes)):
        stats = by_workload[workload]
        winner = min(stats.keys(), key=lambda e: stats[e]["mean_total_ms"])
        winner_ms = stats[winner]["mean_total_ms"]
        row = [str(workload.patterns), workload.input_label, winner, f"{winner_ms:.3f}"]
        for engine in engines:
            if engine in stats:
                row.append(f"{stats[engine]['mean_total_ms']:.3f}")
            else:
                row.append("-")
        for engine in engines:
            if engine in stats and winner_ms > 0:
                row.append(f"{(stats[engine]['mean_total_ms'] / winner_ms):.2f}x")
            else:
                row.append("-")
        lines.append("| " + " | ".join(row) + " |")

    lines.append("")
    lines.append("## Engine Summary Across Workloads")
    lines.append("")
    lines.append("| engine | workload_wins | mean_x_vs_winner | geomean_x_vs_winner |")
    lines.append("|---|---:|---:|---:|")
    for engine in engines:
        summary = normalized_summary[engine]
        mean_x = summary["mean_x_vs_winner"]
        geo_x = summary["geomean_x_vs_winner"]
        mean_x_str = f"{mean_x:.3f}" if math.isfinite(mean_x) else "-"
        geo_x_str = f"{geo_x:.3f}" if math.isfinite(geo_x) else "-"
        lines.append(
            f"| {engine} | {summary['wins']} | {mean_x_str} | {geo_x_str} |"
        )

    lines.append("")
    lines.append("Notes:")
    lines.append("- `*_x` columns are slowdown factors versus the winner for that workload (`1.00x` is best).")
    lines.append("- Ranking uses `mean_total_ms` over successful iterations only.")

    out_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return out_path


def _write_html(
    output_dir: Path,
    run_dir: Path,
    engines: list[str],
    by_workload: dict[WorkloadKey, dict[str, dict[str, float]]],
    normalized_summary: dict[str, Any],
) -> Path:
    out_path = output_dir / "workload_engine_comparison.html"
    generated = dt.datetime.now(dt.timezone.utc).isoformat()
    workloads = sorted(by_workload.keys(), key=lambda w: (w.patterns, w.input_bytes))
    max_wins = max((int(normalized_summary[e]["wins"]) for e in engines), default=1)
    if max_wins <= 0:
        max_wins = 1

    lines: list[str] = []
    lines.append("<!doctype html>")
    lines.append("<html lang=\"en\">")
    lines.append("<head>")
    lines.append("  <meta charset=\"utf-8\">")
    lines.append("  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
    lines.append("  <title>Workload vs Engine Comparison</title>")
    lines.append("  <style>")
    lines.append("    :root {")
    lines.append("      --bg: #f6f8fb;")
    lines.append("      --text: #1f2937;")
    lines.append("      --muted: #4b5563;")
    lines.append("      --card: #ffffff;")
    lines.append("      --line: #d1d5db;")
    lines.append("      --best: #065f46;")
    lines.append("      --best-bg: #d1fae5;")
    lines.append("      --good: #1e40af;")
    lines.append("      --good-bg: #dbeafe;")
    lines.append("      --mid: #92400e;")
    lines.append("      --mid-bg: #fef3c7;")
    lines.append("      --slow: #7f1d1d;")
    lines.append("      --slow-bg: #fee2e2;")
    lines.append("    }")
    lines.append("    * { box-sizing: border-box; }")
    lines.append("    body { margin: 0; font-family: 'Segoe UI', Tahoma, sans-serif; background: var(--bg); color: var(--text); }")
    lines.append("    .container { max-width: 1200px; margin: 0 auto; padding: 20px; }")
    lines.append("    h1, h2 { margin: 0 0 12px; }")
    lines.append("    p.meta { margin: 2px 0; color: var(--muted); font-size: 0.95rem; }")
    lines.append("    .panel { background: var(--card); border: 1px solid var(--line); border-radius: 10px; padding: 16px; margin-bottom: 16px; }")
    lines.append("    .grid { display: grid; gap: 12px; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); }")
    lines.append("    .stat { background: #f9fafb; border: 1px solid var(--line); border-radius: 8px; padding: 10px; }")
    lines.append("    .stat .k { color: var(--muted); font-size: 0.85rem; }")
    lines.append("    .stat .v { font-size: 1.1rem; font-weight: 600; margin-top: 4px; }")
    lines.append("    table { width: 100%; border-collapse: collapse; font-size: 0.92rem; }")
    lines.append("    th, td { border-bottom: 1px solid var(--line); padding: 7px 8px; text-align: left; vertical-align: top; }")
    lines.append("    thead th { position: sticky; top: 0; background: #f3f4f6; z-index: 2; }")
    lines.append("    td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }")
    lines.append("    .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }")
    lines.append("    .winner { font-weight: 700; }")
    lines.append("    .ratio-best { background: var(--best-bg); color: var(--best); font-weight: 700; }")
    lines.append("    .ratio-good { background: var(--good-bg); color: var(--good); }")
    lines.append("    .ratio-mid { background: var(--mid-bg); color: var(--mid); }")
    lines.append("    .ratio-slow { background: var(--slow-bg); color: var(--slow); }")
    lines.append("    .bar-wrap { background: #e5e7eb; border-radius: 999px; height: 10px; overflow: hidden; }")
    lines.append("    .bar { height: 10px; background: #2563eb; }")
    lines.append("    .links a { margin-right: 12px; }")
    lines.append("    .table-wrap { overflow: auto; max-height: 72vh; border: 1px solid var(--line); border-radius: 8px; }")
    lines.append("  </style>")
    lines.append("</head>")
    lines.append("<body>")
    lines.append("  <div class=\"container\">")
    lines.append("    <div class=\"panel\">")
    lines.append("      <h1>Workload vs Engine Comparison</h1>")
    lines.append(f"      <p class=\"meta\">Source run: <span class=\"mono\">{_escape(run_dir)}</span></p>")
    lines.append(f"      <p class=\"meta\">Generated (UTC): <span class=\"mono\">{_escape(generated)}</span></p>")
    lines.append("      <div class=\"grid\" style=\"margin-top: 12px;\">")
    lines.append("        <div class=\"stat\"><div class=\"k\">Engines</div><div class=\"v\">" + _escape(len(engines)) + "</div></div>")
    lines.append("        <div class=\"stat\"><div class=\"k\">Workloads</div><div class=\"v\">" + _escape(len(workloads)) + "</div></div>")
    lines.append("        <div class=\"stat\"><div class=\"k\">Metric</div><div class=\"v\">mean total ms</div></div>")
    lines.append("      </div>")
    lines.append("      <p class=\"links\" style=\"margin-top: 12px;\">")
    lines.append("        <a href=\"workload_engine_comparison.md\">Markdown</a>")
    lines.append("        <a href=\"workload_engine_comparison.csv\">Long CSV</a>")
    lines.append("        <a href=\"workload_engine_matrix.csv\">Matrix CSV</a>")
    lines.append("      </p>")
    lines.append("    </div>")

    lines.append("    <div class=\"panel\">")
    lines.append("      <h2>Engine Summary</h2>")
    lines.append("      <div class=\"table-wrap\">")
    lines.append("        <table>")
    lines.append("          <thead><tr><th>Engine</th><th class=\"num\">Wins</th><th>Win share</th><th class=\"num\">Mean x vs winner</th><th class=\"num\">Geomean x vs winner</th></tr></thead>")
    lines.append("          <tbody>")
    ordered_engines = sorted(
        engines,
        key=lambda e: (
            -int(normalized_summary[e]["wins"]),
            float(normalized_summary[e]["geomean_x_vs_winner"])
            if math.isfinite(float(normalized_summary[e]["geomean_x_vs_winner"]))
            else float("inf"),
            e,
        ),
    )
    for engine in ordered_engines:
        summary = normalized_summary[engine]
        wins = int(summary["wins"])
        mean_x = float(summary["mean_x_vs_winner"])
        geo_x = float(summary["geomean_x_vs_winner"])
        bar_width = int(round((wins / max_wins) * 100))
        mean_x_str = f"{mean_x:.3f}" if math.isfinite(mean_x) else "-"
        geo_x_str = f"{geo_x:.3f}" if math.isfinite(geo_x) else "-"
        lines.append("            <tr>")
        lines.append(f"              <td class=\"mono\">{_escape(engine)}</td>")
        lines.append(f"              <td class=\"num\">{wins}</td>")
        lines.append("              <td><div class=\"bar-wrap\"><div class=\"bar\" style=\"width: " + str(bar_width) + "%\"></div></div></td>")
        lines.append(f"              <td class=\"num\">{_escape(mean_x_str)}</td>")
        lines.append(f"              <td class=\"num\">{_escape(geo_x_str)}</td>")
        lines.append("            </tr>")
    lines.append("          </tbody>")
    lines.append("        </table>")
    lines.append("      </div>")
    lines.append("    </div>")

    lines.append("    <div class=\"panel\">")
    lines.append("      <h2>Per-Workload Winners and Slowdown Ratios</h2>")
    lines.append("      <p class=\"meta\">Ratio columns are x slower than the winner for that workload (1.00 is best).</p>")
    lines.append("      <div class=\"table-wrap\">")
    lines.append("        <table>")
    lines.append("          <thead>")
    lines.append("            <tr>")
    lines.append("              <th>Patterns</th><th>Input</th><th>Winner</th><th class=\"num\">Winner ms</th>")
    for engine in engines:
        lines.append(f"              <th class=\"num\">{_escape(engine)} ms</th>")
    for engine in engines:
        lines.append(f"              <th class=\"num\">{_escape(engine)} x</th>")
    lines.append("            </tr>")
    lines.append("          </thead>")
    lines.append("          <tbody>")

    for workload in workloads:
        stats = by_workload[workload]
        winner = min(stats.keys(), key=lambda e: stats[e]["mean_total_ms"])
        winner_ms = stats[winner]["mean_total_ms"]
        lines.append("            <tr>")
        lines.append(f"              <td class=\"num\">{workload.patterns}</td>")
        lines.append(f"              <td class=\"mono\">{_escape(workload.input_label)}</td>")
        lines.append(f"              <td class=\"mono winner\">{_escape(winner)}</td>")
        lines.append(f"              <td class=\"num\">{winner_ms:.3f}</td>")
        for engine in engines:
            if engine in stats:
                ms = stats[engine]["mean_total_ms"]
                lines.append(f"              <td class=\"num\">{ms:.3f}</td>")
            else:
                lines.append("              <td class=\"num\">-</td>")
        for engine in engines:
            if engine in stats and winner_ms > 0:
                ratio = stats[engine]["mean_total_ms"] / winner_ms
                cls = _ratio_class(ratio)
                lines.append(f"              <td class=\"num {cls}\">{ratio:.2f}</td>")
            else:
                lines.append("              <td class=\"num\">-</td>")
        lines.append("            </tr>")

    lines.append("          </tbody>")
    lines.append("        </table>")
    lines.append("      </div>")
    lines.append("    </div>")
    lines.append("  </div>")
    lines.append("</body>")
    lines.append("</html>")

    out_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return out_path


def generate(input_dir: Path, output_dir: Path) -> dict[str, str]:
    raw_path = input_dir / "raw_results" / "benchmark_results.json"
    partial_path = input_dir / "raw_results" / "benchmark_results.partial.json"
    jobs_db_path = input_dir / "jobs.db"

    raw_rows: list[dict[str, Any]] | None = None
    source = ""
    if raw_path.exists():
        loaded = json.loads(raw_path.read_text(encoding="utf-8"))
        if isinstance(loaded, list):
            raw_rows = loaded
            source = str(raw_path)

    if raw_rows is None and partial_path.exists():
        loaded_partial = json.loads(partial_path.read_text(encoding="utf-8"))
        if isinstance(loaded_partial, dict):
            rows = loaded_partial.get("results")
            if isinstance(rows, list):
                raw_rows = rows
                source = str(partial_path)

    if raw_rows is None and jobs_db_path.exists():
        conn = sqlite3.connect(str(jobs_db_path))
        conn.row_factory = sqlite3.Row
        try:
            q = """
                SELECT
                    engine_name,
                    iteration,
                    status,
                    compilation_ns,
                    scanning_ns,
                    total_ns,
                    match_count,
                    pattern_count,
                    input_size_bytes
                FROM benchmark_jobs
            """
            rows = conn.execute(q).fetchall()
            mapped: list[dict[str, Any]] = []
            for r in rows:
                status_raw = str(r["status"] or "").upper()
                status = "ok" if status_raw == "COMPLETED" else status_raw.lower()
                mapped.append(
                    {
                        "engine_name": r["engine_name"],
                        "iteration": r["iteration"],
                        "status": status,
                        "compilation_ns": r["compilation_ns"] or 0,
                        "scanning_ns": r["scanning_ns"] or 0,
                        "total_ns": r["total_ns"] or 0,
                        "match_count": r["match_count"] or 0,
                        "patterns_compiled": r["pattern_count"] or 0,
                        "corpus_size_bytes": r["input_size_bytes"] or 0,
                    }
                )
            raw_rows = mapped
            source = str(jobs_db_path)
        finally:
            conn.close()

    if raw_rows is None:
        raise FileNotFoundError(
            f"Missing benchmark results file: {raw_path} (and no partial at {partial_path} or jobs DB at {jobs_db_path})"
        )
    if not raw_rows:
        raise RuntimeError(f"No benchmark rows found in {source}")

    output_dir.mkdir(parents=True, exist_ok=True)

    corpus_labels = _load_corpus_label_map(input_dir)
    engines, by_workload = _build_rows(raw_rows, corpus_labels)
    if not engines or not by_workload:
        raise RuntimeError("No successful benchmark rows found to compare.")

    long_csv = _write_long_csv(output_dir, by_workload)
    matrix_csv, normalized_summary = _write_matrix_csv(output_dir, engines, by_workload)
    markdown = _write_markdown(output_dir, input_dir, engines, by_workload, normalized_summary)
    html_report = _write_html(output_dir, input_dir, engines, by_workload, normalized_summary)

    return {
        "markdown": str(markdown),
        "html": str(html_report),
        "long_csv": str(long_csv),
        "matrix_csv": str(matrix_csv),
        "workloads": str(len(by_workload)),
        "engines": ",".join(engines),
        "source": source,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate workload-level engine comparison reports.")
    parser.add_argument(
        "--input",
        required=True,
        help="Benchmark results directory containing raw_results/benchmark_results.json",
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Output directory for generated report files",
    )
    args = parser.parse_args()

    result = generate(Path(args.input), Path(args.output))
    print(f"Generated workload comparison report for {result['workloads']} workloads.")
    print(f"Engines: {result['engines']}")
    print(f"Source: {result['source']}")
    print(f"Markdown: {result['markdown']}")
    print(f"HTML: {result['html']}")
    print(f"Long CSV: {result['long_csv']}")
    print(f"Matrix CSV: {result['matrix_csv']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
