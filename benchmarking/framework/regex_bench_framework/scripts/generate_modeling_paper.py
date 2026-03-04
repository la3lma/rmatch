#!/usr/bin/env python3
"""Generate a compact LaTeX note with runtime modeling and a 2D dominance map."""

from __future__ import annotations

import argparse
import csv
import math
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


REF_PATTERNS = 100.0
REF_INPUT_BYTES = float(10 * 1024 * 1024)


def _latex_escape(value: str) -> str:
    repl = {
        "\\": r"\textbackslash{}",
        "&": r"\&",
        "%": r"\%",
        "$": r"\$",
        "#": r"\#",
        "_": r"\_",
        "{": r"\{",
        "}": r"\}",
        "~": r"\textasciitilde{}",
        "^": r"\textasciicircum{}",
    }
    out = []
    for ch in value:
        out.append(repl.get(ch, ch))
    return "".join(out)


def _fmt_num(value: float, digits: int = 3) -> str:
    if not math.isfinite(value):
        return "-"
    abs_v = abs(value)
    if abs_v >= 1_000_000:
        return f"{value:.3e}"
    if abs_v >= 1000:
        return f"{value:,.1f}"
    return f"{value:.{digits}f}"


def _engine_color(engine: str, index: int) -> str:
    predefined = {
        "java-native-naive": "2563EB",
        "java-native-unfair": "B45309",
        "re2j": "047857",
        "rmatch": "7C3AED",
    }
    if engine in predefined:
        return predefined[engine]
    palette = [
        "0EA5E9",
        "10B981",
        "F59E0B",
        "EF4444",
        "8B5CF6",
        "14B8A6",
        "EC4899",
    ]
    return palette[index % len(palette)]


def _safe_float(row: dict[str, str], key: str, default: float = float("nan")) -> float:
    try:
        return float(row.get(key, ""))
    except (TypeError, ValueError):
        return default


def _safe_int(row: dict[str, str], key: str, default: int = 0) -> int:
    try:
        return int(float(row.get(key, "")))
    except (TypeError, ValueError):
        return default


def _solve_3x3(a: list[list[float]], b: list[float]) -> list[float] | None:
    m = [row[:] + [rhs] for row, rhs in zip(a, b)]
    n = 3
    for col in range(n):
        pivot = col
        pivot_abs = abs(m[col][col])
        for r in range(col + 1, n):
            val = abs(m[r][col])
            if val > pivot_abs:
                pivot_abs = val
                pivot = r
        if pivot_abs < 1e-12:
            return None
        if pivot != col:
            m[col], m[pivot] = m[pivot], m[col]

        denom = m[col][col]
        for c in range(col, n + 1):
            m[col][c] /= denom

        for r in range(n):
            if r == col:
                continue
            factor = m[r][col]
            if abs(factor) < 1e-18:
                continue
            for c in range(col, n + 1):
                m[r][c] -= factor * m[col][c]
    return [m[i][n] for i in range(n)]


@dataclass
class ModelFit:
    engine: str
    samples: int
    intercept: float
    alpha_patterns: float
    beta_input: float
    k_ref_ms: float
    r2: float


