#!/usr/bin/env bash
# Kill the live Timeline Studio instrumentation before Android can finish it.
set -euo pipefail
app=com.wfs.storyboost.studio
runner="$app.test/androidx.test.runner.AndroidJUnitRunner"
test_class=com.wfs.storyboost.ProcessDeathTimelineTest
out=qa-timeline-restart
mkdir -p "$out"
stage_log="$out/stage.txt"
verify_log="$out/verify.txt"
stage_job=
cleanup() {
  if [[ -n "$stage_job" ]]; then
    adb shell am force-stop "$app" || true
    wait "$stage_job" || true
  fi
}
trap cleanup EXIT
fail() {
  echo "$*" >&2
  cat "$stage_log" >&2
  exit 1
}
marker_pid() {
  sed -nE "s/^INSTRUMENTATION_STATUS: $1=([0-9]+)[[:space:]]*$/\1/p" "$2"
}

adb shell am force-stop "$app"
adb shell am instrument -w -r -e class "$test_class#stageVisibleDraftAndHistory" \
  -e wfs.timeline.phase stage "$runner" >"$stage_log" 2>&1 &
stage_job=$!
ready_pid=
for ((i = 0; i < 120; i++)); do
  ready_pid="$(marker_pid wfsTimelineStagePid "$stage_log")"
  [[ "$ready_pid" =~ ^[0-9]+$ ]] && break
  kill -0 "$stage_job" 2>/dev/null || fail "Timeline stage exited before publishing its live PID"
  sleep 0.5
done
[[ "$ready_pid" =~ ^[0-9]+$ ]] || fail "Timeline stage never confirmed the persisted draft"
active_pid="$(adb shell pidof "$app" | tr -d '\r')"
[[ "$active_pid" == "$ready_pid" ]] || fail "Timeline stage PID is not the running app PID"
printf 'livePid=%s\n' "$active_pid" | tee "$out/pids.txt"
adb shell am force-stop "$app"
wait "$stage_job" || true
stage_job=
cat "$stage_log"
if grep -Eq 'OK \(1 test\)|FAILURES!!!' "$stage_log"; then
  fail "Timeline stage completed normally instead of being interrupted"
fi
remaining_pid="$(adb shell 'pidof com.wfs.storyboost.studio || [ "$?" -eq 1 ]' | tr -d '\r')"
[[ -z "$remaining_pid" ]] || fail "Timeline process survived force-stop"

adb shell am instrument -w -r -e class "$test_class#verifyDifferentProcessAndReopenedDraft" \
  -e wfs.timeline.phase verify "$runner" >"$verify_log" 2>&1 || { cat "$verify_log"; exit 1; }
cat "$verify_log"
grep -q 'OK (1 test)' "$verify_log" || fail "Timeline restore instrumentation did not pass"
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$verify_log"; then
  fail "Timeline restore instrumentation reported a failure"
fi
grep -q "^INSTRUMENTATION_STATUS: wfsTimelineCacheRecovery=verified[[:space:]]*$" "$verify_log" || fail "Missing actual preview cache recovery proof"
restored_pid="$(marker_pid wfsTimelineRestoredPid "$verify_log")"
[[ "$restored_pid" =~ ^[0-9]+$ && "$restored_pid" != "$ready_pid" ]] || fail "No independently verified restored PID"
printf 'restoredPid=%s\n' "$restored_pid" | tee -a "$out/pids.txt"
echo "PASS live force-stop, new PID, private preview cache recovery, reordered timeline, local undo/redo, trim, caption and immutable sources"
