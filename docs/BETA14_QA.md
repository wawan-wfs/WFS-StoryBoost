# Beta14 — independent glyph verification and HDR hardware handoff

Baseline main `cd8ad45535078f80c79876bdde8adb4c11dca378`. The beta7–beta13 open drafts share the ancestry of `5d0a32b3707b1eb5aa892315c80446579cf91cf8` without merging or rewriting them. [Beta13 exact push](https://github.com/wawanwfs/WFS-StoryBoost/actions/runs/44344900084) passed four jobs. Connected reports showed API29 25 passed and API35 34 passed, no failures, with HDR transcode capability skips. Beta13 APK metadata was `com.wfs.storyboost.studio`, versionCode 34, versionName 3.0.0-beta3. The [beta13 PR](https://github.com/wawanwfs/WFS-StoryBoost/actions/runs/44344917965/attempts/2) still had a later Pixel Launcher ANR in native DocumentsUI on API35. The separate push run reached and passed that gate.

## New gates

- Real decoded JPEG and H.264 frames must match a manually ordered directional-run template made without the production caption renderer. The test compares Hebrew–Latin–Arabic order against a reversed control, Arabic joining against isolated glyphs, decomposed accents against bare letters, and color emoji placement against a baseline reference. Media files are synthetic and SHA-checked.
- HDR fixture automation logs one result per HLG/PQ transfer, using a fresh per-invocation marker. It records the decoder/GL prerequisite, actual transfer metadata, sampled gradient, decoded/preview caption bounds, AAC/video samples, PTS drift and synthetic input SHA. A capability skip is not a passing export. Use the [owner-operated hardware instructions](PHYSICAL_ARM64_QA_WINDOWS.md) for an actual phone.
- The Studio Export panel labels known HDR input and explains that an edited result targets SDR only if the device can convert it. Byte-preserving HDR is the eligible unedited Quick Enhance MP4 route, not an edited HDR encoder.
- The DocumentsUI retry is CI-only and emulator-only, once, on the reported Pixel Launcher ANR before a picker opens. It still requires actual DocumentsUI selection, the persisted grant, external force-stop and Recent restoration. Arbitrary failures are not retried or hidden.
- The project still has no physical HDR-to-SDR evidence, owner-consented portrait evidence, ARM64 thermal/LMK measurements, manual TalkBack, actual Google Photos URI evidence, or owner-signed upgrade proof in this workspace.

## CapCut mobile comparison

Current StoryBoost code/test coverage provides single-source framing, crop/fit, trim/split, separate merge, static text and social profiles. The exporter checks geometry and stream integrity, not quality after a third-party upload. [CapCut’s Android listing](https://play.google.com/store/apps/details?gl=ID&id=com.lemon.lvoverseas) describes multitrack editing, keyframes and auto captions. Those are gaps, not capabilities implied by the current StoryBoost UI. Start with a persistent multitrack timeline plus per-clip trims and time-ranged text. Keyframing and speech captions should follow after a deterministic export and undo model exist.

23/32 verified modules (71.875%) remain the roadmap baseline. Find exact-head evidence in the draft PR.
