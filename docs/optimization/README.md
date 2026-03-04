# Optimization Documentation

This section collects optimization-focused material that was previously mixed into the top-level README.

## GC Optimization (Java 25)

rmatch includes tooling to evaluate different GC configurations for regex-heavy workloads.

Quick entry points:

- Validate available GC options:
  - `scripts/validate_gc_configs.sh`
- Run full GC experiment matrix:
  - `make bench-gc-experiments`
- Run a faster comparison subset:
  - `make bench-gc-experiments-fast`

Primary docs:

- [GC_EXPERIMENTS.md](GC_EXPERIMENTS.md)
- [GC_OPTIMIZATION_RESULTS.md](GC_OPTIMIZATION_RESULTS.md)
- [QUICKSTART_GC.md](QUICKSTART_GC.md)

## Fast-Path and JIT Optimization

Fast-path matcher optimizations and Java 25 JIT tuning are documented with methodology and measured outcomes.

Recommended production starting point:

```bash
export JAVA_OPTS="-Drmatch.engine=fastpath -Drmatch.prefilter=aho -XX:+TieredCompilation -XX:CompileThreshold=500"
```

Primary docs:

- [FASTPATH_PERFORMANCE_ANALYSIS.md](FASTPATH_PERFORMANCE_ANALYSIS.md)
- [FASTPATH_OPTIMIZATION.md](FASTPATH_OPTIMIZATION.md)

## Dispatch Optimization Experiments

Dispatch strategy experiments cover:

- pattern matching `instanceof`
- switch expressions
- enum dispatch and character classification dispatch style

Quick run command:

```bash
make bench-dispatch
```

Primary docs:

- [DISPATCH_OPTIMIZATION_RESULTS.md](DISPATCH_OPTIMIZATION_RESULTS.md)
- [DISPATCH_OPTIMIZATION_GUIDE.md](DISPATCH_OPTIMIZATION_GUIDE.md)

## Notes

- Optimization recommendations are workload-dependent; prefer production-like benchmark scenarios over micro-only conclusions.
- For cross-engine comparisons and current campaign snapshots, use the benchmarking docs in `docs/benchmarking/`.
