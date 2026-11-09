#!/usr/bin/env bash
# In-app updater: installs an older signed release, then updates it from Settings > Check for updates.
set -x
PKG=org.smarttube.phone
SPLASH=com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
OLD_TAG="${OLD_TAG:-build-41}"  # any signed build (39 or newer) older than the latest
OUT=smoke
mkdir -p $OUT
for i in 1 2 3 4 5; do
  curl -fsSL -o old.apk "https://github.com/shreyas-bangera/smarttube-phone/releases/download/$OLD_TAG/SmartTube-Phone-arm64.apk" && break
  sleep 10
done
adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher
adb install -g old.apk > $OUT/install_old.txt 2>&1
adb shell dumpsys package $PKG | grep -E "versionCode|versionName" > $OUT/version_before.txt

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
    if any(v == want or v.startswith(want) for v in vals):
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
tap_text "You"
sleep 5
tap_text "Settings"
sleep 5
shot 1_settings
tap_text "Check for updates"
sleep 8
shot 2_check_result
tap_text "Download and install"
sleep 4
shot 3_after_install_tap
# First time: Android's "allow from this source" screen
if grep -qi "Allow from this source" <(dump; cat $OUT/ui.xml); then
  shot 3b_permission
  tap_text "Allow from this source"
  sleep 2
  adb shell input keyevent KEYCODE_BACK
  sleep 3
  tap_text "Check for updates"
  sleep 8
  tap_text "Download and install"
fi
# Download, then the installer's confirmation
for i in $(seq 1 30); do
  sleep 4
  dump
  if grep -qiE 'text="(Update|Install)"' $OUT/ui.xml; then break; fi
done
shot 4_confirm
tap_text "Update"
sleep 20
shot 5_after_update
adb shell dumpsys package $PKG | grep -E "versionCode|versionName|lastUpdateTime" > $OUT/version_after.txt
adb logcat -d -b crash > $OUT/crash.txt
adb logcat -d | grep -iE "PackageInstaller|PhoneUpdater|AndroidRuntime" | tail -100 > $OUT/logcat_install.txt
exit 0
