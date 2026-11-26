#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" "$ROOT/app/src/main/java/com/wfs/storyboost/AspectRatioProfile.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/OutputProfile.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/FrameMath.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/VideoBitrate.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/VideoFramePlan.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/CropFocus.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/ClipWindow.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/ExportOptions.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/ColorLooks.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/AiPhotoPolicy.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/PortraitMaskMath.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/ImageQualityMetrics.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/PhotoPassthrough.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/SocialExportProfile.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/QualityAdvisor.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/VideoPassthrough.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/ClipAdjustments.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/OverlapPlan.java" \
  "$ROOT/tests/OverlapPlanTest.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/RestorationLab.java" \
  "$ROOT/tests/RestorationLabModelTest.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/TimelineProject.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/TimelinePreviewCache.java" \
  "$ROOT/app/src/main/java/com/wfs/storyboost/TimelineNavigation.java" \
  "$ROOT/app/src/androidTest/java/com/wfs/storyboost/PcmToneProbe.java" \
  "$ROOT/tests/PcmToneProbeTest.java" \
  "$ROOT/tests/AspectRatioProfileTest.java" "$ROOT/tests/TimelineProjectTest.java" "$ROOT/tests/TimelinePreviewCacheTest.java" "$ROOT/tests/TimelineBatchTest.java"
java -cp "$TMP" com.wfs.storyboost.AspectRatioProfileTest
java -cp "$TMP" com.wfs.storyboost.TimelineProjectTest
java -cp "$TMP" com.wfs.storyboost.TimelinePreviewCacheTest
java -cp "$TMP" com.wfs.storyboost.TimelineBatchTest
java -cp "$TMP" com.wfs.storyboost.PcmToneProbeTest
java -cp "$TMP" com.wfs.storyboost.OverlapPlanTest
java -cp "$TMP" com.wfs.storyboost.RestorationLabModelTest "$ROOT/app/src/main/assets/restoration-lab"
