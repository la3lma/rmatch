# Security Policy

## Supported versions

Security fixes target the newest published release line on Maven Central
(currently `1.9.x`, soon `2.0.x`). Older pre-release lines are not patched.

## Reporting a vulnerability

Please report suspected vulnerabilities privately via
[GitHub security advisories](https://github.com/la3lma/rmatch/security/advisories/new)
rather than public issues.

Relevant classes of problems for a regex library include pathological
pattern or input handling (denial of service through compile-time or
scan-time blowup) and any input that makes the matcher report incorrect
matches. rmatch's engine is automata-based and does not backtrack, but
counted-quantifier expansion is capped (currently at 1000) precisely to
bound compile-time cost — reports that pierce such bounds are very welcome.

You should normally get a first response within a week.
