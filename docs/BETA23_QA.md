# Beta23 integrated candidate

Base c62579af920faba8d5e1529a1c84061bf5fde37b. Run 45944921050 build and smoke pass; both instrumentation jobs still running during development. No actual new failure known at that checkpoint. Preserve accepted Beta20 and Beta21 actual decoded crossfade proof. Do not mark Beta22 or Beta23 accepted without exact-head 4/4.

## Implemented
- Per-adjacent-cut crossfade override (0–1000ms) using ordered stable IDs; effective overlap clamped to half of either clip. Mixed hard cut/crossfade, trim/duplicate/remove/reorder, orphan override removal, undo restoration, global transition resets overrides, no simultaneous global dip plus override. Selective v8 storage, strict validation, v1–v7 readers preserved. Explicit staged native dialog. Arbitrary per-cut dip is not implemented.
- PiP endpoint motion modes linear, ease-in, ease-out and smoothstep. Nonlinear trim preserves original timing domain and target; editing trim/target/curve resets domain. v8 stores curve and motion duration; v7 remains readable. PiP still one muted overlay; HDR not supported. Combined crossfade + PiP uses existing three video sequence path.
- Proportional timeline overview can scrub by touch with one committed seek on release; native seekbar remains accessible. Home descriptions updated. Not a full independent redesign.
- Required Android test for three clips with first 500ms overlap, next hard cut, 50% opacity PiP, preview/export duration 5500ms, real decoded overlap pixel, overlay pixel, base/final color, two PCM tones, isolation, hard-cut tone switch, A/V PTS drift and source hashes. Mandatory evidence parser; v8 roundtrip test includes corrupt curve and cut-only/curve-only drafts.
- API35 required actual Android Media3 H264/AAC 720p24 benchmark 2/10/30s, both preview and export, actual track bytes-derived bitrate, wall clock, sampled process PSS at 500ms intervals, frame count, PTS drift, decoded three frames and PCM, source hash. API29 skips only this new expensive benchmark; prior gates unchanged. This is sampled PSS, not true peak or physical benchmark. No Android numbers until run.
- Exact tiled restoration inference for all four pinned models, 128 core, 2 pixel halo for two convolution layers, bit-identical overlap seams, max 1024px experimental preview (SR input max512), cancel per tile/row. Same weights, not a new quality claim. Model parity and 1024x720 bounded device inference mandatory. No photo export or production Deblur activation.

## Local executed evidence
Exact-source Git blobs verified: OverlapPlan 292b33505663b8fd6b56247c7c854f1088408f73, PictureLayer cdb10bd3a2caa17938b3f5d4daeb3ab73efa7ffd, RestorationLab d79745de9ee9869a182dc0709b9c1f7a7aab141a.
Standalone Java harness with matching project constants: 38098 schedule assertions (200 seeded cases), 4210 nonlinear motion/trim assertions, 1474584 model assertions; 337948 values compared bitwise across four models and five dimensions. All pass, total 1516892 assertions (primarily finite pixel values, not independent test cases). Actual 1024x720 refined CPU host inference Deblur 52.941ms, SR2x 86.445ms single measured pass, not mobile. Full project build and new Android gates pending. No local Android SDK/Gradle/adb available. 
Remaining: exact Beta22/23 CI, combined compositor edge cases, distinct layer URI revocation and v8 force-stop, full multilayer and per-cut dip/curve, independent all-screen redesign, real naturally degraded photo corpora and physical ARM64, actual Android benchmark result, HDR, signing. 

## Beta24 checkpoint: real run 45945717562
Exact commit 1ef3ec6a5f762e63e06dea788c480a45882d38be build, install, API35 full instrumented job PASS, API29 still running. Actual artifact 15175356436 CRC verified JUnit XML 63 passed, 0 failed, 18 skipped (81 total). Artifact 15175216933 CRC verified SHA256 be71414763c3a9b81089f268f8c55fb7d8bbfc0286300b33134ddf86fbe49dbb. Both combined PiP/crossfade preview/export 5500ms with midpoint R/B 128/127 and opacity overlay R/B 128/127, base blue and final red 253. Screenshot PNG 1080x2400 SHA256 272f2870ba052ccf6769f0b498d40efaa8e0d7546162e5f18381d1f4115a45b0 matches exact device log marker. Tiled refined models actual API35 inference 1725.054617ms deblur / 1767.49322ms SR on 1024x720, seam parity verified.

| duration s | mode | elapsed ms | bytes | actual video bitrate bps | sampled PSS KiB | frames | drift ms |
|---:|---|---:|---:|---:|---:|---:|---:|
|2|export|3477|1442008|4070876|106683|48|4|
|2|preview|3172|1442008|4070876|111041|48|4|
|10|export|7953|5856009|4268487|107807|240|4|
|10|preview|7330|5856009|4268487|109691|240|4|
|30|export|19653|17720101|4522425|125371|720|15|
|30|preview|19473|17720101|4522425|127151|720|15|
All x86_64 Android API35 720p24; sampled PSS, not physical or 1080p performance. No final 4/4 claim at checkpoint.
