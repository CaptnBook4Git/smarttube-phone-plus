#!/usr/bin/env bash
# Search > play a result > swipe down to picture in picture: the search page must stay underneath. Results in ./smoke
set -x
PKG=org.smarttube.phone
SPLASH=com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
OUT=smoke
mkdir -p $OUT

adb install -r -g app.apk > $OUT/install.txt 2>&1
adb logcat -c
adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher

tasks() { adb shell dumpsys activity activities | grep -E "^ +\* Task\{|^ +\* ActivityRecord\{|topResumedActivity" > "$OUT/tasks_$1.txt"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

adb shell am start -n "$PKG/$SPLASH"
sleep 35
# Search (magnifier at the top right of Home)
adb shell input tap 985 205
sleep 3
adb shell input text "lofi%shiphop"
adb shell input keyevent KEYCODE_ENTER
sleep 12
shot 1_results
tasks 1_results

# Play the first result
adb shell input tap 540 520
sleep 20
shot 2_player
tasks 2_player

# Swipe the player down: mini player
adb shell input swipe 540 350 540 1500 150
sleep 1
shot 3a_swipe
sleep 5
shot 3_after_swipe
tasks 3_after_swipe

adb logcat -d -b crash > $OUT/crash.txt
adb logcat -d | grep -E "ActivityTaskManager: (START|Displayed)|wm_finish|wm_destroy|SmartTubePhone" | grep -i smarttube > $OUT/activity_log.txt
exit 0
