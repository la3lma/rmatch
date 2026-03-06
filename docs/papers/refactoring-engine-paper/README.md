# refactoring-engine-paper

Short LaTeX note documenting the current optimization loop in this repository:

1. Generate candidate patch
2. Evaluate feasibility
3. Loop through correctness gate + fixes
4. Run fixed 10K/1MB A/B performance gate
5. Keep only candidates with evidence of improvement

It also sketches how this loop can be generalized into an automated high-performance refactoring engine.

## Build

```bash
make pdf
```
