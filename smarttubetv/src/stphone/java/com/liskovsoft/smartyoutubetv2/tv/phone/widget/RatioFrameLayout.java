package com.liskovsoft.smartyoutubetv2.tv.phone.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * FrameLayout whose height follows its width (16:9 by default) and clips children to its rounded background.
 */
public class RatioFrameLayout extends FrameLayout {
    private float mHeightToWidth = 9f / 16f;

    public RatioFrameLayout(Context context) {
        super(context);
        init();
    }

    public RatioFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RatioFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipToOutline(true);
    }

    public void setHeightToWidth(float heightToWidth) {
        if (mHeightToWidth != heightToWidth) {
            mHeightToWidth = heightToWidth;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width * mHeightToWidth);
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
