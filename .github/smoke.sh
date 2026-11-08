#!/usr/bin/env bash
# Emulator smoke test: home screen, then open a video. Collects crash log, logcat and screenshots in ./smoke
set -x
PKG=org.smarttube.phone
SPLASH=com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
URL="${VIDEO_URL:-https://www.youtube.com/watch?v=jNQXAC9IVRw}"
mkdir -p smoke

adb install -r -g app.apk > smoke/install.txt 2>&1
adb logcat -c
adb shell settings put global hide_error_dialogs 1
# The emulator's launcher often freezes and its "isn't responding" dialog covers the app: take it out of the way
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher
dismiss_anr() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  if adb shell cat /sdcard/ui.xml | grep -q "responding"; then
    adb shell input tap 320 1358
    sleep 2
  fi
}

# 1. Home
adb shell am start -n "$PKG/$SPLASH"
sleep 35
dismiss_anr; adb exec-out screencap -p > smoke/1_home.png
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" > smoke/1_activity.txt

# Shorts: open the Shorts tab from Home, swipe up to the next Short
dismiss_anr
adb shell input tap 405 2250
sleep 20
dismiss_anr; adb exec-out screencap -p > smoke/5_shorts.png
adb shell input swipe 540 1700 540 500 180
adb exec-out screencap -p > smoke/6a_shorts_swipe_now.png
sleep 1
adb exec-out screencap -p > smoke/6_shorts_swipe.png
sleep 12
dismiss_anr; adb exec-out screencap -p > smoke/7_shorts_next.png
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" > smoke/5_activity.txt
adb shell input keyevent KEYCODE_BACK
sleep 3
adb shell am start -n "$PKG/$SPLASH"
sleep 8

# 2. Tap the first video card (upper third of the screen)
dismiss_anr
adb shell input tap 540 900
sleep 25
dismiss_anr; adb exec-out screencap -p > smoke/2_after_tap.png
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" > smoke/2_activity.txt

# 3. Open a video by link
adb shell am start -a android.intent.action.VIEW -d "$URL" -n "$PKG/$SPLASH"
sleep 30
dismiss_anr; adb exec-out screencap -p > smoke/3_video.png
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" > smoke/3_activity.txt

# 4. Tap the video area (shows the phone controls)
dismiss_anr
adb shell input tap 540 400
sleep 2
dismiss_anr; adb exec-out screencap -p > smoke/4_controls.png

adb logcat -d -b crash > smoke/crash.txt
adb logcat -d > smoke/logcat_full.txt
grep -E "AndroidRuntime|FATAL|SmartTube|liskovsoft|SmartTubePhone" smoke/logcat_full.txt | tail -400 > smoke/logcat_app.txt
exit 0
