package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.phone.widget.RatioFrameLayout;

/**
 * Grey placeholder cards that pulse while a feed loads (like the YouTube app), shaped like the real cards:
 * a 16:9 thumbnail, a round channel picture and two lines of text.
 */
final class PhoneSkeleton extends LinearLayout {
    private static final int CARDS = 4;
    private ObjectAnimator mPulse;

    PhoneSkeleton(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        float density = getResources().getDisplayMetrics().density;
        int color = getResources().getColor(R.color.phone_surface_2);
        int side = (int) (12 * density);

        for (int i = 0; i < CARDS; i++) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(VERTICAL);
            card.setPadding(0, 0, 0, (int) (22 * density));

            RatioFrameLayout thumb = new RatioFrameLayout(context);
            thumb.setBackground(block(color, 12 * density, false));
            LayoutParams thumbParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            thumbParams.leftMargin = side;
            thumbParams.rightMargin = side;
            card.addView(thumb, thumbParams);

            LinearLayout details = new LinearLayout(context);
            details.setOrientation(HORIZONTAL);
            details.setPadding(side, side, side, 0);

            View avatar = new View(context);
            avatar.setBackground(block(color, 0, true));
            details.addView(avatar, new LayoutParams((int) (38 * density), (int) (38 * density)));

            LinearLayout lines = new LinearLayout(context);
            lines.setOrientation(VERTICAL);
            LayoutParams linesParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            linesParams.leftMargin = side;
            linesParams.rightMargin = (int) (32 * density);
            details.addView(lines, linesParams);

            View first = new View(context);
            first.setBackground(block(color, 7 * density, false));
            lines.addView(first, new LayoutParams(LayoutParams.MATCH_PARENT, (int) (14 * density)));

            View second = new View(context);
            second.setBackground(block(color, 7 * density, false));
            LayoutParams secondParams = new LayoutParams(LayoutParams.MATCH_PARENT, (int) (14 * density));
            secondParams.topMargin = (int) (8 * density);
            secondParams.rightMargin = (int) (60 * density);
            lines.addView(second, secondParams);

            card.addView(details);
            addView(card, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private static GradientDrawable block(int color, float radius, boolean oval) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        if (oval) {
            drawable.setShape(GradientDrawable.OVAL);
        } else {
            drawable.setCornerRadius(radius);
        }
        return drawable;
    }

    /** Shows the placeholders with a gentle pulse */
    void start() {
        animate().cancel();
        setVisibility(VISIBLE);
        setAlpha(1f);
        if (mPulse == null) {
            mPulse = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0.45f);
            mPulse.setDuration(750);
            mPulse.setRepeatMode(ValueAnimator.REVERSE);
            mPulse.setRepeatCount(ValueAnimator.INFINITE);
            mPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        }
        if (!mPulse.isStarted()) {
            mPulse.start();
        }
    }

    /** Fades the placeholders out (the real cards appear underneath) */
    void stop() {
        if (getVisibility() != VISIBLE) {
            return;
        }
        if (mPulse != null) {
            mPulse.cancel();
        }
        animate().alpha(0f).setDuration(160).withEndAction(() -> setVisibility(GONE)).start();
    }

    boolean isShowing() {
        return getVisibility() == VISIBLE && (mPulse != null && mPulse.isStarted());
    }

    static FrameLayout.LayoutParams layoutParams() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }
}
