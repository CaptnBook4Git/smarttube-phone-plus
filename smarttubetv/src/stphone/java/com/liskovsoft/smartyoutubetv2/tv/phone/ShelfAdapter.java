package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.phone.widget.RatioFrameLayout;

import java.util.List;

/**
 * Horizontal row inside the feed: video cards, Shorts tiles or channel avatars.
 */
class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.Holder> {
    static final int STYLE_CARD = 10;
    static final int STYLE_SHORT = 11;
    static final int STYLE_AVATAR = 12;

    interface SelectionProvider {
        Video getSelected();
    }

    private final List<Video> mVideos;
    private final int mStyle;
    private final FeedAdapter.Listener mListener;
    private final SelectionProvider mSelection;

    ShelfAdapter(List<Video> videos, int style, FeedAdapter.Listener listener, SelectionProvider selection) {
        mVideos = videos;
        mStyle = style;
        mListener = listener;
        mSelection = selection;
    }

    int getStyle() {
        return mStyle;
    }

    void onDataChanged(int oldCount) {
        int newCount = mVideos.size();
        if (newCount > oldCount && oldCount > 0) {
            notifyItemRangeInserted(oldCount, newCount - oldCount);
        } else {
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemCount() {
        return mVideos.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mStyle;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        int parentWidth = parent.getWidth() > 0 ? parent.getWidth() : PhoneUi.screenWidthPx();
        View view;

        if (viewType == STYLE_AVATAR) {
            view = inflater.inflate(R.layout.phone_item_avatar, parent, false);
        } else if (viewType == STYLE_SHORT) {
            view = inflater.inflate(R.layout.phone_item_short, parent, false);
            ((RatioFrameLayout) view).setHeightToWidth(16f / 9f);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.width = (int) (parentWidth * 0.40f);
            view.setLayoutParams(params);
        } else {
            view = inflater.inflate(R.layout.phone_item_shelf_card, parent, false);
            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.width = (int) (parentWidth * 0.80f);
            view.setLayoutParams(params);
        }

        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Video video = mVideos.get(position);
        View view = holder.itemView;

        if (mStyle == STYLE_AVATAR) {
            TextView title = view.findViewById(R.id.phone_title);
            ImageView avatar = view.findViewById(R.id.phone_thumb);
            title.setText(!TextUtils.isEmpty(video.getTitle()) ? video.getTitle() : video.getAuthor());
            PhoneUi.loadAvatar(avatar, video.getCardImageUrl());
            boolean selected = video.equals(mSelection.getSelected());
            title.setTextColor(view.getResources().getColor(selected ? R.color.phone_text : R.color.phone_text_2));
            avatar.setAlpha(mSelection.getSelected() == null || selected ? 1f : 0.55f);
            view.setOnClickListener(v -> mListener.onStripClicked(video));
            view.setOnLongClickListener(v -> {
                mListener.onVideoLongClicked(video);
                return true;
            });
            return;
        }

        if (mStyle == STYLE_SHORT) {
            TextView title = view.findViewById(R.id.phone_title);
            TextView meta = view.findViewById(R.id.phone_meta);
            title.setText(video.getTitle());
            CharSequence metaText = PhoneUi.meta(video);
            meta.setText(metaText);
            meta.setVisibility(TextUtils.isEmpty(metaText) ? View.GONE : View.VISIBLE);
            PhoneUi.loadThumb(view.findViewById(R.id.phone_thumb), video, true);
        } else {
            PhoneUi.bindCard(view, video);
            View more = view.findViewById(R.id.phone_more);
            if (more != null) {
                more.setOnClickListener(v -> mListener.onVideoLongClicked(video));
            }
        }

        view.setOnClickListener(v -> mListener.onVideoClicked(video));
        view.setOnLongClickListener(v -> {
            mListener.onVideoLongClicked(video);
            return true;
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View view) {
            super(view);
        }
    }
}
