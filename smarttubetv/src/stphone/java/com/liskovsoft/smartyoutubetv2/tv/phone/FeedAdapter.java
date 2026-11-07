package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.os.Parcelable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.phone.widget.RatioFrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns SmartTube's VideoGroup updates (the same ones the TV grids and rows get) into a phone feed.
 * <ul>
 *     <li>MODE_FLAT: every video is a full width card (grids, search, uploads)</li>
 *     <li>MODE_SHELVES: every group is a titled horizontal shelf (home rows, channel page)</li>
 *     <li>MODE_SHORTS: two column vertical tiles</li>
 *     <li>MODE_MULTI: channel avatars strip on top, the selected channel's videos below</li>
 *     <li>MODE_SETTINGS: plain settings rows</li>
 * </ul>
 */
public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int MODE_FLAT = 0;
    public static final int MODE_SHELVES = 1;
    public static final int MODE_SHORTS = 2;
    public static final int MODE_MULTI = 3;
    public static final int MODE_SETTINGS = 4;

    private static final int VT_VIDEO = 1;
    private static final int VT_SHORT = 2;
    private static final int VT_CHANNEL = 3;
    private static final int VT_SHELF = 4;
    private static final int VT_STRIP = 5;
    private static final int VT_SETTING = 6;

    public interface Listener {
        void onVideoClicked(Video video);

        void onVideoLongClicked(Video video);

        void onStripClicked(Video channel);

        void onShelfScrollEnd(Video lastVideo);
    }

    static final class Group {
        VideoGroup ref;
        final List<Video> videos = new ArrayList<>();
        final Shelf shelf = new Shelf(this);
        final Strip strip = new Strip(this);
        ShelfAdapter innerAdapter;
        Parcelable innerState;

        Group(VideoGroup ref) {
            this.ref = ref;
        }
    }

    static final class Shelf {
        final Group group;

        Shelf(Group group) {
            this.group = group;
        }
    }

    static final class Strip {
        final Group group;

        Strip(Group group) {
            this.group = group;
        }
    }

    private final Listener mListener;
    private final List<Group> mGroups = new ArrayList<>();
    private final List<Object> mItems = new ArrayList<>();
    private final List<SettingsItem> mSettings = new ArrayList<>();
    private final RecyclerView.RecycledViewPool mInnerPool = new RecyclerView.RecycledViewPool();
    private int mMode = MODE_FLAT;
    private Video mSelectedStripItem;

    public FeedAdapter(Listener listener) {
        mListener = listener;
        setHasStableIds(true);
    }

    // region Public API

    public void setMode(int mode) {
        mMode = mode;
        clear();
    }

    public int getMode() {
        return mMode;
    }

    public void clear() {
        mGroups.clear();
        mSettings.clear();
        mSelectedStripItem = null;
        rebuild();
    }

    public boolean isEmpty() {
        return mItems.isEmpty();
    }

    public void setSettings(List<SettingsItem> items) {
        mMode = MODE_SETTINGS;
        mGroups.clear();
        mSettings.clear();
        if (items != null) {
            mSettings.addAll(items);
        }
        rebuild();
    }

    public void setSelectedStripItem(Video video) {
        mSelectedStripItem = video;
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i) instanceof Strip) {
                Strip strip = (Strip) mItems.get(i);
                if (strip.group.innerAdapter != null) {
                    strip.group.innerAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    /**
     * Same semantics as the TV adapters (VideoGroupObjectAdapter / MultipleRowsFragment).
     */
    public void update(VideoGroup group) {
        if (group == null) {
            return;
        }

        switch (group.getAction()) {
            case VideoGroup.ACTION_REMOVE:
                removeVideos(group.getVideos(), null);
                return;
            case VideoGroup.ACTION_REMOVE_AUTHOR:
                if (group.getVideos() != null && !group.getVideos().isEmpty()) {
                    removeVideos(null, group.getVideos().get(0).getAuthor());
                }
                return;
            case VideoGroup.ACTION_SYNC:
                sync(group);
                return;
            case VideoGroup.ACTION_REPLACE:
                if (mMode == MODE_SHELVES && group.getPosition() != -1) {
                    removeGroup(group.getId());
                } else if (mMode == MODE_MULTI) {
                    removeMultiGroups(group.getPosition() == 0);
                } else {
                    mGroups.clear();
                }
                break;
        }

        if (group.getVideos() == null || group.getVideos().isEmpty()) {
            rebuild();
            return;
        }

        Group existing = findGroup(group);

        if (existing != null) {
            existing.ref = group;
            int oldCount = existing.videos.size();
            existing.videos.clear();
            existing.videos.addAll(group.getVideos());
            if (existing.innerAdapter != null) {
                existing.innerAdapter.onDataChanged(oldCount);
            }
        } else {
            Group newGroup = new Group(group);
            newGroup.videos.addAll(group.getVideos());
            int position = group.getPosition();
            if (mMode == MODE_SHELVES && position >= 0 && position < mGroups.size()) {
                mGroups.add(position, newGroup);
            } else {
                mGroups.add(newGroup);
            }
        }

        rebuild();
    }

    /**
     * The video whose group should be continued when the vertical list reaches its end.
     */
    public Video getScrollEndVideo() {
        if (mMode == MODE_SHELVES || mMode == MODE_SETTINGS) {
            return null;
        }

        for (int i = mGroups.size() - 1; i >= 0; i--) {
            Group group = mGroups.get(i);
            if (isStripGroup(group) || group.videos.isEmpty()) {
                continue;
            }
            return group.videos.get(group.videos.size() - 1);
        }

        return null;
    }

    public int indexOf(Video video) {
        for (int i = 0; i < mItems.size(); i++) {
            Object item = mItems.get(i);
            if (item instanceof Video && item.equals(video)) {
                return i;
            }
        }
        return -1;
    }

    public GridLayoutManager.SpanSizeLookup getSpanSizeLookup() {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getItemViewType(position) == VT_SHORT ? 1 : 2;
            }
        };
    }

    // endregion

    // region Model

    private Group findGroup(VideoGroup group) {
        for (Group g : mGroups) {
            if (g.ref == group) {
                return g;
            }
        }
        for (Group g : mGroups) {
            if (g.ref.getId() == group.getId()) {
                return g;
            }
        }
        return null;
    }

    private void removeGroup(int id) {
        for (int i = mGroups.size() - 1; i >= 0; i--) {
            if (mGroups.get(i).ref.getId() == id) {
                mGroups.remove(i);
            }
        }
    }

    private void removeMultiGroups(boolean strip) {
        for (int i = mGroups.size() - 1; i >= 0; i--) {
            if (isStripGroup(mGroups.get(i)) == strip) {
                mGroups.remove(i);
            }
        }
    }

    private boolean isStripGroup(Group group) {
        return mMode == MODE_MULTI && group.ref.getPosition() == 0;
    }

    private void removeVideos(List<Video> videos, String author) {
        for (Group group : mGroups) {
            boolean changed = false;
            for (int i = group.videos.size() - 1; i >= 0; i--) {
                Video video = group.videos.get(i);
                boolean match = videos != null ? videos.contains(video) : author != null && Helpers.equals(author, video.getAuthor());
                if (match) {
                    group.videos.remove(i);
                    if (video.getGroup() != null) {
                        video.getGroup().remove(video);
                    }
                    changed = true;
                }
            }
            if (changed && group.innerAdapter != null) {
                group.innerAdapter.notifyDataSetChanged();
            }
        }
        rebuild();
    }

    private void sync(VideoGroup group) {
        if (group.getVideos() == null) {
            return;
        }

        for (Video video : group.getVideos()) {
            for (Group g : mGroups) {
                for (int i = 0; i < g.videos.size(); i++) {
                    Video origin = g.videos.get(i);
                    if (origin.equals(video)) {
                        origin.sync(video);
                        if (g.innerAdapter != null) {
                            g.innerAdapter.notifyItemChanged(i);
                        }
                    }
                }
            }
        }

        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i) instanceof Video) {
                for (Video video : group.getVideos()) {
                    if (mItems.get(i).equals(video)) {
                        notifyItemChanged(i);
                        break;
                    }
                }
            }
        }
    }

    private void rebuild() {
        List<Object> items = new ArrayList<>();

        if (mMode == MODE_SETTINGS) {
            items.addAll(mSettings);
        } else {
            boolean multipleGroups = mGroups.size() > 1;
            for (Group group : mGroups) {
                if (group.videos.isEmpty()) {
                    continue;
                }
                if (mMode == MODE_SHELVES) {
                    items.add(group.shelf);
                } else if (isStripGroup(group)) {
                    items.add(group.strip);
                } else if (mMode != MODE_SHORTS && multipleGroups && group.ref.isShorts()) {
                    items.add(group.shelf);
                } else {
                    items.addAll(group.videos);
                }
            }
        }

        int oldSize = mItems.size();
        boolean isPrefix = oldSize > 0 && items.size() >= oldSize;
        for (int i = 0; isPrefix && i < oldSize; i++) {
            if (items.get(i) != mItems.get(i)) {
                isPrefix = false;
            }
        }

        mItems.clear();
        mItems.addAll(items);

        if (isPrefix) {
            if (items.size() > oldSize) {
                notifyItemRangeInserted(oldSize, items.size() - oldSize);
            }
        } else {
            notifyDataSetChanged();
        }
    }

    // endregion

    // region Adapter

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public long getItemId(int position) {
        Object item = mItems.get(position);
        long hash = System.identityHashCode(item) & 0xffffffffL;
        if (item instanceof Shelf) {
            return (1L << 32) | hash;
        } else if (item instanceof Strip) {
            return (2L << 32) | hash;
        } else if (item instanceof SettingsItem) {
            return (3L << 32) | hash;
        }
        return hash;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = mItems.get(position);
        if (item instanceof Shelf) {
            return VT_SHELF;
        } else if (item instanceof Strip) {
            return VT_STRIP;
        } else if (item instanceof SettingsItem) {
            return VT_SETTING;
        }

        Video video = (Video) item;
        if (mMode == MODE_SHORTS) {
            return VT_SHORT;
        } else if (isChannelItem(video)) {
            return VT_CHANNEL;
        }
        return VT_VIDEO;
    }

    static boolean isChannelItem(Video video) {
        return video.videoId == null && video.playlistId == null && video.channelId != null;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        switch (viewType) {
            case VT_SHORT: {
                View view = inflater.inflate(R.layout.phone_item_short, parent, false);
                ((RatioFrameLayout) view).setHeightToWidth(16f / 9f);
                return new Holder(view);
            }
            case VT_CHANNEL:
                return new Holder(inflater.inflate(R.layout.phone_item_channel, parent, false));
            case VT_SHELF:
            case VT_STRIP: {
                View view = inflater.inflate(R.layout.phone_item_shelf, parent, false);
                RecyclerView inner = view.findViewById(R.id.phone_shelf_list);
                inner.setLayoutManager(new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
                inner.setRecycledViewPool(mInnerPool);
                inner.setHasFixedSize(false);
                ShelfHolder holder = new ShelfHolder(view, inner);
                inner.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        holder.checkScrollEnd();
                    }
                });
                return holder;
            }
            case VT_SETTING:
                return new Holder(inflater.inflate(R.layout.phone_item_setting, parent, false));
            case VT_VIDEO:
            default:
                return new Holder(inflater.inflate(R.layout.phone_item_video, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = mItems.get(position);
        int type = holder.getItemViewType();

        if (type == VT_SHELF) {
            ((ShelfHolder) holder).bind(((Shelf) item).group, false);
        } else if (type == VT_STRIP) {
            ((ShelfHolder) holder).bind(((Strip) item).group, true);
        } else if (type == VT_SETTING) {
            bindSetting(holder.itemView, (SettingsItem) item);
        } else if (type == VT_SHORT) {
            bindShort(holder.itemView, (Video) item);
        } else if (type == VT_CHANNEL) {
            bindChannel(holder.itemView, (Video) item);
        } else {
            Video video = (Video) item;
            PhoneUi.bindCard(holder.itemView, video);
            bindClicks(holder.itemView, video);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof ShelfHolder) {
            ((ShelfHolder) holder).saveState();
        }
    }

    private void bindClicks(View view, Video video) {
        view.setOnClickListener(v -> mListener.onVideoClicked(video));
        view.setOnLongClickListener(v -> {
            mListener.onVideoLongClicked(video);
            return true;
        });
        View more = view.findViewById(R.id.phone_more);
        if (more != null) {
            more.setOnClickListener(v -> mListener.onVideoLongClicked(video));
        }
    }

    private void bindShort(View view, Video video) {
        TextView title = view.findViewById(R.id.phone_title);
        TextView meta = view.findViewById(R.id.phone_meta);
        title.setText(video.getTitle());
        CharSequence metaText = PhoneUi.meta(video);
        meta.setText(metaText);
        meta.setVisibility(TextUtils.isEmpty(metaText) ? View.GONE : View.VISIBLE);
        PhoneUi.loadThumb(view.findViewById(R.id.phone_thumb), video, true);
        bindClicks(view, video);
    }

    private void bindChannel(View view, Video video) {
        TextView title = view.findViewById(R.id.phone_title);
        TextView meta = view.findViewById(R.id.phone_meta);
        title.setText(!TextUtils.isEmpty(video.getTitle()) ? video.getTitle() : video.getAuthor());
        CharSequence metaText = PhoneUi.meta(video);
        meta.setText(metaText);
        meta.setVisibility(TextUtils.isEmpty(metaText) ? View.GONE : View.VISIBLE);
        PhoneUi.loadAvatar(view.findViewById(R.id.phone_thumb), video.getCardImageUrl());
        bindClicks(view, video);
    }

    private void bindSetting(View view, SettingsItem item) {
        ImageView icon = view.findViewById(R.id.phone_thumb);
        TextView title = view.findViewById(R.id.phone_title);
        title.setText(item.title);
        if (item.imageResId > 0) {
            icon.setImageResource(item.imageResId);
            icon.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.INVISIBLE);
        }
        view.setOnClickListener(v -> {
            if (item.onClick != null) {
                item.onClick.run();
            }
        });
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(View view) {
            super(view);
        }
    }

    private final class ShelfHolder extends RecyclerView.ViewHolder {
        private final TextView mTitle;
        private final RecyclerView mInner;
        private Group mGroup;

        ShelfHolder(View view, RecyclerView inner) {
            super(view);
            mTitle = view.findViewById(R.id.phone_title);
            mInner = inner;
        }

        void bind(Group group, boolean isStrip) {
            if (mGroup != null && mGroup != group) {
                saveState();
            }
            mGroup = group;

            String title = group.ref.getTitle();
            mTitle.setText(title);
            mTitle.setVisibility(isStrip || TextUtils.isEmpty(title) ? View.GONE : View.VISIBLE);

            int style = isStrip ? ShelfAdapter.STYLE_AVATAR : group.ref.isShorts() ? ShelfAdapter.STYLE_SHORT : ShelfAdapter.STYLE_CARD;
            if (group.innerAdapter == null || group.innerAdapter.getStyle() != style) {
                group.innerAdapter = new ShelfAdapter(group.videos, style, mListener, () -> mSelectedStripItem);
            }

            if (mInner.getAdapter() != group.innerAdapter) {
                mInner.setAdapter(group.innerAdapter);
                RecyclerView.LayoutManager manager = mInner.getLayoutManager();
                if (manager != null) {
                    if (group.innerState != null) {
                        manager.onRestoreInstanceState(group.innerState);
                    } else {
                        manager.scrollToPosition(0);
                    }
                }
            }
        }

        void saveState() {
            if (mGroup != null && mInner.getLayoutManager() != null) {
                mGroup.innerState = mInner.getLayoutManager().onSaveInstanceState();
            }
        }

        void checkScrollEnd() {
            if (mGroup == null || mGroup.videos.isEmpty() || isStripGroup(mGroup)) {
                return;
            }
            LinearLayoutManager manager = (LinearLayoutManager) mInner.getLayoutManager();
            if (manager == null) {
                return;
            }
            int last = manager.findLastVisibleItemPosition();
            if (last >= mGroup.videos.size() - 3) {
                mListener.onShelfScrollEnd(mGroup.videos.get(mGroup.videos.size() - 1));
            }
        }
    }

    // endregion
}
