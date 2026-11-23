package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.content.ContentUris;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.widget.LinearLayout;

import com.liskovsoft.smartyoutubetv2.tv.phone.PhoneDownloads.Kind;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * You > Downloads: what StreamTube saved to Download/StreamTube (newest first). Tap to play it in the app, the menu to
 * share or delete it.
 */
public class PhoneDownloadsActivity extends PhoneActivity {
    static final String EXTRA_KIND = "download_kind";
    private static final int REQUEST_DELETE = 4801;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private List<Item> mAllItems = Collections.emptyList();
    private final List<Item> mItems = new ArrayList<>();
    private final List<TextView> mTabs = new ArrayList<>();
    private final Runnable mReload = this::load;
    private final ContentObserver mDownloadObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            mHandler.removeCallbacks(mReload);
            mHandler.postDelayed(mReload, 300);
        }
    };
    private Kind mKind = Kind.VIDEO;
    private RecyclerView mDownloads;
    private TextView mEmpty;
    private TextView mTotal;
    private Item mPendingDelete;

    static final class Item {
        final Uri uri;
        final String title;
        final long size;
        final long addedMs;
        final Kind kind;
        long durationMs;

        Item(Uri uri, String title, long size, long addedMs, Kind kind, long durationMs) {
            this.uri = uri;
            this.title = title;
            this.size = size;
            this.addedMs = addedMs;
            this.kind = kind;
            this.durationMs = durationMs;
        }
    }

    static void open(Activity activity) {
        activity.startActivity(new Intent(activity, PhoneDownloadsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_activity_downloads);
        mDownloads = findViewById(R.id.phone_downloads_list);
        mEmpty = findViewById(R.id.phone_downloads_empty);
        mTotal = findViewById(R.id.phone_downloads_total);
        applyInsets(findViewById(R.id.phone_root), findViewById(R.id.phone_top_bar), mDownloads);
        findViewById(R.id.phone_back).setOnClickListener(v -> finish());

        mDownloads.setLayoutManager(new LinearLayoutManager(this));
        mDownloads.setAdapter(new Adapter());
        LinearLayout tabs = findViewById(R.id.phone_download_tabs);
        tabs.setVisibility(View.VISIBLE);
        float density = getResources().getDisplayMetrics().density;
        int normal = getResources().getColor(R.color.phone_text);
        int selected = getResources().getColor(R.color.phone_bg);
        for (Kind kind : Kind.values()) {
            TextView tab = new TextView(this);
            tab.setText(kind.label);
            tab.setTypeface(PhoneUi.medium(this));
            tab.setTextSize(14);
            tab.setGravity(Gravity.CENTER);
            tab.setFocusable(true);
            tab.setBackgroundResource(R.drawable.phone_chip_bg);
            tab.setForeground(getDrawable(R.drawable.phone_ripple));
            tab.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_selected}, {}}, new int[]{selected, normal}));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Math.round(48 * density), 1f);
            if (!mTabs.isEmpty()) {
                params.setMarginStart(Math.round(8 * density));
            }
            tabs.addView(tab, params);
            mTabs.add(tab);
            tab.setOnClickListener(v -> select(kind));
        }
        select(readKind(savedInstanceState != null ? savedInstanceState.getString(EXTRA_KIND)
                : getIntent().getStringExtra(EXTRA_KIND)));
        mEmpty.setVisibility(View.GONE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        select(readKind(intent.getStringExtra(EXTRA_KIND)));
        load();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString(EXTRA_KIND, mKind.name());
    }

    private static Kind readKind(String value) {
        for (Kind kind : Kind.values()) {
            if (kind.name().equals(value)) {
                return kind;
            }
        }
        return Kind.VIDEO;
    }

    private void select(Kind kind) {
        mKind = kind;
        for (int i = 0; i < mTabs.size(); i++) {
            mTabs.get(i).setSelected(i == kind.ordinal());
        }
        show(mAllItems);
        mDownloads.scrollToPosition(0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 29) {
            getContentResolver().registerContentObserver(MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, mDownloadObserver);
        }
    }

    @Override
    protected void onStop() {
        if (Build.VERSION.SDK_INT >= 29) {
            getContentResolver().unregisterContentObserver(mDownloadObserver);
            mHandler.removeCallbacks(mReload);
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(); // a download may have finished meanwhile
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected boolean hasPendingActions() {
        return false;
    }

    @Override
    protected void onListScrollEnd(Video lastVideo) {
    }

    // region Files

    private void load() {
        LOADER.execute(() -> {
            List<Item> items = Build.VERSION.SDK_INT >= 29 ? queryMediaStore() : listFolder();
            for (Item item : items) {
                if (item.durationMs <= 0) {
                    item.durationMs = readDuration(item.uri);
                }
            }
            Collections.sort(items, (a, b) -> Long.compare(b.addedMs, a.addedMs));
            mHandler.post(() -> show(items));
        });
    }

    /** Android 10+: the files this app put in Download/StreamTube (an app sees its own downloads without permission) */
    private List<Item> queryMediaStore() {
        List<Item> items = new ArrayList<>();
        if (Build.VERSION.SDK_INT < 29) {
            return items;
        }
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.VOLUME_NAME};
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " IN (?, ?, ?) AND "
                + MediaStore.MediaColumns.IS_PENDING + " = 0";
        String[] args = {Kind.VIDEO.relativePath(), Kind.SHORT.relativePath(), Kind.MUSIC.relativePath()};
        try (Cursor cursor = getContentResolver().query(collection, projection, selection, args, null)) {
            if (cursor == null) {
                return items;
            }
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(4);
                Kind folder = Kind.SHORT.relativePath().equals(cursor.getString(6)) ? Kind.SHORT : Kind.VIDEO;
                String volume = cursor.getString(7);
                Uri uri = ContentUris.withAppendedId(volume == null ? collection : MediaStore.Downloads.getContentUri(volume), id);
                addFile(items, uri, name, mime, cursor.getLong(2), cursor.getLong(3) * 1000,
                        cursor.isNull(5) ? 0 : cursor.getLong(5), folder);
            }
        } catch (RuntimeException e) {
            // no list: shows the empty state
        }
        return items;
    }

    /** Android 8 and 9: the folder itself (the app already has the storage permission it downloaded with) */
    @SuppressWarnings("deprecation")
    private List<Item> listFolder() {
        List<Item> items = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            File folder = new File(Environment.getExternalStorageDirectory(), kind.relativePath());
            File[] files = folder.listFiles(File::isFile);
            if (files != null) {
                for (File file : files) {
                    addFile(items, Uri.fromFile(file), file.getName(), null, file.length(), file.lastModified(), 0, kind);
                }
            }
        }
        return items;
    }

    private void addFile(List<Item> items, Uri uri, String name, String mime, long size, long added, long duration, Kind folder) {
        boolean audio = isAudio(name, mime);
        if (!audio && (mime == null || !mime.startsWith("video/"))
                && (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4"))) {
            return;
        }
        String title = PhoneDownloadDetails.read(this, uri, title(name)).title;
        items.add(new Item(uri, title, size, added, audio ? Kind.MUSIC : folder, duration));
    }

    private long readDuration(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return duration != null ? Long.parseLong(duration) : 0;
        } catch (RuntimeException e) {
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // released
            }
        }
    }

    private static String title(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static boolean isAudio(String name, String mime) {
        return (mime != null && mime.startsWith("audio")) || (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".m4a"));
    }

    private void show(List<Item> items) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        mAllItems = items;
        mItems.clear();
        for (Item item : items) {
            if (item.kind == mKind) {
                mItems.add(item);
            }
        }
        mDownloads.getAdapter().notifyDataSetChanged();
        mEmpty.setText(mKind.empty);
        mEmpty.setVisibility(mItems.isEmpty() ? View.VISIBLE : View.GONE);
        long total = 0;
        for (Item item : mItems) {
            total += item.size;
        }
        mTotal.setText(mItems.isEmpty() ? null : getString(R.string.phone_downloads_total, mItems.size(),
                Formatter.formatShortFileSize(this, total)));
    }

    // endregion

    // region Actions

    private void play(Item item) {
        PhoneLocalPlayerActivity.open(this, item);
    }

    private void share(Item item) {
        if ("file".equals(item.uri.getScheme())) {
            return; // Android 8 and 9: no shareable link for a plain file
        }
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType(item.kind == Kind.MUSIC ? "audio/mp4" : "video/mp4")
                .putExtra(Intent.EXTRA_STREAM, item.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, item.title));
    }

    private void confirmDelete(Item item) {
        new AlertDialog.Builder(this, PhoneTheme.isNight(this)
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle(R.string.phone_downloads_delete_title)
                .setMessage(item.title)
                .setNegativeButton(R.string.phone_comment_delete_cancel, null)
                .setPositiveButton(R.string.phone_comment_delete, (dialog, which) -> delete(item))
                .show();
    }

    private void delete(Item item) {
        try {
            if ("file".equals(item.uri.getScheme())) {
                File file = new File(item.uri.getPath());
                if (!file.delete()) {
                    throw new SecurityException("not deleted");
                }
            } else {
                getContentResolver().delete(item.uri, null, null);
            }
            removed(item);
        } catch (SecurityException e) {
            // Downloaded before a reinstall: the file belongs to the old install, Android asks the user first
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    mPendingDelete = item;
                    startIntentSenderForResult(MediaStore.createDeleteRequest(getContentResolver(),
                            Collections.singletonList(item.uri)).getIntentSender(), REQUEST_DELETE, null, 0, 0, 0);
                    return;
                } catch (Exception ignored) {
                    // falls through to the message
                }
            } else if (Build.VERSION.SDK_INT == 29 && e instanceof android.app.RecoverableSecurityException) {
                try {
                    mPendingDelete = item;
                    startIntentSenderForResult(((android.app.RecoverableSecurityException) e).getUserAction()
                            .getActionIntent().getIntentSender(), REQUEST_DELETE, null, 0, 0, 0);
                    return;
                } catch (Exception ignored) {
                    // falls through to the message
                }
            }
            android.widget.Toast.makeText(this, R.string.phone_downloads_delete_failed, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DELETE && mPendingDelete != null) {
            Item item = mPendingDelete;
            mPendingDelete = null;
            if (resultCode == RESULT_OK) {
                if (Build.VERSION.SDK_INT == 29) {
                    delete(item); // allowed now: delete it for real
                } else {
                    removed(item); // Android deleted it
                }
            }
        }
    }

    private void removed(Item item) {
        PhoneDownloadDetails.remove(this, item.uri);
        List<Item> remaining = new ArrayList<>(mAllItems);
        remaining.removeIf(other -> other.uri.equals(item.uri));
        show(remaining);
        load();
        android.widget.Toast.makeText(this, R.string.phone_downloads_deleted, android.widget.Toast.LENGTH_SHORT).show();
    }

    // endregion

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return mItems.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.phone_item_row, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = mItems.get(position);
            View view = holder.itemView;
            ImageView thumb = view.findViewById(R.id.phone_thumb);
            TextView badge = view.findViewById(R.id.phone_badge);
            View progress = view.findViewById(R.id.phone_progress);
            ((TextView) view.findViewById(R.id.phone_title)).setText(item.title);

            String size = Formatter.formatShortFileSize(view.getContext(), item.size);
            CharSequence when = DateUtils.getRelativeTimeSpanString(item.addedMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            int kindLabel = item.kind == Kind.VIDEO ? R.string.phone_downloads_video
                    : item.kind == Kind.MUSIC ? R.string.phone_downloads_audio : R.string.phone_downloads_shorts_tab;
            String kind = getString(kindLabel);
            ((TextView) view.findViewById(R.id.phone_meta)).setText(kind + " · " + size + " · " + when);

            if (progress != null) {
                progress.setVisibility(View.GONE);
            }
            if (badge != null) {
                if (item.durationMs > 0) {
                    badge.setText(DateUtils.formatElapsedTime(item.durationMs / 1000));
                    badge.setBackgroundResource(R.drawable.phone_badge_bg);
                    badge.setVisibility(View.VISIBLE);
                } else {
                    badge.setVisibility(View.GONE);
                }
            }
            if (PhoneUi.isAlive(PhoneDownloadsActivity.this)) {
                if (item.kind == Kind.MUSIC) {
                    Glide.with(PhoneDownloadsActivity.this).clear(thumb);
                    thumb.setScaleType(ImageView.ScaleType.CENTER);
                    thumb.setBackgroundColor(getResources().getColor(R.color.phone_surface_2));
                    thumb.setImageResource(R.drawable.ic_phone_headphones);
                    thumb.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.phone_text_2)));
                } else {
                    thumb.setImageTintList(null);
                    thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    thumb.setBackgroundColor(Color.BLACK);
                    Glide.with(PhoneDownloadsActivity.this).load(item.uri).centerCrop()
                            .placeholder(new ColorDrawable(Color.BLACK)).into(thumb); // a frame of the video
                }
            }

            view.setOnClickListener(v -> play(item));
            View more = view.findViewById(R.id.phone_more);
            more.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(PhoneDownloadsActivity.this, more);
                menu.getMenu().add(0, 1, 0, R.string.phone_downloads_play);
                if (!"file".equals(item.uri.getScheme())) {
                    menu.getMenu().add(0, 2, 1, R.string.phone_share);
                }
                menu.getMenu().add(0, 3, 2, R.string.phone_comment_delete);
                menu.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case 1:
                            play(item);
                            return true;
                        case 2:
                            share(item);
                            return true;
                        case 3:
                            confirmDelete(item);
                            return true;
                        default:
                            return false;
                    }
                });
                menu.show();
            });
            view.setOnLongClickListener(v -> {
                confirmDelete(item);
                return true;
            });
        }
    }
}
