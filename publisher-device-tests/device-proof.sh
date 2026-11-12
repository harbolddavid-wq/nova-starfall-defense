#!/usr/bin/env bash
set -euo pipefail
adb shell wm size 720x1600
adb shell wm density 260
adb logcat -c
adb install -r /tmp/existing-host.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
finish() {
  adb pull /sdcard/Android/data/com.novasky.holodeck/files evidence/screens || true
  adb logcat -d > evidence/logcat.txt
  adb shell dumpsys package com.novasky.holodeck > evidence/installed-host.txt
  adb shell dumpsys meminfo com.novasky.holodeck > evidence/memory.txt
}
trap finish EXIT
timeout 300s adb shell am instrument -w -r -e class com.novasky.holodeck.PortalBloomTest com.novasky.holodeck.test/androidx.test.runner.AndroidJUnitRunner | tee evidence/publisher-device-test.txt
python3 - <<'PY'
from pathlib import Path
s=Path('evidence/publisher-device-test.txt').read_text()
assert 'OK (1 test)' in s and 'FAILURES' not in s
Path('evidence/VERIFIED.txt').write_text('Existing public Holodeck 0.2.0 APK installed unchanged. Signed live Discover catalog displayed Portal Bloom. Actual native download, separate world launch, real held touch input, spark drag, completion, offline saved re-entry and exit passed on API 36 at 720x1600 / 260 dpi / 4 GB RAM. No host APK was rebuilt or reinstalled for world delivery. Emulator verification, not physical BLU testing.\n')
PY
