package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.reactivex.disposables.Disposable;

/**
 * Top of the Subscriptions tab, like the YouTube app: a row of your channels (newest uploads first, a blue dot on
 * channels with new videos), then filter chips (All, Today, Videos, Shorts, Live) for the feed below.
 * Tapping a channel selects it (the feed shows only its videos and Shorts); tapping it again shows every channel.
 */
final class PhoneSubscriptionsHeader {
    interface Callback {
        /** A channel was selected, or null: the selection was cleared */
        void onChannelSelected(Video channel);

        void onChannelLongClicked(Video channel);

        void onFilterChanged(FeedAdapter.VideoFilter filter);
    }

    private static final long RELOAD_AFTER_MS = 5 * 60 * 1000;
    /** "3 hours ago", "Streamed 20 minutes ago": uploaded today */
    private static final Pattern TODAY = Pattern.compile("\\b(second|minute|hour)s?\\b", Pattern.CASE_INSENSITIVE);

    private static final int FILTER_ALL = 0;
    private static final int FILTER_TODAY = 1;
    private static final int FILTER_VIDEOS = 2;
    private static final int FILTER_SHORTS = 3;
    private static final int FILTER_LIVE = 4;

    private final Activity mActivity;
    private final Callback mCallback;
    private final View mView;
    private final RecyclerView mChannelsList;
    private final LinearLayout mFilters;
    private final List<Video> mChannels = new ArrayList<>();
    private final ShelfAdapter mChannelsAdapter;
    private Video mSelected;
    private int mFilter = FILTER_ALL;
    private long mLoadedAt;
    private Disposable mLoadAction;

    PhoneSubscriptionsHeader(Activity activity, ViewGroup parent, Callback callback) {
        mActivity = activity;
        mCallback = callback;
        mView = LayoutInflater.from(activity).inflate(R.layout.phone_subs_header, parent, false);
        mChannelsList = mView.findViewById(R.id.phone_subs_channels);
        mFilters = mView.findViewById(R.id.phone_subs_filters);

        mChannelsList.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
        mChannelsAdapter = new ShelfAdapter(mChannels, ShelfAdapter.STYLE_AVATAR, new FeedAdapter.Listener() {
            @Override
            public void onVideoClicked(Video video) {
                toggle(video);
            }

            @Override
            public void onVideoLongClicked(Video video) {
                mCallback.onChannelLongClicked(video);
            }

            @Override
            public void onStripClicked(Video channel) {
                toggle(channel);
            }

            @Override
            public void onShelfScrollEnd(Video lastVideo) {
            }
        }, () -> mSelected);
        mChannelsList.setAdapter(mChannelsAdapter);
        mChannelsList.setVisibility(View.GONE);



        buildFilters();
    }

    View getView() {
        return mView;
    }

    /**
     * The Subscriptions feed was opened (or refreshed): every channel, the All filter, and the channels reloaded when
     * they are a few minutes old.
     */
    void onShown(boolean forceReload) {
        select(null);
        setFilter(FILTER_ALL);
        if (forceReload || mChannels.isEmpty() || SystemClock.elapsedRealtime() - mLoadedAt > RELOAD_AFTER_MS) {
            loadChannels();
        }
    }

    private void toggle(Video channel) {
        select(channel != null && channel.equals(mSelected) ? null : channel);
    }

    private void select(Video channel) {
        if (channel == mSelected) {
            return;
        }
        mSelected = channel;
        mChannelsAdapter.notifyDataSetChanged();
        mCallback.onChannelSelected(channel);
    }

    void release() {
        RxHelper.disposeActions(mLoadAction);
    }

    private void loadChannels() {
        RxHelper.disposeActions(mLoadAction);
        mLoadAction = RxHelper.execute(
                YouTubeServiceManager.instance().getContentService().getSubscribedChannelsByNewContentObserve(),
                this::showChannels,
                error -> { /* keep what is shown; the All link still lists the channels */ });
    }

    private void showChannels(MediaGroup group) {
        if (!PhoneUi.isAlive(mActivity) || group == null || group.getMediaItems() == null) {
            return;
        }
        mChannels.clear();
        // YouTube's TV channel list repeats channels and has entries without a picture (sorting, A-Z): one avatar
        // per channel
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (MediaItem item : group.getMediaItems()) {
            if (item == null || android.text.TextUtils.isEmpty(item.getCardImageUrl()) || android.text.TextUtils.isEmpty(item.getTitle())) {
                continue;
            }
            String title = item.getTitle().trim().toLowerCase();
            String channelId = item.getChannelId();
            if (seen.contains(title) || (channelId != null && seen.contains(channelId))) {
                continue;
            }
            seen.add(title);
            if (channelId != null) {
                seen.add(channelId);
            }
            mChannels.add(Video.from(item));
        }
        if (mSelected != null && !mChannels.contains(mSelected)) {
            select(null);
        }
        mLoadedAt = SystemClock.elapsedRealtime();
        mChannelsAdapter.notifyDataSetChanged();
        mChannelsList.scrollToPosition(0);
        mChannelsList.setVisibility(mChannels.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // region Filters

    private void buildFilters() {
        int[] titles = {R.string.phone_filter_all, R.string.phone_filter_today, R.string.phone_filter_videos,
                R.string.phone_filter_shorts, R.string.phone_filter_live};
        for (int i = 0; i < titles.length; i++) {
            TextView chip = new TextView(mActivity);
            chip.setText(titles[i]);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), 0, dp(12), 0);
            chip.setBackgroundResource(R.drawable.phone_chip_bg);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            final int filter = i;
            chip.setOnClickListener(v -> setFilter(filter));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
            params.setMarginStart(dp(4));
            params.setMarginEnd(dp(4));
            mFilters.addView(chip, params);
        }
        renderFilters();
    }

    private void setFilter(int filter) {
        mFilter = filter;
        renderFilters();
        mCallback.onFilterChanged(filterOf(filter));
    }

    private void renderFilters() {
        for (int i = 0; i < mFilters.getChildCount(); i++) {
            TextView chip = (TextView) mFilters.getChildAt(i);
            boolean selected = i == mFilter;
            chip.setSelected(selected);
            chip.setTextColor(mActivity.getResources().getColor(selected ? R.color.phone_bg : R.color.phone_text));
        }
    }

    private static FeedAdapter.VideoFilter filterOf(int filter) {
        switch (filter) {
            case FILTER_TODAY:
                return video -> video.isLive || isToday(video);
            case FILTER_VIDEOS:
                return video -> !video.isShorts && !video.isLive && !video.isUpcoming;
            case FILTER_SHORTS:
                return video -> video.isShorts;
            case FILTER_LIVE:
                return video -> video.isLive || video.isUpcoming;
            case FILTER_ALL:
            default:
                return null;
        }
    }

    private static boolean isToday(Video video) {
        CharSequence details = video.getSecondTitle();
        return details != null && TODAY.matcher(details).find();
    }

    // endregion

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, mActivity.getResources().getDisplayMetrics());
    }
}
