package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

public class PhoneMomentsActivity extends PhoneActivity {
    private final MomentsAdapter adapter = new MomentsAdapter();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.phone_activity_downloads);
        ((TextView) findViewById(R.id.phone_screen_title)).setText(R.string.phone_moments);
        ((TextView) findViewById(R.id.phone_downloads_empty)).setText(R.string.phone_moments_empty);
        findViewById(R.id.phone_back).setOnClickListener(v -> finish());
        RecyclerView list = findViewById(R.id.phone_downloads_list);
        applyInsets(findViewById(R.id.phone_root), findViewById(R.id.phone_top_bar), list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        adapter.rows.clear();
        adapter.headers.clear();
        String previous = null;
        for (PhoneMoments.Moment moment : PhoneMoments.get(this).list()) {
            if (!moment.videoId.equals(previous)) {
                adapter.headers.put(adapter.rows.size(), true);
                adapter.rows.add(moment);
                previous = moment.videoId;
            }
            adapter.rows.add(moment);
        }
        adapter.notifyDataSetChanged();
        findViewById(R.id.phone_downloads_empty).setVisibility(adapter.rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void play(PhoneMoments.Moment moment) {
        Video video = Video.from(moment.videoId);
        video.title = moment.title;
        // The player uses zero to mean "restore history".
        video.pendingPosMs = Math.max(1, moment.position);
        PhoneMusic.onCardClick();
        PhoneUi.prepareExpandLaunch(video);
        PlaybackPresenter.instance(this).openVideo(video);
    }

    private void options(View anchor, PhoneMoments.Moment moment) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(moment.note.isEmpty() ? R.string.phone_moment_add_note : R.string.phone_moment_edit_note)
                .setOnMenuItemClickListener(item -> {
                    PhoneMoments.editNote(this, moment, this::refresh);
                    return true;
                });
        menu.getMenu().add(R.string.phone_moment_remove).setOnMenuItemClickListener(item -> {
            PhoneMoments.get(this).remove(moment);
            refresh();
            return true;
        });
        menu.show();
    }

    private final class MomentsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<PhoneMoments.Moment> rows = new ArrayList<>();
        private final SparseBooleanArray headers = new SparseBooleanArray();

        @Override
        public int getItemViewType(int position) {
            return headers.get(position) ? R.layout.phone_moment_header : R.layout.phone_moment_row;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int layout) {
            View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new RecyclerView.ViewHolder(view) { };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            View row = holder.itemView;
            PhoneMoments.Moment moment = rows.get(position);
            if (headers.get(position)) {
                ((TextView) row.findViewById(R.id.phone_title)).setText(moment.title);
                ImageView thumb = row.findViewById(R.id.phone_thumb);
                PhoneUi.loadThumb(thumb, Video.from(moment.videoId), false);
                return;
            }
            String time = moment.time();
            ((TextView) row.findViewById(R.id.phone_moment_time)).setText(time);
            TextView note = row.findViewById(R.id.phone_moment_note);
            note.setText(moment.note);
            View target = row.findViewById(R.id.phone_moment_play);
            String label = getString(R.string.phone_moment_play, time);
            target.setContentDescription(TextUtils.isEmpty(moment.note) ? label : label + ", " + moment.note);
            target.setOnClickListener(v -> play(moment));
            View more = row.findViewById(R.id.phone_moment_options);
            more.setContentDescription(getString(R.string.phone_moment_menu, time));
            more.setOnClickListener(v -> options(v, moment));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    @Override
    protected boolean hasPendingActions() {
        return false;
    }

    @Override
    protected void onListScrollEnd(Video lastVideo) {
    }
}
