package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.data.FeedbackReasons.FeedbackItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Direct quick-action buttons ("Not interested", "Don't recommend channel", Like, Dislike) on video cards,
 * shelves, rows, Shorts tiles and in the player, with toggles in Phone Settings.
 */
public final class PhoneQuickActions {
    private static final String PREFS_NAME = "phone_quick_actions";
    private static final String KEY_NOT_INTERESTED = "show_not_interested";
    private static final String KEY_DONT_RECOMMEND_CHANNEL = "show_dont_recommend_channel";
    private static final String KEY_LIKE_DISLIKE = "show_like_dislike";

    public static final int RATING_NONE = 0;
    public static final int RATING_LIKE = 1;
    public static final int RATING_DISLIKE = -1;

    private static final Map<String, Integer> sRatings = new ConcurrentHashMap<>();
    private static volatile int sConfigVersion = 1;

    private PhoneQuickActions() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int configVersion() {
        return sConfigVersion;
    }

    public static boolean isNotInterestedEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NOT_INTERESTED, true);
    }

    public static void setNotInterestedEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_NOT_INTERESTED, enabled).apply();
        sConfigVersion++;
    }

    public static boolean isDontRecommendChannelEnabled(Context context) {
        return prefs(context).getBoolean(KEY_DONT_RECOMMEND_CHANNEL, true);
    }

    public static void setDontRecommendChannelEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DONT_RECOMMEND_CHANNEL, enabled).apply();
        sConfigVersion++;
    }

    public static boolean isLikeDislikeEnabled(Context context) {
        return prefs(context).getBoolean(KEY_LIKE_DISLIKE, true);
    }

    public static void setLikeDislikeEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_LIKE_DISLIKE, enabled).apply();
        sConfigVersion++;
    }

    public static boolean isAnyEnabled(Context context) {
        return isNotInterestedEnabled(context)
                || isDontRecommendChannelEnabled(context)
                || isLikeDislikeEnabled(context);
    }

    public static int getRating(Video video) {
        if (video == null || TextUtils.isEmpty(video.videoId)) {
            return RATING_NONE;
        }
        Integer rating = sRatings.get(video.videoId);
        return rating != null ? rating : RATING_NONE;
    }

    public static void setRating(Video video, int rating) {
        if (video == null || TextUtils.isEmpty(video.videoId)) {
            return;
        }
        if (rating == RATING_NONE) {
            sRatings.remove(video.videoId);
        } else {
            sRatings.put(video.videoId, rating);
        }
    }

    private static MediaItem toMediaItem(Video video) {
        if (video == null) {
            return null;
        }
        return video.mediaItem != null ? video.mediaItem : video.toMediaItem();
    }

    public static void toggleLike(Context context, Video video, Runnable onUiUpdate) {
        if (video == null || TextUtils.isEmpty(video.videoId)) {
            return;
        }
        SignInService signIn = YouTubeServiceManager.instance().getSignInService();
        if (!signIn.isSigned()) {
            MessageHelpers.showMessage(context, R.string.msg_signed_users_only);
            return;
        }
        MediaItem item = toMediaItem(video);
        if (item == null) {
            return;
        }
        MediaItemService service = YouTubeServiceManager.instance().getMediaItemService();
        int current = getRating(video);
        if (current == RATING_LIKE) {
            setRating(video, RATING_NONE);
            RxHelper.execute(service.removeLikeObserve(item));
            MessageHelpers.showMessage(context, R.string.phone_quick_rating_removed_toast);
        } else {
            setRating(video, RATING_LIKE);
            RxHelper.execute(service.setLikeObserve(item));
            MessageHelpers.showMessage(context, R.string.phone_quick_liked_toast);
        }
        if (onUiUpdate != null) {
            onUiUpdate.run();
        }
    }

    public static void toggleDislike(Context context, Video video, Runnable onUiUpdate) {
        if (video == null || TextUtils.isEmpty(video.videoId)) {
            return;
        }
        SignInService signIn = YouTubeServiceManager.instance().getSignInService();
        if (!signIn.isSigned()) {
            MessageHelpers.showMessage(context, R.string.msg_signed_users_only);
            return;
        }
        MediaItem item = toMediaItem(video);
        if (item == null) {
            return;
        }
        MediaItemService service = YouTubeServiceManager.instance().getMediaItemService();
        int current = getRating(video);
        if (current == RATING_DISLIKE) {
            setRating(video, RATING_NONE);
            RxHelper.execute(service.removeDislikeObserve(item));
            MessageHelpers.showMessage(context, R.string.phone_quick_rating_removed_toast);
        } else {
            setRating(video, RATING_DISLIKE);
            RxHelper.execute(service.setDislikeObserve(item));
            MessageHelpers.showMessage(context, R.string.phone_quick_disliked_toast);
        }
        if (onUiUpdate != null) {
            onUiUpdate.run();
        }
    }

    public static void markNotInterested(Context context, Video video, Runnable onRemoveVideo) {
        if (video == null) {
            return;
        }
        MediaItemService itemService = YouTubeServiceManager.instance().getMediaItemService();
        if (video.mediaItem != null) {
            if (video.mediaItem.getFeedbackToken() != null) {
                showNotInterestedReasons(context, itemService, video, video.mediaItem.getFeedbackToken(), onRemoveVideo);
                return;
            } else if (video.mediaItem.getFeedbackEndpoint() != null) {
                itemService.getFeedbackTokensObserve(video.mediaItem.getFeedbackEndpoint())
                        .subscribe(
                                tokens -> {
                                    if (tokens != null && !tokens.isEmpty() && tokens.get(0) != null) {
                                        showNotInterestedReasons(context, itemService, video, tokens.get(0), onRemoveVideo);
                                    } else {
                                        finishNotInterested(context, video, onRemoveVideo);
                                    }
                                },
                                error -> finishNotInterested(context, video, onRemoveVideo)
                        );
                return;
            }
        }
        finishNotInterested(context, video, onRemoveVideo);
    }

    private static void showNotInterestedReasons(
            Context context,
            MediaItemService itemService,
            Video video,
            String feedbackToken,
            Runnable onRemoveVideo) {
        itemService.getFeedbackReasonsObserve(feedbackToken)
                .subscribe(
                        reasons -> {
                            if (reasons != null && reasons.getItems() != null && !reasons.getItems().isEmpty()) {
                                AppDialogPresenter dialog = AppDialogPresenter.instance(context);
                                final boolean[] removed = {false};
                                Runnable removeOnce = () -> {
                                    if (!removed[0]) {
                                        removed[0] = true;
                                        finishNotInterested(context, video, onRemoveVideo);
                                    }
                                };
                                for (FeedbackItem item : reasons.getItems()) {
                                    if (item == null || TextUtils.isEmpty(item.getTitle())) {
                                        continue;
                                    }
                                    dialog.appendSingleButton(
                                            UiOptionItem.from(item.getTitle(), option -> {
                                                if (item.getToken() != null) {
                                                    RxHelper.execute(itemService.markAsNotInterestedObserve(item.getToken()));
                                                }
                                                removeOnce.run();
                                                dialog.closeDialog();
                                            })
                                    );
                                }
                                String title = !TextUtils.isEmpty(reasons.getTitle())
                                        ? reasons.getTitle()
                                        : context.getString(R.string.not_interested);
                                dialog.showDialog(title, removeOnce);
                            } else {
                                finishNotInterested(context, video, onRemoveVideo);
                            }
                        },
                        error -> finishNotInterested(context, video, onRemoveVideo)
                );
    }

    private static void finishNotInterested(Context context, Video video, Runnable onRemoveVideo) {
        if (video.belongsToHistory() && !TextUtils.isEmpty(video.videoId)) {
            VideoStateService.instance(context).removeByVideoId(video.videoId);
        }
        if (video.belongsToNotifications()) {
            MediaServiceManager.instance().hideNotification(video);
        }
        PhoneShortsQueue.remove(video);
        MessageHelpers.showMessage(context, R.string.you_wont_see_this_video);
        if (onRemoveVideo != null) {
            onRemoveVideo.run();
        }
    }

    public static void dontRecommendChannel(Context context, Video video, Runnable onRemoveAuthor) {
        if (video == null) {
            return;
        }
        MediaItemService itemService = YouTubeServiceManager.instance().getMediaItemService();
        if (video.mediaItem != null) {
            if (video.mediaItem.getFeedbackToken2() != null) {
                RxHelper.execute(itemService.markAsNotInterestedObserve(video.mediaItem.getFeedbackToken2()));
            } else if (video.mediaItem.getFeedbackEndpoint() != null) {
                itemService.getFeedbackTokensObserve(video.mediaItem.getFeedbackEndpoint())
                        .subscribe(
                                tokens -> {
                                    if (tokens != null && tokens.size() > 1 && tokens.get(1) != null) {
                                        RxHelper.execute(itemService.markAsNotInterestedObserve(tokens.get(1)));
                                    } else if (tokens != null && !tokens.isEmpty() && tokens.get(0) != null) {
                                        RxHelper.execute(itemService.markAsNotInterestedObserve(tokens.get(0)));
                                    }
                                },
                                error -> {}
                        );
            } else if (video.mediaItem.getFeedbackToken() != null) {
                RxHelper.execute(itemService.markAsNotInterestedObserve(video.mediaItem.getFeedbackToken()));
            }
        }
        PhoneShortsQueue.removeAuthor(video.getAuthor());
        PhoneShortsQueue.remove(video);
        MessageHelpers.showMessage(context, R.string.phone_channel_not_recommended);
        if (onRemoveAuthor != null) {
            onRemoveAuthor.run();
        }
    }

    public static void removeFromHistory(Context context, Video video, Runnable onRemoveVideo) {
        if (video == null) {
            return;
        }
        MediaItemService itemService = YouTubeServiceManager.instance().getMediaItemService();
        if (video.mediaItem != null) {
            if (video.mediaItem.getFeedbackToken() != null) {
                RxHelper.execute(itemService.markAsNotInterestedObserve(video.mediaItem.getFeedbackToken()));
            } else if (video.mediaItem.getFeedbackEndpoint() != null) {
                itemService.getFeedbackTokensObserve(video.mediaItem.getFeedbackEndpoint())
                        .subscribe(
                                tokens -> {
                                    if (tokens != null && !tokens.isEmpty() && tokens.get(0) != null) {
                                        RxHelper.execute(itemService.markAsNotInterestedObserve(tokens.get(0)));
                                    }
                                },
                                error -> {}
                        );
            }
        }
        if (!TextUtils.isEmpty(video.videoId)) {
            VideoStateService.instance(context).removeByVideoId(video.videoId);
        }
        MessageHelpers.showMessage(context, R.string.removed_from_history);
        if (onRemoveVideo != null) {
            onRemoveVideo.run();
        }
    }

    private static Activity getActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /**
     * Binds the quick action bar inside a video card, shelf card, row or Short tile.
     */
    public static void bind(View root, Video video, Runnable onRemoveVideo, Runnable onRemoveAuthor, boolean isOverlay) {
        View bar = root.findViewById(R.id.phone_quick_actions);
        if (bar == null) {
            return;
        }
        Context context = root.getContext();
        if (video == null || TextUtils.isEmpty(video.videoId) || video.isChannel() || !isAnyEnabled(context)) {
            bar.setVisibility(View.GONE);
            return;
        }

        Activity activity = getActivity(context);
        boolean isPlayback = activity instanceof PhonePlaybackActivity;
        boolean isHistory = !isPlayback
                && video.belongsToHistory()
                && !isOverlay
                && !video.belongsToSuggestions()
                && !video.belongsToPlaybackQueue();
        boolean showLikeDislike = isLikeDislikeEnabled(context) && !isHistory;
        boolean showRemoveHistory = isHistory;
        boolean showNotInterested = isNotInterestedEnabled(context) && !isHistory;
        boolean showDontRecommend = isDontRecommendChannelEnabled(context) && !isOverlay;

        if (!showLikeDislike && !showRemoveHistory && !showNotInterested && !showDontRecommend) {
            bar.setVisibility(View.GONE);
            return;
        }

        bar.setVisibility(View.VISIBLE);

        View likeBtn = bar.findViewById(R.id.phone_quick_like);
        View dislikeBtn = bar.findViewById(R.id.phone_quick_dislike);
        View removeHistoryBtn = bar.findViewById(R.id.phone_quick_remove_history);
        View notInterestedBtn = bar.findViewById(R.id.phone_quick_not_interested);
        View dontRecommendBtn = bar.findViewById(R.id.phone_quick_dont_recommend_channel);

        ImageView likeIcon = bar.findViewById(R.id.phone_quick_like_icon);
        ImageView dislikeIcon = bar.findViewById(R.id.phone_quick_dislike_icon);

        Runnable updateRatingIcons = () -> {
            int rating = getRating(video);
            int accentColor = context.getResources().getColor(R.color.phone_accent);
            int inactiveColor = isOverlay
                    ? Color.WHITE
                    : context.getResources().getColor(R.color.phone_text_2);
            if (likeIcon != null) {
                likeIcon.setImageTintList(ColorStateList.valueOf(
                        rating == RATING_LIKE ? accentColor : inactiveColor));
            }
            if (dislikeIcon != null) {
                dislikeIcon.setImageTintList(ColorStateList.valueOf(
                        rating == RATING_DISLIKE ? accentColor : inactiveColor));
            }
        };

        if (likeBtn != null) {
            likeBtn.setVisibility(showLikeDislike ? View.VISIBLE : View.GONE);
            likeBtn.setOnClickListener(v -> toggleLike(context, video, updateRatingIcons));
        }
        if (dislikeBtn != null) {
            dislikeBtn.setVisibility(showLikeDislike ? View.VISIBLE : View.GONE);
            dislikeBtn.setOnClickListener(v -> toggleDislike(context, video, updateRatingIcons));
        }
        if (removeHistoryBtn != null) {
            removeHistoryBtn.setVisibility(showRemoveHistory ? View.VISIBLE : View.GONE);
            removeHistoryBtn.setOnClickListener(v -> removeFromHistory(context, video, onRemoveVideo));
        }
        updateRatingIcons.run();

        if (notInterestedBtn != null) {
            notInterestedBtn.setVisibility(showNotInterested ? View.VISIBLE : View.GONE);
            notInterestedBtn.setOnClickListener(v -> markNotInterested(context, video, onRemoveVideo));
            notInterestedBtn.setOnLongClickListener(v -> {
                dontRecommendChannel(context, video, onRemoveAuthor != null ? onRemoveAuthor : onRemoveVideo);
                return true;
            });
        }

        if (dontRecommendBtn != null) {
            boolean hasAuthor = !TextUtils.isEmpty(video.getAuthor())
                    || !TextUtils.isEmpty(video.channelId)
                    || (video.mediaItem != null && (video.mediaItem.getFeedbackToken2() != null || video.mediaItem.getFeedbackEndpoint() != null));
            dontRecommendBtn.setVisibility(showDontRecommend && hasAuthor ? View.VISIBLE : View.GONE);
            dontRecommendBtn.setOnClickListener(v ->
                    dontRecommendChannel(context, video, onRemoveAuthor != null ? onRemoveAuthor : onRemoveVideo));
        }
    }

    /**
     * Opens the bottom sheet in Phone Settings to toggle quick actions on or off.
     */
    public static void openSettings(Context context, Runnable onChanged) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        OptionItem notInterestedOpt = UiOptionItem.from(
                context.getString(R.string.phone_quick_not_interested_setting),
                context.getString(R.string.phone_quick_not_interested_desc),
                item -> {
                    setNotInterestedEnabled(context, item.isSelected());
                    if (onChanged != null) {
                        onChanged.run();
                    }
                },
                isNotInterestedEnabled(context)
        );

        OptionItem dontRecommendOpt = UiOptionItem.from(
                context.getString(R.string.phone_quick_dont_recommend_setting),
                context.getString(R.string.phone_quick_dont_recommend_desc),
                item -> {
                    setDontRecommendChannelEnabled(context, item.isSelected());
                    if (onChanged != null) {
                        onChanged.run();
                    }
                },
                isDontRecommendChannelEnabled(context)
        );

        OptionItem likeDislikeOpt = UiOptionItem.from(
                context.getString(R.string.phone_quick_like_dislike_setting),
                context.getString(R.string.phone_quick_like_dislike_desc),
                item -> {
                    setLikeDislikeEnabled(context, item.isSelected());
                    if (onChanged != null) {
                        onChanged.run();
                    }
                },
                isLikeDislikeEnabled(context)
        );

        dialog.appendSingleSwitch(notInterestedOpt);
        dialog.appendSingleSwitch(dontRecommendOpt);
        dialog.appendSingleSwitch(likeDislikeOpt);
        dialog.showDialog(context.getString(R.string.phone_quick_actions_title));
    }
}
