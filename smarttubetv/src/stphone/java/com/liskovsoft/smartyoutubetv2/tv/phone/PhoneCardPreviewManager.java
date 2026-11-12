package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer.EmbedPlayerView;

import java.util.Random;

public final class PhoneCardPreviewManager {
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static EmbedPlayerView sActivePlayer;
    private static ViewGroup sActiveContainer;
    private static Video sActiveVideo;
    private static float sOriginalPercent = -1f;

    private PhoneCardPreviewManager() {
    }

    public static void stopActivePreview(Context context) {
        sHandler.removeCallbacksAndMessages(null);
        if (sActivePlayer != null) {
            try {
                sActivePlayer.finish();
            } catch (Throwable ignored) {
            }
            if (sActiveContainer != null) {
                sActiveContainer.removeView(sActivePlayer);
            }
            sActivePlayer = null;
        }
        if (sActiveVideo != null) {
            sActiveVideo.percentWatched = sOriginalPercent;
            sActiveVideo.pendingPosMs = 0;
            if (context != null && sActiveVideo.videoId != null) {
                try {
                    VideoStateService.instance(context).removeByVideoId(sActiveVideo.videoId);
                } catch (Throwable ignored) {
                }
            }
            sActiveVideo = null;
        }
        sActiveContainer = null;
        sOriginalPercent = -1f;
    }

    public static void bind(View cardView, Video video, Runnable onNormalClick) {
        if (cardView == null || video == null) {
            return;
        }

        final Context context = cardView.getContext();
        final int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        final int longPressTimeout = ViewConfiguration.getLongPressTimeout();

        cardView.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private long downTime;
            private boolean isPreviewRunning;
            private boolean isMoved;

            private final Runnable startPreviewRunnable = () -> {
                int previewType = MainUIData.instance(context).getCardPreviewType();
                if (previewType == MainUIData.CARD_PREVIEW_DISABLED) {
                    return;
                }

                ViewGroup thumbFrame = cardView.findViewById(R.id.phone_thumb_frame);
                if (thumbFrame == null) {
                    return;
                }

                stopActivePreview(context);

                isPreviewRunning = true;
                if (cardView.getParent() != null) {
                    cardView.getParent().requestDisallowInterceptTouchEvent(true);
                }
                try {
                    cardView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } catch (Throwable ignored) {
                }

                sActiveVideo = video;
                sOriginalPercent = video.percentWatched;
                sActiveContainer = thumbFrame;

                if (PhoneUi.isCardPreviewRandomStart(context) && video.getDurationMs() > 20_000) {
                    long rand = (long) (video.getDurationMs() * (0.15f + new Random().nextFloat() * 0.55f));
                    video.pendingPosMs = rand;
                }

                try {
                    EmbedPlayerView playerView = new EmbedPlayerView(context);
                    playerView.setQuality(EmbedPlayerView.QUALITY_LOW);
                    playerView.setMute(previewType == MainUIData.CARD_PREVIEW_MUTED);
                    playerView.setBackgroundColor(Color.BLACK);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    );
                    thumbFrame.addView(playerView, lp);
                    playerView.openVideo(video);
                    sActivePlayer = playerView;
                } catch (Throwable ignored) {
                    stopActivePreview(context);
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        downTime = System.currentTimeMillis();
                        isPreviewRunning = false;
                        isMoved = false;
                        sHandler.removeCallbacks(startPreviewRunnable);
                        int previewType = MainUIData.instance(context).getCardPreviewType();
                        if (previewType != MainUIData.CARD_PREVIEW_DISABLED && !video.isChannel()) {
                            sHandler.postDelayed(startPreviewRunnable, longPressTimeout);
                        }
                        return false; // let ripple show
                    case MotionEvent.ACTION_MOVE:
                        if (!isMoved && (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop)) {
                            isMoved = true;
                            sHandler.removeCallbacks(startPreviewRunnable);
                        }
                        if (isPreviewRunning) {
                            return true;
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                        sHandler.removeCallbacks(startPreviewRunnable);
                        if (isPreviewRunning) {
                            isPreviewRunning = false;
                            stopActivePreview(context);
                            return true; // consumed preview release, don't trigger click
                        }
                        if (!isMoved && (System.currentTimeMillis() - downTime) < longPressTimeout) {
                            if (onNormalClick != null) {
                                PhoneUi.rememberClick(cardView);
                                onNormalClick.run();
                                return true;
                            }
                        }
                        return false;
                    case MotionEvent.ACTION_CANCEL:
                        sHandler.removeCallbacks(startPreviewRunnable);
                        if (isPreviewRunning) {
                            isPreviewRunning = false;
                            stopActivePreview(context);
                            return true;
                        }
                        return false;
                    default:
                        return false;
                }
            }
        });
    }
}
