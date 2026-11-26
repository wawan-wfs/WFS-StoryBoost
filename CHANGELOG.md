# WFS StoryBoost changelog

## Beta20 integrated candidate — 2026-11-26
- Actual Beta19 failure on both APIs; strengthen dual-track PCM oracle with separate 440/780 Hz inputs, phase-independent spectral checks and pre-assert diagnostics, preserve RMS threshold.
- Full-range waveform up to 120s; distributed sampled overview for longer ranges, global time alignment; zoom keyframes 50–200% and selective v5 draft storage.
- 50 local tone assertions pass; 20s late-tone fixture verified. Added real host crossfade video/audio reference and mandatory CI oracle, not an Android compositor. Android pixel/PCM/migration tests pending exact-head CI; no neural model activation claim.

## Beta19 integrated candidate — 2026-11-26
- Beta18 accepted 4/4; adds two independent audio lanes, bounded real PCM waveform, local drag reorder, RGB gain keyframes and selective v4 drafts.
- Added exact media/runtime, storage, UI edit and v4 force-stop tests; new Android acceptance pending. Crossfade and neural model activation remain open.

## Beta18 integrated candidate — 2026-11-26
- Timeline scrub, zoom, split and duplicate; separate soundtrack, source trim, start offset, gain and dip-to-black transitions; v3 selective draft schema; shared rendered preview/export and source-aware bitrate.
- Added bounded asynchronous thumbnail loading, keyboard scrub, host assertions, real interactive UI, v3 process-death and Android PCM/pixel/schema scenarios with mandatory evidence parser. 140 exact-source host assertions and 3 synthetic restoration harness tests pass locally; 8 synthetic reference cases generated. Runtime pending exact-head final batch CI. No AI model activation, crossfade or release claim.

## Beta17 repair CI activation — 2026-11-26
- Owner-authorized push CI on Beta17 QA and Beta18 QA branches only; manual dispatch retained, concurrency per ref without cancelling active runs.
- Stable release signing remains manual-only; no deployment or acceptance changes. Repair runtime acceptance still pending.

## Beta17 repair — 2026-11-25
- CI run 45651350422: build and API35 launch pass; both instrumented jobs fail at Missing dialog button Batal. Both real decoded color/PCM and migration tests pass.
- Repair native action labels/semantics, 48dp touch targets and dialog-root Espresso clicks with staged-state checks. Exact-head CI retest pending.

## Beta17 candidate — 2026-11-25
- Non-destructive per-clip color and mute, schema v2, shared video effects, native timeline sequence strip and staged adjustments.
- Save-before-history mutation; 62 local timeline checks passed; Android test scenarios include decoded PCM mute oracle and staged panel/history.
- Beta16 exact baseline passed four CI jobs; Beta17 Android verification pending.
- No new AI weights, independent audio tracks, release signing, version bump or production claim.

Earlier historical details remain in docs/PROGRESS.md and the individual QA documents.
