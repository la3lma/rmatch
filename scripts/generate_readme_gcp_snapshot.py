#!/usr/bin/env python3
"""Generate README-ready comparable GCP benchmark charts and snapshot markdown."""

from __future__ import annotations

import argparse
import csv
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import matplotlib.pyplot as plt


@dataclass
class Row:
    input_label: str
    input_bytes: int
    winner_engine: str
    winner_total_ms: float
    totals_ms: dict[str, float]
    ratios_x: dict[str, float]


def _parse_float(value: str) -> float | None:
    value = (value or "").strip()
    if not value:
        return None
    try:
        return float(value)
    except Exception:
        return None


def _load_rows(matrix_csv: Path, cohort: str, patterns: int) -> tuple[list[Row], list[str]]:
    with matrix_csv.open("r", encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        all_rows = list(reader)

    if not all_rows:
        raise RuntimeError(f"No rows found in {matrix_csv}")

    total_cols = [c for c in all_rows[0].keys() if c.endswith("_total_ms")]
    engine_total_cols = [c for c in total_cols if c != "winner_total_ms"]
    ratio_cols = [c for c in all_rows[0].keys() if c.endswith("_x_vs_winner")]
    engines = [c.replace("_total_ms", "") for c in engine_total_cols]

    rows: list[Row] = []
    for raw in all_rows:
        if raw.get("cohort", "") != cohort:
            continue
        if int(raw.get("patterns", "0") or "0") != patterns:
            continue

        totals: dict[str, float] = {}
        for col in engine_total_cols:
            value = _parse_float(raw.get(col, ""))
            if value is not None:
                totals[col.replace("_total_ms", "")] = value

        ratios: dict[str, float] = {}
        for col in ratio_cols:
            value = _parse_float(raw.get(col, ""))
            if value is not None:
                ratios[col.replace("_x_vs_winner", "")] = value

        rows.append(
            Row(
                input_label=str(raw.get("input_label", "")).strip(),
                input_bytes=int(raw.get("input_bytes", "0") or "0"),
                winner_engine=str(raw.get("winner_engine", "")).strip(),
                winner_total_ms=float(raw.get("winner_total_ms", "0") or "0"),
                totals_ms=totals,
                ratios_x=ratios,
            )
        )

    rows.sort(key=lambda r: r.input_bytes)
    if not rows:
        raise RuntimeError(
            f"No matching rows found for cohort={cohort}, patterns={patterns} in {matrix_csv}"
        )
    return rows, engines


def _bytes_to_mib(num_bytes: int) -> float:
    return float(num_bytes) / (1024.0 * 1024.0)


def _runtime_plot(rows: list[Row], engines: list[str], out_path: Path) -> None:
    x_vals = [_bytes_to_mib(r.input_bytes) for r in rows]
    x_labels = [r.input_label for r in rows]

    colors = {
        "rmatch": "#16a34a",
        "re2j": "#0ea5e9",
        "java-native-naive": "#ef4444",
        "java-native-unfair": "#f97316",
    }

    plt.figure(figsize=(9, 5))
    for engine in engines:
        y_vals: list[float] = []
        x_ok: list[float] = []
        for idx, row in enumerate(rows):
            val = row.totals_ms.get(engine)
            if val is None:
                continue
            x_ok.append(x_vals[idx])
            y_vals.append(val / 1000.0)
        if not y_vals:
            continue
        plt.plot(
            x_ok,
            y_vals,
            marker="o",
            linewidth=2.2,
            label=engine,
            color=colors.get(engine, None),
        )

    plt.xscale("log")
    plt.yscale("log")
    plt.xticks(x_vals, x_labels)
    plt.xlabel("Corpus size")
    plt.ylabel("Runtime (seconds, lower is better)")
    plt.title("Comparable GCP Runtime: 10K patterns on e2-standard-8|x86_64")
    plt.grid(True, which="both", linestyle="--", alpha=0.35)
    plt.legend()
    plt.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(out_path, dpi=180)
    plt.close()


def _ratio_plot(rows: list[Row], engines: list[str], out_path: Path) -> None:
    x_labels = [r.input_label for r in rows]
    x_positions = list(range(len(rows)))
    ratio_engines = [e for e in engines if e in {"rmatch", "re2j", "java-native-naive"}]
    if not ratio_engines:
        ratio_engines = engines

    colors = {
        "rmatch": "#16a34a",
        "re2j": "#0ea5e9",
        "java-native-naive": "#ef4444",
    }

    width = 0.24
    plt.figure(figsize=(10, 5))
    for idx, engine in enumerate(ratio_engines):
        bar_x = [x + (idx - (len(ratio_engines) - 1) / 2.0) * width for x in x_positions]
        values: list[float] = []
        for row in rows:
            ratio = row.ratios_x.get(engine)
            values.append(ratio if ratio is not None else float("nan"))
        plt.bar(
            bar_x,
            values,
            width=width,
            label=engine,
            color=colors.get(engine, None),
            alpha=0.9,
        )

    plt.axhline(1.0, color="#374151", linestyle="--", linewidth=1.2)
    plt.yscale("log")
    plt.xticks(x_positions, x_labels)
    plt.xlabel("Corpus size")
    plt.ylabel("Runtime ratio vs winner (1.0x is best)")
    plt.title("Relative slowdown vs winner (same cohort, 10K patterns)")
    plt.grid(True, axis="y", which="both", linestyle="--", alpha=0.3)
    plt.legend()
    plt.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(out_path, dpi=180)
    plt.close()


def _throughput_plot(rows: list[Row], engines: list[str], out_path: Path) -> None:
    x_vals = [_bytes_to_mib(r.input_bytes) for r in rows]
    x_labels = [r.input_label for r in rows]
    colors = {
        "rmatch": "#16a34a",
        "re2j": "#0ea5e9",
        "java-native-naive": "#ef4444",
        "java-native-unfair": "#f97316",
    }

    plt.figure(figsize=(9, 5))
    for engine in engines:
        y_vals: list[float] = []
        x_ok: list[float] = []
        for idx, row in enumerate(rows):
            runtime_ms = row.totals_ms.get(engine)
            if runtime_ms is None or runtime_ms <= 0:
                continue
            throughput = _bytes_to_mib(row.input_bytes) / (runtime_ms / 1000.0)
            x_ok.append(x_vals[idx])
            y_vals.append(throughput)
        if not y_vals:
            continue
        plt.plot(
            x_ok,
            y_vals,
            marker="o",
            linewidth=2.2,
            label=engine,
            color=colors.get(engine, None),
        )

    plt.xscale("log")
    plt.yscale("log")
    plt.xticks(x_vals, x_labels)
    plt.xlabel("Corpus size")
    plt.ylabel("Throughput (MiB/s, higher is better; 1 MiB = 1,048,576 bytes)")
    plt.title("Throughput trend by engine (same cohort, 10K patterns)")
    plt.grid(True, which="both", linestyle="--", alpha=0.35)
    plt.legend()
    plt.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(out_path, dpi=180)
    plt.close()


def _fmt_ms(value: float | None) -> str:
    if value is None:
        return "-"
    return f"{value:,.1f}"


def _fmt_x(value: float | None) -> str:
    if value is None:
        return "-"
    return f"{value:.2f}x"


def _write_snapshot_md(
    rows: list[Row],
    cohort: str,
    patterns: int,
    matrix_csv: Path,
    md_out: Path,
    runtime_plot: Path,
    ratio_plot: Path,
    throughput_plot: Path,
) -> None:
    rel_matrix = os.path.relpath(matrix_csv, md_out.parent).replace("\\", "/")
    rel_runtime = os.path.relpath(runtime_plot, md_out.parent).replace("\\", "/")
    rel_ratio = os.path.relpath(ratio_plot, md_out.parent).replace("\\", "/")
    rel_tp = os.path.relpath(throughput_plot, md_out.parent).replace("\\", "/")

    lines: list[str] = []
    lines.append("# Latest Performance Tests Running 10K Regular Expression Patterns on Google Compute Node")
    lines.append("")
    lines.append(f"- Cohort: `{cohort}`")
    lines.append(f"- Pattern count: `{patterns}`")
    lines.append(f"- Source matrix: [{matrix_csv.as_posix()}]({rel_matrix})")
    lines.append("")
    lines.append(
        "| Corpus | Winner | rmatch (ms) | re2j (ms) | java-native-naive (ms) | re2j vs winner | java-native-naive vs winner |"
    )
    lines.append("|---|---:|---:|---:|---:|---:|---:|")

    for row in rows:
        rmatch_ms = row.totals_ms.get("rmatch")
        re2j_ms = row.totals_ms.get("re2j")
        java_ms = row.totals_ms.get("java-native-naive")
        re2j_x = row.ratios_x.get("re2j")
        java_x = row.ratios_x.get("java-native-naive")
        lines.append(
            f"| {row.input_label} | {row.winner_engine} | {_fmt_ms(rmatch_ms)} | {_fmt_ms(re2j_ms)} | {_fmt_ms(java_ms)} | {_fmt_x(re2j_x)} | {_fmt_x(java_x)} |"
        )

    lines.append("")
    lines.append("Missing entries indicate no successful completed run for that engine/workload yet.")
    lines.append("")
    lines.append(f"![Comparable runtime]({rel_runtime})")
    lines.append("")
    lines.append(f"![Relative slowdown vs winner]({rel_ratio})")
    lines.append("")
    lines.append(f"![Throughput trend]({rel_tp})")
    lines.append("")
    lines.append("*MiB = mebibyte (2^20 bytes = 1,048,576 bytes).*")
    lines.append("")

    md_out.parent.mkdir(parents=True, exist_ok=True)
    md_out.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate README-ready charts from comparable GCP workload matrix."
    )
    parser.add_argument(
        "--matrix-csv",
        default="../rmatch-perftest/benchmarking/framework/regex_bench_framework/reports/workload_all_live/cohort_workload_engine_matrix.csv",
    )
    parser.add_argument("--cohort", default="e2-standard-8|x86_64")
    parser.add_argument("--patterns", type=int, default=10000)
    parser.add_argument("--output-dir", default="/tmp/rmatch-gcp-snapshot")
    parser.add_argument(
        "--md-output",
        default="/tmp/rmatch-gcp-snapshot/LATEST_PERFORMANCE_TESTS_10K_REGEX_PATTERNS_GOOGLE_COMPUTE_NODE.md",
    )
    args = parser.parse_args()

    matrix_csv = Path(args.matrix_csv)
    output_dir = Path(args.output_dir)
    md_output = Path(args.md_output)

    rows, engines = _load_rows(matrix_csv, args.cohort, args.patterns)

    runtime_plot = output_dir / "gcp_e2_10k_runtime_seconds.png"
    ratio_plot = output_dir / "gcp_e2_10k_relative_x_vs_winner.png"
    throughput_plot = output_dir / "gcp_e2_10k_throughput_mib_s.png"

    _runtime_plot(rows, engines, runtime_plot)
    _ratio_plot(rows, engines, ratio_plot)
    _throughput_plot(rows, engines, throughput_plot)
    _write_snapshot_md(
        rows,
        args.cohort,
        args.patterns,
        matrix_csv,
        md_output,
        runtime_plot,
        ratio_plot,
        throughput_plot,
    )

    print("Generated comparable snapshot artifacts:")
    print(f"  - {runtime_plot}")
    print(f"  - {ratio_plot}")
    print(f"  - {throughput_plot}")
    print(f"  - {md_output}")


if __name__ == "__main__":
    main()
