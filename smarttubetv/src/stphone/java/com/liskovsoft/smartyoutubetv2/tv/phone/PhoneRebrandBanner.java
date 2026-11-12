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
 * "Meet StreamTube" card on Home: shown once per app start until the user taps Don't show again. Got it hides it
 * until the next start.
 */
final class PhoneRebrandBanner {
    private static final String PREFS = "phone_defaults";
    private static final String KEY_NEVER = "rebrand_streamtube_never"; // Don't show again
    /** Shown (or dismissed with Got it) in this run of the app */
    private static boolean sDoneThisRun;

    private static java.lang.ref.WeakReference<View> sCard;

    private PhoneRebrandBanner() {
    }

    /** Application start (kept for the call site; nothing to remember any more) */
    static void init(Context context) {
    }

    static boolean isPending(Context context) {
        return false; // SmartTube+ does not show StreamTube rebrand banner
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
        TextView never = new TextView(activity);
        never.setText(R.string.phone_rebrand_never);
        never.setTextSize(15);
        never.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        never.setTextColor(activity.getResources().getColor(R.color.phone_text));
        never.setGravity(Gravity.CENTER);
        never.setBackgroundResource(R.drawable.phone_pill_bg);

        // Don't show again | Got it
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams neverParams = new LinearLayout.LayoutParams(0, (int) (44 * density), 1f);
        neverParams.rightMargin = (int) (6 * density);
        buttons.addView(never, neverParams);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, (int) (44 * density), 1f);
        buttonParams.leftMargin = (int) (6 * density);
        buttons.addView(button, buttonParams);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = (int) (16 * density);
        card.addView(buttons, buttonsParams);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        int side = (int) (12 * density);
        params.leftMargin = side;
        params.rightMargin = side;
        View bottomBar = activity.findViewById(R.id.phone_bottom_bar);
        params.bottomMargin = (bottomBar != null ? bottomBar.getHeight() : 0) + side;
        content.addView(card, params);
        sCard = new java.lang.ref.WeakReference<>(card);
        sDoneThisRun = true; // once per run of the app

        Runnable hide = () -> card.animate().alpha(0f).translationY(24 * density).setDuration(200)
                .withEndAction(() -> {
                    content.removeView(card);
                    if (sCard != null && sCard.get() == card) {
                        sCard = null;
                    }
                }).start();
        button.setOnClickListener(v -> hide.run()); // Got it: until the next start
        never.setOnClickListener(v -> {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_NEVER, true).apply();
            hide.run();
        });

        card.setAlpha(0f);
        card.setTranslationY(24 * density);
        card.animate().alpha(1f).translationY(0f).setDuration(250).start();
    }
}
