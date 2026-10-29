#!/usr/bin/env bash
# Android 16 software emulator. It is not David's physical BLU hardware.
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
    echo "Phone Lab test failed (exit $rc). Inspect screenshots and logcat." | tee "$OUT/RESULT.txt"
  else
    echo "Phone Lab passed install, launch, selected Bloom and one aim/release swipe. Inspect impact frames separately." | tee "$OUT/RESULT.txt"
  fi
}
trap collect_evidence EXIT

echo "Android 16 emulator test of $PKG" | tee "$OUT/README.txt"
test -s "$APK"
SDK="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Device API: $SDK" | tee -a "$OUT/README.txt"
test "$SDK" = "36" || { echo "Expected Android API 36"; exit 1; }
adb shell wm size 720x1600
adb shell wm density 260
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
# Suppress the one-time immersive-mode tutorial in this disposable emulator.
adb shell settings put secure immersive_mode_confirmations confirmed || true
echo "Resolution: $(adb shell wm size | tr -d '\r')" >> "$OUT/README.txt"
echo "Density: $(adb shell wm density | tr -d '\r')" >> "$OUT/README.txt"
echo "AVD RAM requested: 4096M; generic virtual CPU/GPU, audio disabled." >> "$OUT/README.txt"
echo "BLU touch latency, hardware GPU, speakers and haptics are not reproduced." >> "$OUT/README.txt"

adb install -r "$APK" | tee "$OUT/install.txt"
adb shell pm path "$PKG" | tee "$OUT/package-path.txt"
grep -q 'package:' "$OUT/package-path.txt"
adb logcat -c
adb shell am force-stop "$PKG"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 | tee "$OUT/launch.txt"
sleep 13
adb exec-out screencap -p > "$OUT/01-launched.png"
test -s "$OUT/01-launched.png"
if ! adb shell pidof "$PKG" | grep -Eq '[0-9]'; then echo "App did not launch";exit 1;fi
read -r SCREEN_W SCREEN_H < <(python3 -c 'import sys,struct; f=open(sys.argv[1],"rb"); f.read(16); print(*struct.unpack(">II",f.read(8)))' "$OUT/01-launched.png")
echo "Visible resolution: ${SCREEN_W}x${SCREEN_H}" | tee -a "$OUT/README.txt"
test "$SCREEN_W" -gt "$SCREEN_H" || { echo "Expected forced landscape game"; exit 1; }

# Game canvas is 960x540, letterboxed in the current screen. Map world units
# rather than percentages of the whole screen so taps land on actual controls.
VIEW_W=$((SCREEN_H * 960 / 540))
LEFT=$(((SCREEN_W - VIEW_W) / 2))
world_x(){ echo $((LEFT + $1 * SCREEN_H / 540)); }
world_y(){ echo $(($1 * SCREEN_H / 540)); }

# A first touch can still raise the platform's fullscreen notice on a fresh AVD.
# Its "Got it" location is over the noninteractive title, so these are harmless
# if the notice was already suppressed.
DISMISS_X=$((SCREEN_W * 693 / 1000));DISMISS_Y=$((SCREEN_H * 430 / 1000))
adb shell input tap "$DISMISS_X" "$DISMISS_Y";sleep 1
adb shell input tap "$DISMISS_X" "$DISMISS_Y";sleep 1
adb shell input tap "$(world_x 507)" "$(world_y 392)"
sleep 2
adb exec-out screencap -p > "$OUT/02-arena.png"
adb shell input tap "$(world_x 500)" "$(world_y 505)"
sleep 1
adb exec-out screencap -p > "$OUT/03-bloom-selected.png"
# Fail the run if Android's overlay or the title screen intercepted input.
python3 tools/verify_phone_lab_screen.py "$OUT/03-bloom-selected.png" | tee "$OUT/selection-check.txt"

# Pull the launcher back; this trajectory strikes a structure in the opening
# arena. Capture several frames because the Bloom effect lasts half a second.
adb shell input swipe "$(world_x 158)" "$(world_y 368)" "$(world_x 90)" "$(world_y 400)" 350
sleep .68
adb exec-out screencap -p > "$OUT/04-impact-a.png"
sleep .10
adb exec-out screencap -p > "$OUT/05-impact-b.png"
sleep .10
adb exec-out screencap -p > "$OUT/06-impact-c.png"
sleep .80
adb exec-out screencap -p > "$OUT/07-after-shot.png"
python3 tools/verify_phone_lab_screen.py --shot "$OUT/03-bloom-selected.png" "$OUT/07-after-shot.png" | tee "$OUT/shot-check.txt"

# The unmodified APK above is the one saved as the build artifact. A separate,
# disposable variant seeds a completed ninth night only inside this emulator so
# the newly authored chapter can also be inspected without modifying a real save.
adb shell am force-stop "$PKG"
adb install -r phone-lab-variants/seeded-night-10.apk | tee "$OUT/fixture-install.txt"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > "$OUT/fixture-launch.txt"
sleep 8
adb shell input tap "$(world_x 507)" "$(world_y 392)"
sleep 2
adb exec-out screencap -p > "$OUT/08-night-10.png"
python3 tools/verify_phone_lab_screen.py --rival "$OUT/08-night-10.png" | tee "$OUT/rival-check.txt"
adb shell input tap "$(world_x 500)" "$(world_y 505)"
sleep 1
adb exec-out screencap -p > "$OUT/09-night-10-bloom.png"
python3 tools/verify_phone_lab_screen.py "$OUT/09-night-10-bloom.png" | tee "$OUT/rival-bloom-check.txt"
adb shell input swipe "$(world_x 158)" "$(world_y 368)" "$(world_x 50)" "$(world_y 396)" 350
sleep 2
adb exec-out screencap -p > "$OUT/10-night-10-after-shot.png"
python3 tools/verify_phone_lab_screen.py --shot-6 "$OUT/09-night-10-bloom.png" "$OUT/10-night-10-after-shot.png" | tee "$OUT/rival-shot-check.txt"

if ! adb shell pidof "$PKG" | grep -Eq '[0-9]'; then echo "App process exited during play";exit 1;fi
adb logcat -d -v time | grep -A 15 -E 'FATAL EXCEPTION|AndroidRuntime' > "$OUT/fatals-candidate.txt" || true
if grep -q "Process: $PKG" "$OUT/fatals-candidate.txt"; then echo "App-scoped fatal exception";exit 1;fi
echo "The unmodified app and a separate progress-seeded fixture both accepted a Bloom shot; inspect screenshots for visual evidence."
