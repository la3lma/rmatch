# Legacy Front-Page Notes

This content was moved out of the top-level `README.md` to keep the front page focused on current comparable benchmark results.

---

rmatch

The project is getting closer to a state where it may be useful for others
than myself, but it's not quite there yet.  Be patient ;)


### Key Performance Metrics

- **Benchmark Data Sources**: `benchmarking/results/` and `benchmarking/framework/regex_bench_framework/results/`
- **JMH Microbenchmarks**: Precise timing measurements with statistical confidence intervals  
- **Macro Benchmarks**: End-to-end performance testing with real workloads
- **Automated Tracking**: Performance evolution tracked continuously via GitHub Actions



[![codebeat badge](https://codebeat.co/badges/0a25fe03-4371-4c5f-a125-ab524f477898)](https://codebeat.co/projects/github-com-la3lma-rmatch-master)

[![Maintainability](https://api.codeclimate.com/v1/badges/ecfba15253e7095438fb/maintainability)](https://codeclimate.com/repos/64a2ba4d1c8c821c92003b52/maintainability)

[![Test Coverage](https://api.codeclimate.com/v1/badges/ecfba15253e7095438fb/test_coverage)](https://codeclimate.com/repos/64a2ba4d1c8c821c92003b52/test_coverage)

---

## Performance Evaluation Practice

Performance work is measured with production-like workloads and compared on matched hardware cohorts.
Performance checks are maintained as benchmark reports and campaigns (including GCP runs), while merge gating is currently focused on compile + smoke correctness.

---

## Optimization Docs

Detailed optimization experiment documentation has moved out of the top-level README.
See:

- [Optimization index](../optimization/README.md)
- [GC experiments](../optimization/GC_EXPERIMENTS.md)
- [Fast-path and JIT analysis](../optimization/FASTPATH_PERFORMANCE_ANALYSIS.md)
- [Dispatch optimization results](../optimization/DISPATCH_OPTIMIZATION_RESULTS.md)
