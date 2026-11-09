#!/usr/bin/env bash
# Picture in picture from search, also while a mini player is already floating; the drag follows the finger.
# Results in ./smoke
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

# 1. Play from Home, drag the video down slowly (screenshot mid-drag), release: mini player
adb shell input tap 540 1000
sleep 20
adb shell input motionevent DOWN 540 350
for y in 420 500 600 700 800; do adb shell input motionevent MOVE 540 $y; done
shot 1a_mid_drag
for y in 950 1100 1300; do adb shell input motionevent MOVE 540 $y; done
adb shell input motionevent UP 540 1300
sleep 6
shot 1b_mini_over_home
tasks 1_mini_over_home

# 2. With the mini player floating: search and play a result
adb shell input tap 985 205
sleep 3
adb shell input text "lofi%shiphop"
adb shell input keyevent KEYCODE_ENTER
sleep 12
shot 2_results
adb shell input tap 540 520
sleep 20
shot 3_player
tasks 3_player

# 3. Swipe down: search must still be underneath
adb shell input swipe 540 350 540 1500 150
sleep 6
shot 4_after_swipe
tasks 4_after_swipe

adb logcat -d -b crash > $OUT/crash.txt
adb logcat -d | grep -E "ActivityTaskManager: (START|Displayed)|wm_finish|wm_destroy" | grep -i smarttube > $OUT/activity_log.txt
exit 0
