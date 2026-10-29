#!/usr/bin/env bash
# Nova Phone Lab: Android 16 APK smoke test in disposable GitHub Actions AVD.
# An emulator approximates display/RAM; it does NOT reproduce BLU chipset, GPU or audio.
set -Eeuo pipefail

PKG="com.novasky.velvetfling.afterhours.test"
APK="app/build/outputs/apk/debug/app-debug.apk"
OUT="$PWD/phone-lab-evidence"
mkdir -p "$OUT"

collect_evidence() {
  local rc=$?
  set +e
  adb logcat -d -v time > "$OUT/logcat.txt" 2>&1
  adb shell dumpsys activity activities > "$OUT/activity.txt" 2>&1
  adb shell dumpsys meminfo "$PKG" > "$OUT/memory.txt" 2>&1
  adb shell wm size > "$OUT/display-size.txt" 2>&1
  adb shell wm density > "$OUT/display-density.txt" 2>&1
  adb exec-out screencap -p > "$OUT/final-screen.png" 2>/dev/null
  if [ "$rc" -ne 0 ]; then
    echo "Phone Lab smoke test failed (exit $rc). Inspect logcat and screenshots." | tee "$OUT/RESULT.txt"
  else
    echo "Phone Lab smoke test passed: install, launch, basic tap/swipe, no observed process exit." | tee "$OUT/RESULT.txt"
  fi
}
trap collect_evidence EXIT

echo "Android 16 emulator smoke test of $PKG" | tee "$OUT/README.txt"
test -s "$APK"

SDK="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Device API: $SDK" | tee -a "$OUT/README.txt"
test "$SDK" = "36" || { echo "Expected Android API 36"; exit 1; }

# The runner supplies 4096M guest RAM. Set a BLU-like logical display.
adb shell wm size 720x1600
adb shell wm density 260
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
echo "Resolution: $(adb shell wm size | tr -d '\r')" >> "$OUT/README.txt"
echo "Density: $(adb shell wm density | tr -d '\r')" >> "$OUT/README.txt"
echo "Memory profile (AVD): 4096M" >> "$OUT/README.txt"
echo "Physical BLU touch latency, GPU, speakers and haptics NOT replicated." >> "$OUT/README.txt"

# Install fresh on the disposable emulator; do not touch David's phone.
adb install -r "$APK" | tee "$OUT/install.txt"
adb shell pm path "$PKG" | tee "$OUT/package-path.txt"
grep -q 'package:' "$OUT/package-path.txt"

adb logcat -c
adb shell am force-stop "$PKG"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 | tee "$OUT/launch.txt"
sleep 13
adb exec-out screencap -p > "$OUT/01-launched.png"
test -s "$OUT/01-launched.png"

if ! adb shell pidof "$PKG" | grep -Eq '[0-9]'; then
  echo "App process not running after launch"; exit 1
fi

# Android can rotate the 720x1600 AVD into 1600x720 landscape for games.
# Use ACTUAL screenshot coordinates; the first run's portrait y=1150 tap was offscreen.
read -r SCREEN_W SCREEN_H < <(python3 -c 'import sys,struct; f=open(sys.argv[1],"rb"); f.read(16); print(*struct.unpack(">II",f.read(8)))' "$OUT/01-launched.png")
echo "Visible screenshot resolution: ${SCREEN_W}x${SCREEN_H}" | tee -a "$OUT/README.txt"

if [ "$SCREEN_W" -gt "$SCREEN_H" ]; then
  # Velvet Fling landscape main menu: Play Theater button in right central panel.
  TAP_X=$(( SCREEN_W * 70 / 100 ))
  TAP_Y=$(( SCREEN_H * 40 / 100 ))
  SWIPE_X1=$(( SCREEN_W * 21 / 100 ))
  SWIPE_Y1=$(( SCREEN_H * 62 / 100 ))
  SWIPE_X2=$(( SCREEN_W * 32 / 100 ))
  SWIPE_Y2=$(( SCREEN_H * 52 / 100 ))
else
  TAP_X=$(( SCREEN_W / 2 ))
  TAP_Y=$(( SCREEN_H * 40 / 100 ))
  SWIPE_X1=$(( SCREEN_W * 20 / 100 ))
  SWIPE_Y1=$(( SCREEN_H * 68 / 100 ))
  SWIPE_X2=$(( SCREEN_W * 42 / 100 ))
  SWIPE_Y2=$(( SCREEN_H * 55 / 100 ))
fi

echo "Tapping screen at $TAP_X,$TAP_Y and swiping onscreen" | tee "$OUT/inputs.txt"
adb shell input tap "$TAP_X" "$TAP_Y"
sleep 5
adb exec-out screencap -p > "$OUT/02-after-play-tap.png"
adb shell input swipe "$SWIPE_X1" "$SWIPE_Y1" "$SWIPE_X2" "$SWIPE_Y2" 600
sleep 3
adb exec-out screencap -p > "$OUT/03-after-game-swipe.png"

if ! adb shell pidof "$PKG" | grep -Eq '[0-9]'; then
  echo "App process exited during smoke interactions"; exit 1
fi

# Look for an app-scoped fatal exception. Other Android services may log crashes.
adb logcat -d -v time | grep -A 15 -E 'FATAL EXCEPTION|AndroidRuntime' > "$OUT/fatals-candidate.txt" || true
if grep -q "Process: $PKG" "$OUT/fatals-candidate.txt"; then
  echo "Detected app-scoped fatal exception"; exit 1
fi
echo "On-screen smoke interactions completed; real touch smoothness/Bloom explosion remain phone-playtest questions."
