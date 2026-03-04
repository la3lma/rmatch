Structured benchmark plan for rmatch
0) Goal and claims (write these down first)
Primary claim: throughput advantage vs Java java.util.regex when matching large pattern sets over large corpora.
Secondary claims (optional): lower tail latency, better scaling in #regexes, predictable performance, acceptable memory tradeoffs.
Define success metrics up front:
Throughput: GB/s and/or MB/s
Cost: CPU-seconds per GB, core-hours per TB
Match output: correctness + match counts + optional match positions
Memory: peak RSS, compiled structure size, GC pressure
1) Test matrix (what you vary)
Axes you should sweep (systematically):
#regexes: 10, 100, 1k, 10k, (maybe 50k)
Input size: 10MB, 100MB, 1GB, (optionally 10GB)
Regex “difficulty” (classes below)
Match density: sparse vs dense matches
Input type: ASCII log-like vs UTF-8 natural language vs “binary-ish” bytes (if relevant)
Pattern update frequency: compile once vs rebuild per batch (if your use-case supports it)
Keep this matrix big in design, but run it in tiers so it’s feasible.
2) Pattern sets (make them realistic + controlled)
Create several pattern suites, each with a generator + a fixed seed so it’s reproducible.
Suite A — “Log mining”
Anchored-ish patterns, timestamps, ids, IPs, URLs, error codes, key=value, etc.
Suite B — “Security signatures”
Lots of alternations, prefixes, substrings; avoid exotic features if you want Hyperscan comparison later.
Suite C — “Pathological/backtracking stress”
Patterns known to hurt backtracking engines (nested quantifiers etc.)
Label this suite clearly as “stress”, not “typical”.
Suite D — “Real-world harvested”
Curated patterns from open rule sets (e.g., generic log parsers, IDS-like rules, public regex lists). De-duplicate and sanitize.
For each suite, record:
Feature usage (groups, alternation, lookaround, backrefs)
Average length, max length
Expected match rate (estimate)
3) Corpora (inputs you scan)
Use 3–4 corpora types:
Synthetic controllable corpus
You can tune match density and distribution.
Real logs
Web server logs, application logs (public datasets or your own anonymized).
Natural language
Wikipedia dumps / books text (UTF-8 heavy).
Source code
Large codebase text (identifiers, symbols, uneven line lengths).
For each corpus:
Fix a version hash / source
Provide a script that downloads or generates it
Ensure licensing is clear
4) Competitors and configurations
At minimum:
Java java.util.regex
Two modes:
naïve loop: compile each pattern then scan input per pattern
“best effort” Java approach (e.g., precompile Patterns; reuse Matchers where possible)
rmatch (your engine)
Strongly recommended (if feasible):
RE2J (Google’s non-backtracking engine)
Hyperscan (later tier; or separate “native baseline” tier)
For each engine, document:
Compile step cost
Scan API usage pattern
Whether it supports your pattern suite fully
5) Benchmark harness (reproducible, auditable)
One harness repo layout (suggestion):
bench/ runner (Java, JMH-based for micro + your own driver for large streaming)
patterns/ pattern suite generators + frozen suites
corpora/ download/generate scripts (don’t commit huge files)
results/ raw JSON/CSV + plots
docs/ methodology + machine specs
Two kinds of benchmarks:
Microbench (JMH)
Measure compile time, per-chunk scan time, overheads, small inputs.
Macrobench (streaming driver)
Large files, OS page cache effects, realistic IO modes.
6) Measurement protocol (to avoid self-inflicted doubt)
Machine discipline
Dedicated machine (or quiet single-tenant VM)
Record:
CPU model, cores, RAM, OS, kernel
Java version, flags, GC
Fix CPU governor (performance), pin cores if possible
Warmup & runs
Warmup: discard first N runs
Do ≥ 10 measured repetitions per cell (more for noisy environments)
Report mean + stddev + median + p95
IO control
Run in two modes:
In-memory mode (read file into byte[]/String once) → pure scan throughput
Streaming mode (mmap or buffered stream) → end-to-end throughput
Correctness gate
Before timing, validate each engine’s matches against a reference (for suites where semantics overlap)
If semantics differ, define “common subset” and benchmark that subset for apples-to-apples
7) Outputs and KPIs (what you publish)
For each test cell, store:
Throughput (MB/s)
Compile time (ms)
Total scan time (ms)
Peak RSS (MB) + GC stats (for JVM)
Match count (and optionally a hash of match positions) for correctness
Primary plots
Throughput vs #regexes (log scale)
Throughput vs input size
Speedup heatmap (rmatch / java.util.regex)
Memory vs #regexes
Compile time vs #regexes
8) Fairness rules (write these explicitly)
Use “best reasonable” configurations for each engine.
Separate compile and scan unless your use-case requires frequent recompiles (then show both).
Don’t compare engines on pattern suites they can’t support; instead:
(a) common subset comparison, and
(b) “full Java semantics” comparison (Java engines only)
9) Phased execution plan (so you ship results steadily)
Phase 1 (2–3 suites, 2 corpora, 3 regex-count points)
Establish: does the 10× effect replicate?
Produce first credible charts
Phase 2 (expand matrix + RE2J)
Show scaling curves and memory/compile tradeoffs
Phase 3 (Hyperscan baseline + paper-ready methodology)
Add native baseline and “where each engine wins”
10) Deliverables checklist
README_bench.md with exact commands
make bench that produces:
raw CSV/JSON
plots (PNG/SVG)
a short “benchmark report” markdown (auto-generated)
A “methods” section you could paste into a paper/blog post
A CI sanity benchmark (tiny) to prevent regressions