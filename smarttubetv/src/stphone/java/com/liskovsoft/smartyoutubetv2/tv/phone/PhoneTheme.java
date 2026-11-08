package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;
import android.content.res.Configuration;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Appearance setting for the phone screens: dark (default), light or follow the system.
 * Colors come from values (light) and values-night (dark).
 */
final class PhoneTheme {
    static final int DARK = 0;
    static final int LIGHT = 1;
    static final int SYSTEM = 2;

    private static final String PREFS = "phone_ui";
    private static final String KEY = "appearance";

    private PhoneTheme() {
    }

    static int get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, DARK);
    }

    static void set(Context context, int mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply();
    }

    static int next(int mode) {
        return mode == DARK ? LIGHT : mode == LIGHT ? SYSTEM : DARK;
    }

    static int titleRes(int mode) {
        return mode == LIGHT ? R.string.phone_appearance_light : mode == SYSTEM ? R.string.phone_appearance_system : R.string.phone_appearance_dark;
    }

    /**
     * Context with the chosen day/night mode applied (used in attachBaseContext).
     */
    static Context wrap(Context base) {
        if (base == null) {
            return null;
        }
        int mode = get(base);
        if (mode == SYSTEM) {
            return base;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (mode == LIGHT ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES);
        return base.createConfigurationContext(config);
    }

    static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
