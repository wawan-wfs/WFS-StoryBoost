# Beta16 manual acceptance runbook

## Start one exact-head run
Use the public repository wawan-wfs/WFS-StoryBoost, workflow build-apk.yml, branch qa/beta16-rendered-preview-20261120. Use the [Actions workflow page](https://github.com/wawan-wfs/WFS-StoryBoost/actions/workflows/build-apk.yml), Run workflow, choose the branch and start manually. The workflow is present on the default branch; the selected branch provides the updated workflow. Alternatively, on an owner-authenticated local GitHub CLI:

```sh
gh workflow run build-apk.yml --repo wawan-wfs/WFS-StoryBoost --ref qa/beta16-rendered-preview-20261120
gh run list --repo wawan-wfs/WFS-StoryBoost --branch qa/beta16-rendered-preview-20261120 --event workflow_dispatch --limit 5
```

Record exact head SHA and run ID. Never use a PR to trigger a build or rerun an older SHA as proof of a newer one. No auto push or deployment.

## Acceptance ledger

| Gate | Required evidence |
| --- | --- |
| Build/sign/inspect | Green build; exact head ARM64 and universal debug artifacts; package, signature, model and ZIP validated |
| Launch | Android 35 smoke and actual screenshots |
| API29/API35 connected | Both instrumentation jobs green, both required VideoMediaQaTest methods individually passed (not skipped), decoded caption and clip-cut parity, source hashes, gallery unchanged, cancelled render no callbacks |
| Preview timing | WFS-StoryBoost-beta16-Preview-Evidence-API29 and API35: summary.json, preview.log, provenance.txt. Match commit and API, render and ready duration, actual settled seek before and after cut |
| Force-stop | Per-API timeline-restart artifacts: live PID, different restored PID, verified JUnit, wfsTimelineCacheRecovery=verified status, draft and hashes |
| Independent provider | Per-API provider artifact: distinct UID, owner revoke/delete/reimport, timeline-revoked wfsTimelinePreviewRevocation=verified; API35 encoder revoke |
| Prior regressions | Existing geometry, timeline, AI, viewport, photo/video, picker, process-death and cancellation suites must remain enabled |
| Physical release | Owner-only ARM64 low RAM and HDR, TalkBack, actual Google Photos, consented portraits and matching signed upgrade. Record explicitly if blocked |

If a job fails, inspect its exact run logs; fix the cause and run the new head once manually. If an evidence artifact is missing or a required test skipped, do not count it as pass even if unrelated tests succeeded. For a failed build, no new APK may be claimed. Existing Beta15 evidence remains valid only for its own commit.

## Host checks (no emulator)

```sh
python3 tests/test_beta16_evidence.py -v
bash tests/test_beta16_wrapper.sh
bash scripts/test-ratio-geometry.sh
```

The first two commands test the evidence parser and shell orchestration using synthetic XML, fake adb and fake Gradle. They prove no Android behavior or actual render metrics. The wrapper itself is intended for a synthetic x86_64 QA emulator, not a personal device; it removes only generated connected test reports and writes qa-beta16-preview/. Do not run full destructive fixture suites against an owner device with valuable drafts.

## Next batch after gate
See BETA17_DESIGN.md and NEXT_PROMPT.md. No feature code is authorized before the Beta16 acceptance gate; no production-release claim follows from emulator-only acceptance.
