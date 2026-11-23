package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings > What's new: what this version brings, in plain words (only this version, not older ones). A dot on the Settings row until the
 * page is read; once after every update of the app, a card on Home with the newest ones.
 *
 * Release copy comes from .github/whats-new.md for both phone channels.
 */
final class PhoneWhatsNew {
    /** Increment when phone release notes change. */
    private static final int VERSION = 13;

    private static final String PREFS = "phone_whats_new";
    private static final String KEY_READ = "read";
    private static final String KEY_SHOWN = "shown";
    /** The app build the card was last shown for: it shows once after each update */
    private static final String KEY_SHOWN_BUILD = "shown_build";

    static final class Feature {
        final int icon;
        final String title;
        final String text;

        Feature(int icon, String title, String text) {
            this.icon = icon;
            this.title = title;
            this.text = text;
        }
    }

    private static final Feature[] LATEST_FEATURES = releaseFeatures();

    private PhoneWhatsNew() {
    }

    private static Feature[] releaseFeatures() {
        List<String> notes = new ArrayList<>();
        PhoneUpdater.parseNotes(BuildConfig.PHONE_WHATS_NEW, notes);
        Feature[] features = new Feature[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            String note = notes.get(i);
            int separator = note.indexOf(" — ");
            String title = separator >= 0 ? note.substring(0, separator) : note;
            String text = separator >= 0 ? note.substring(separator + 3) : "";
            features[i] = new Feature(R.drawable.ic_phone_update, title, text);
        }
        return features;
    }

    static Feature[] latestFeatures() {
        return LATEST_FEATURES;
    }

    private static int noticeVersion() {
        return BuildConfig.PHONE_BETA ? BuildConfig.VERSION_CODE : VERSION;
    }

    static boolean hasUnread(Context context) {
        return LATEST_FEATURES.length > 0 && prefs(context).getInt(KEY_READ, 0) < noticeVersion();
    }

    static void markRead(Context context) {
        int version = noticeVersion();
        prefs(context).edit().putInt(KEY_READ, version).putInt(KEY_SHOWN, version).apply();
    }

    private static long buildNumber(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Once after every update of the app (each new build, once): a card with the newest features. Not on a fresh
     * install: everything is new then, and the page is there to read.
     */
    static void showAfterUpdate(Activity activity) {
        SharedPreferences prefs = prefs(activity);
        long build = buildNumber(activity);
        long shownFor = prefs.getLong(KEY_SHOWN_BUILD, 0);
        if (build <= 0 || shownFor >= build || activity.isFinishing()) {
            return;
        }
        int version = noticeVersion();
        prefs.edit().putLong(KEY_SHOWN_BUILD, build).putInt(KEY_SHOWN, version).apply();
        if (isFreshInstall(activity)) {
            prefs.edit().putInt(KEY_READ, version).apply();
            return;
        }
        if (LATEST_FEATURES.length > 0) {
            showCard(activity);
        }
    }

    private static boolean isFreshInstall(Context context) {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.firstInstallTime == info.lastUpdateTime;
        } catch (Exception e) {
            return false;
        }
    }

    private static void showCard(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        float d = activity.getResources().getDisplayMetrics().density;
        int text = activity.getResources().getColor(R.color.phone_text);
        int text2 = activity.getResources().getColor(R.color.phone_text_2);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.phone_sheet_bg);
        card.setPadding(px(d, 24), px(d, 24), px(d, 24), px(d, 16));

        TextView title = new TextView(activity);
        title.setText(R.string.phone_whats_new_title);
        title.setTextColor(text);
        title.setTextSize(20);
        title.setTypeface(PhoneUi.medium(activity));
        card.addView(title);

        TextView sub = new TextView(activity);
        sub.setText(R.string.phone_whats_new_updated);
        sub.setTextColor(text2);
        sub.setTextSize(14);
        sub.setPadding(0, px(d, 6), 0, px(d, 10));
        card.addView(sub);

        ScrollView changes = new ScrollView(activity);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (Feature feature : LATEST_FEATURES) {
            rows.addView(featureRow(activity, feature, d, text, text2));
        }
        changes.addView(rows);
        card.addView(changes, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, px(d, 12), 0, 0);
        TextView all = button(activity, R.string.phone_whats_new_see_all, false, d);
        TextView ok = button(activity, R.string.phone_whats_new_got_it, true, d);
        all.setOnClickListener(v -> {
            dialog.dismiss();
            PhoneWhatsNewActivity.open(activity);
        });
        ok.setOnClickListener(v -> {
            markRead(activity);
            dialog.dismiss();
        });
        buttons.addView(all);
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(d, 40));
        okParams.leftMargin = px(d, 8);
        buttons.addView(ok, okParams);
        card.addView(buttons);

        dialog.setContentView(card);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    /** One feature: its icon, title and the line explaining it (the card and the page) */
    static View featureRow(Context context, Feature feature, float d, int text, int text2) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, px(d, 10), 0, px(d, 10));
        ImageView icon = new ImageView(context);
        icon.setImageResource(feature.icon);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(text));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(px(d, 24), px(d, 24));
        iconParams.rightMargin = px(d, 16);
        iconParams.topMargin = px(d, 2);
        row.addView(icon, iconParams);
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(feature.title);
        title.setTextColor(text);
        title.setTextSize(15);
        title.setTypeface(PhoneUi.medium(context));
        words.addView(title);
        if (!feature.text.isEmpty()) {
            TextView body = new TextView(context);
            body.setText(feature.text);
            body.setTextColor(text2);
            body.setTextSize(14);
            body.setLineSpacing(px(d, 2), 1f);
            body.setPadding(0, px(d, 3), 0, 0);
            words.addView(body);
        }
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private static TextView button(Context context, int label, boolean primary, float d) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(PhoneUi.medium(context));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(px(d, 40));
        button.setPadding(px(d, 18), 0, px(d, 18), 0);
        if (primary) {
            button.setBackgroundResource(R.drawable.phone_button_bg);
            button.setTextColor(context.getResources().getColor(R.color.phone_bg));
        } else {
            button.setTextColor(context.getResources().getColor(R.color.phone_text));
        }
        return button;
    }

    private static int px(float density, int dp) {
        return Math.round(dp * density);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
