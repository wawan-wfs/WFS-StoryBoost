# Beta17 — per-clip editing candidate (runtime gate failed; fix pending retest)

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

## Local evidence

62 pure-Java timeline assertions passed on 2026-11-25 (40 retained, 22 added). Exact Git blob SHA-1 for TimelineProject.java 13fa46acade5e978a01529334eceb7e4469a09e8, ClipAdjustments.java 1de328029e90b8ac77ee518123dbbd7942924c85, ColorLooks.java dd3012ce4deb8572ef35cb5c74ba95db8710cfad, TimelineProjectTest.java 1116f932b473c25805967c5125d39f1288467c8f matched local git hash-object before execution. No Android compilation, encoder or screenshots for this candidate. The other baseline suites were verified on Beta16, not rerun on Beta17 in this session.

## Open gates
Run manually on API29/35. Check actual new tests and all existing preview, provider, process-death, HDR capability, photo/AI, viewport and cancel regressions. Inspect the new decoded PCM muted-window vs audible-window test and staged cancel/apply/reset/undo/redo/recreate UI automation; add dedicated small and large viewport coverage, real portrait quality and hardware evidence. Keep roadmap 23/32 until independent module acceptance. No main promotion.

## Actual run 45651350422 — 2026-11-25

Exact source bb8bda67046e80310752fa0ba71990f3812952b7: [workflow](https://github.com/wawan-wfs/WFS-StoryBoost/actions/runs/45651350422), 2/4 jobs pass. Build 254 host checks and debug APKs pass; API35 install/launch and screenshot pass. Downloaded and CRC-checked both JUnit ZIPs, both preview evidence ZIPs, and screenshot ZIP. XML testcase counts API29 32 passed/1 failed/18 skipped; API35 41 passed/1 failed/18 skipped. Both failed only timelineAdjustmentPanelStagesCancelApplyAndReset, AssertionError Missing dialog button Batal. Exact cause of the missing text is not proven (native all-caps/theme is a likely explanation). Both legacySchemaReadOnlyAndV2RoundTrip, baseline preview, source cancellation and timelineAdjustedRepeatedClipRendersColorAndMute passed. Actual decoded PCM muted RMS 0.0 on both preview/export; audible RMS API29 0.08778415992462482 and API35 0.08792614431994573 on both paths. Two-second preview render/ready API29 43492/43948ms and API35 5296/5923ms. No speedup claim. Connected wrapper exit 1, no summary and later force-stop/provider phases did not run. HDR capabilities skipped; stable signing skipped.

## Repair candidate

Native Cancel/Apply buttons retain framework IDs, use explicit accessibility descriptions and non-transformed labels. 48dp touch targets for mute/reset/actions. Instrumentation uses dialog-root scoped Espresso real clicks by ID plus content description, scrolls the real editor and dialog, checks staged changes before cancel/apply and reset, undo/redo/recreation and source hash. The prior case-sensitive label lookup is removed, not the acceptance method or assertions. No new version or features. Repair must pass exact-head CI; no runtime evidence claimed for it yet.
