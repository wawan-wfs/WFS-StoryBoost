#!/usr/bin/env bash
# Owner-operated, no installation/uninstallation/upload, synthetic tests only.
set -euo pipefail
command -v adb >/dev/null
api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
abi="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
emulator="$(adb shell getprop ro.kernel.qemu | tr -d '\r')"
[[ "$api" =~ ^[0-9]+$ && "$api" -ge 29 && "$abi" == arm64-v8a && "$emulator" != 1 ]] ||
 { echo "Physical Android 10+ ARM64 required." >&2; exit 1; }
runner=com.wfs.storyboost.studio.test/androidx.test.runner.AndroidJUnitRunner
adb shell pm list instrumentation | grep -F "$runner" >/dev/null ||
 { echo "Install the matching debug app + androidTest APK pair on a spare test phone." >&2; exit 1; }
token="restore-$(date -u +%s)-$$"
folder="qa-physical-results/$token";mkdir -p "$folder"
printf 'api=%s\nabi=%s\nscope=physical device synthetic model parity; installed APK provenance must be recorded separately\n' "$api" "$abi" > "$folder/device.txt"
adb shell dumpsys meminfo com.wfs.storyboost.studio > "$folder/before-meminfo.txt"
adb shell am instrument -w -r -e class 'com.wfs.storyboost.RestorationLabQaTest#tiledModelsPreserveSeamsAndBoundedPreview' \
 -e wfs.restoration.run "$token" "$runner" > "$folder/instrument.txt" 2>&1
grep -q 'OK (1 test)' "$folder/instrument.txt"
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$folder/instrument.txt";then exit 1;fi
adb logcat -d -s 'WfsTimelinePreviewQA:I' '*:S' | tr -d '\r' | grep -F "run=$token " > "$folder/metrics.txt"
python3 - "$folder/metrics.txt" "$token" <<'PY'
import re,sys
from pathlib import Path
rows=re.findall(r'tiledModel=(deblur|sr2x) width=1024 height=720 elapsedMs=([0-9.]+) seamParity=verified run='+re.escape(sys.argv[2])+r' pid=(\d+)',Path(sys.argv[1]).read_text())
if len(rows)!=2 or {r[0] for r in rows}!={'deblur','sr2x'} or any(float(r[1])<=0 or int(r[2])<=0 for r in rows) or len({r[2] for r in rows})!=1:
    raise SystemExit('Missing exact run-tagged model evidence')
print('PASS physical ARM64 synthetic parity and inference timing',rows)
PY
adb shell dumpsys meminfo com.wfs.storyboost.studio > "$folder/after-meminfo.txt"
echo "Evidence: $folder. No natural-photo, HDR, release signing or complete acceptance claim."
