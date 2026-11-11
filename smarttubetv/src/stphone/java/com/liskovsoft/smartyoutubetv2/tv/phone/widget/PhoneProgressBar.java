package com.liskovsoft.smartyoutubetv2.tv.phone.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.ProgressBar;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;

import java.util.ArrayList;
import java.util.List;

public class PhoneProgressBar extends ProgressBar {
    private final List<SeekBarSegment> mSegments = new ArrayList<>();
    private final Paint mSegmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mSegmentRect = new RectF();

    public PhoneProgressBar(Context context) {
        super(context);
    }

    public PhoneProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PhoneProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSegments(List<SeekBarSegment> segments) {
        mSegments.clear();
        if (segments != null) {
            mSegments.addAll(segments);
        }
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mSegments.isEmpty()) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        for (SeekBarSegment segment : mSegments) {
            if (segment == null || segment.endProgress <= segment.startProgress) {
                continue;
            }
            float start = Math.max(0f, Math.min(1f, segment.startProgress));
            float end = Math.max(0f, Math.min(1f, segment.endProgress));
            float left = start * width;
            float right = end * width;
            if (right - left < 2f * density) {
                right = left + 2f * density;
            }
            mSegmentPaint.setColor(segment.color);
            mSegmentRect.set(left, 0, right, height);
            canvas.drawRect(mSegmentRect, mSegmentPaint);
        }
    }
}
