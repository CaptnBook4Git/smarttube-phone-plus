package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.liskovsoft.smartyoutubetv2.tv.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Settings, "Check for updates": looks up the latest GitHub release, downloads its APK and hands it to Android's
 * package installer, which shows the usual "Update" confirmation.
 *
 * Release tags are build-N and the app's version code is BASE_VERSION_CODE + N (see smarttubetv/build.gradle).
 */
final class PhoneUpdater {
    private static final String REPO = "shreyas-bangera/smarttube-phone";
    private static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String APK_NAME = "SmartTube-Phone-arm64.apk";
    private static final int BASE_VERSION_CODE = 2462;
    private static final String ACTION_INSTALL_STATUS = "org.smarttube.phone.INSTALL_STATUS";

    private static volatile boolean sBusy;

    private PhoneUpdater() {
    }

    /** Build number of the installed app (0 for local builds) */
    static int currentBuild(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return (int) Math.max(0, code - BASE_VERSION_CODE);
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    static String settingsTitle(Context context) {
        int build = currentBuild(context);
        return build > 0 ? context.getString(R.string.phone_update_check_build, build) : context.getString(R.string.phone_update_check);
    }

    static void check(Activity activity) {
        if (sBusy) {
            Toast.makeText(activity, R.string.phone_update_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        sBusy = true;
        Toast.makeText(activity, R.string.phone_update_checking, Toast.LENGTH_SHORT).show();
        Handler main = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            Release release = null;
            try {
                release = fetchLatest();
            } catch (Throwable e) {
                // handled below
            }
            final Release result = release;
            main.post(() -> {
                sBusy = false;
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (result == null) {
                    dialog(activity).setTitle(R.string.phone_update_error_title)
                            .setMessage(R.string.phone_update_error_network)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
                int current = currentBuild(activity);
                if (current > 0 && result.build <= current) {
                    dialog(activity).setTitle(R.string.phone_update_none_title)
                            .setMessage(activity.getString(R.string.phone_update_none, current))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
                String size = result.size > 0 ? String.format(Locale.US, "%.1f MB", result.size / 1048576f) : "";
                dialog(activity).setTitle(R.string.phone_update_available_title)
                        .setMessage(activity.getString(R.string.phone_update_available, result.build, current > 0 ? String.valueOf(current) : "?", size))
                        .setPositiveButton(R.string.phone_update_install, (d, w) -> startInstall(activity, result))
                        .setNegativeButton(R.string.phone_update_later, null)
                        .show();
            });
        }, "PhoneUpdater").start();
    }

    private static void startInstall(Activity activity, Release release) {
        // Android asks once per app before it may install updates
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            dialog(activity).setTitle(R.string.phone_update_permission_title)
                    .setMessage(R.string.phone_update_permission)
                    .setPositiveButton(R.string.phone_update_open_settings, (d, w) -> {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName())));
                        } catch (Throwable e) {
                            Toast.makeText(activity, R.string.phone_update_permission, Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        // Progress dialog
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(true);
        TextView label = new TextView(activity);
        label.setPadding(0, pad / 2, 0, 0);
        label.setText(R.string.phone_update_downloading);
        box.addView(bar);
        box.addView(label);
        final boolean[] cancelled = {false};
        AlertDialog progress = dialog(activity).setTitle(R.string.phone_update_downloading_title)
                .setView(box)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> cancelled[0] = true)
                .show();

        sBusy = true;
        Context app = activity.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            PackageInstaller installer = app.getPackageManager().getPackageInstaller();
            int sessionId = -1;
            String error = null;
            try {
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(app.getPackageName());
                if (release.size > 0) {
                    params.setSize(release.size);
                }
                sessionId = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    HttpURLConnection connection = open(release.apkUrl);
                    long total = connection.getContentLengthLong() > 0 ? connection.getContentLengthLong() : release.size;
                    try (InputStream in = connection.getInputStream();
                         OutputStream out = session.openWrite(APK_NAME, 0, total > 0 ? total : -1)) {
                        byte[] buffer = new byte[64 * 1024];
                        long done = 0;
                        int lastPercent = -1;
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            if (cancelled[0]) {
                                throw new InterruptedException("cancelled");
                            }
                            out.write(buffer, 0, read);
                            done += read;
                            if (total > 0) {
                                int percent = (int) (done * 100 / total);
                                if (percent != lastPercent) {
                                    lastPercent = percent;
                                    final long doneMb = done;
                                    main.post(() -> {
                                        bar.setIndeterminate(false);
                                        bar.setProgress(percent);
                                        label.setText(String.format(Locale.US, "%d%%  ·  %.1f of %.1f MB", percent,
                                                doneMb / 1048576f, total / 1048576f));
                                    });
                                }
                            }
                        }
                        session.fsync(out);
                    } finally {
                        connection.disconnect();
                    }

                    Intent status = new Intent(ACTION_INSTALL_STATUS).setPackage(app.getPackageName());
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent pending = PendingIntent.getBroadcast(app, sessionId, status, flags);
                    session.commit(pending.getIntentSender());
                }
            } catch (InterruptedException e) {
                error = "";
            } catch (Throwable e) {
                error = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            }
            if (error != null && sessionId != -1) {
                try {
                    installer.abandonSession(sessionId);
                } catch (Throwable ignored) {
                    // already gone
                }
            }
            final String failure = error;
            main.post(() -> {
                sBusy = false;
                try {
                    progress.dismiss();
                } catch (Throwable ignored) {
                    // activity gone
                }
                if (failure != null && !failure.isEmpty()) {
                    Toast.makeText(app, app.getString(R.string.phone_update_failed, failure), Toast.LENGTH_LONG).show();
                }
            });
        }, "PhoneUpdaterDownload").start();
    }

    /** Receives the installer's answer: shows Android's confirmation screen, or explains a failure */
    public static class InstallReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(confirm);
                    } catch (Throwable e) {
                        Toast.makeText(context, context.getString(R.string.phone_update_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                }
            } else if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
                Toast.makeText(context, R.string.phone_update_conflict, Toast.LENGTH_LONG).show();
            } else if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(context, context.getString(R.string.phone_update_failed, message != null ? message : String.valueOf(status)),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // region GitHub

    private static final class Release {
        int build;
        String apkUrl;
        long size;
    }

    private static Release fetchLatest() throws Exception {
        HttpURLConnection connection = open(LATEST_URL);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        String body;
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
            }
            body = bytes.toString("UTF-8");
        } finally {
            connection.disconnect();
        }

        JSONObject json = new JSONObject(body);
        String tag = json.optString("tag_name", "");
        if (!tag.startsWith("build-")) {
            return null;
        }
        Release release = new Release();
        release.build = Integer.parseInt(tag.substring("build-".length()));
        JSONArray assets = json.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (APK_NAME.equals(asset.optString("name"))) {
                    release.apkUrl = asset.optString("browser_download_url");
                    release.size = asset.optLong("size", 0);
                }
            }
        }
        if (release.apkUrl == null || release.apkUrl.isEmpty()) {
            release.apkUrl = "https://github.com/" + REPO + "/releases/download/" + tag + "/" + APK_NAME;
        }
        return release;
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "SmartTube-Phone-Updater");
        int code = connection.getResponseCode();
        // GitHub sends release files from another host: follow that redirect by hand if needed
        for (int hops = 0; hops < 5 && code >= 300 && code < 400; hops++) {
            String next = connection.getHeaderField("Location");
            connection.disconnect();
            connection = (HttpURLConnection) new URL(new URL(url), next).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "SmartTube-Phone-Updater");
            code = connection.getResponseCode();
        }
        if (code != 200) {
            connection.disconnect();
            throw new java.io.IOException("HTTP " + code);
        }
        return connection;
    }

    // endregion

    private static AlertDialog.Builder dialog(Activity activity) {
        return new AlertDialog.Builder(activity, PhoneTheme.isNight(activity)
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }
}
