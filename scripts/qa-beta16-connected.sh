#!/usr/bin/env bash
# Synthetic emulator only. Preserve preview telemetry before the AVD is removed.
set -euo pipefail
cd "$(dirname "$0")/.."
api="${1:?Pass expected emulator API 29 or 35}"
[[ "$api" == 29 || "$api" == 35 ]]
actual_api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
abi="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
[[ "$actual_api" == "$api" && "$abi" == x86_64 ]] || { echo 'Wrong Android test target' >&2; exit 1; }
commit="$(git rev-parse HEAD)"
[[ -z "${GITHUB_SHA:-}" || "$GITHUB_SHA" == "$commit" ]] || { echo 'Checkout SHA mismatch' >&2; exit 1; }
out=qa-beta16-preview
mkdir -p "$out"
rm -f "$out/summary.json"
printf 'commit=%s\napi=%s\nabi=%s\n' "$commit" "$api" "$abi" > "$out/provenance.txt"
# The connected report directory is generated, never source media.
rm -rf app/build/outputs/androidTest-results/connected
adb logcat -T 1 -v brief -s 'WfsTimelinePreviewQA:I' '*:S' > "$out/preview.log" 2> "$out/logcat.stderr" &
reader=$!
stop_reader(){
  if [[ -n "$reader" ]]; then
    kill "$reader" 2>/dev/null || true
    wait "$reader" 2>/dev/null || true
    reader=
  fi
}
cleanup(){
  local status=$?
  stop_reader
  printf 'wrapperExit=%s\n' "$status" >> "$out/provenance.txt"
}
trap cleanup EXIT
sleep 1
kill -0 "$reader" || { echo 'Preview logcat capture stopped' >&2; exit 1; }
gradle --no-daemon :app:connectedDebugAndroidTest
sleep 1
kill -0 "$reader" || { echo 'Preview logcat capture stopped early' >&2; exit 1; }
stop_reader
python3 scripts/qa-beta16-evidence.py \
  --reports app/build/outputs/androidTest-results/connected \
  --log "$out/preview.log" --api "$api" --commit "$commit" --output "$out/summary.json"
