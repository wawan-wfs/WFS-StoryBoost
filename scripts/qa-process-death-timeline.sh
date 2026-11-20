#!/usr/bin/env bash
# Two native instrumentation processes separated by adb am force-stop.
set -euo pipefail
app=com.wfs.storyboost.studio
runner="$app.test/androidx.test.runner.AndroidJUnitRunner"
test_class=com.wfs.storyboost.ProcessDeathTimelineTest
out=qa-timeline-restart
mkdir -p "$out"
phase() {
  local method="$1" name="$2" marker="$3" log="$out/$2.txt"
  adb shell am instrument -w -r -e class "$test_class#$method" -e wfs.timeline.phase "$name" "$runner" >"$log" 2>&1 || {
    cat "$log"; return 1;
  }
  cat "$log"
  grep -q 'OK (1 test)' "$log" && grep -q "$marker" "$log" &&
    ! grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$log"
}
adb shell am force-stop "$app"
phase stageVisibleDraftAndHistory stage TIMELINE_STAGE
adb shell am force-stop "$app"
phase verifyDifferentProcessAndReopenedDraft verify TIMELINE_RESTORED
echo "PASS two process IDs, reordered timeline, local undo/redo, trim, caption and immutable sources"
