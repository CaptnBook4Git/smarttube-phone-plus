package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The Notifications tab: new videos from subscribed channels, newest first (collected by {@link PhoneNewVideos}).
 * Opening it also looks for new ones right away and marks everything as seen.
 */
public class PhoneNotificationsActivity extends PhoneActivity {
    private final List<PhoneNewVideos.Entry> mItems = new ArrayList<>();
    private RecyclerView mList;
    private TextView mEmpty;
    private TextView mAction;

    static void open(Activity activity) {
        activity.startActivity(new Intent(activity, PhoneNotificationsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_activity_downloads); // the same plain list screen
        ((TextView) findViewById(R.id.phone_screen_title)).setText(R.string.phone_tab_notifications);
        mList = findViewById(R.id.phone_downloads_list);
        mEmpty = findViewById(R.id.phone_downloads_empty);
        mAction = findViewById(R.id.phone_downloads_total);
        applyInsets(findViewById(R.id.phone_root), findViewById(R.id.phone_top_bar), mList);
        findViewById(R.id.phone_back).setOnClickListener(v -> finish());

        mAction.setText(R.string.phone_new_videos_clear);
        mAction.setTextColor(getResources().getColor(R.color.phone_text));
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        mAction.setPadding(pad, pad, 0, pad);
        mAction.setOnClickListener(v -> {
            PhoneNewVideos.clear(this);
            load();
        });

        mList.setLayoutManager(new LinearLayoutManager(this));
        mList.setAdapter(new Adapter());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        PhoneNewVideos.markRead(this);
        // Fresh right now, not only at the next background check
        RxHelper.runAsync(() -> {
            try {
                if (PhoneNewVideos.check(getApplicationContext()) > 0) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            load();
                            PhoneNewVideos.markRead(this);
                        }
                    });
                }
            } catch (Throwable ignored) {
                // offline: the list shows what was found before
            }
        });
    }

    private void load() {
        mItems.clear();
        mItems.addAll(PhoneNewVideos.inbox(this));
        if (mList.getAdapter() != null) {
            mList.getAdapter().notifyDataSetChanged();
        }
        boolean signedIn = YouTubeServiceManager.instance().getSignInService().isSigned();
        mEmpty.setText(!signedIn ? R.string.phone_new_videos_sign_in
                : PhoneNewVideos.isEnabled(this) ? R.string.phone_new_videos_empty : R.string.phone_new_videos_off);
        mEmpty.setVisibility(mItems.isEmpty() ? View.VISIBLE : View.GONE);
        mAction.setVisibility(mItems.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected boolean hasPendingActions() {
        return false;
    }

    @Override
    protected void onListScrollEnd(Video lastVideo) {
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.phone_item_row, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            PhoneNewVideos.Entry entry = mItems.get(position);
            Video video = entry.video;
            View view = holder.itemView;
            ((TextView) view.findViewById(R.id.phone_title)).setText(video.getTitle());
            CharSequence when = DateUtils.getRelativeTimeSpanString(entry.foundAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            String author = video.getAuthor();
            ((TextView) view.findViewById(R.id.phone_meta)).setText(author != null ? author + " · " + when : when);

            ImageView thumb = view.findViewById(R.id.phone_thumb);
            thumb.setImageTintList(null);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            PhoneUi.loadThumb(thumb, video, false);
            View badge = view.findViewById(R.id.phone_badge);
            if (badge != null) {
                badge.setVisibility(View.GONE);
            }
            View progress = view.findViewById(R.id.phone_progress);
            if (progress != null) {
                progress.setVisibility(View.GONE);
            }
            View more = view.findViewById(R.id.phone_more);
            if (more != null) {
                more.setVisibility(View.GONE);
            }
            view.setOnClickListener(v -> PlaybackPresenter.instance(PhoneNotificationsActivity.this).openVideo(video));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }
}
