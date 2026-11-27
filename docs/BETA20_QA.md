# Integrated candidate after Beta19 failure
Base 59d4e9671dc4b4ea3a2c3acce090eb49f682dd7b. See BETA19_QA.md for actual failure. Distinct-frequency PCM oracle and mandatory evidence parser reject early voice leakage, missing first tone, missing second tone, and inadequate RMS gain. Added host phase-sweep tests and Android equal-luma keyframe oracle. This is a stronger test repair, not proof the actual mixer works.
Full trimmed PCM decode up to 120s with 30s deadline, 80 bounded peak bins aligned to timeline start and duration; longer tracks sample eight distributed 1.5s windows (explicitly labelled sampled; not exhaustive waveform). Added real 20s late-tone test. Zoom keyframes 50–200%, global vertex matrix rendering, v5 selective storage and v1–v4 migration, split/trim continuity, staged UI. Added decoded black-border and center pixel tests for real preview/export and v5 force-stop. Preserve all old gates. Host and device tests pending exact-head CI.
Crossfade overlapping video/audio not directly supported by Media3 Composition (https://developer.android.com/media/media3/transformer/composition#current-limitations); no fake feature or activation. True overlap compositor, arbitrary keyframe curves, real multi-video layers, model licensing/inference, complete redesign, hardware and 2/10/30s benchmarks remain open.

## Verified local evidence before final push

- PcmToneProbeTest: 50/50 phase-sweep assertions using javac/java. Exact Git blob hashes a2c4dc5f47514c00762bd2943e7cbc619e8a4e0c and 3cc0623ae0ac5635425cbcbecb7261c71fd22dd4.
- Late waveform fixture generated H.264/AAC, each stream 20.0s. FFmpeg decoded first 15s RMS 0.0, late RMS 0.07068741845995426. Android waveform decoder itself still untested.
- Executable FFmpeg host overlap reference (scripts/crossfade_reference.py) passed: output 3.51s with 0.5s overlap, red/midpoint/blue RGB (253,0,0)/(127,0,126)/(0,0,254). Early tone 440 amplitude 0.12477, 780 approximately zero; overlap 440 0.06348 and 780 0.06131; late 780 0.12491, 440 approximately zero. Original SHA unchanged. Output SHA-256 e25a312f72db5131df6d04424a75b542c77955a75b118b30769c4214cc28f798. This is a real host reference media artifact and CI oracle, NOT a production Android compositor. Port the overlap contract to Android and require equivalent device evidence.

## Actual run 45847642066 checkpoint

Build and API35 smoke PASS; host 477 checks (159+62+12+165+50+21+5+3), host overlap reference PASS output 3.51s SHA-256 38d848c7804e2634dc36f0fc27f873d09a262fb31b4b9ac9e58f14828a0cb1f9. API35 full instrumented job PASS (87 reported by Gradle, not unique XML count), preview/export first RMS 0.030774698703875836, overlap RMS 0.043498774561062395, 780Hz early 0.04349974071043, early 440Hz 0.0000035879498667421464, overlap 780Hz 0.04343472310111392, overlap 440Hz 0.043515690848987675. PID 8734→8858 and preview source revocation verified. API29 still in progress at checkpoint. No acceptance claim for Beta21 based on this run.

## Final result
Run 45847642066 exact commit 34297dd0218f838d996a3903286c4dbfe52badbd passed all 4 jobs. API29 spectral first RMS 0.030744882738730526 and overlap 0.04346616293779256; force-stop PID 11596→11720. API35 full result above. Accepted emulator baseline, not physical device or stable signing approval.
