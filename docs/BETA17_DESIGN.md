# Beta17 large batch design — blocked until Beta16 exact-head acceptance

This is a design and test plan, not implemented functionality. Retain one-track semantics and all existing photo/video/AI behavior. The following scope starts only after both Android APIs pass Beta16 acceptance.

## P0: non-destructive clip adjustments
- Extend immutable Clip with validated color look, bounded brightness/contrast/saturation and mute. Existing IDs, URI and in/out remain stable through trim/reorder and history. Neutral defaults reproduce v1 behavior.
- Read v1 and v2 schema explicitly; preserve the raw old document until a valid changed snapshot is committed; unknown schema or malformed draft must not silently reset. Bound strings/number ranges and reject nonfinite data. Save only after validated edits. Document rollback limitations; keep single-source drafts separate.
- Use one per-clip effect factory in the Media3 composition path for preview and export. Verify real decoded video color patches, independent clip settings, preserved caption geometry and audio presence/silence; ensure mixed muted/nonmuted clips do not break A/V timing or the all-silent case.
- Add reset-to-neutral per clip and history-aware edits; cancellation/discard must not persist tentative slider values. Test reorder, trim, repeated source, process death, corrupted/unsupported schema, failed save and exact old source SHA.

## P1: premium native editor UX
- One coherent timeline strip with time ruler, selected clip, cut indicators, duration, accessible controls and playhead. No misleading instant preview or multi-track claim. Keep existing clips, import, reorder, caption, export, and direct single-source editor entry.
- Native bottom tool panel for adjust/look/audio and clear confirm/reset/undo; test 320x640 and 1080x2400 actual viewport layout, TalkBack semantics, focus, touch and small-screen usability. Use latest-snapshot rendering, cancel on exit, no Gallery side effects from preview.
- Verify visual preview/export parity on API29/35 for neutral, individual adjustments, combined adjustments, repeated source clips with different settings, timed caption across cut and source rotation/mixed ratios.

## P2: export, AI and performance
- Record dimensions, video/audio MIME, sample timestamps, duration, color, clipping, source hashes and cancellation result. Avoid fake upscaling or guaranteed lossless social upload. Preserve current DnCNN and portrait paths and pin current models.
- Benchmark cold/warm synthetic 2/10/30 second timeline render, readiness, peak sampled PSS and failure/cancellation. Distinguish emulator from hardware. Consider reduced-resolution interactive preview only after a separate explicit parity/quality gate; no claim of identical frames if resolution differs.
- Investigate AI deblur/SR as distinct model evaluation gates: license, pinned hash, offline inference, calibrated PSNR/SSIM, identity preservation, memory/thermal and real consented images. No new model activated without evidence.
- Independent audio tracks, transitions and keyframes remain separate measured feature gates. Do not claim CapCut parity based on the single-track redesign.

## Release discipline
One large substantive batch, complete source, exact commit and manual Actions run, actual artifacts, regression ledger and documented blockers. No force push or automated CI on routine pushes. Keep 23/32 roadmap until independent acceptance proves a new module. Stable release signing and physical QA require owner.
