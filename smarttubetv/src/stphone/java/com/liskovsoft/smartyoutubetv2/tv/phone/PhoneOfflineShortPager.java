package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

/** Pages the local player and an adjacent preview together while the user drags a Short. */
final class PhoneOfflineShortPager implements View.OnTouchListener {
    private final PhoneLocalPlayerActivity activity;
    private final View page;
    private final ImageView preview;
    private final int slop;
    private final int flickVelocity;
    private final Runnable frameTimeout = this::frameReady;
    private VelocityTracker velocity;
    private ValueAnimator animation;
    private float startX;
    private float startY;
    private int direction;
    private boolean tap;
    private boolean waitingForFrame;

    PhoneOfflineShortPager(PhoneLocalPlayerActivity activity, View page, View overlay) {
        this.activity = activity;
        this.page = page;
        slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        flickVelocity = Math.round(300 * activity.getResources().getDisplayMetrics().density);
        FrameLayout stage = (FrameLayout) page.getParent();
        preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.BLACK);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        preview.setVisibility(View.GONE);
        stage.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        overlay.setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(View overlay, MotionEvent event) {
        if (animation != null || waitingForFrame) {
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                releaseVelocity();
                velocity = VelocityTracker.obtain();
                track(event);
                startX = event.getRawX();
                startY = event.getRawY();
                direction = 0;
                tap = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (velocity == null) break;
                track(event);
                float dx = event.getRawX() - startX;
                float dy = event.getRawY() - startY;
                if (tap && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    tap = false;
                    if (Math.abs(dy) > Math.abs(dx)) {
                        direction = dy < 0 ? 1 : -1;
                        showPreview(direction);
                    }
                }
                if (direction != 0) {
                    float displacement = Math.min(page.getHeight(), Math.max(0f, -dy * direction));
                    setOffset(-direction * displacement * (hasPreview() ? 1f : 0.22f));
                }
                break;
            case MotionEvent.ACTION_UP:
                if (velocity == null) break;
                if (tap) {
                    if (Math.abs(event.getRawX() - startX) <= slop && Math.abs(event.getRawY() - startY) <= slop) {
                        overlay.performClick();
                    }
                } else if (direction != 0) {
                    track(event);
                    velocity.computeCurrentVelocity(1000);
                    float distance = (startY - event.getRawY()) * direction;
                    boolean flick = distance > slop && -velocity.getYVelocity() * direction >= flickVelocity;
                    boolean advance = hasPreview() && (distance >= page.getHeight() * 0.14f || flick);
                    settle(advance);
                }
                releaseVelocity();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                releaseVelocity();
                if (direction != 0) settle(false);
                break;
            default:
                break;
        }
        return true;
    }

    boolean navigate(int step) {
        if (animation != null || waitingForFrame || !activity.canStep(step)) {
            return false;
        }
        direction = step;
        showPreview(step);
        settle(true);
        return true;
    }

    private void showPreview(int step) {
        Uri uri = activity.adjacentShort(step);
        if (uri == null) return;
        preview.setTranslationY(step * page.getHeight());
        preview.setVisibility(View.VISIBLE);
        Glide.with(activity).load(uri).fitCenter().into(preview);
    }

    private boolean hasPreview() {
        return preview.getVisibility() == View.VISIBLE;
    }

    private void setOffset(float offset) {
        page.setTranslationY(offset);
        if (hasPreview()) {
            preview.setTranslationY(offset + direction * page.getHeight());
        }
    }

    private void settle(boolean advance) {
        float target = advance ? -direction * page.getHeight() : 0f;
        animation = ValueAnimator.ofFloat(page.getTranslationY(), target);
        animation.setDuration(advance ? 210 : 180);
        animation.setInterpolator(new DecelerateInterpolator(1.6f));
        animation.addUpdateListener(animator -> setOffset((float) animator.getAnimatedValue()));
        animation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animator) {
                animation = null;
                if (advance) {
                    // Keep the local preview over the newly selected page until its first frame arrives.
                    waitingForFrame = true;
                    if (activity.step(direction)) {
                        page.postDelayed(frameTimeout, 900);
                    } else {
                        frameReady();
                    }
                } else {
                    clearPreview();
                }
                page.setTranslationY(0);
                direction = 0;
            }
        });
        animation.start();
    }

    void frameReady() {
        if (waitingForFrame) {
            waitingForFrame = false;
            page.removeCallbacks(frameTimeout);
            clearPreview();
        }
    }

    void reset() {
        if (animation != null) {
            animation.removeAllListeners();
            animation.cancel();
            animation = null;
        }
        releaseVelocity();
        direction = 0;
        waitingForFrame = false;
        page.removeCallbacks(frameTimeout);
        page.setTranslationY(0);
        clearPreview();
    }

    private void clearPreview() {
        Glide.with(preview).clear(preview);
        preview.setVisibility(View.GONE);
    }

    private void track(MotionEvent event) {
        // The page translates while being dragged, so record velocity in screen coordinates.
        MotionEvent screenEvent = MotionEvent.obtain(event);
        screenEvent.setLocation(event.getRawX(), event.getRawY());
        velocity.addMovement(screenEvent);
        screenEvent.recycle();
    }

    private void releaseVelocity() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }
}
