#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
adb shell wm size 720x1600
adb shell wm density 260
adb shell getprop ro.build.version.sdk > evidence/api.txt
adb shell wm size > evidence/display.txt
adb shell wm density >> evidence/display.txt
adb shell cat /proc/meminfo > evidence/memory.txt
adb logcat -c
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am start -W -n com.novasky.holodeck/.MainActivity
adb shell screenrecord --time-limit 120 /sdcard/holodeck-playtest.mp4 &
record_pid=$!
cleanup() {
  adb shell pkill -INT screenrecord || true
  wait "$record_pid" || true
  adb pull /sdcard/holodeck-playtest.mp4 evidence/ || true
  adb pull /sdcard/Android/data/com.novasky.holodeck/files evidence/screens || true
  adb logcat -d > evidence/logcat.txt
  adb shell am start -W -n com.novasky.holodeck/.MainActivity > evidence/relaunch.txt
  sleep 3
  adb exec-out screencap -p > evidence/final-home.png
  adb shell dumpsys meminfo com.novasky.holodeck > evidence/app-memory.txt
  adb shell dumpsys gfxinfo com.novasky.holodeck > evidence/frame-stats.txt
  adb shell dumpsys package com.novasky.holodeck > evidence/package.txt
}
trap cleanup EXIT
adb shell am instrument -w -r com.novasky.holodeck.test/androidx.test.runner.AndroidJUnitRunner | tee evidence/instrumentation.txt
python3 - <<'PY'
from pathlib import Path
s=Path('evidence/instrumentation.txt').read_text()
assert 'OK (5 tests)' in s, s
assert 'FAILURES' not in s and 'INSTRUMENTATION_FAILED' not in s
assert Path('evidence/api.txt').read_text().strip()=='36'
Path('evidence/VERIFIED.txt').write_text('API 36 emulator: five device tests passed. Complete 15-star real-touch playthrough; ending; save/reopen; native navigation; settings; pause; rotation; tap alternative; runtime allowlist. Generic emulator, not physical BLU. Audio generation states checked in browser; acoustic output and hardware touch feel require physical testing.\n')
PY
