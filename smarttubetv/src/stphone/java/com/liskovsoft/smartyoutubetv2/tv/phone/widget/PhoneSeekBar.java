package com.liskovsoft.smartyoutubetv2.tv.phone.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SeekBar;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;

import java.util.ArrayList;
import java.util.List;

public class PhoneSeekBar extends SeekBar {
    private final List<SeekBarSegment> mSegments = new ArrayList<>();
    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBufferPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSegmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private boolean mIsDragging;

    public PhoneSeekBar(Context context) {
        super(context);
        init();
    }

    public PhoneSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PhoneSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Transparent native thumb so native AbsSeekBar handles touch tracking
        // without drawing any misaligned or clipped native framework thumb
        setThumb(new ColorDrawable(0x00000000));
        setThumbOffset(0);
        setSplitTrack(false);

        mBgPaint.setColor(0x4DFFFFFF);       // 30% translucent white
        mBufferPaint.setColor(0x80FFFFFF);   // 50% translucent white
        mProgressPaint.setColor(0xFFFF0033); // YouTube Red
        mThumbPaint.setColor(0xFFFF0033);    // YouTube Red
        mThumbGlowPaint.setColor(0x55FF0033);
        mThumbBorderPaint.setColor(0xFFFFFFFF);
        mThumbBorderPaint.setStyle(Paint.Style.STROKE);
    }

    public void setSegments(List<SeekBarSegment> segments) {
        mSegments.clear();
        if (segments != null) {
            mSegments.addAll(segments);
        }
        invalidate();
    }

    public void setDragging(boolean dragging) {
        mIsDragging = dragging;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsDragging = true;
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsDragging = false;
                invalidate();
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        // We draw the entire seekbar cleanly and deterministically on canvas:
        // Background track -> Buffered -> Progress -> SponsorBlocks -> Thumb point
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int width = getWidth() - paddingLeft - paddingRight;
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float trackHeight = 3.5f * density;
        float centerY = height / 2f;
        float top = centerY - trackHeight / 2f;
        float bottom = centerY + trackHeight / 2f;
        float cornerRadius = trackHeight / 2f;

        float left = paddingLeft;
        float right = paddingLeft + width;

        int max = Math.max(1, getMax());
        float progressFraction = Math.max(0f, Math.min(1f, (float) getProgress() / max));
        float bufferFraction = Math.max(0f, Math.min(1f, (float) getSecondaryProgress() / max));

        float progressX = left + progressFraction * width;
        float bufferX = left + bufferFraction * width;

        // 1. Background track (full width)
        mRect.set(left, top, right, bottom);
        canvas.drawRoundRect(mRect, cornerRadius, cornerRadius, mBgPaint);

        // 2. Buffered progress
        if (bufferX > left) {
            mRect.set(left, top, Math.min(right, bufferX), bottom);
            canvas.drawRoundRect(mRect, cornerRadius, cornerRadius, mBufferPaint);
        }

        // 3. Watched progress (red)
        if (progressX > left) {
            mRect.set(left, top, Math.min(right, progressX), bottom);
            canvas.drawRoundRect(mRect, cornerRadius, cornerRadius, mProgressPaint);
        }

        // 4. SponsorBlock segments
        for (SeekBarSegment segment : mSegments) {
            if (segment == null || segment.endProgress <= segment.startProgress) {
                continue;
            }
            float segStart = Math.max(0f, Math.min(1f, segment.startProgress));
            float segEnd = Math.max(0f, Math.min(1f, segment.endProgress));
            float sLeft = left + segStart * width;
            float sRight = left + segEnd * width;
            if (sRight - sLeft < 2f * density) {
                sRight = sLeft + 2f * density;
            }
            mSegmentPaint.setColor(segment.color);
            mRect.set(sLeft, top, sRight, bottom);
            canvas.drawRoundRect(mRect, cornerRadius, cornerRadius, mSegmentPaint);
        }

        // 5. Scrubber thumb circle ("Punkt")
        float thumbRadius = (mIsDragging ? 7.5f : 6.0f) * density;
        mThumbBorderPaint.setStrokeWidth(1.2f * density);

        if (mIsDragging) {
            canvas.drawCircle(progressX, centerY, thumbRadius + 5f * density, mThumbGlowPaint);
        }
        canvas.drawCircle(progressX, centerY, thumbRadius, mThumbPaint);
        canvas.drawCircle(progressX, centerY, thumbRadius, mThumbBorderPaint);
    }
}
