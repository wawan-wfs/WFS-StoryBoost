# WFS StoryBoost — Next Execution Prompt v3.0 beta16

Use the public wawan-wfs/WFS-StoryBoost repository and the exact beta15 QA head. Read [Beta15 QA](BETA15_QA.md) and [the physical Windows runbook](PHYSICAL_ARM64_QA_WINDOWS.md). Preserve the one-track draft, original single-source Recent, URI grants, original media bytes, package, bundled presets, pinned DnCNN, and verified Arabic/Hebrew/emoji caption tests. Keep Actions off for routine push and PR updates. No force pushes.

1. Close any outstanding exact-head beta15 Android QA with one on-demand CI run or an equivalent API29/API35 device run, including decoded cut/caption marks, sample PTS, actual adb process death, deleted/revoked URI, cancellation and source SHA. Do not treat a build or host test as the Android gate.
2. Extend the one-track workflow to a continuous, seekable preview tied to the rendered clip and timed captions. Keep the immutable ordered model and define time bookkeeping at clip boundaries. Compare preview and exported frames before, inside and after caption intervals. Test empty/gapped input, orientation changes, fast swaps and cancelled render on both APIs.
3. Add reversible per-clip adjustments and a later independent audio track only after single-track playback/export stability. Persist schema migrations non-destructively, keep existing files in place, and measure performance and accessibility rather than equating the UI with CapCut.
4. Complete the owner-assisted physical gates on a compatible Android 15 ARM64 and low-RAM Android 10 where applicable. Confirm actual HLG/PQ tone mapping, memory/thermal, consented portraits, TalkBack, Google Photos and signed upgrade without uploading private inputs or credentials.

Report exact commits, CI, APKs, verified module count, and evidence gaps. Include the next prompt.
