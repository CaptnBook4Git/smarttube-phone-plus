package com.liskovsoft.smartyoutubetv2.tv.phone.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatSeekBar;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;

import java.util.ArrayList;
import java.util.List;

public class PhoneSeekBar extends AppCompatSeekBar {
    private final List<SeekBarSegment> mSegments = new ArrayList<>();
    private final Paint mSegmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mSegmentRect = new RectF();

    public PhoneSeekBar(Context context) {
        super(context);
    }

    public PhoneSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PhoneSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
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

        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int width = getWidth() - paddingLeft - paddingRight;
        if (width <= 0) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float trackHeight = 4f * density;
        float centerY = getHeight() / 2f;
        float top = centerY - trackHeight / 2f;
        float bottom = centerY + trackHeight / 2f;
        float cornerRadius = 1.5f * density;

        for (SeekBarSegment segment : mSegments) {
            if (segment == null || segment.endProgress <= segment.startProgress) {
                continue;
            }
            float start = Math.max(0f, Math.min(1f, segment.startProgress));
            float end = Math.max(0f, Math.min(1f, segment.endProgress));
            float left = paddingLeft + start * width;
            float right = paddingLeft + end * width;
            if (right - left < 2f * density) {
                right = left + 2f * density;
            }
            mSegmentPaint.setColor(segment.color);
            mSegmentRect.set(left, top, right, bottom);
            canvas.drawRoundRect(mSegmentRect, cornerRadius, cornerRadius, mSegmentPaint);
        }

        Drawable thumb = getThumb();
        if (thumb != null) {
            thumb.draw(canvas);
        }
    }
}
