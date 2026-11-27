#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cd "$tmp"
mkdir -p scripts bin
cp "$root/scripts/qa-beta16-connected.sh" "$root/scripts/qa-beta16-evidence.py" scripts/
git init -q
git -c user.name=QA -c user.email=qa@example.invalid commit --allow-empty -qm fixture
cat > bin/adb <<'MOCK'
#!/usr/bin/env bash
if [[ "$*" == 'shell getprop ro.build.version.sdk' ]]; then echo "${MOCK_API:-29}"
elif [[ "$*" == 'shell getprop ro.product.cpu.abi' ]]; then echo x86_64
elif [[ "$1" == logcat ]]; then
  printf '%s\n' 'I/WfsTimelinePreviewQA: renderMs=300 bytes=4096 durationMs=2000' 'I/WfsTimelinePreviewQA: readyMs=430 durationMs=2001' 'I/WfsTimelinePreviewQA: seekSettledMs=600' 'I/WfsTimelinePreviewQA: seekSettledMs=1400' 'I/WfsTimelinePreviewQA: soundtrackRms=0.05 gapRms=0.0 dip=verified' 'I/WfsTimelinePreviewQA: soundtrackRms=0.05 gapRms=0.0 dip=verified' 'I/WfsTimelinePreviewQA: dualTrackFirstRms=0.03 overlapRms=0.045 early780=0.04 early440=0.001 late780=0.04 late440=0.04 keyframe=verified' 'I/WfsTimelinePreviewQA: dualTrackFirstRms=0.03 overlapRms=0.045 early780=0.04 early440=0.001 late780=0.04 late440=0.04 keyframe=verified' 'I/WfsTimelinePreviewQA: crossfadeVerified duration=3500 midRed=127 midBlue=126 early440=0.12 early780=0.001 overlap440=0.06 overlap780=0.06 late780=0.12 late440=0.001' 'I/WfsTimelinePreviewQA: crossfadeVerified duration=3500 midRed=127 midBlue=126 early440=0.12 early780=0.001 overlap440=0.06 overlap780=0.06 late780=0.12 late440=0.001' 'I/WfsTimelinePreviewQA: pipVerified before=0 early=2000 late=2000 after=0 dx=0.4 dy=0.4' 'I/WfsTimelinePreviewQA: pipVerified before=0 early=2000 late=2000 after=0 dx=0.4 dy=0.4'
  exec sleep 300
else exit 2
fi
MOCK
cat > bin/gradle <<'MOCK'
#!/usr/bin/env bash
set -e
[[ "${MOCK_GRADLE:-ok}" != fail ]] || exit 17
out=app/build/outputs/androidTest-results/connected
mkdir -p "$out"
cat > "$out/TEST-fixture.xml" <<'XML'
<testsuite><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelinePreviewUsesRenderedCaptionAndCutsAndRemovesPrivateFileAfterRecreation"/><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelinePrivatePreviewRejectsDeletedSourceAndCanCancelBeforeCompletion"/><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelineAdjustedRepeatedClipRendersColorAndMute"/><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelineAdjustmentPanelStagesCancelApplyAndReset"/><testcase classname="com.wfs.storyboost.TimelineAdjustmentsQaTest" name="legacySchemaReadOnlyAndV2RoundTrip"/><testcase classname="com.wfs.storyboost.TimelineAdjustmentsQaTest" name="soundtrackAndFadeV3RoundTrip"/><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelineSoundtrackAndDipRenderRealMedia"/><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelineInteractiveSplitAndSoundtrackPanel"/><testcase classname="com.wfs.storyboost.TimelineAdjustmentsQaTest" name="dualTrackAndKeyframesV4RoundTrip"/><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelineDualTrackKeyframeAndWaveformRealMedia"/><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelineVoiceAndKeyframeUiPersists"/><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelineWaveformCoversLateAudio"/><testcase classname="com.wfs.storyboost.TimelineAdjustmentsQaTest" name="zoomV5RoundTrip"/><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelineZoomTransformsActualFrames"/><testcase classname="com.wfs.storyboost.TimelineAdjustmentsQaTest" name="overlapV6RoundTrip"/><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelineCrossfadeRendersOverlappingVideoAndAudio"/><testcase classname="com.wfs.storyboost.RestorationLabQaTest" name="originalModelInferenceAndHashGate"/><testcase classname="com.wfs.storyboost.RestorationLabQaTest" name="restorationLabSyntheticUiRunsOffline"/><testcase classname="com.wfs.storyboost.RestorationLabQaTest" name="refinedModelsInferenceAndOriginalCompatibility"/><testcase classname="com.wfs.storyboost.TimelineAdjustmentsQaTest" name="pictureV7RoundTrip"/><testcase classname="com.wfs.storyboost.VideoMediaQaTest" name="timelinePictureLayerRendersRealMovingOverlay"/></testsuite>
XML
if [[ "${MOCK_GRADLE:-ok}" == skip ]]; then sed -i 's@<testcase @<testcase @;s@Recreation"/>@Recreation"><skipped/></testcase>@' "$out/TEST-fixture.xml"; fi
MOCK
chmod +x bin/*
export PATH="$tmp/bin:$PATH"
unset GITHUB_SHA
bash scripts/qa-beta16-connected.sh 29 >/dev/null
grep -q '"status": "passed"' qa-beta16-preview/summary.json
grep -q 'wrapperExit=0' qa-beta16-preview/provenance.txt
if MOCK_GRADLE=skip bash scripts/qa-beta16-connected.sh 29 >/dev/null 2>&1; then exit 1; fi
grep -q '"status": "failed"' qa-beta16-preview/summary.json
if MOCK_GRADLE=fail bash scripts/qa-beta16-connected.sh 29 >/dev/null 2>&1; then exit 1; fi
test ! -e qa-beta16-preview/summary.json
grep -q 'wrapperExit=17' qa-beta16-preview/provenance.txt
if MOCK_API=35 bash scripts/qa-beta16-connected.sh 29 >/dev/null 2>&1; then exit 1; fi
if GITHUB_SHA=bad bash scripts/qa-beta16-connected.sh 29 >/dev/null 2>&1; then exit 1; fi
echo 'PASS 5 mock wrapper cases: success, skip, Gradle error, API mismatch, SHA mismatch'
