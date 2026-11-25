# Beta17 — per-clip editing candidate (unverified Android)

Baseline Beta16 d0b7d435923d3e95673dc762b13dc83ea2a83858 accepted in run 45552840094. New independent branch, no main changes or automatic workflows.

## Implemented
- Immutable integer percent clip adjustments, 24 existing Color Looks, brightness/contrast/saturation and mute; reset is staged, apply one history transaction; trim/reorder preserve stable IDs and effects. Undo/redo saves target before altering history.
- Read-only v1 loading with neutral defaults; v2 writes adjustments, rejects invalid integer/non-boolean or unsupported versions. Older app cannot read v2; do not downgrade without a retained backup. Existing single-source draft remains separate; its migration behavior unchanged.
- Shared VideoColorEffects uses the existing single-video formula (brightness is RGB gain, not exposure). No extra AI model or fake super resolution. Rendered timeline preview/export use the same composition path; raw selected-source player reflects mute only, not color. Label is explicit in dialog.
- Native scrollable sequence strip with bounded clip widths, start times, selection, position label and accessible buttons; color/audio staged panel and reset. No drag reorder, multitrack or new transition claim.
- Android QA for v1/v2, per-clip adjusted repeated source pixel difference, preview/export parity, mixed audio track and all-muted AAC absence, force-stop v2 restore. The new PCM16 decoder oracle requires both interior windows to have real samples, audible RMS > 0.015 and muted RMS < 10% of audible RMS for both preview and export. This test has not run.
- Existing preview evidence gate extended to require adjusted-render method; existing all-suite regression remains. No version bump or release signing. Build and screenshot for this branch not yet available.

## Performance and AI
API29 43.489s vs API35 3.937s for 2s baseline rendering, single emulator samples, no claim of speedup. Adjustments stage without encoding on every slider movement; only explicit Preview/Export encodes. Need 2/10/30s cold/warm runs, PSS/thermal and physical tests. DnCNN and portrait models preserved, no deblur/SR activation or real-photo quality claim.

## Open gates
Run manually on API29/35. Check actual new tests and all existing preview, provider, process-death, HDR capability, photo/AI, viewport and cancel regressions. Inspect the new decoded PCM muted-window vs audible-window test; add UI staged cancel/reset/apply automation at small and large viewports, real portrait quality and hardware evidence. Keep roadmap 23/32 until independent module acceptance. No main promotion.
