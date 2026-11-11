#!/usr/bin/env bash
# README pictures that need taps by position: comments and search. Pictures in the log (base64 JPEG).
set -x
PKG=org.smarttube.phone
SPLASH=com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
OUT=smoke/shots
mkdir -p "$OUT"
adb install -r -g app.apk > smoke/install.txt 2>&1
adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

adb shell am start -n "$PKG/$SPLASH"
sleep 40
shot a_home
# Search: the magnifying glass at the top right
adb shell input tap 985 205
sleep 4
shot b_search_open
adb shell input text "lofi%shiphop"
sleep 5
shot 06_search_suggestions
adb shell input keyevent KEYCODE_ENTER
sleep 15
shot 07_search_results
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 3

# Comments: open the first video, then the Comments button under it
adb shell am start -n "$PKG/$SPLASH"
sleep 6
adb shell input tap 540 1000
sleep 25
shot c_player
adb shell input tap 612 1124
sleep 10
shot 03_comments

adb shell am broadcast -a com.android.systemui.demo -e command exit
for f in "$OUT"/*.png; do
  echo "==IMG $(basename "$f" .png)=="
  convert "$f" -resize 540x -quality 85 jpg:- | base64 -w0
  echo
  echo "==END=="
done
exit 0
