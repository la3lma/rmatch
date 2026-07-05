#!/usr/bin/env python3
"""Charts for the cross-engine grid sweep.

Figure 1 (as requested): per-engine panels, X = corpus size (MB), Y = median
scan time (ms, log), colored series per regexp-set size.

Figure 2 (bonus): engines head-to-head at 10k patterns, colors = engines.
"""

import statistics
import sys
from collections import defaultdict

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

ENGINE_LABELS = {
    "rmatch-main": "rmatch (main)",
    "rmatch-twochar": "rmatch (two-char branch)",
    "javanative": "java.util.regex loop",
    "re2j": "RE2J loop",
}
SIZE_COLORS = {1000: "tab:blue", 5000: "tab:orange", 10000: "tab:red"}


def load(path):
    # (engineKey, n, corpusMB) -> [ms...]
    cells = defaultdict(list)
    for line in open(path):
        parts = line.strip().split(",")
        if len(parts) != 9 or parts[0] != "RESULT" or parts[6] != "measured":
            continue
        _, variant, impl, n, chars, _it, _phase, ms, _total = parts
        engine = variant if variant.startswith("rmatch") else impl
        cells[(engine, int(n), int(chars) / 1e6)].append(int(ms))
    return {k: statistics.median(v) for k, v in cells.items()}


def main():
    med = load(sys.argv[1])
    out_prefix = sys.argv[2]

    engines = ["rmatch-main", "rmatch-twochar", "javanative", "re2j"]
    sizes = sorted({k[1] for k in med})
    all_y = [v for v in med.values()]

    # ---- Figure 1: panel per engine, colored by regexp-set size
    fig, axes = plt.subplots(2, 2, figsize=(11, 8), sharex=True, sharey=True)
    for ax, eng in zip(axes.flat, engines):
        for n in sizes:
            pts = sorted(
                (mb, ms) for (e, nn, mb), ms in med.items() if e == eng and nn == n
            )
            if pts:
                ax.plot(
                    [p[0] for p in pts],
                    [p[1] for p in pts],
                    marker="o",
                    color=SIZE_COLORS.get(n, "gray"),
                    label=f"{n:,} regexps",
                )
        ax.set_title(ENGINE_LABELS.get(eng, eng))
        ax.set_xscale("log")
        ax.set_yscale("log")
        ax.set_ylim(min(all_y) * 0.5, max(all_y) * 2)
        ax.grid(True, which="both", alpha=0.3)
    for ax in axes[-1]:
        ax.set_xlabel("corpus size (MB)")
    for ax in axes[:, 0]:
        ax.set_ylabel("median scan time (ms)")
    handles, labels = axes.flat[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="upper center", ncol=3, frameon=False, bbox_to_anchor=(0.5, 0.93))
    fig.suptitle(
        "Multi-pattern scan time vs corpus size (agogo, 32-thread x86)\n"
        "word-list patterns over Wuthering Heights; loop engines = one scan per pattern",
        y=0.995,
        fontsize=10,
    )
    fig.tight_layout(rect=(0, 0, 1, 0.86))
    f1 = f"{out_prefix}_per_engine.png"
    fig.savefig(f1, dpi=140)
    print(f1)

    # ---- Figure 2: engines head-to-head at the largest pattern count
    nmax = max(sizes)
    fig2, ax = plt.subplots(figsize=(8, 6))
    eng_colors = {
        "rmatch-main": "tab:gray",
        "rmatch-twochar": "tab:green",
        "javanative": "tab:purple",
        "re2j": "tab:brown",
    }
    for eng in engines:
        pts = sorted(
            (mb, ms) for (e, nn, mb), ms in med.items() if e == eng and nn == nmax
        )
        if pts:
            ax.plot(
                [p[0] for p in pts],
                [p[1] for p in pts],
                marker="o",
                color=eng_colors[eng],
                label=ENGINE_LABELS.get(eng, eng),
            )
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_xlabel("corpus size (MB)")
    ax.set_ylabel("median scan time (ms)")
    ax.set_title(f"Engines head-to-head at {nmax:,} regexps (agogo)")
    ax.grid(True, which="both", alpha=0.3)
    ax.legend()
    fig2.tight_layout()
    f2 = f"{out_prefix}_head_to_head.png"
    fig2.savefig(f2, dpi=140)
    print(f2)


if __name__ == "__main__":
    main()
