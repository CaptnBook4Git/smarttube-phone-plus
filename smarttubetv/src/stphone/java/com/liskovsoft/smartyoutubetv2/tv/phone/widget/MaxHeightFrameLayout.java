package com.liskovsoft.smartyoutubetv2.tv.phone.widget;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Wraps its content but never grows taller than a share of the screen (bottom sheets).
 */
public class MaxHeightFrameLayout extends FrameLayout {
    private float mMaxScreenFraction = 0.72f;

    public MaxHeightFrameLayout(Context context) {
        super(context);
    }

    public MaxHeightFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MaxHeightFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setMaxScreenFraction(float fraction) {
        mMaxScreenFraction = fraction;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxHeight = (int) (Resources.getSystem().getDisplayMetrics().heightPixels * mMaxScreenFraction);
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int size = MeasureSpec.getSize(heightMeasureSpec);
        if (mode == MeasureSpec.UNSPECIFIED || size > maxHeight) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
