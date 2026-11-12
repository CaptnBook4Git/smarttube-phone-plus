package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
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
    private static boolean sIsPreviewActive;

    private PhoneCardPreviewManager() {
    }

    public static boolean isPreviewActive() {
        return sIsPreviewActive;
    }

    public static void stopActivePreview(Context context, boolean resetState) {
        sHandler.removeCallbacksAndMessages(null);
        sIsPreviewActive = false;
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
            if (resetState) {
                sActiveVideo.percentWatched = sOriginalPercent;
                sActiveVideo.pendingPosMs = 0;
                if (context != null && sActiveVideo.videoId != null) {
                    try {
                        VideoStateService.instance(context).removeByVideoId(sActiveVideo.videoId);
                    } catch (Throwable ignored) {
                    }
                }
            }
            sActiveVideo = null;
        }
        sActiveContainer = null;
        sOriginalPercent = -1f;
    }

    public static void bind(View cardView, Video video, Runnable onOpenVideo) {
        if (cardView == null || video == null) {
            return;
        }

        final Context context = cardView.getContext();

        // 1. Long click starts preview if enabled
        cardView.setOnLongClickListener(v -> {
            int previewType = MainUIData.instance(context).getCardPreviewType();
            if (previewType == MainUIData.CARD_PREVIEW_DISABLED || video.isChannel()) {
                return true; // Consume long-press, do not show TV context menu
            }

            ViewGroup thumbFrame = cardView.findViewById(R.id.phone_thumb_frame);
            if (thumbFrame == null) {
                return true;
            }

            stopActivePreview(context, true);

            sIsPreviewActive = true;
            if (cardView.getParent() != null) {
                cardView.getParent().requestDisallowInterceptTouchEvent(true);
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
                playerView.setAlpha(1f);
                sActivePlayer = playerView;
            } catch (Throwable ignored) {
                stopActivePreview(context, true);
            }

            return true;
        });

        // 2. Touch listener handles release (stops preview) or swipe up (launches video)
        cardView.setOnTouchListener(new View.OnTouchListener() {
            private float downY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = event.getRawY();
                        return false; // let normal click and long-click handle ACTION_DOWN
                    case MotionEvent.ACTION_MOVE:
                        if (sIsPreviewActive) {
                            float dy = event.getRawY() - downY;
                            float threshold = -48f * context.getResources().getDisplayMetrics().density;
                            if (dy < threshold) {
                                // User swiped UP while in preview -> start video!
                                sIsPreviewActive = false;
                                boolean randomStart = PhoneUi.isCardPreviewRandomStart(context);
                                long pos = 0;
                                if (!randomStart && sActivePlayer != null) {
                                    pos = sActivePlayer.getPositionMs();
                                }
                                stopActivePreview(context, false);
                                if (randomStart) {
                                    video.pendingPosMs = 0;
                                } else if (pos > 0) {
                                    video.pendingPosMs = pos;
                                }
                                if (onOpenVideo != null) {
                                    onOpenVideo.run();
                                }
                                return true;
                            }
                            return true; // consume touch while preview is active
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (sIsPreviewActive) {
                            sIsPreviewActive = false;
                            stopActivePreview(context, true); // ungesehen / reset state
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
