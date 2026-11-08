package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * "Liquid Glass" look: translucent bars over the feed, frosted chips, pills and sheets.
 */
final class PhoneGlass {
    private static final String PREFS = "phone_ui";
    private static final String KEY = "liquid_glass";

    private PhoneGlass() {
    }

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply();
    }

    static int chip(Context context) {
        return isEnabled(context) ? R.drawable.phone_glass_chip : R.drawable.phone_chip_bg;
    }

    static int pill(Context context) {
        return isEnabled(context) ? R.drawable.phone_glass_pill : R.drawable.phone_pill_bg;
    }

    static int sheet(Context context) {
        return isEnabled(context) ? R.drawable.phone_glass_sheet : R.drawable.phone_sheet_bg;
    }
}
