package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.content.res.ColorStateList;
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
 * Small floating card on Home: "Update available · Build N  [Update]". Opens the update screen; slides away by
 * itself after a few seconds.
 */
final class PhoneUpdateBanner {
    private static final long SHOW_FOR_MS = 12_000;

    private PhoneUpdateBanner() {
    }

    static void show(Activity activity, int build) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (14 * density);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setColor(activity.getResources().getColor(R.color.phone_surface_2));
        background.setCornerRadius(18 * density);
        card.setBackground(background);
        card.setElevation(10 * density);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_phone_update);
        icon.setImageTintList(ColorStateList.valueOf(activity.getResources().getColor(R.color.phone_accent)));
        card.addView(icon, new LinearLayout.LayoutParams((int) (24 * density), (int) (24 * density)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(activity);
        title.setText(R.string.phone_update_available_title);
        title.setTextSize(15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(activity.getResources().getColor(R.color.phone_text));
        TextView subtitle = new TextView(activity);
        subtitle.setText(activity.getString(R.string.phone_update_banner, build));
        subtitle.setTextSize(13);
        subtitle.setTextColor(activity.getResources().getColor(R.color.phone_text_2));
        texts.addView(title);
        texts.addView(subtitle);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = pad;
        card.addView(texts, textsParams);

        TextView button = new TextView(activity);
        button.setText(R.string.phone_update_banner_button);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(0xFFFFFFFF);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.phone_primary_button_bg);
        button.setPadding((int) (18 * density), 0, (int) (18 * density), 0);
        card.addView(button, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (38 * density)));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        int side = (int) (12 * density);
        params.leftMargin = side;
        params.rightMargin = side;
        View bottomBar = activity.findViewById(R.id.phone_bottom_bar);
        params.bottomMargin = (bottomBar != null ? bottomBar.getHeight() : 0) + side;
        content.addView(card, params);

        Runnable hide = () -> card.animate().alpha(0f).translationY(24 * density).setDuration(200)
                .withEndAction(() -> content.removeView(card)).start();
        View.OnClickListener open = v -> {
            content.removeView(card);
            PhoneUpdater.open(activity);
        };
        card.setOnClickListener(open);
        button.setOnClickListener(open);

        card.setAlpha(0f);
        card.setTranslationY(24 * density);
        card.animate().alpha(1f).translationY(0f).setDuration(250).start();
        card.postDelayed(hide, SHOW_FOR_MS);
    }
}
