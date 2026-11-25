# Beta16 candidate — one continuous preview

This candidate starts from `ccb211fdd2bef7823eb13c0c1d3aace645c7c724` without moving `main` or the Beta15 QA branch. [Beta15's manual verification](https://github.com/wawan-wfs/WFS-StoryBoost/actions/runs/44777681040) applies to that exact parent, not to this preview candidate.

Open **Timeline Studio → Preview Timeline**. Playback is one seekable, private H.264/AAC render of the saved ordered trim sequence with the same composition, scaling, color path and timed caption as export. It is not an instant preview. The wait and temporary disk cost depend on the length, quality and device encoder. The existing individual-clip preview remains available. At an exact cut, the timeline label switches to the next clip. The times identify rendered-timeline positions, not the original file positions. Empty timelines cannot render and a missing clip fails the whole preview rather than stitching around it.

Play, pause, seek and close do not write a Gallery item or modify source media or either draft schema. The preview reader owns its cache MP4 until close or Activity destruction. Cancelling discards render output and callbacks, and recreating the Activity preserves the saved Timeline draft but closes the private player. The screen remains portrait as before.

## Verification

- Host suite checks exact cut, last frame, out of range, reorder and shortest trim boundaries. No encoder/emulator is exercised by the host suite.
- Android tests require the Preview button and seek bar to work on a rendered two-clip fixture, compare decoded preview/export frames before, during and after the caption, and verify the retained draft and source hashes after Activity recreation. The independent deleted-source and immediate cancellation test verifies no private MP4 or success callback is accepted. These are test scenarios, not pass claims before the exact candidate has run on API 29 and 35.
- Screen playback across unusual aspect changes, rapid seek under load, full-HD physical encoder timing, HDR/ARM64, TalkBack and low-RAM acceptance still need measured evidence. Per-clip color controls and independent audio are not added here.

## 2026-11-24 acceptance checkpoint

Beta15 parent `ccb211fdd2bef7823eb13c0c1d3aace645c7c724` passed all four jobs in [run 44777681040](https://github.com/wawan-wfs/WFS-StoryBoost/actions/runs/44777681040). Logs show live PID 9074 → 9166 (API29) and 7142 → 7238 (API35), successful restoration, and separate provider UIDs with revocation/deletion/reimport acceptance. [API29 timeline proof](https://github.com/wawan-wfs/WFS-StoryBoost/actions/runs/44777681040/artifacts/14651413378) and [API35 timeline proof](https://github.com/wawan-wfs/WFS-StoryBoost/actions/runs/44777681040/artifacts/14651626317) are retained. [API29 provider evidence](https://github.com/wawan-wfs/WFS-StoryBoost/actions/runs/44777681040/artifacts/14652681987) and [API35 provider evidence](https://github.com/wawan-wfs/WFS-StoryBoost/actions/runs/44777681040/artifacts/14651161600) are retained. Artifact contents are not independently extracted in this checkpoint; job logs and artifact metadata were inspected.

Beta16 hardening: a cold process prunes only regular UUID-named private preview MP4s, never other exports, directories, symlinks or source media. Backgrounding closes the preview instead of retaining a stale player; reopening renders again. Seek displays pending status until the player settles, and the latest requested position is queued. Logcat tag `WfsTimelinePreviewQA` records renderMs, readyMs, file size, settled positions and startup cleanup counts without source URIs. Added host cache assertions, synthetic orphan recovery over actual force-stop, rapid-seek checks, Gallery row checks and an independent provider revoked-preview test. No real-device results are claimed.

The Beta16 candidate had no Actions run at the checkpoint. The available GitHub connection can inspect runs and retry existing jobs but cannot start a new workflow_dispatch run. The local environment has Java but no Android SDK/Gradle/adb or GitHub network access. Run the candidate once manually before acceptance. No versionCode bump, release signing, main update or per-clip feature expansion.

## 2026-11-24 exact-source evidence gate

At entry, branch head `4d551e3e54b768432c01419533ef0ab5c22d14c4` had zero workflow runs. The connector exposes read/retry, not workflow dispatch; local Java is present but Android SDK, Gradle, adb and GitHub DNS are unavailable. Beta16 is not accepted. Do not move to Beta17 feature code yet.

This checkpoint adds a fail-closed preview evidence validator, a synthetic-emulator-only connected-suite wrapper with exact checked-out SHA/API/ABI, filtered live logcat, JSON summary and CI upload, plus raw instrumentation status checks for orphan-cache recovery and independent-provider revocation. It preserves the original connected suite and all subsequent regression phases. Workflow trigger is now strictly workflow_dispatch on this branch (no PR-open trigger). Artifact labels for legacy APKs remain unchanged; identify them by run head, not label.

Local verification: 16 Python parser tests and 5 mocked wrapper cases passed (success, skipped result, Gradle error, wrong API and wrong SHA). These are synthetic host tests of the harness, not app/encoder/emulator results. The new Android status markers and workflow have not run. No new APK, no main merge, no version bump. See [manual acceptance runbook](BETA16_ACCEPTANCE_RUNBOOK.md).

## Accepted 2026-11-25

Exact head d0b7d435923d3e95673dc762b13dc83ea2a83858: [run 45552840094](https://github.com/wawan-wfs/WFS-StoryBoost/actions/runs/45552840094) 4/4 success. Both required preview JUnit methods passed. API29 render/ready 43489/43964 ms; API35 3937/4312 ms for 2s synthetic timeline. Live PID replacement 9612→9704 and 7522→7612 respectively; both cache-recovery and provider-revocation markers verified. Artifact metadata and log JSON inspected, ZIP contents not independently extracted. HDR capability skips and physical/signing gates remain. No claim of production readiness.
