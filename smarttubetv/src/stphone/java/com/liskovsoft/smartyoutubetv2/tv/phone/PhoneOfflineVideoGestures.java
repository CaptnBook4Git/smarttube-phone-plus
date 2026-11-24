package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.text.NumberFormat;

/** Touches on the video surface. The overlay buttons and scrubber handle their own touch events. */
final class PhoneOfflineVideoGestures implements View.OnTouchListener {
    private final PhoneLocalPlayerActivity activity;
    private final FrameLayout surface;
    private final TextView feedback;
    private final GestureDetector taps;
    private final int slop;
    private final AudioManager audio;
    private final Runnable hideFeedback;
    private float startX;
    private float startY;
    private float startLevel;
    private boolean fullscreen;
    private boolean brightness;
    private boolean changingLevel;
    private boolean ignoreTouch;
    private boolean pullingForPip;

    PhoneOfflineVideoGestures(PhoneLocalPlayerActivity activity, FrameLayout surface) {
        this.activity = activity;
        this.surface = surface;
        slop = ViewConfiguration.get(activity).getScaledTouchSlop() * 2;
        audio = activity.getSystemService(AudioManager.class);
        feedback = new TextView(activity);
        feedback.setGravity(Gravity.CENTER);
        feedback.setTextColor(Color.WHITE);
        feedback.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        feedback.setBackgroundResource(R.drawable.phone_level_bg);
        feedback.setPadding(dp(14), dp(10), dp(14), dp(10));
        feedback.setCompoundDrawablePadding(dp(6));
        feedback.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        feedback.setVisibility(View.GONE);
        surface.addView(feedback, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        hideFeedback = () -> feedback.setVisibility(View.GONE);
        taps = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent event) {
                return surface.performClick();
            }

            @Override
            public boolean onDoubleTap(MotionEvent event) {
                return skip(event.getX() >= surface.getWidth() / 2f);
            }
        });
        surface.setOnTouchListener(this);
    }

    void fullscreen(boolean value) {
        if (fullscreen && !value) {
            WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            activity.getWindow().setAttributes(attrs);
            stop();
        }
        fullscreen = value;
    }

    void stop() {
        cancelTap(null);
        changingLevel = false;
        pullingForPip = false;
        ignoreTouch = true;
        surface.removeCallbacks(hideFeedback);
        hideFeedback.run();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                ignoreTouch = false;
                changingLevel = false;
                pullingForPip = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = event.getY() - startY;
                if (pullingForPip) {
                    return true;
                }
                if (!ignoreTouch && (changingLevel || isVerticalDrag(event.getX() - startX, dy))) {
                    float across = startX / Math.max(1f, surface.getWidth());
                    if (!changingLevel && dy > 0 && (!fullscreen || across > 1f / 3f && across < 2f / 3f)) {
                        pullingForPip = true;
                        cancelTap(event);
                        showFeedback(R.drawable.ic_phone_pip, activity.getText(R.string.phone_pip),
                                Gravity.CENTER_HORIZONTAL);
                        return true;
                    }
                    if (fullscreen) {
                        if (!changingLevel) beginLevelChange(event);
                        if (changingLevel) {
                            updateLevel(event.getY());
                            return true;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                cancelTap(event);
                ignoreTouch = true;
                changingLevel = false;
                pullingForPip = false;
                break;
            case MotionEvent.ACTION_UP:
                if (pullingForPip) {
                    pullingForPip = false;
                    hideFeedback.run();
                    if (event.getY() - startY >= dp(48)
                            && isVerticalDrag(event.getX() - startX, event.getY() - startY)) {
                        activity.enterFloatingVideo();
                    }
                    return true;
                }
                if (changingLevel) {
                    updateLevel(event.getY());
                    changingLevel = false;
                    return true;
                }
                break;
            default:
                break;
        }
        if (!ignoreTouch) {
            taps.onTouchEvent(event);
        }
        return true;
    }

    private boolean isVerticalDrag(float dx, float dy) {
        return Math.abs(dy) >= slop && Math.abs(dy) > Math.abs(dx) * 1.5f;
    }

    private void beginLevelChange(MotionEvent event) {
        brightness = startX < surface.getWidth() / 2f;
        changingLevel = brightness ? PhoneTweaks.brightnessSwipeEnabled(activity)
                : audio != null && !audio.isVolumeFixed() && PhoneTweaks.volumeSwipeEnabled(activity);
        cancelTap(event);
        if (!changingLevel) {
            ignoreTouch = true;
            return;
        }
        startLevel = brightness ? readBrightness() : readVolume();
    }

    private void updateLevel(float y) {
        float change = (startY - y) / Math.max(1f, surface.getHeight() * 0.7f);
        float level = Math.max(0f, Math.min(1f, startLevel + change));
        int icon;
        if (brightness) {
            WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
            level = Math.max(0.01f, level);
            attrs.screenBrightness = level;
            activity.getWindow().setAttributes(attrs);
            icon = R.drawable.ic_phone_brightness;
        } else {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int target = Math.round(level * max);
            try {
                if (target != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
                }
            } catch (SecurityException ignored) {
                // Do Not Disturb can block a change.
            }
            level = readVolume();
            icon = level == 0f ? R.drawable.ic_phone_volume_off : R.drawable.ic_phone_volume;
        }
        showFeedback(icon, NumberFormat.getPercentInstance().format(level), brightness ? Gravity.LEFT : Gravity.RIGHT);
    }

    boolean skip(boolean forward) {
        SimpleExoPlayer player = activity.exo();
        if (player == null || !player.isCurrentWindowSeekable() || player.getDuration() <= 0) {
            return false;
        }
        long target = player.getCurrentPosition() + (forward ? 10000 : -10000);
        player.seekTo(Math.max(0, Math.min(player.getDuration(), target)));
        showFeedback(forward ? R.drawable.ic_phone_seek_ahead_10 : R.drawable.ic_phone_seek_back_10,
                activity.getText(forward ? R.string.phone_seek_ahead_10 : R.string.phone_seek_back_10),
                forward ? Gravity.RIGHT : Gravity.LEFT);
        return true;
    }

    private void showFeedback(int icon, CharSequence text, int side) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) feedback.getLayoutParams();
        params.gravity = side | Gravity.CENTER_VERTICAL;
        params.leftMargin = dp(12);
        params.rightMargin = dp(12);
        feedback.setLayoutParams(params);
        feedback.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
        feedback.setText(text);
        feedback.setVisibility(View.VISIBLE);
        surface.removeCallbacks(hideFeedback);
        surface.postDelayed(hideFeedback, 700);
    }

    private float readVolume() {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max > 0 ? audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max : 0f;
    }

    private float readBrightness() {
        float override = activity.getWindow().getAttributes().screenBrightness;
        return override >= 0 ? override : Settings.System.getInt(activity.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 128) / 255f;
    }

    private void cancelTap(MotionEvent event) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent cancel = event == null ? MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0)
                : MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        taps.onTouchEvent(cancel);
        cancel.recycle();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
