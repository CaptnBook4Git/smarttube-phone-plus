package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Binds the regular phone player and Shorts overlays to a local ExoPlayer. */
final class PhoneLocalControls {
    private final PhoneLocalPlayerActivity activity;
    private final boolean shorts;
    private final FrameLayout frame;
    private final View overlay;
    private final SeekBar seek;
    private final PhoneOfflineVideoGestures videoGestures;
    private final PhoneOfflineShortPager shortPager;
    private boolean dragging;
    private final Runnable hide = this::hideWhilePlaying;
    private final Runnable tick = new Runnable() {
        public void run() {
            update();
            overlay.postDelayed(this, 300);
        }
    };

    PhoneLocalControls(PhoneLocalPlayerActivity activity, PlayerView view, boolean shorts) {
        this.activity = activity;
        this.shorts = shorts;
        frame = view.getOverlayFrameLayout();
        overlay = activity.getLayoutInflater().inflate(shorts ? R.layout.phone_shorts_overlay : R.layout.phone_player_controls, frame, false);
        frame.addView(overlay);
        overlay.setVisibility(View.VISIBLE);
        seek = overlay.findViewById(shorts ? R.id.phone_shorts_seek : R.id.phone_ctl_seek);
        if (shorts) {
            videoGestures = null;
            setupShorts();
            shortPager = new PhoneOfflineShortPager(activity, view, overlay);
        } else {
            shortPager = null;
            gone(R.id.phone_ctl_mode, R.id.phone_ctl_autoplay, R.id.phone_ctl_cc, R.id.phone_ctl_settings,
                    R.id.phone_ctl_prev, R.id.phone_ctl_next);
            click(R.id.phone_ctl_collapse, v -> activity.onBackPressed());
            click(R.id.phone_ctl_play, v -> activity.togglePlay());
            click(R.id.phone_ctl_fullscreen, v -> activity.toggleFullscreen());
            click(R.id.phone_ctl_speed, activity::showSpeed);
            boolean pipAvailable = activity.supportsFloatingVideo();
            if (pipAvailable) {
                overlay.findViewById(R.id.phone_ctl_pip).setVisibility(View.VISIBLE);
                click(R.id.phone_ctl_pip, v -> activity.enterFloatingVideo());
            }
            View title = overlay.findViewById(R.id.phone_ctl_title);
            ((FrameLayout.LayoutParams) title.getLayoutParams()).setMarginEnd(dp(pipAvailable ? 108 : 60));
            overlay.setClickable(false);
            frame.setOnClickListener(v -> {
                if (overlay.getVisibility() == View.VISIBLE) {
                    overlay.setVisibility(View.INVISIBLE);
                } else {
                    reveal();
                }
            });
            videoGestures = new PhoneOfflineVideoGestures(activity, frame);
            frame.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo node) {
                    super.onInitializeAccessibilityNodeInfo(host, node);
                    node.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.phone_ctl_prev,
                            activity.getText(R.string.phone_seek_back_10)));
                    node.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.phone_ctl_next,
                            activity.getText(R.string.phone_seek_ahead_10)));
                }

                @Override
                public boolean performAccessibilityAction(View host, int action, Bundle args) {
                    if (action == R.id.phone_ctl_prev || action == R.id.phone_ctl_next) {
                        return videoGestures.skip(action == R.id.phone_ctl_next);
                    }
                    return super.performAccessibilityAction(host, action, args);
                }
            });
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
                overlay.removeCallbacks(hide);
            }
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                SimpleExoPlayer player = activity.exo();
                if (user && player != null && player.getDuration() > 0) {
                    player.seekTo(player.getDuration() * progress / bar.getMax());
                }
            }
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                reveal();
            }
        });
    }

    private void setupShorts() {
        gone(R.id.phone_shorts_tabs, R.id.phone_shorts_side, R.id.phone_shorts_top,
                R.id.phone_shorts_subscribe, R.id.phone_shorts_avatar);
        overlay.findViewById(R.id.phone_shorts_back).setVisibility(View.VISIBLE);
        click(R.id.phone_shorts_back, v -> activity.finish());
        View info = overlay.findViewById(R.id.phone_shorts_info);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) info.getLayoutParams();
        params.bottomMargin = dp(32);
        params.setMarginEnd(dp(24));
        info.setLayoutParams(params);
        overlay.setOnClickListener(v -> activity.togglePlay());
        overlay.setContentDescription(activity.getText(R.string.phone_play_pause));
        overlay.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo node) {
                super.onInitializeAccessibilityNodeInfo(host, node);
                node.setScrollable(true);
                if (activity.canStep(1)) node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                if (activity.canStep(-1)) node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) return shortPager.navigate(1);
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) return shortPager.navigate(-1);
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    void bind(PhoneDownloadDetails details) {
        ((TextView) overlay.findViewById(shorts ? R.id.phone_shorts_title : R.id.phone_ctl_title)).setText(details.title);
        if (shorts) {
            TextView author = overlay.findViewById(R.id.phone_shorts_author);
            author.setText(details.author);
            author.setVisibility(details.author.isEmpty() ? View.GONE : View.VISIBLE);
        }
        reveal();
    }

    void fullscreen(boolean full) {
        if (shorts) {
            shortPager.reset();
            return;
        }
        videoGestures.fullscreen(full);
        ImageView button = overlay.findViewById(R.id.phone_ctl_fullscreen);
        button.setImageResource(full ? R.drawable.ic_phone_fullscreen_exit : R.drawable.ic_phone_fullscreen);
        button.setContentDescription(activity.getText(full ? R.string.phone_offline_exit_fullscreen : R.string.phone_fullscreen));
        overlay.findViewById(R.id.phone_ctl_title).setVisibility(full ? View.VISIBLE : View.GONE);
        reveal();
    }

    void start() { overlay.removeCallbacks(tick); overlay.post(tick); }
    void stop() {
        overlay.removeCallbacks(tick);
        overlay.removeCallbacks(hide);
        if (videoGestures != null) videoGestures.stop();
        if (shortPager != null) shortPager.reset();
    }

    void pictureInPicture(boolean active) {
        frame.setVisibility(active ? View.INVISIBLE : View.VISIBLE);
        if (active) {
            videoGestures.stop();
            overlay.removeCallbacks(hide);
        } else {
            reveal();
        }
    }

    void onShortFrameReady() {
        if (shortPager != null) shortPager.frameReady();
    }

    void reveal() {
        overlay.setVisibility(View.VISIBLE);
        update();
        if (!shorts) {
            overlay.removeCallbacks(hide);
            overlay.postDelayed(hide, 3500);
        }
    }

    private void hideWhilePlaying() {
        SimpleExoPlayer player = activity.exo();
        android.view.accessibility.AccessibilityManager manager = activity.getSystemService(android.view.accessibility.AccessibilityManager.class);
        if (!dragging && player != null && player.getPlayWhenReady() && !manager.isTouchExplorationEnabled()) {
            overlay.setVisibility(View.INVISIBLE);
        }
    }

    private void update() {
        SimpleExoPlayer player = activity.exo();
        if (player == null || overlay.getVisibility() != View.VISIBLE) return;
        long pos = player.getCurrentPosition();
        long duration = Math.max(0, player.getDuration());
        if (!dragging) seek.setProgress(duration > 0 ? (int) (pos * seek.getMax() / duration) : 0);
        if (shorts) {
            overlay.findViewById(R.id.phone_shorts_paused).setVisibility(player.getPlayWhenReady() ? View.GONE : View.VISIBLE);
        } else {
            ((ImageView) overlay.findViewById(R.id.phone_ctl_play)).setImageResource(
                    player.getPlayWhenReady() ? R.drawable.ic_phone_pause : R.drawable.ic_phone_play);
            ((TextView) overlay.findViewById(R.id.phone_ctl_time)).setText(
                    DateUtils.formatElapsedTime(pos / 1000) + " / " + DateUtils.formatElapsedTime(duration / 1000));
        }
    }

    private void click(int id, View.OnClickListener listener) { overlay.findViewById(id).setOnClickListener(listener); }
    private void gone(int... ids) { for (int id : ids) overlay.findViewById(id).setVisibility(View.GONE); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
