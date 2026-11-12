package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Incognito: videos watched meanwhile aren't added to the YouTube watch history (SmartTube's "history off" state).
 * Turning it off puts the history setting back to what it was. The account's history itself is never changed or
 * cleared, and search history is left alone (SmartTube's "search history off" would delete it).
 */
final class PhoneIncognito {
    private static final String PREFS = "phone_defaults";
    private static final String KEY_ON = "incognito_on";
    private static final String KEY_HISTORY_BEFORE = "incognito_history_before";

    private PhoneIncognito() {
    }

    static boolean isOn(Context context) {
        return prefs(context).getBoolean(KEY_ON, false);
    }

    static void toggle(Context context) {
        GeneralData data = GeneralData.instance(context);
        SharedPreferences prefs = prefs(context);
        if (isOn(context)) {
            int before = prefs.getInt(KEY_HISTORY_BEFORE, GeneralData.HISTORY_AUTO);
            data.setHistoryState(before); // what it was, also "off" if the user had turned history off
            prefs.edit().putBoolean(KEY_ON, false).apply();
            android.widget.Toast.makeText(context, R.string.phone_incognito_ended, android.widget.Toast.LENGTH_SHORT).show();
        } else {
            prefs.edit().putInt(KEY_HISTORY_BEFORE, data.getHistoryState()).putBoolean(KEY_ON, true).apply();
            data.setHistoryState(GeneralData.HISTORY_DISABLED);
            android.widget.Toast.makeText(context, R.string.phone_incognito_started, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
