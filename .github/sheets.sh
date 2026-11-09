#!/usr/bin/env bash
# Sheets over the player: comments and player settings must open below the video. Results in ./smoke
set -x
PKG=org.smarttube.phone
SPLASH=com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
OUT=smoke
mkdir -p $OUT

adb install -r -g app.apk > $OUT/install.txt 2>&1
adb logcat -c
adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher

dump() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb shell cat /sdcard/ui.xml > $OUT/ui.xml; }
tap_text() {
  dump
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys
xml = open('smoke/ui.xml', encoding='utf-8', errors='ignore').read()
want = sys.argv[1].lower()
for node in re.finditer(r'<node [^>]*>', xml):
    n = node.group(0)
    t = re.search(r' text="([^"]*)"', n)
    d = re.search(r' content-desc="([^"]*)"', n)
    vals = [m.group(1).lower() for m in (t, d) if m]
    if any(v == want for v in vals):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -n "$xy" ]; then adb shell input tap $xy; else echo "not found: $1" >> $OUT/missing.txt; fi
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

adb shell am start -n "$PKG/$SPLASH"
sleep 35
adb shell input tap 540 1000
sleep 20
shot 1_player

# The UI dump fails while video plays: tap the Comments chip by position
adb shell input tap 612 1126
sleep 3
shot 2a_comments_tap
sleep 10
shot 2_comments
adb shell input keyevent KEYCODE_BACK
sleep 3
shot 2c_after_back

# Player settings (gear in the controls)
adb shell input tap 540 400
sleep 1
adb shell input tap 1005 200
sleep 5
shot 3_settings
adb shell input keyevent KEYCODE_BACK
sleep 3
shot 4_after

adb logcat -d -b crash > $OUT/crash.txt
exit 0
