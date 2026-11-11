package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * One-time card on Home for people who had the app before it was renamed: "Meet StreamTube". Shown until they tap
 * Got it; new installs never see it.
 */
final class PhoneRebrandBanner {
    private static final String PREFS = "phone_defaults";
    // v2: shown once more, also to those who already tapped Got it on the first one
    private static final String KEY_PENDING = "rebrand_streamtube_v2_pending";
    private static final String KEY_DONE = "rebrand_streamtube_v2_done";
    /** Set by builds before the rename on their first start: tells an update from a new install */
    private static final String KEY_EXISTING_INSTALL = "background_playback_v1";

    private static java.lang.ref.WeakReference<View> sCard;

    private PhoneRebrandBanner() {
    }

    /** Application start, before the phone defaults are written: remember whether this is an update from SmartTube */
    static void init(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_DONE, false) || prefs.getBoolean(KEY_PENDING, false)) {
            return;
        }
        if (prefs.getBoolean(KEY_EXISTING_INSTALL, false)) {
            prefs.edit().putBoolean(KEY_PENDING, true).apply();
        } else {
            prefs.edit().putBoolean(KEY_DONE, true).apply(); // new install: it was always StreamTube
        }
    }

    static boolean isPending(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PENDING, false) && !prefs.getBoolean(KEY_DONE, false);
    }

    static boolean isShowing() {
        View card = sCard != null ? sCard.get() : null;
        return card != null && card.getParent() != null;
    }

    static void showIfPending(Activity activity) {
        if (!isPending(activity) || isShowing() || !PhoneUi.isAlive(activity)) {
            return;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (18 * density);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setColor(activity.getResources().getColor(R.color.phone_surface_2));
        background.setCornerRadius(22 * density);
        card.setBackground(background);
        card.setElevation(12 * density);
        card.setClickable(true); // taps stay on the card

        // Badge + "Meet StreamTube"
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge = new ImageView(activity);
        badge.setImageResource(R.drawable.ic_phone_brand);
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(badge, new LinearLayout.LayoutParams((int) (40 * density), (int) (40 * density)));
        TextView title = new TextView(activity);
        title.setText(R.string.phone_rebrand_title);
        title.setTextSize(22);
        Typeface brand = androidx.core.content.res.ResourcesCompat.getFont(activity, R.font.oswald_semibold);
        title.setTypeface(brand != null ? brand : Typeface.DEFAULT_BOLD);
        title.setTextColor(activity.getResources().getColor(R.color.phone_text));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = (int) (10 * density);
        header.addView(title, titleParams);
        card.addView(header);

        TextView message = new TextView(activity);
        message.setText(R.string.phone_rebrand_message);
        message.setTextSize(15);
        message.setLineSpacing(3 * density, 1f);
        message.setTextColor(activity.getResources().getColor(R.color.phone_text));
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = (int) (12 * density);
        card.addView(message, messageParams);

        TextView details = new TextView(activity);
        details.setText(R.string.phone_rebrand_details);
        details.setTextSize(14);
        details.setLineSpacing(3 * density, 1f);
        details.setTextColor(activity.getResources().getColor(R.color.phone_text_2));
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailsParams.topMargin = (int) (8 * density);
        card.addView(details, detailsParams);

        TextView button = new TextView(activity);
        button.setText(R.string.phone_rebrand_button);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(0xFFFFFFFF);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.phone_primary_button_bg);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * density));
        buttonParams.topMargin = (int) (16 * density);
        card.addView(button, buttonParams);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        int side = (int) (12 * density);
        params.leftMargin = side;
        params.rightMargin = side;
        View bottomBar = activity.findViewById(R.id.phone_bottom_bar);
        params.bottomMargin = (bottomBar != null ? bottomBar.getHeight() : 0) + side;
        content.addView(card, params);
        sCard = new java.lang.ref.WeakReference<>(card);

        button.setOnClickListener(v -> {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_DONE, true).putBoolean(KEY_PENDING, false).apply();
            card.animate().alpha(0f).translationY(24 * density).setDuration(200)
                    .withEndAction(() -> {
                        content.removeView(card);
                        if (sCard != null && sCard.get() == card) {
                            sCard = null;
                        }
                    }).start();
        });

        card.setAlpha(0f);
        card.setTranslationY(24 * density);
        card.animate().alpha(1f).translationY(0f).setDuration(250).start();
    }
}
