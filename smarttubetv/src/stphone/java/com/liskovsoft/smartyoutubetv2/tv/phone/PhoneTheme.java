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

    /** The mode's name alone ("Dark"), for the value shown on the settings row */
    static int valueRes(int mode) {
        return mode == LIGHT ? R.string.phone_theme_light : mode == SYSTEM ? R.string.phone_theme_system : R.string.phone_theme_dark;
    }

    /**
     * Context with the chosen day/night mode applied (used in attachBaseContext).
     */
    /**
     * Android 12+ (and ColorOS's bolder text) thickens every font, also the app's own Roboto: titles came out
     * noticeably bolder than in the YouTube app, which keeps its text at its normal weight. The app does the same.
     */
    static void clearFontWeightAdjustment(Configuration config) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            config.fontWeightAdjustment = 0;
        }
    }

    private static int fontWeightAdjustment(Configuration config) {
        return android.os.Build.VERSION.SDK_INT >= 31 ? config.fontWeightAdjustment : 0;
    }

    static Context wrap(Context base) {
        if (base == null) {
            return null;
        }
        int mode = get(base);
        boolean adjusted = fontWeightAdjustment(base.getResources().getConfiguration()) != 0;
        if (!adjusted && (mode == SYSTEM || (mode == DARK) == isNight(base))) {
            return base; // already what the phone uses: leave the context untouched
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        clearFontWeightAdjustment(config);
        if (mode == SYSTEM || (mode == DARK) == isNight(base)) {
            return base.createConfigurationContext(config);
        }
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (mode == LIGHT ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES);
        return base.createConfigurationContext(config);
    }

    /**
     * SmartTube re-applies the system configuration on create/resume (locale, dpi), which resets day/night.
     * Put the chosen mode back on the activity resources afterwards.
     */
    static void apply(android.app.Activity activity) {
        int mode = get(activity);
        android.content.res.Resources resources = activity.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        boolean changed = fontWeightAdjustment(config) != 0; // SmartTube's re-applied configuration brings it back
        clearFontWeightAdjustment(config);
        if (mode != SYSTEM) {
            int wanted = mode == LIGHT ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES;
            if ((config.uiMode & Configuration.UI_MODE_NIGHT_MASK) != wanted) {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | wanted;
                changed = true;
            }
        }
        if (changed) {
            resources.updateConfiguration(config, resources.getDisplayMetrics());
        }
    }

    static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
