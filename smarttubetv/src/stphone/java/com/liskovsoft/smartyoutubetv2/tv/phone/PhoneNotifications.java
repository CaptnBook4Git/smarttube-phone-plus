package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The app's own notifications (playback controls in the shade and on the lock screen, update alerts).
 * Android 13+ asks for permission; once denied, Android only lets the user turn them on in its settings.
 */
final class PhoneNotifications {
    /** Request code of a permission request made from Settings (Home's first launch request uses another one) */
    static final int REQUEST_FROM_SETTINGS = 4713;
    private static final String PLAYBACK_CHANNEL = "phone_playback";
    private static final String KEY_ASKED = "asked_notifications";

    private PhoneNotifications() {
    }

    static boolean isEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = manager != null ? manager.getNotificationChannel(PLAYBACK_CHANNEL) : null;
            if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                return false;
            }
        }
        return true;
    }

    static String settingsTitle(Context context) {
        return context.getString(isEnabled(context) ? R.string.phone_notifications_on : R.string.phone_notifications_off);
    }

    /**
     * Settings row tapped: ask for the permission when Android still shows its dialog, otherwise open the app's
     * notification settings.
     */
    static void open(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences prefs = activity.getSharedPreferences("phone_defaults", Context.MODE_PRIVATE);
            boolean asked = prefs.getBoolean(KEY_ASKED, false);
            if (!asked || activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                prefs.edit().putBoolean(KEY_ASKED, true).apply();
                activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_FROM_SETTINGS);
                return;
            }
        }
        openSystemSettings(activity);
    }

    /**
     * Called with the answer to a request made from Settings: still denied and Android won't ask again, so go to its
     * settings page instead of doing nothing.
     */
    static void onPermissionResult(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33 && !isEnabled(activity)
                && !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
            openSystemSettings(activity);
        }
    }

    static void openSystemSettings(Activity activity) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 26) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.getPackageName(), null));
        }
        try {
            activity.startActivity(intent);
            if (!isEnabled(activity)) {
                Toast.makeText(activity, R.string.phone_notifications_hint, Toast.LENGTH_LONG).show();
            }
        } catch (RuntimeException e) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", activity.getPackageName(), null)));
            } catch (RuntimeException ignored) {
                // no settings app
            }
        }
    }
}
