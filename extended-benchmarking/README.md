# Extended benchmarking harness

This directory contains a portable benchmarking harness for comparing `rmatch` against other popular regular expression engines such as `java.util.regex`, RE2J, and Hyperscan.

The harness is intentionally generic: engines are described in a small JSON config file, corpora and pattern sets live in dedicated folders, and a single Python runner orchestrates the workloads. Engines can be added or removed by editing configuration without touching the driver code.

## Layout

- `configs/` – engine definitions. The sample config shows how to wire in Python's `re` module, `java.util.regex`, and placeholders for RE2J, Hyperscan, and other engines.
- `corpora/` – input text corpora. A small sample corpus is provided; add larger datasets here for real benchmarks.
- `patterns/` – pattern files. Each non-empty line is treated as a pattern.
- `results/` – output directory for CSV/JSON summaries written by the runner.
- `scripts/` – helper scripts used by engine definitions (for example a small `java.util.regex` runner).
- `runner.py` – the orchestrator that executes each engine against the selected corpus and pattern set.

## Quickstart

1. Ensure Python 3.10+ and a JDK (for the Java baseline) are available.
2. Run the sample benchmark:

   ```bash
   python extended-benchmarking/runner.py \
     --patterns extended-benchmarking/patterns/sample_patterns.txt \
     --corpus extended-benchmarking/corpora/sample_corpus.txt \
     --engines extended-benchmarking/configs/example_engines.json \
     --output-dir extended-benchmarking/results/sample
   ```

   The command produces `summary.csv` and `summary.json` in the output directory and logs per-engine timing to stdout.

## Adding engines

- **`python_re` (built-in):** Uses Python's `re` module. No additional setup required.
- **`external` engines:** Described by a command array in the config. The runner injects the corpus and pattern paths using `{corpus}` and `{patterns}` placeholders. Optional `requires` entries ensure the command is only invoked when required binaries are available.
- **RE2J and rmatch:** Provide a CLI entry point (for example a `java -jar ...` command) and add it to `configs/example_engines.json`. The runner will handle timing and result collection.
- **Hyperscan:** Point an `external` engine at a small wrapper binary or script that emits `MATCHES=<n>` (see the Java example). This keeps the harness language-agnostic.

## Output format

The runner emits one row per engine/iteration to `summary.csv` with the following columns:

- `engine` – engine name from the config
- `iteration` – zero-based iteration index
- `status` – `ok` or `skipped`
- `wall_time_ms` – measured wall-clock time
- `match_count` – total matches reported (if available)
- `notes` – skip reason or command output summary

A `summary.json` file mirrors the same data for easy ingestion by downstream tooling.

## Extending corpora and patterns

Populate `corpora/` with representative text files (logs, JSON, HTML, etc.) and add corresponding pattern sets in `patterns/`. Keep filenames descriptive so benchmark runs can be traced back to the workloads they represent.
