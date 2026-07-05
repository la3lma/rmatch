#!/usr/bin/env python3
"""Compare cascade benchmark CSVs: baseline vs candidate.

Rows: RESULT,variant,impl,nRegexps,corpusChars,iter,phase,match_ms,totalMatches
"""

import statistics
import sys
from collections import defaultdict


def load(path):
    cells = defaultdict(list)  # (impl, n, chars) -> [ms...]
    matches = {}  # (impl, n, chars) -> set of match counts
    with open(path) as f:
        for line in f:
            parts = line.strip().split(",")
            if len(parts) != 9 or parts[0] != "RESULT":
                continue
            _, variant, impl, n, chars, it, phase, ms, total = parts
            key = (impl, int(n), int(chars))
            matches.setdefault(key, set()).add(int(total))
            if phase == "measured":
                cells[key].append(int(ms))
    return cells, matches


def fmt_ms(v):
    return f"{v:>9.0f}"


def main():
    base_cells, base_matches = load(sys.argv[1])
    cand_cells, cand_matches = load(sys.argv[2])

    keys = sorted(set(base_cells) | set(cand_cells))
    print(
        f"{'impl':<8} {'nRegex':>6} {'corpusMB':>8} | {'base med':>9} {'base min':>9}"
        f" | {'cand med':>9} {'cand min':>9} | {'speedup':>7} {'matches':>9}"
    )
    print("-" * 92)
    for k in keys:
        impl, n, chars = k
        b = base_cells.get(k)
        c = cand_cells.get(k)
        bm = statistics.median(b) if b else None
        cm = statistics.median(c) if c else None
        speed = f"{bm / cm:>6.2f}x" if bm and cm else "    n/a"
        # correctness: match counts must agree between variants and be stable
        bset = base_matches.get(k, set())
        cset = cand_matches.get(k, set())
        ok = "OK" if bset and bset == cset and len(bset) == 1 else f"MISMATCH {bset} vs {cset}"
        print(
            f"{impl:<8} {n:>6} {chars / 1e6:>8.1f} |"
            f" {fmt_ms(bm) if bm else '      n/a'} {fmt_ms(min(b)) if b else '      n/a'} |"
            f" {fmt_ms(cm) if cm else '      n/a'} {fmt_ms(min(c)) if c else '      n/a'} |"
            f" {speed} {ok:>9}"
        )


if __name__ == "__main__":
    main()
