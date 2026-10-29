#!/usr/bin/env bash
set -Eeuo pipefail
PKG=com.novasky.afterglow
OUT="$PWD/phone-lab-evidence"
mkdir -p "$OUT" /tmp/afterglow-fixtures
collect(){ local rc=$?; set +e; adb logcat -d -v time > "$OUT/logcat.txt"; adb exec-out screencap -p > "$OUT/final.png"; adb shell dumpsys meminfo "$PKG" > "$OUT/memory.txt"; echo "exit=$rc; software emulator, not David's BLU; host audio disabled" > "$OUT/RESULT.txt"; }
trap collect EXIT
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 36
adb shell wm size 720x1600
adb shell wm density 260
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n "$PKG/.PlayerActivity"
sleep 8
adb exec-out screencap -p > "$OUT/home.png"
adb shell pidof "$PKG" | grep -Eq '[0-9]'
adb shell input tap 194 1131
sleep 3
adb exec-out screencap -p > "$OUT/file-picker.png"
adb shell input keyevent 4
sleep 2
ffmpeg -v error -y -f lavfi -i 'testsrc2=size=320x180:rate=12' -f lavfi -i 'sine=frequency=440:sample_rate=48000' -t 4 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest /tmp/afterglow-fixtures/sample.mp4
ffmpeg -v error -y -i /tmp/afterglow-fixtures/sample.mp4 -vn -c:a libmp3lame /tmp/afterglow-fixtures/sample.mp3
ffmpeg -v error -y -i /tmp/afterglow-fixtures/sample.mp4 -vn -c:a flac /tmp/afterglow-fixtures/sample.flac
ffmpeg -v error -y -i /tmp/afterglow-fixtures/sample.mp4 -vn -c:a libopus /tmp/afterglow-fixtures/sample.opus
ffmpeg -v error -y -i /tmp/afterglow-fixtures/sample.mp4 -vn -c:a pcm_s16le /tmp/afterglow-fixtures/sample.wav
ffmpeg -v error -y -i /tmp/afterglow-fixtures/sample.mp4 -c:v copy -c:a libopus /tmp/afterglow-fixtures/sample.mkv
ffmpeg -v error -y -i /tmp/afterglow-fixtures/sample.mp4 -c:v libvpx-vp9 -deadline realtime -c:a libvorbis /tmp/afterglow-fixtures/sample.webm
ffmpeg -v error -y -i /tmp/afterglow-fixtures/sample.mp4 -c:v mpeg4 -c:a libmp3lame /tmp/afterglow-fixtures/sample.avi
adb shell mkdir -p /data/local/tmp/afterglow-fixtures
adb push /tmp/afterglow-fixtures/. /data/local/tmp/afterglow-fixtures/ > "$OUT/push.txt"
adb shell run-as "$PKG" mkdir -p files/fixtures
for f in sample.mp3 sample.flac sample.opus sample.wav sample.mp4 sample.mkv sample.webm sample.avi; do adb shell run-as "$PKG" cp "/data/local/tmp/afterglow-fixtures/$f" "files/fixtures/$f"; done
adb logcat -c
adb shell am start -n "$PKG/.DiagnosticsActivity"
ok=0
for i in $(seq 1 20); do
  sleep 5
  adb logcat -d -s AFTERGLOW_TEST:I '*:S' > "$OUT/playback.txt"
  if grep -q 'FAIL ' "$OUT/playback.txt"; then cat "$OUT/playback.txt";exit 1;fi
  if grep -q 'ALL_PASS 8' "$OUT/playback.txt"; then ok=1;break;fi
done
cat "$OUT/playback.txt"
test "$ok" = 1
test "$(grep -c 'PASS sample\.' "$OUT/playback.txt")" = 8
grep -q 'PASS controls pause/resume/seek' "$OUT/playback.txt"
adb shell pidof "$PKG" | grep -Eq '[0-9]'
echo 'Eight generated files advanced in the debug player; this is not a BLU or an audible listening test.'
