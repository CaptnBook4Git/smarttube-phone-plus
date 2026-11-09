#!/usr/bin/env bash
# Loading placeholders and pull to refresh on Home. Results in ./smoke
set -x
PKG=org.smarttube.phone
SPLASH=com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
OUT=smoke
mkdir -p $OUT

adb install -r -g app.apk > $OUT/install.txt 2>&1
adb logcat -c
adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher

adb shell am start -n "$PKG/$SPLASH"
for t in 2 3 4 5 6 8 10 14 20 30; do
  sleep 1.5
  adb exec-out screencap -p > "$OUT/1_load_$t.png"
done
sleep 10
adb exec-out screencap -p > $OUT/2_home.png

# Switch topic: the feed reloads, placeholders show until it arrives
python3 - > /dev/null 2>&1 <<'PY' || true
PY
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
XY=$(adb shell cat /sdcard/ui.xml | python3 -c "
import re,sys
x=sys.stdin.read()
m=re.search(r'text=\"Kids\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]', x)
print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else print('')
")
if [ -n "$XY" ]; then
  adb shell input tap $XY
  for t in a b c d; do adb exec-out screencap -p > "$OUT/2b_switch_$t.png"; sleep 0.4; done
  sleep 8
  adb shell input tap 120 300
  sleep 6
fi

# Pull down to refresh
adb shell input swipe 540 700 540 1700 900
sleep 0.3
adb exec-out screencap -p > $OUT/3_pull_a.png
sleep 1
adb exec-out screencap -p > $OUT/3_pull_b.png
sleep 2
adb exec-out screencap -p > $OUT/3_pull_c.png
sleep 12
adb exec-out screencap -p > $OUT/4_after_refresh.png

# Scroll a little (thumbnail prefetch)
for i in 1 2 3; do adb shell input swipe 540 1800 540 600 250; sleep 1; done
adb exec-out screencap -p > $OUT/5_scrolled.png

adb logcat -d -b crash > $OUT/crash.txt
adb logcat -d | grep -E "AndroidRuntime|org.smarttube.phone" | tail -200 > $OUT/logcat_app.txt
exit 0
