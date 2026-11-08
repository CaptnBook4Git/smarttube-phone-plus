#!/usr/bin/env bash
# Records opening several videos from Home in a row (with back in between) to compare the open transitions.
# Results in ./smoke: rec.mp4, events.txt (seconds since the recording started), stack_*.txt, shots.
set -x
PKG=org.smarttube.phone
SPLASH=com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
mkdir -p smoke

adb install -r -g app.apk > smoke/install.txt 2>&1
adb logcat -c
adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher
dismiss_anr() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  if adb shell cat /sdcard/ui.xml | grep -q "responding"; then
    adb shell input tap 320 1358
    sleep 2
  fi
}
stack() {
  adb shell dumpsys activity activities | grep -E "^ +\* Task\{|^ +\* ActivityRecord\{|topResumedActivity|mResumedActivity" > "smoke/stack_$1.txt"
}

adb shell am start -n "$PKG/$SPLASH"
sleep 40
dismiss_anr
adb exec-out screencap -p > smoke/0_home.png
stack 0_home

adb shell screenrecord --bit-rate 6000000 --time-limit 175 /sdcard/rec.mp4 &
REC_PID=$!
START=$(date +%s.%N)
event() { echo "$(echo "$(date +%s.%N) - $START" | bc) $1" >> smoke/events.txt; }
sleep 2

# 1. First open from Home
event "tap card 1 (first open)"
adb shell input tap 540 1000
sleep 12
stack 1_open1
event "swipe player down (picture in picture)"
adb shell input swipe 540 350 540 1500 120
sleep 5
stack 2_back1

# 2. Second open from Home
dismiss_anr
event "tap card 2 (second open)"
adb shell input tap 540 1800
sleep 12
stack 3_open2
event "swipe player down (picture in picture)"
adb shell input swipe 540 350 540 1500 120
sleep 5
stack 4_back2

# 3. Third open from Home
dismiss_anr
event "tap card 1 (third open)"
adb shell input tap 540 1000
sleep 12
stack 5_open3
event "back"
adb shell input keyevent KEYCODE_BACK
sleep 5
stack 6_back3
event "end"

adb shell pkill -INT screenrecord
sleep 4
wait $REC_PID
adb pull /sdcard/rec.mp4 smoke/rec.mp4

adb logcat -d -b crash > smoke/crash.txt
adb logcat -d > smoke/logcat_full.txt
grep -E "ActivityTaskManager: (START|Displayed)|wm_|SmartTubePhone" smoke/logcat_full.txt | grep -i smarttube > smoke/activity_log.txt
exit 0