def _fit_log_model(points: list[tuple[float, float, float]], engine: str) -> ModelFit | None:
    if len(points) < 3:
        return None

    xs1: list[float] = []
    xs2: list[float] = []
    ys: list[float] = []
    for patterns, input_bytes, ms in points:
        if patterns <= 0 or input_bytes <= 0 or ms <= 0:
            continue
        xs1.append(math.log(patterns))
        xs2.append(math.log(input_bytes))
        ys.append(math.log(ms))
    if len(ys) < 3:
        return None

    n = float(len(ys))
    sum_x1 = sum(xs1)
    sum_x2 = sum(xs2)
    sum_y = sum(ys)
    sum_x1x1 = sum(v * v for v in xs1)
    sum_x2x2 = sum(v * v for v in xs2)
    sum_x1x2 = sum(a * b for a, b in zip(xs1, xs2))
    sum_x1y = sum(a * y for a, y in zip(xs1, ys))
    sum_x2y = sum(a * y for a, y in zip(xs2, ys))

    a = [
        [n, sum_x1, sum_x2],
        [sum_x1, sum_x1x1, sum_x1x2],
        [sum_x2, sum_x1x2, sum_x2x2],
    ]
    rhs = [sum_y, sum_x1y, sum_x2y]
    sol = _solve_3x3(a, rhs)
    if sol is None:
        return None
    b0, b1, b2 = sol

    y_mean = sum_y / n
    ss_res = 0.0
    ss_tot = 0.0
    for x1, x2, y in zip(xs1, xs2, ys):
        pred = b0 + b1 * x1 + b2 * x2
        ss_res += (y - pred) ** 2
        ss_tot += (y - y_mean) ** 2
    r2 = 1.0 - (ss_res / ss_tot) if ss_tot > 0 else float("nan")

    k_ref = math.exp(b0 + b1 * math.log(REF_PATTERNS) + b2 * math.log(REF_INPUT_BYTES))
    return ModelFit(
        engine=engine,
        samples=len(ys),
        intercept=b0,
        alpha_patterns=b1,
        beta_input=b2,
        k_ref_ms=k_ref,
        r2=r2,
    )


