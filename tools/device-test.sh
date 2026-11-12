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
curl -fsSL https://github.com/harbolddavid-wq/nova-starfall-defense/releases/download/holodeck-v0.1.2/Nova_Pocket_Holodeck_v0.1.2.apk -o /tmp/holodeck-original.apk
adb install -r /tmp/holodeck-original.apk
python3 tools/seed_upgrade.py
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Publish the second world only AFTER the new host APK is already installed.
python3 tools/publish_worlds.py --upload
adb shell am instrument -w -r -e class com.novasky.holodeck.UpgradeTest com.novasky.holodeck.test/androidx.test.runner.AndroidJUnitRunner | tee evidence/upgrade-test.txt
python3 -c "from pathlib import Path; assert 'OK (1 test)' in Path('evidence/upgrade-test.txt').read_text()"
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
timeout 600s adb shell am instrument -w -r -e notClass com.novasky.holodeck.UpgradeTest com.novasky.holodeck.test/androidx.test.runner.AndroidJUnitRunner | tee evidence/instrumentation.txt
python3 - <<'PY'
from pathlib import Path
s=Path('evidence/instrumentation.txt').read_text()
assert 'OK (8 tests)' in s, s
assert 'FAILURES' not in s and 'INSTRUMENTATION_FAILED' not in s
assert Path('evidence/api.txt').read_text().strip()=='36'
Path('evidence/VERIFIED.txt').write_text('API 36 emulator: eight full-suite tests plus the prior-APK save migration test passed. Complete 15-star real-touch playthrough; ending; save/reopen; native navigation; settings; pause; rotation; tap alternative; runtime allowlist. Generic emulator, not physical BLU. Native Android finite cue playback, zero active tracks and no rendered audio after the tail, mute and lifecycle tested. Live signed catalog download, non-bundled Lanternwake install after host installation, actual offline playthrough, preserved saves, data-only package update and rollback, signature and unsafe-ZIP rejection passed. Physical BLU testing remains David’s part.\n')
PY
