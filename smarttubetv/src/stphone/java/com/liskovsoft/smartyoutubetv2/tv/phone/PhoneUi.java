package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import android.graphics.drawable.Drawable;

/**
 * Shared helpers for binding videos into the phone cards.
 */
final class PhoneUi {
    private PhoneUi() {
    }

    static boolean isAlive(Context context) {
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            return !activity.isFinishing() && !activity.isDestroyed();
        }
        return context != null;
    }

    /**
     * SmartTube scales density so a 1920px wide TV layout fits any screen (MotherActivity.initDpi), which makes
     * phone UI ~20% too small and ignores the font size setting. Phone screens use the real device metrics.
     */
    static void applyPhoneDensity(android.app.Activity activity) {
        android.util.DisplayMetrics system = Resources.getSystem().getDisplayMetrics();
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        metrics.density = system.density;
        metrics.densityDpi = system.densityDpi;
        metrics.scaledDensity = system.scaledDensity;
        metrics.xdpi = system.xdpi;
        metrics.ydpi = system.ydpi;
    }

    private static final String PREF_NAME = "phone_ui_prefs";
    private static final String KEY_GRID_COLUMNS = "feed_grid_columns";
    private static final String KEY_LONG_PRESS_SPEED = "phone_long_press_speed";
    private static final String KEY_CARD_PREVIEW_RANDOM_START = "phone_card_preview_random_start";

    public static float getLongPressSpeed(Context context) {
        if (context == null) {
            return 2.0f;
        }
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_LONG_PRESS_SPEED, 2.0f);
    }

    public static void setLongPressSpeed(Context context, float speed) {
        if (context == null) {
            return;
        }
        context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_LONG_PRESS_SPEED, speed).apply();
    }

    public static String getLongPressSpeedTitle(Context context) {
        if (context == null) {
            return "Geschwindigkeit bei Halten: 2x";
        }
        float speed = getLongPressSpeed(context);
        String speedStr = (speed == (int) speed) ? String.format(java.util.Locale.US, "%dx", (int) speed) : String.format(java.util.Locale.US, "%.2fx", speed);
        return context.getString(R.string.phone_speed_hold_title) + ": " + speedStr;
    }

    public static void openLongPressSpeedSettings(Context context, Runnable onChanged) {
        if (context == null) {
            return;
        }
        com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter dialog =
                com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.instance(context);

        float current = getLongPressSpeed(context);
        java.util.List<com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem> opts = new java.util.ArrayList<>();

        float[] choices = {1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f};

        for (float speedVal : choices) {
            String label = (speedVal == (int) speedVal)
                    ? String.format(java.util.Locale.US, "%dx", (int) speedVal)
                    : String.format(java.util.Locale.US, "%.2fx", speedVal);
            if (speedVal == 2.0f) {
                label += " (Standard)";
            }
            opts.add(com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem.from(
                    label,
                    item -> {
                        setLongPressSpeed(context, speedVal);
                        dialog.closeDialog();
                        if (onChanged != null) {
                            onChanged.run();
                        }
                    },
                    Math.abs(current - speedVal) < 0.05f
            ));
        }

        dialog.appendRadioCategory(context.getString(R.string.phone_speed_hold_title), opts);
        dialog.showDialog(context.getString(R.string.phone_speed_hold_title));
    }

    public static boolean isCardPreviewRandomStart(Context context) {
        if (context == null) {
            return false;
        }
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_CARD_PREVIEW_RANDOM_START, false);
    }

    public static void setCardPreviewRandomStart(Context context, boolean random) {
        if (context == null) {
            return;
        }
        context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_CARD_PREVIEW_RANDOM_START, random).apply();
    }

    public static String getCardPreviewRandomStartTitle(Context context) {
        if (context == null) {
            return "Kartenvorschau Startpunkt";
        }
        boolean random = isCardPreviewRandomStart(context);
        return context.getString(R.string.phone_preview_start_title) + ": " +
                context.getString(random ? R.string.phone_preview_start_random : R.string.phone_preview_start_beginning);
    }

    public static void openCardPreviewRandomStartSettings(Context context, Runnable onChanged) {
        if (context == null) {
            return;
        }
        com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter dialog =
                com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.instance(context);

        boolean current = isCardPreviewRandomStart(context);
        java.util.List<com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem> opts = new java.util.ArrayList<>();

        opts.add(com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem.from(
                context.getString(R.string.phone_preview_start_beginning) + " (Standard)",
                item -> {
                    setCardPreviewRandomStart(context, false);
                    dialog.closeDialog();
                    if (onChanged != null) onChanged.run();
                },
                !current
        ));
        opts.add(com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem.from(
                context.getString(R.string.phone_preview_start_random),
                item -> {
                    setCardPreviewRandomStart(context, true);
                    dialog.closeDialog();
                    if (onChanged != null) onChanged.run();
                },
                current
        ));

        dialog.appendRadioCategory(context.getString(R.string.phone_preview_start_title), opts);
        dialog.showDialog(context.getString(R.string.phone_preview_start_title));
    }

    public static int getCustomColumns(Context context) {
        if (context == null) {
            return 0;
        }
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_GRID_COLUMNS, 0);
    }

    public static void setCustomColumns(Context context, int columns) {
        if (context == null) {
            return;
        }
        context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_GRID_COLUMNS, columns).apply();
    }

    public static String getGridSettingsTitle(Context context) {
        if (context == null) {
            return "Grid-Größe / Spalten";
        }
        int custom = getCustomColumns(context);
        if (custom == 0) {
            return context.getString(R.string.phone_grid_settings_title) + ": " + context.getString(R.string.phone_grid_auto);
        }
        return context.getString(R.string.phone_grid_settings_title) + ": " + custom;
    }

    public static void openGridSettings(Context context, Runnable onChanged) {
        if (context == null) {
            return;
        }
        com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter dialog =
                com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.instance(context);

        int current = getCustomColumns(context);
        java.util.List<com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem> opts = new java.util.ArrayList<>();

        int[][] choices = {
                {0, R.string.phone_grid_auto},
                {1, R.string.phone_grid_1_col},
                {2, R.string.phone_grid_2_cols},
                {3, R.string.phone_grid_3_cols},
                {4, R.string.phone_grid_4_cols},
                {5, R.string.phone_grid_5_cols}
        };

        for (int[] pair : choices) {
            final int colVal = pair[0];
            opts.add(com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem.from(
                    context.getString(pair[1]),
                    item -> {
                        setCustomColumns(context, colVal);
                        dialog.closeDialog();
                        if (onChanged != null) {
                            onChanged.run();
                        }
                    },
                    current == colVal
            ));
        }

        dialog.appendRadioCategory(context.getString(R.string.phone_grid_settings_title), opts);
        dialog.showDialog(context.getString(R.string.phone_grid_settings_title));
    }

    public static boolean isTablet(Context context) {
        if (context == null) {
            return false;
        }
        return context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    public static int calculateVideoColumns(Context context) {
        if (context == null) {
            return 1;
        }
        int custom = getCustomColumns(context);
        if (custom > 0) {
            return custom;
        }
        int widthDp = context.getResources().getConfiguration().screenWidthDp;
        int smallestWidthDp = context.getResources().getConfiguration().smallestScreenWidthDp;

        // Tablet (sw >= 600) or wide landscape screen:
        if (smallestWidthDp >= 600 || widthDp >= 600) {
            if (widthDp >= 1300) {
                return 4; // Extra-large tablet / desktop monitor
            } else if (widthDp >= 840) {
                return 3; // 10"-12" tablet landscape
            } else {
                return 2; // Tablet in portrait or 7"-8" tablet or foldable
            }
        }

        // Standard phone: 1 column in portrait, 2 columns in landscape
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            return 2;
        }
        return 1;
    }

    private static java.lang.ref.WeakReference<View> sLastClickedThumb;

    /**
     * Remember the thumbnail of the tapped card, so the player can expand from it.
     */
    static void rememberClick(View itemView) {
        View thumb = itemView.findViewById(R.id.phone_thumb_frame);
        if (thumb == null) {
            thumb = itemView.findViewById(R.id.phone_thumb);
        }
        sLastClickedThumb = new java.lang.ref.WeakReference<>(thumb != null ? thumb : itemView);
    }

    /**
     * The next player launch scales up from the tapped thumbnail (like the YouTube app) instead of opening a new page.
     */
    static void prepareExpandLaunch(Video video) {
        if (video != null && !TextUtils.isEmpty(video.videoId) && !video.isChannel()) {
            PhonePlaybackActivity.releaseDetachedPlayer();
        }
        if (video != null && video.isShorts && video.getGroup() != null) {
            PhoneShortsQueue.set(video.getGroup());
        }
        View view = sLastClickedThumb != null ? sLastClickedThumb.get() : null;
        sLastClickedThumb = null;
        if (view == null || video == null || TextUtils.isEmpty(video.videoId) || !view.isAttachedToWindow()) {
            return;
        }
        android.os.Bundle options = android.app.ActivityOptions
                .makeScaleUpAnimation(view, 0, 0, view.getWidth(), view.getHeight()).toBundle();
        com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager.instance(view.getContext())
                .setNextLaunchOptions(PhonePlaybackActivity.class, options);
    }

    static int screenWidthPx() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    /**
     * Wide cards need a sharper image than the TV card url. Try the 720p thumbnail first and fall back to the original.
     */
    static void loadThumb(ImageView view, Video video, boolean vertical) {
        Context context = view.getContext();
        if (!isAlive(context)) {
            return;
        }

        String original = video != null ? video.getCardImageUrl() : null;
        if (TextUtils.isEmpty(original) && video != null && !TextUtils.isEmpty(video.videoId)) {
            original = "https://i.ytimg.com/vi/" + video.videoId + "/hqdefault.jpg"; // e.g. opened from a link
        }
        String better = null;

        if (video != null && !TextUtils.isEmpty(video.videoId) && !video.isChannel()) {
            better = "https://i.ytimg.com/vi/" + video.videoId + (vertical ? "/oar2.jpg" : "/hq720.jpg");
        }

        RequestBuilder<Drawable> fallback = Glide.with(context).load(original).centerCrop();

        if (better != null) {
            Glide.with(context)
                    .load(better)
                    .centerCrop()
                    .error(fallback)
                    .transition(DrawableTransitionOptions.withCrossFade(150))
                    .into(view);
        } else {
            fallback.transition(DrawableTransitionOptions.withCrossFade(150)).into(view);
        }
    }

    /** Fetches a card thumbnail into the disk cache ahead of time (no decoding, no memory used) */
    static void prefetchThumb(Context context, Video video) {
        prefetchThumb(context, video, video != null && video.isShorts);
    }

    /** @param vertical the full height picture of a Short (items of a Shorts list don't always carry the flag) */
    static void prefetchThumb(Context context, Video video, boolean vertical) {
        if (video == null || TextUtils.isEmpty(video.videoId) || video.isChannel() || !isAlive(context)) {
            return;
        }
        String url = "https://i.ytimg.com/vi/" + video.videoId + (vertical ? "/oar2.jpg" : "/hq720.jpg");
        try {
            Glide.with(context).downloadOnly().load(url).submit();
        } catch (Throwable ignored) {
            // best effort
        }
    }

    static void loadAvatar(ImageView view, String url) {
        Context context = view.getContext();
        if (!isAlive(context)) {
            return;
        }

        Glide.with(context)
                .load(url)
                .circleCrop()
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .into(view);
    }

    static CharSequence meta(Video video) {
        CharSequence second = video.getSecondTitle();
        if (TextUtils.isEmpty(second)) {
            return video.getAuthor();
        }
        return second.toString().replace("\n", " · ").trim();
    }

    /**
     * Binds the shared big/shelf card (phone_thumb + phone_card_text).
     */
    static void bindCard(View root, Video video) {
        ImageView thumb = root.findViewById(R.id.phone_thumb);
        TextView badge = root.findViewById(R.id.phone_badge);
        View progress = root.findViewById(R.id.phone_progress);
        TextView title = root.findViewById(R.id.phone_title);
        TextView meta = root.findViewById(R.id.phone_meta);

        title.setText(video.getTitle());
        CharSequence metaText = meta(video);
        meta.setText(metaText);
        meta.setVisibility(TextUtils.isEmpty(metaText) ? View.GONE : View.VISIBLE);

        loadThumb(thumb, video, false);

        ImageView avatar = root.findViewById(R.id.phone_avatar);
        if (avatar != null) {
            bindAuthorAvatar(avatar, video);
        }

        if (badge != null) {
            if (video.isLive) {
                badge.setText(R.string.phone_live);
                badge.setBackgroundResource(R.drawable.phone_live_bg);
                badge.setVisibility(View.VISIBLE);
            } else if (!TextUtils.isEmpty(video.badge)) {
                badge.setText(video.badge);
                badge.setBackgroundResource(R.drawable.phone_badge_bg);
                badge.setVisibility(View.VISIBLE);
            } else {
                badge.setVisibility(View.GONE);
            }
        }

        bindProgress(root, video);
    }

    /**
     * The red bar under a thumbnail: how much of the video was watched. YouTube's own number when signed in, otherwise
     * (or when this app has a newer one) the position this app saved when the video was last played here.
     */
    static void bindProgress(View root, Video video) {
        View progress = root.findViewById(R.id.phone_progress);
        if (progress == null) {
            return;
        }
        float percent = video.isLive ? 0 : Math.max(video.percentWatched, savedPercent(root.getContext(), video));
        if (percent > 0) {
            percent = Math.max(3f, Math.min(100f, percent)); // a sliver at least, like YouTube
            setWeight(root.findViewById(R.id.phone_progress_fill), percent);
            setWeight(root.findViewById(R.id.phone_progress_rest), 100f - percent);
            progress.setVisibility(View.VISIBLE);
        } else {
            progress.setVisibility(View.GONE);
        }
    }

    private static float savedPercent(android.content.Context context, Video video) {
        if (video.videoId == null) {
            return 0;
        }
        com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State state =
                com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.instance(context)
                        .getByVideoId(video.videoId);
        if (state == null || state.durationMs <= 0 || state.positionMs <= 0) {
            return 0;
        }
        return state.positionMs * 100f / state.durationMs;
    }

    /**
     * Channel picture next to the title (known pictures only, otherwise a colored initial). Tapping it opens the channel.
     */
    static void bindAuthorAvatar(ImageView view, Video video) {
        Context context = view.getContext();
        if (!isAlive(context)) {
            return;
        }

        String name = !TextUtils.isEmpty(video.getAuthor()) ? video.getAuthor() : video.getTitle();
        Drawable letter = new AvatarCache.LetterDrawable(name);
        String url = AvatarCache.find(context, video);

        final String key = AvatarFetcher.keyOf(video);
        view.setTag(R.id.phone_avatar, key);

        if (url != null) {
            Glide.with(context).load(url).circleCrop().placeholder(letter).error(letter).into(view);
        } else {
            Glide.with(context).clear(view);
            view.setImageDrawable(letter);
            // Look the channel picture up once, then show it if this view still shows the same channel
            AvatarFetcher.fetch(context, video, fetched -> {
                if (key != null && key.equals(view.getTag(R.id.phone_avatar)) && isAlive(view.getContext())) {
                    Glide.with(view.getContext()).load(fetched).circleCrop().placeholder(letter).into(view);
                }
            });
        }

        if (ChannelPresenter.canOpenChannel(video)) {
            view.setOnClickListener(v -> ChannelPresenter.instance(context).openChannel(video));
        } else {
            view.setOnClickListener(null);
            view.setClickable(false);
        }
    }

    private static void setWeight(View view, float weight) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.weight = weight;
        view.setLayoutParams(params);
    }
}
