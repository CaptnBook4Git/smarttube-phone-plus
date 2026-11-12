package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * SmartTube settings that only do something on a TV: a remote's buttons, the TV's cards and grids, the display's
 * refresh rate, pairing as a cast target. The phone UI never reads them, so they're left out of the phone's settings.
 */
final class PhoneTvOnly {
    /** Rows of the Settings list */
    private static final int[] SETTINGS = {
            R.string.settings_remote_control, // this device as a cast target, paired with a TV code
            R.string.auto_frame_rate,         // switches the TV's refresh rate to the video's
    };

    /** Categories inside the settings pages */
    private static final int[] CATEGORIES = {
            R.string.key_remapping,              // General: buttons of the remote
            R.string.player_ok_button_behavior,  // Player: the remote's OK button
            R.string.various_buttons,            // Interface: buttons above the TV's video grid
            R.string.card_text_scroll_factor,    // Interface: title scrolling on a focused TV card
            R.string.video_grid_scale,           // Interface: size of the TV's video grid
    };

    private PhoneTvOnly() {
    }

    static boolean isSetting(Context context, CharSequence title) {
        return matches(context, title, SETTINGS);
    }

    static boolean isCategory(Context context, CharSequence title) {
        return matches(context, title, CATEGORIES);
    }

    private static boolean matches(Context context, CharSequence title, int[] titles) {
        if (TextUtils.isEmpty(title)) {
            return false;
        }
        for (int resId : titles) {
            if (TextUtils.equals(title, context.getString(resId))) {
                return true;
            }
        }
        return false;
    }
}
