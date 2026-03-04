# 2026-03 Repository Cleanup Move Manifest

This file tracks non-destructive moves performed in the 2026-03 cleanup campaign.

## Moves Completed

1. `papers/` -> `docs/papers/`
   - Reason: keep papers under documentation hierarchy as cleanup amendment.
   - Risk: relative path breakage inside LaTeX includes.
   - Mitigation: updated status report LaTeX include path after move.

## Pending Move Groups

Pending groups are tracked in:

- `docs/cleanup/repo-move-manifest-draft.csv`