def _read_rows(csv_path: Path) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    with csv_path.open("r", encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            if not row:
                continue
            rows.append(row)
    return rows


def _aggregate(
    rows: Iterable[dict[str, str]],
) -> tuple[dict[tuple[str, int, int, str], float], dict[tuple[str, int, int, str], int]]:
    weighted_sum: dict[tuple[str, int, int, str], float] = defaultdict(float)
    total_weight: dict[tuple[str, int, int, str], int] = defaultdict(int)

    for row in rows:
        cohort = str(row.get("cohort", "")).strip()
        engine = str(row.get("engine", "")).strip()
        patterns = _safe_int(row, "patterns", default=-1)
        input_bytes = _safe_int(row, "input_bytes", default=-1)
        mean_ms = _safe_float(row, "mean_total_ms")
        samples = _safe_int(row, "samples", default=0)
        if not cohort or not engine or patterns <= 0 or input_bytes <= 0 or not math.isfinite(mean_ms) or mean_ms <= 0:
            continue
        weight = max(samples, 1)
        key = (cohort, patterns, input_bytes, engine)
        weighted_sum[key] += mean_ms * weight
        total_weight[key] += weight

    mean_ms_by_key: dict[tuple[str, int, int, str], float] = {}
    for key, total in weighted_sum.items():
        w = total_weight[key]
        if w > 0:
            mean_ms_by_key[key] = total / float(w)
    return mean_ms_by_key, total_weight


def _build_cohort_scenarios(
    mean_ms_by_key: dict[tuple[str, int, int, str], float]
) -> dict[str, dict[tuple[int, int], dict[str, float]]]:
    out: dict[str, dict[tuple[int, int], dict[str, float]]] = defaultdict(lambda: defaultdict(dict))
    for (cohort, patterns, input_bytes, engine), mean_ms in mean_ms_by_key.items():
        out[cohort][(patterns, input_bytes)][engine] = mean_ms
    return out


def _pick_cohort(
    cohort_scenarios: dict[str, dict[tuple[int, int], dict[str, float]]],
    target_engines: list[str],
) -> str:
    best_cohort = ""
    best_score = (-1, -1, -1, -1)
    for cohort, scenarios in cohort_scenarios.items():
        full = 0
        two_plus = 0
        total = 0
        present_engines: set[str] = set()
        for scenario in scenarios.values():
            present = [e for e in target_engines if e in scenario]
            if len(present) >= 2:
                two_plus += 1
            if len(present) == len(target_engines):
                full += 1
            total += 1
            present_engines.update(scenario.keys())
        score = (full, two_plus, len(present_engines), total)
        if score > best_score:
            best_score = score
            best_cohort = cohort
    return best_cohort


def _scenario_rows_for_table(
    scenarios: dict[tuple[int, int], dict[str, float]],
    selected_engines: list[str],
) -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = []
    for (patterns, input_bytes), by_engine in sorted(scenarios.items(), key=lambda it: (it[0][0], it[0][1])):
        present = [e for e in selected_engines if e in by_engine]
        if len(present) < 2:
            continue
        winner = min(present, key=lambda e: by_engine[e])
        rows.append(
            {
                "patterns": patterns,
                "input_bytes": input_bytes,
                "input_mib": input_bytes / (1024.0 * 1024.0),
                "winner": winner,
                "winner_ms": by_engine[winner],
                "present": ", ".join(present),
            }
        )
    return rows


def _winner_coordinates(
    scenarios: dict[tuple[int, int], dict[str, float]],
    selected_engines: list[str],
) -> dict[str, list[tuple[int, int]]]:
    points: dict[str, list[tuple[int, int]]] = defaultdict(list)
    for (patterns, input_bytes), by_engine in scenarios.items():
        present = [e for e in selected_engines if e in by_engine]
        if len(present) < 2:
            continue
        winner = min(present, key=lambda e: by_engine[e])
        points[winner].append((input_bytes, patterns))
    for engine in list(points.keys()):
        points[engine].sort(key=lambda xy: (xy[1], xy[0]))
    return points


def _build_model_boundaries(
    fits: list[ModelFit],
    *,
    x_min: float,
    x_max: float,
    y_min: float,
    y_max: float,
    samples: int = 180,
) -> list[dict[str, Any]]:
    eps = 1e-9
    if x_min <= 0 or x_max <= 0 or y_min <= 0 or y_max <= 0:
        return []

    fit_by_engine = {fit.engine: fit for fit in fits}
    engine_names = sorted(fit_by_engine.keys())
    if len(engine_names) < 2:
        return []

    ln_x_min = math.log(x_min)
    ln_x_max = math.log(x_max)
    boundaries: list[dict[str, Any]] = []

    for i in range(len(engine_names)):
        for j in range(i + 1, len(engine_names)):
            e1 = engine_names[i]
            e2 = engine_names[j]
            f1 = fit_by_engine[e1]
            f2 = fit_by_engine[e2]
            d0 = f1.intercept - f2.intercept
            d1 = f1.alpha_patterns - f2.alpha_patterns
            d2 = f1.beta_input - f2.beta_input
            if abs(d1) < eps and abs(d2) < eps:
                continue

            pair = f"{e1} = {e2}"

            if abs(d1) < eps:
                if abs(d2) < eps:
                    continue
                ln_x = -d0 / d2
                x_val = math.exp(ln_x)
                if x_min <= x_val <= x_max:
                    boundaries.append(
                        {
                            "pair": pair,
                            "segments": [[(x_val, y_min), (x_val, y_max)]],
                        }
                    )
                continue

            seg: list[tuple[float, float]] = []
            segments: list[list[tuple[float, float]]] = []
            for s in range(samples):
                t = 0.0 if samples <= 1 else float(s) / float(samples - 1)
                x_val = math.exp(ln_x_min + t * (ln_x_max - ln_x_min))
                ln_y = -(d0 + d2 * math.log(x_val)) / d1
                y_val = math.exp(ln_y)
                valid = (
                    math.isfinite(x_val)
                    and math.isfinite(y_val)
                    and x_min <= x_val <= x_max
                    and y_min <= y_val <= y_max
                )
                if valid:
                    seg.append((x_val, y_val))
                elif len(seg) >= 2:
                    segments.append(seg)
                    seg = []
                else:
                    seg = []
            if len(seg) >= 2:
                segments.append(seg)

            if segments:
                boundaries.append({"pair": pair, "segments": segments})

    return boundaries


def _build_tex(
    *,
    source_csv: Path,
    cohort: str,
    selected_engines: list[str],
    fits: list[ModelFit],
    scenario_rows: list[dict[str, str | int | float]],
    winner_points: dict[str, list[tuple[int, int]]],
    generated_utc: str,
) -> str:
    color_by_engine = {engine: _engine_color(engine, idx) for idx, engine in enumerate(selected_engines)}

    lines: list[str] = []
    lines.append(r"\documentclass[11pt]{article}")
    lines.append(r"\usepackage[margin=1in]{geometry}")
    lines.append(r"\usepackage[T1]{fontenc}")
    lines.append(r"\usepackage{lmodern}")
    lines.append(r"\usepackage{booktabs}")
    lines.append(r"\usepackage{longtable}")
    lines.append(r"\usepackage{amsmath}")
    lines.append(r"\usepackage{xcolor}")
    lines.append(r"\usepackage{pgfplots}")
    lines.append(r"\usepackage{hyperref}")
    lines.append(r"\pgfplotsset{compat=1.16}")
    lines.append("")
    for engine in selected_engines:
        lines.append(fr"\definecolor{{eng{_latex_escape(engine).replace('-', '')}}}{{HTML}}{{{color_by_engine[engine]}}}")
    lines.append("")
    lines.append(r"\title{Runtime Modeling and 2D Dominance Map for Regex Engine Benchmarks}")
    lines.append(r"\author{Regex Benchmark Framework}")
    lines.append(fr"\date{{Generated UTC: {generated_utc}}}")
    lines.append("")
    lines.append(r"\begin{document}")
    lines.append(r"\maketitle")
    lines.append("")
    lines.append(r"\section*{Scope}")
    lines.append(
        "This note summarizes current benchmark evidence with two artifacts: "
        "(1) a compact runtime scaling model per engine and "
        "(2) a two-dimensional dominance map over workload space."
    )
    lines.append("")
    lines.append(r"\section*{Data Selection}")
    lines.append(r"\begin{itemize}")
    lines.append(fr"\item Source CSV: \texttt{{{_latex_escape(str(source_csv))}}}")
    lines.append(fr"\item Cohort used: \texttt{{{_latex_escape(cohort)}}}")
    lines.append(fr"\item Selected engines: \texttt{{{_latex_escape(', '.join(selected_engines))}}}")
    lines.append(
        r"\item Dominance points include scenarios with at least two selected engines measured, "
        r"to avoid one-engine-only artifacts."
    )
    lines.append(r"\end{itemize}")
    lines.append("")
    lines.append(r"\section*{Model}")
    lines.append(
        r"For each engine, we fit a log-linear model:"
        r"\[ \ln T = \beta_0 + \alpha \ln P + \beta \ln B \]"
        r"where \(T\) is total runtime in milliseconds, \(P\) is pattern count, and \(B\) is input bytes."
    )
    lines.append(
        r"Equivalent anchored form (with \(P_0=100\), \(B_0=10\,\mathrm{MiB}\)):"
        r"\[ T \approx K_{100,10\mathrm{MiB}}\left(\frac{P}{P_0}\right)^{\alpha}\left(\frac{B}{B_0}\right)^{\beta}. \]"
    )
    lines.append("")
    lines.append(r"\begin{table}[h!]")
    lines.append(r"\centering")
    lines.append(r"\begin{tabular}{lrrrrr}")
    lines.append(r"\toprule")
    lines.append(r"Engine & $N$ & $K_{100,10\mathrm{MiB}}$ (ms) & $\alpha$ (patterns) & $\beta$ (input) & $R^2$ \\")
    lines.append(r"\midrule")
    fit_by_engine = {fit.engine: fit for fit in fits}
    for engine in selected_engines:
        fit = fit_by_engine.get(engine)
        if fit is None:
            lines.append(
                fr"\texttt{{{_latex_escape(engine)}}} & - & - & - & - & - \\"
            )
            continue
        lines.append(
            fr"\texttt{{{_latex_escape(engine)}}} & {fit.samples} & {_fmt_num(fit.k_ref_ms)} & {_fmt_num(fit.alpha_patterns)} & {_fmt_num(fit.beta_input)} & {_fmt_num(fit.r2)} \\"
        )
    lines.append(r"\bottomrule")
    lines.append(r"\end{tabular}")
    lines.append(r"\caption{Per-engine scaling fits over the selected cohort.}")
    lines.append(r"\end{table}")
    lines.append("")
    lines.append(r"\paragraph{Interpretation.}")
    lines.append(
        "A practical dominance heuristic is to compare per-engine effective work terms "
        r"$K_{100,10\mathrm{MiB}}\,P^{\alpha}B^{\beta}$ and select the minimum."
    )
    lines.append("")
    lines.append(r"\section*{2D Dominance Map}")
    x_vals = [float(row["input_bytes"]) for row in scenario_rows if float(row["input_bytes"]) > 0]
    y_vals = [float(row["patterns"]) for row in scenario_rows if float(row["patterns"]) > 0]
    if not x_vals:
        for coords in winner_points.values():
            for x, _ in coords:
                if x > 0:
                    x_vals.append(float(x))
    if not y_vals:
        for coords in winner_points.values():
            for _, y in coords:
                if y > 0:
                    y_vals.append(float(y))
    boundaries: list[dict[str, Any]] = []
    if x_vals and y_vals:
        boundaries = _build_model_boundaries(
            fits,
            x_min=min(x_vals),
            x_max=max(x_vals),
            y_min=min(y_vals),
            y_max=max(y_vals),
        )

    lines.append(r"\begin{figure}[h!]")
    lines.append(r"\centering")
    lines.append(r"\begin{tikzpicture}")
    lines.append(
        r"\begin{loglogaxis}["
        r"width=0.96\linewidth,"
        r"height=0.58\linewidth,"
        r"xlabel={Corpus size (bytes)},"
        r"ylabel={Pattern count},"
        r"grid=both,"
        r"major grid style={draw=gray!30},"
        r"minor grid style={draw=gray!15},"
        r"legend style={at={(0.02,0.98)},anchor=north west,fill=white,draw=gray!40,font=\small},"
        r"]"
    )

    for curve in boundaries:
        pair = str(curve.get("pair", ""))
        segments = curve.get("segments", [])
        if not isinstance(segments, list):
            continue
        first = True
        for seg in segments:
            if not isinstance(seg, list) or len(seg) < 2:
                continue
            coords_txt = " ".join(f"({x},{y})" for x, y in seg)
            if not coords_txt:
                continue
            if first:
                lines.append(
                    r"\addplot+[no marks,dashed,thick,color=black] coordinates {" + coords_txt + "};"
                )
                lines.append(
                    r"\addlegendentry{\texttt{" + _latex_escape(pair) + r"} model}"
                )
                first = False
            else:
                lines.append(
                    r"\addplot+[no marks,dashed,thick,color=black,forget plot] coordinates {" + coords_txt + "};"
                )

    for engine in selected_engines:
        coords = winner_points.get(engine, [])
        if not coords:
            continue
        coords_txt = " ".join(f"({x},{y})" for x, y in coords)
        color_name = f"eng{_latex_escape(engine).replace('-', '')}"
        lines.append(
            fr"\addplot+[only marks,mark=square*,mark size=3pt,color={color_name}] coordinates {{{coords_txt}}};"
        )
        lines.append(fr"\addlegendentry{{\texttt{{{_latex_escape(engine)}}} winner ({len(coords)})}}")
    lines.append(r"\end{loglogaxis}")
    lines.append(r"\end{tikzpicture}")
    lines.append(
        r"\caption{Observed winner per workload point (\(x=\) corpus size, \(y=\) pattern count) for scenarios with at least two selected engines present, with dashed model-equality boundaries (\(T_i=T_j\)).}"
    )
    lines.append(r"\end{figure}")
    lines.append("")
    lines.append(r"\section*{Observed Scenario Winners}")
    lines.append(r"\begin{longtable}{rrrrl}")
    lines.append(r"\toprule")
    lines.append(r"Patterns & Input (MiB) & Winner & Winner ms & Engines present \\")
    lines.append(r"\midrule")
    lines.append(r"\endhead")
    for row in scenario_rows:
        patterns = int(row["patterns"])
        input_mib = float(row["input_mib"])
        winner = str(row["winner"])
        winner_ms = float(row["winner_ms"])
        present = str(row["present"])
        lines.append(
            fr"{patterns} & {_fmt_num(input_mib, digits=1)} & \texttt{{{_latex_escape(winner)}}} & {_fmt_num(winner_ms)} & \texttt{{{_latex_escape(present)}}} \\"
        )
    lines.append(r"\bottomrule")
    lines.append(r"\end{longtable}")
    lines.append("")
    lines.append(r"\end{document}")
    return "\n".join(lines) + "\n"


def generate(
    *,
    input_csv: Path,
    output_tex: Path,
    cohort: str,
    engines: list[str],
) -> dict[str, str]:
    rows = _read_rows(input_csv)
    if not rows:
        raise RuntimeError(f"No rows found in {input_csv}")

    mean_ms_by_key, _weights = _aggregate(rows)
    if not mean_ms_by_key:
        raise RuntimeError("No valid benchmark rows with positive timing values.")

    cohort_scenarios = _build_cohort_scenarios(mean_ms_by_key)
    available_engines = sorted({k[3] for k in mean_ms_by_key.keys()})
    selected_engines = [e for e in engines if e in available_engines]
    if len(selected_engines) < 2:
        selected_engines = available_engines
    if len(selected_engines) < 2:
        raise RuntimeError("Need at least two engines with data to produce a dominance map.")

    chosen_cohort = cohort
    if cohort == "auto":
        chosen_cohort = _pick_cohort(cohort_scenarios, selected_engines)
    if not chosen_cohort:
        raise RuntimeError("Could not select a cohort from the provided CSV.")
    if chosen_cohort not in cohort_scenarios:
        raise RuntimeError(f"Requested cohort '{chosen_cohort}' not found in CSV.")

    scenarios = cohort_scenarios[chosen_cohort]
    scenario_rows = _scenario_rows_for_table(scenarios, selected_engines)
    winner_points = _winner_coordinates(scenarios, selected_engines)

    fits: list[ModelFit] = []
    for engine in selected_engines:
        pts = []
        for (patterns, input_bytes), by_engine in scenarios.items():
            if engine in by_engine:
                pts.append((float(patterns), float(input_bytes), float(by_engine[engine])))
        fit = _fit_log_model(pts, engine)
        if fit is not None:
            fits.append(fit)

    generated_utc = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    tex = _build_tex(
        source_csv=input_csv,
        cohort=chosen_cohort,
        selected_engines=selected_engines,
        fits=fits,
        scenario_rows=scenario_rows,
        winner_points=winner_points,
        generated_utc=generated_utc,
    )

    output_tex.parent.mkdir(parents=True, exist_ok=True)
    output_tex.write_text(tex, encoding="utf-8")
    return {
        "output_tex": str(output_tex),
        "cohort": chosen_cohort,
        "engines": ",".join(selected_engines),
        "model_rows": str(len(fits)),
        "dominance_points": str(sum(len(v) for v in winner_points.values())),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate a small LaTeX paper with scaling model and 2D dominance map.")
    parser.add_argument("--input-csv", required=True, help="Path to all_runs_workload_engine.csv")
    parser.add_argument("--output-tex", required=True, help="Path to output .tex file")
    parser.add_argument("--cohort", default="auto", help="Cohort to analyze (default: auto)")
    parser.add_argument(
        "--engines",
        default="java-native-naive,re2j,rmatch",
        help="Comma-separated engines to include (default: java-native-naive,re2j,rmatch)",
    )
    args = parser.parse_args()

    engines = [part.strip() for part in args.engines.split(",") if part.strip()]
    if not engines:
        raise SystemExit("No engines selected.")

    result = generate(
        input_csv=Path(args.input_csv),
        output_tex=Path(args.output_tex),
        cohort=args.cohort.strip() or "auto",
        engines=engines,
    )
    print("Generated modeling paper LaTeX.")
    print(f"Cohort: {result['cohort']}")
    print(f"Engines: {result['engines']}")
    print(f"Model rows: {result['model_rows']}")
    print(f"Dominance points: {result['dominance_points']}")
    print(f"Output: {result['output_tex']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
