#!/usr/bin/env bash
# Takes the README screenshots on the emulator. Results in ./smoke/shots
set -x
PKG=org.smarttube.phone
SPLASH=com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
OUT=smoke/shots
mkdir -p "$OUT"

adb install -r -g app.apk > smoke/install.txt 2>&1
adb logcat -c
adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher
# Clean status bar for the pictures
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

dump() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb shell cat /sdcard/ui.xml > smoke/ui.xml
}
dismiss_anr() {
  dump
  if grep -q "responding" smoke/ui.xml; then
    adb shell input tap 320 1358
    sleep 2
  fi
}
# Taps the first element whose text or content description is exactly $1
tap_text() {
  dump
  local xy
  xy=$(python3 - "$1" <<'EOF'
import re, sys
xml = open('smoke/ui.xml', encoding='utf-8', errors='ignore').read()
want = sys.argv[1]
for node in re.finditer(r'<node [^>]*>', xml):
    n = node.group(0)
    t = re.search(r' text="([^"]*)"', n)
    d = re.search(r' content-desc="([^"]*)"', n)
    if (t and t.group(1) == want) or (d and d.group(1) == want):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
EOF
)
  if [ -n "$xy" ]; then
    adb shell input tap $xy
  else
    echo "not found: $1" >> smoke/missing.txt
  fi
}
shot() { dismiss_anr; adb exec-out screencap -p > "$OUT/$1.png"; }

adb shell am start -n "$PKG/$SPLASH"
sleep 40
shot 01_home

# Player with details, then comments
adb shell input tap 540 1000
sleep 25
adb shell input tap 540 400
sleep 1
shot 02_player
adb shell input tap 612 1124
sleep 8
shot 03_comments
adb shell input keyevent KEYCODE_BACK
sleep 4
# Mini player (drag the video down)
adb shell input swipe 540 300 540 1600 150
sleep 6
shot 04_mini_player
adb shell input keyevent KEYCODE_BACK
sleep 2

# Shorts
adb shell am start -n "$PKG/$SPLASH"
sleep 6
tap_text "Shorts"
sleep 20
shot 05_shorts
adb shell input keyevent KEYCODE_BACK
sleep 3

# Search
adb shell am start -n "$PKG/$SPLASH"
sleep 6
tap_text "Search YouTube"
sleep 3
adb shell input text "lofi%shiphop"
sleep 4
shot 06_search_suggestions
adb shell input keyevent KEYCODE_ENTER
sleep 12
shot 07_search_results
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 2

# You tab and settings
adb shell am start -n "$PKG/$SPLASH"
sleep 6
tap_text "You"
sleep 8
shot 08_you
tap_text "Settings"
sleep 6
shot 09_settings

# Sign in screen
tap_text "Sign in"
sleep 15
shot 10_sign_in
adb shell input keyevent KEYCODE_BACK
sleep 3

# Light appearance
adb shell am start -n "$PKG/$SPLASH"
sleep 4
tap_text "You"
sleep 4
tap_text "Settings"
sleep 6
tap_text "Appearance: Dark"
sleep 8
shot 12_settings_light
tap_text "Home"
sleep 15
shot 11_home_light

adb shell am broadcast -a com.android.systemui.demo -e command exit
adb logcat -d -b crash > smoke/crash.txt
adb logcat -d > smoke/logcat_full.txt
exit 0
