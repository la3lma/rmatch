---
name: Performance improvement
about: Propose an optimization with a measurement plan
labels: [perf]
---

### Idea

Describe the proposed optimization and target hot paths.

### Measurement plan

- Micro (JMH): benches to add/run
- Macro: corpus / args
- Profiling: async-profiler/JFR captures to collect

### Acceptance criteria

- % improvement on specific benchmarks without correctness regressions
