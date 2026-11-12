package com.liskovsoft.smartyoutubetv2.tv.phone;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;

/**
 * A row of the phone's Settings list: SmartTube's {@link SettingsItem} (icon, title, action) plus what a phone
 * settings screen shows on the right: the current value ("Dark") or a switch. Also the heading of a group of rows.
 */
final class PhoneSetting extends SettingsItem {
    /** Shown on the right, e.g. the chosen theme; null for none */
    final String value;
    /** A switch in this state; null for a row without a switch */
    final Boolean checked;
    final boolean header;

    private PhoneSetting(String title, Runnable onClick, int icon, String value, Boolean checked, boolean header) {
        super(title, onClick, icon);
        this.value = value;
        this.checked = checked;
        this.header = header;
    }

    static PhoneSetting header(String title) {
        return new PhoneSetting(title, null, -1, null, null, true);
    }

    static PhoneSetting row(String title, int icon, Runnable onClick) {
        return new PhoneSetting(title, onClick, icon, null, null, false);
    }

    static PhoneSetting value(String title, String value, int icon, Runnable onClick) {
        return new PhoneSetting(title, onClick, icon, value, null, false);
    }

    static PhoneSetting toggle(String title, boolean checked, int icon, Runnable onClick) {
        return new PhoneSetting(title, onClick, icon, null, checked, false);
    }
}
