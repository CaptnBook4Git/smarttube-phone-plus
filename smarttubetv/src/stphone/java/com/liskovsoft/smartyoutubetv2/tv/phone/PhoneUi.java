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

        if (progress != null) {
            float percent = video.percentWatched;
            if (percent > 0 && !video.isLive) {
                percent = Math.min(100f, percent);
                setWeight(root.findViewById(R.id.phone_progress_fill), percent);
                setWeight(root.findViewById(R.id.phone_progress_rest), 100f - percent);
                progress.setVisibility(View.VISIBLE);
            } else {
                progress.setVisibility(View.GONE);
            }
        }
    }

    private static void setWeight(View view, float weight) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.weight = weight;
        view.setLayoutParams(params);
    }
}
