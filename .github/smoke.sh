#!/usr/bin/env bash
# Emulator smoke test: home screen, then open a video. Collects crash log, logcat and screenshots in ./smoke
set -x
PKG=org.smarttube.phone
SPLASH=com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
URL="${VIDEO_URL:-https://www.youtube.com/watch?v=jNQXAC9IVRw}"
mkdir -p smoke

adb install -r -g app.apk > smoke/install.txt 2>&1
adb logcat -c

# 1. Home
adb shell am start -n "$PKG/$SPLASH"
sleep 35
adb exec-out screencap -p > smoke/1_home.png
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" > smoke/1_activity.txt

# 2. Tap the first video card (upper third of the screen)
adb shell input tap 540 900
sleep 25
adb exec-out screencap -p > smoke/2_after_tap.png
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" > smoke/2_activity.txt

# 3. Open a video by link
adb shell am start -a android.intent.action.VIEW -d "$URL" -n "$PKG/$SPLASH"
sleep 30
adb exec-out screencap -p > smoke/3_video.png
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" > smoke/3_activity.txt

# 4. Tap the video area (shows the phone controls)
adb shell input tap 540 400
sleep 2
adb exec-out screencap -p > smoke/4_controls.png

adb logcat -d -b crash > smoke/crash.txt
adb logcat -d > smoke/logcat_full.txt
grep -E "AndroidRuntime|FATAL|SmartTube|liskovsoft" smoke/logcat_full.txt | tail -400 > smoke/logcat_app.txt
exit 0
