package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Keeps the last crash and shows it on the next launch, so it can be copied and reported.
 */
final class CrashReport {
    private static final String FILE = "phone_last_crash.txt";

    private CrashReport() {
    }

    static void install(Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String text = "SmartTube Phone crash\n"
                        + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                        + "Thread: " + thread.getName() + "\n\n"
                        + Log.getStackTraceString(error);
                try (FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), FILE))) {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable ignored) {
                // Never fail inside the crash handler
            }

            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    static void showIfAny(Activity activity) {
        File file = new File(activity.getFilesDir(), FILE);
        if (!file.exists()) {
            return;
        }

        String text;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 64 * 1024)];
            int read = in.read(data);
            text = new String(data, 0, Math.max(0, read), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            text = null;
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        if (text == null || text.isEmpty()) {
            return;
        }

        final String report = text;
        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("The app crashed last time")
                .setMessage(report)
                .setPositiveButton("Copy", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("crash", report));
                        Toast.makeText(activity, "Crash report copied", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }
}
