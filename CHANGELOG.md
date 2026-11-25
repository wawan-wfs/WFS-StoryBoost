# WFS StoryBoost changelog

## Beta17 repair — 2026-11-25
- CI run 45651350422: build and API35 launch pass; both instrumented jobs fail at Missing dialog button Batal. Both real decoded color/PCM and migration tests pass.
- Repair native action labels/semantics, 48dp touch targets and dialog-root Espresso clicks with staged-state checks. Exact-head CI retest pending.

## Beta17 candidate — 2026-11-25
- Non-destructive per-clip color and mute, schema v2, shared video effects, native timeline sequence strip and staged adjustments.
- Save-before-history mutation; 62 local timeline checks passed; Android test scenarios include decoded PCM mute oracle and staged panel/history.
- Beta16 exact baseline passed four CI jobs; Beta17 Android verification pending.
- No new AI weights, independent audio tracks, release signing, version bump or production claim.

Earlier historical details remain in docs/PROGRESS.md and the individual QA documents.
