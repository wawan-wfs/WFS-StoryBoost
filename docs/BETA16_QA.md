# Beta16 candidate — one continuous preview

This candidate starts from `ccb211fdd2bef7823eb13c0c1d3aace645c7c724` without moving `main` or the Beta15 QA branch. [Beta15's manual verification](https://github.com/wawan-wfs/WFS-StoryBoost/actions/runs/44777681040) applies to that exact parent, not to this preview candidate.

Open **Timeline Studio → Preview Timeline**. Playback is one seekable, private H.264/AAC render of the saved ordered trim sequence with the same composition, scaling, color path and timed caption as export. It is not an instant preview. The wait and temporary disk cost depend on the length, quality and device encoder. The existing individual-clip preview remains available. At an exact cut, the timeline label switches to the next clip. The times identify rendered-timeline positions, not the original file positions. Empty timelines cannot render and a missing clip fails the whole preview rather than stitching around it.

Play, pause, seek and close do not write a Gallery item or modify source media or either draft schema. The preview reader owns its cache MP4 until close or Activity destruction. Cancelling discards render output and callbacks, and recreating the Activity preserves the saved Timeline draft but closes the private player. The screen remains portrait as before.

## Verification

- Host suite checks exact cut, last frame, out of range, reorder and shortest trim boundaries. No encoder/emulator is exercised by the host suite.
- Android tests require the Preview button and seek bar to work on a rendered two-clip fixture, compare decoded preview/export frames before, during and after the caption, and verify the retained draft and source hashes after Activity recreation. The independent deleted-source and immediate cancellation test verifies no private MP4 or success callback is accepted. These are test scenarios, not pass claims before the exact candidate has run on API 29 and 35.
- Screen playback across unusual aspect changes, rapid seek under load, full-HD physical encoder timing, HDR/ARM64, TalkBack and low-RAM acceptance still need measured evidence. Per-clip color controls and independent audio are not added here.
