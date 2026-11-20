# Beta15 — Timeline Studio, one track

The migrated public project is [wawan-wfs/WFS-StoryBoost](https://github.com/wawan-wfs/WFS-StoryBoost). Beta15 starts from the migrated QA branch `6af7992b34d3faaafc315c2fc5612827cfba06ac`, whose source tree differs from the original verified `c9da749ad3e46fd5a51a712b8df45615745705bc` only in the Actions trigger policy. The new main at `618c7a01ea61dee065b011d8ec9981fbea767d1e` contains the older recovery snapshot. No force push, migration overwrite, main update or actions-on-push was performed.

## Workflow

- From Home, open **Timeline Studio**. Add up to ten videos using the Android file picker. Add the same video again with a different trim when needed. Each entry has a stable ID. Source permissions must persist to add clips.
- Select a clip, tap **Trim klip**, and enter source-time milliseconds. Up/down moves change order; **Undo** / **Redo** retain changes while the activity is open. They are intentionally session-local. The current state, order, trim, output quality and caption are saved in one private draft slot after each edit. Reopen via the same tile.
- **Caption waktu** accepts one caption, using timeline-time milliseconds and an inclusive start/exclusive end. If deleting or shrinking clips would leave a caption beyond the timeline, the end clamps and a wholly out-of-range caption clears. Preview is for the selected source window, not yet continuous playback or a rendered caption preview.
- A single-source Video Editor can import the current video, trim, and caption into an empty Timeline via **Social Export → Buka di Timeline Studio**. The versioned timeline record is separate. It does not alter single-source Recent. If a timeline already exists it is reopened untouched. Single-source filters, muted audio, split, AI and framing options are not imported.
- **Ekspor Timeline MP4** uses a normalized canvas derived from the first ordered clip, H.264/AAC where sound exists, and time-based caption compositing. A lost source fails rather than writing a partial playlist. The existing cancellable Gallery transaction and immutable source URIs are reused.
- This is one sequence, not CapCut parity. No independent audio layers, per-clip transitions, scrubbable composition preview, keyframes, automatic subtitles, or clip-level color adjustments yet.

## Evidence gates

Host tests check immutable model states, repeated media, stable IDs, validation, history and caption edges. Android QA covers actual decoded 2-clip MP4 order, luma marker trims, caption before/in/after window across a clip boundary, compressed A/V PTS and unchanged sources. A separate run force-stops the app with adb, verifies a new PID and restores trim/order/caption without altering the old single-source draft. Gallery cancellation and independent provider grant revocation remain hard gates. The beta14 JPEG/MP4 glyph oracle remains enabled.

The [matched beta14 ARM64 app/test bundle](https://github.com/wawan-wfs/WFS-StoryBoost/actions/runs/44755959960/artifacts/14639971804) was rebuilt and verified from the migrated beta14 source. It is an earlier debug bundle, not a new build or proof of HDR hardware support. If the old artifact is unavailable, use the [build and hardware runbook](PHYSICAL_ARM64_QA_WINDOWS.md) on a compatible spare device. Both emulator HDR conversions previously skipped because Main10 and GL support were missing. Physical HDR HLG/PQ, low-RAM ARM64 measurements, consented portrait/low-light inspection, manual TalkBack, actual Google Photos and an owner-signed two-version upgrade require the owner's devices or samples.

Report committed head and exact-branch workflow evidence separately. Do not count a newly implemented module as verified until both emulator jobs and decoded media checks pass.
