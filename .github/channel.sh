#!/usr/bin/env bash
# Picture-in-picture + search task check, player recommendations, channel page. Results in ./smoke
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
dismiss_anr() { dump; if grep -q "responding" $OUT/ui.xml; then adb shell input tap 320 1358; sleep 2; fi; }
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
shot() { dismiss_anr; adb exec-out screencap -p > "$OUT/$1.png"; }
tasks() { adb shell dumpsys activity activities | grep -E "^ +\* Task\{|^ +\* ActivityRecord\{|topResumedActivity" > "$OUT/tasks_$1.txt"; }

adb shell am start -n "$PKG/$SPLASH"
sleep 40
shot 01_home

# 1. Player and its recommendations
adb shell input tap 540 1000
sleep 25
shot 02_player
dump; cp $OUT/ui.xml $OUT/ui_player.xml
adb shell input swipe 540 1900 540 1100 300
sleep 3
shot 03_player_recommendations
dump; cp $OUT/ui.xml $OUT/ui_player_scrolled.xml
adb shell input swipe 540 1100 540 1900 300
sleep 2

# 2. Picture in picture, then search from Home
adb shell input swipe 540 300 540 1600 150
sleep 6
shot 04_pip_home
tasks pip
tap_text "Search YouTube"
sleep 5
shot 05_search_with_pip
tasks search

# 3. Search a channel and open it
adb shell input text "t%sseries"
sleep 2
adb shell input keyevent KEYCODE_ENTER
sleep 12
shot 06_search_results
tap_text "T-Series"
sleep 15
shot 07_channel
tasks channel
tap_text "Shorts"
sleep 8
shot 08_channel_shorts
tap_text "Videos"
sleep 4
adb shell input swipe 540 1900 540 600 300
sleep 4
shot 09_channel_scrolled

adb logcat -d -b crash > $OUT/crash.txt
adb logcat -d > $OUT/logcat_full.txt
exit 0
