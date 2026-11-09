#!/usr/bin/env bash
# Home "Update available" card: install an older build while a newer one is released, open Home. Results in ./smoke
set -x
PKG=org.smarttube.phone
SPLASH=com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
OLD_TAG="${OLD_TAG:-build-64}"
OUT=smoke
mkdir -p $OUT
for i in 1 2 3 4 5; do
  curl -fsSL -o old.apk "https://github.com/shreyas-bangera/smarttube-phone/releases/download/$OLD_TAG/SmartTube-Phone-arm64.apk" && break
  sleep 10
done
adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher
adb install -g old.apk > $OUT/install_old.txt 2>&1

adb shell am start -n "$PKG/$SPLASH"
sleep 6
for t in 1 2 3 4 5 6; do
  adb exec-out screencap -p > "$OUT/home_$t.png"
  sleep 2
done
adb logcat -d | grep -iE "PhoneUpdater|AndroidRuntime" | tail -50 > $OUT/logcat_updater.txt
exit 0
