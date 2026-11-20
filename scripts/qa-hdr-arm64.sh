#!/usr/bin/env bash
# Owner-operated synthetic test only. No install, signing, upload, log clearing, or personal media.
set -euo pipefail
command -v adb >/dev/null || { echo "adb is required on PATH" >&2; exit 1; }
runner=com.wfs.storyboost.studio.test/androidx.test.runner.AndroidJUnitRunner
class=com.wfs.storyboost.VideoMediaQaTest
app=com.wfs.storyboost.studio
device=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then device+=(-s "$ANDROID_SERIAL"); fi
api="$("${device[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
abi="$("${device[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
emulator="$("${device[@]}" shell getprop ro.kernel.qemu | tr -d '\r')"
[[ "$api" =~ ^[0-9]+$ && "$api" -ge 29 && "$abi" == arm64-v8a && "$emulator" != 1 ]] ||
  { echo "Connect a physical Android 10+ ARM64 test device (got API=$api ABI=$abi)." >&2; exit 1; }
if ! "${device[@]}" shell pm list instrumentation | grep -F "$runner" >/dev/null; then
  echo "Install the matching app and instrumented-test APKs on a spare phone first." >&2
  exit 1
fi
folder="qa-physical-results/hdr-api${api}-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$folder"
printf 'api=%s\nabi=%s\n' "$api" "$abi" >"$folder/device.txt"
capture() {
  local stage="$1" service
  for service in meminfo cpuinfo battery thermalservice; do
    if [[ "$service" == meminfo ]]; then
      "${device[@]}" shell dumpsys meminfo "$app" >"$folder/$stage-meminfo.txt" 2>&1 || true
    else
      "${device[@]}" shell dumpsys "$service" >"$folder/$stage-$service.txt" 2>&1 || true
    fi
  done
}
capture before
result=0
for spec in 'arib-std-b67:hlgCaptionToneMapsToSdrWhenSupported' 'smpte2084:hdr10CaptionToneMapsToSdrWhenSupported'; do
  transfer="${spec%%:*}"
  method="${spec#*:}"
  token="hdr${api}-$(date -u +%s)-$$-${transfer}"
  raw="$folder/$transfer-instrument.txt"
  rc=0
  "${device[@]}" shell am instrument -w -r -e class "$class#$method" \
    -e wfs.hdr.run "$token" "$runner" >"$raw" 2>&1 || rc=$?
  "${device[@]}" logcat -d -s 'WfsBeta14HdrQA:I' '*:S' |
    tr -d '\r' | grep -F "run=$token " >"$folder/$transfer-metrics.txt" || true
  metrics="$folder/$transfer-metrics.txt"
  if (( rc == 0 )) && ! grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$raw"; then
    if grep -q 'status=PASS$' "$metrics" && grep -Eq 'OK \(1 test\)' "$raw"; then
      echo "PASS $transfer"
      continue
    elif grep -q 'status=CAPABILITY_SKIP$' "$metrics" && ! grep -q 'status=READY$' "$metrics"; then
      echo "NOT VERIFIED $transfer (missing HDR capability)"
      if (( result == 0 )); then result=2; fi
      continue
    fi
  fi
  echo "FAIL $transfer (missing completion evidence or instrumentation failure)" >&2
  result=1
done
capture after
echo "Private device reports: $folder (share only reviewed synthetic HDR metrics, not raw device logs)."
exit "$result"
