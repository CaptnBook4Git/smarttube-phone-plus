package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.phone.PhoneDownloads.Kind;
import com.liskovsoft.smartyoutubetv2.tv.phone.PhoneDownloadsActivity.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Offline playback uses only the saved file and its local download details. */
public class PhoneLocalPlayerActivity extends Activity implements PhoneMusicPlayer.Host {
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_KIND = "kind";
    private static final String EXTRA_SIZE = "size";
    private static final String EXTRA_ADDED = "added";
    private static final String EXTRA_DURATION = "duration";
    private static final String STATE_CURRENT = "current";
    private static final String STATE_POSITION = "position";
    private static final String STATE_PLAYING = "playing";
    private static final String STATE_SPEED = "speed";
    private static final String STATE_FULLSCREEN = "fullscreen";
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

    private SimpleExoPlayer mPlayer;
    private PlayerView mPlayerView;
    private Kind mKind;
    private PhoneLocalControls mControls;
    private PhoneMusicPlayer mMusic;
    private Video mCurrent;
    private final List<Video> mQueue = new ArrayList<>();
    private final Map<String, Item> mFiles = new HashMap<>();
    private int mIndex;

    private long mResumePosition;
    private float mSpeed = 1f;
    private boolean mPlaying = true;
    private boolean mFullscreen;

    static void open(Activity activity, PhoneDownloadsActivity.Item item) {
        PlaybackView streaming = PlaybackPresenter.instance(activity).getView();
        if (streaming != null) {
            streaming.setPlayWhenReady(false);
        }
        activity.startActivity(intent(activity, item));
    }

    private static Intent intent(Activity activity, Item item) {
        return new Intent(activity, PhoneLocalPlayerActivity.class)
                .setData(item.uri)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_TITLE, item.title)
                .putExtra(EXTRA_KIND, item.kind.name())
                .putExtra(EXTRA_SIZE, item.size)
                .putExtra(EXTRA_ADDED, item.addedMs)
                .putExtra(EXTRA_DURATION, item.durationMs);
    }

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(PhoneTheme.wrap(context));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PhoneTheme.apply(this);
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_CURRENT)) {
            setIntent(savedInstanceState.getParcelable(STATE_CURRENT));
        }
        Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }
        mKind = Kind.valueOf(getIntent().getStringExtra(EXTRA_KIND));
        if (savedInstanceState != null) {
            mResumePosition = savedInstanceState.getLong(STATE_POSITION);
            mPlaying = savedInstanceState.getBoolean(STATE_PLAYING, true);
            mSpeed = savedInstanceState.getFloat(STATE_SPEED, 1f);
            mFullscreen = savedInstanceState.getBoolean(STATE_FULLSCREEN);
        } else {
            mFullscreen = mKind != Kind.MUSIC && isLandscape();
        }

        setContentView(R.layout.phone_activity_offline_player);
        mPlayerView = findViewById(R.id.phone_offline_player);
        mPlayerView.setErrorMessageProvider(error -> android.util.Pair.create(0, getString(R.string.phone_offline_failed)));
        if (mKind == Kind.MUSIC) {
            findViewById(R.id.phone_offline_root).setVisibility(View.GONE);
            mMusic = new PhoneMusicPlayer(this, this, true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mControls = new PhoneLocalControls(this, mPlayerView, mKind == Kind.SHORT);
        }
        bindItem();
        setFullscreen(mKind == Kind.SHORT || mFullscreen);
        if (mKind != Kind.VIDEO) {
            PhoneDownloadsActivity.loadQueue(this, mKind, this::setQueue);
        }
    }

    private void bindItem() {
        Uri uri = getIntent().getData();
        PhoneDownloadDetails details = PhoneDownloadDetails.read(this, uri, getIntent().getStringExtra(EXTRA_TITLE));
        mCurrent = Video.from(uri.toString());
        mCurrent.title = details.title;
        mCurrent.author = details.author;
        if (mControls != null) mControls.bind(details);
        ((TextView) findViewById(R.id.phone_offline_title)).setText(details.title);
        TextView author = findViewById(R.id.phone_offline_author);
        author.setText(details.author);
        author.setVisibility(details.author.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.phone_offline_description)).setText(details.description.isEmpty()
                ? getText(R.string.phone_offline_no_description) : details.description);
        showFileInfo(getIntent().getLongExtra(EXTRA_DURATION, 0));
    }

    private void showFileInfo(long duration) {
        List<CharSequence> info = new ArrayList<>();
        info.add(getText(R.string.phone_offline_badge));
        if (duration > 0) {
            info.add(DateUtils.formatElapsedTime(duration / 1000));
        }
        long size = getIntent().getLongExtra(EXTRA_SIZE, 0);
        if (size > 0) {
            info.add(Formatter.formatShortFileSize(this, size));
        }
        long added = getIntent().getLongExtra(EXTRA_ADDED, 0);
        if (added > 0) {
            info.add(DateUtils.formatDateTime(this, added, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
        }
        ((TextView) findViewById(R.id.phone_offline_info)).setText(TextUtils.join(" · ", info));
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (mPlayerView != null) {
            setFullscreen(mKind == Kind.SHORT || mKind == Kind.VIDEO && isLandscape());
        }
    }

    @SuppressWarnings("deprecation")
    private void setFullscreen(boolean fullscreen) {
        mFullscreen = fullscreen;
        findViewById(R.id.phone_offline_details).setVisibility(fullscreen ? View.GONE : View.VISIBLE);

        ViewGroup.LayoutParams layout = mPlayerView.getLayoutParams();
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        layout.height = fullscreen ? ViewGroup.LayoutParams.MATCH_PARENT
                : Math.min(Math.round(metrics.widthPixels * 9f / 16), Math.round(metrics.heightPixels * 0.52f));
        mPlayerView.setLayoutParams(layout);

        if (mControls != null) mControls.fullscreen(fullscreen);
        boolean darkBars = fullscreen || mKind == Kind.MUSIC;
        getWindow().setStatusBarColor(darkBars ? Color.BLACK : getColor(R.color.phone_bg));
        getWindow().setNavigationBarColor(darkBars ? Color.BLACK : getColor(R.color.phone_bg));
        getWindow().getDecorView().setSystemUiVisibility(fullscreen
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                : darkBars || PhoneTheme.isNight(this) ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    void toggleFullscreen() { setFullscreen(!mFullscreen); }

    void showSpeed(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        for (float speed : SPEEDS) {
            String label = speed == 1f ? "1×" : java.text.NumberFormat.getNumberInstance().format(speed) + "×";
            menu.getMenu().add(1, android.view.Menu.NONE, android.view.Menu.NONE, label)
                    .setChecked(speed == mSpeed)
                    .setOnMenuItemClickListener(item -> {
                        mSpeed = speed;
                        if (mPlayer != null) {
                            mPlayer.setPlaybackParameters(new PlaybackParameters(speed));
                        }
                        return true;
                    });
        }
        menu.getMenu().setGroupCheckable(1, true, true);
        menu.show();
    }

    @Override
    public void onBackPressed() {
        if (mMusic != null && mMusic.onBack()) return;
        if (mFullscreen && mKind == Kind.VIDEO) {
            setFullscreen(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isFinishing()) {
            return;
        }
        mPlayer = ExoPlayerFactory.newSimpleInstance(this);
        mPlayer.setAudioAttributes(AudioAttributes.DEFAULT, true);
        if (mMusic == null) mPlayerView.setPlayer(mPlayer);
        mPlayer.addListener(new Player.EventListener() {
            @Override
            public void onPlayerError(com.google.android.exoplayer2.ExoPlaybackException error) {
                if (mMusic != null) {
                    android.widget.Toast.makeText(PhoneLocalPlayerActivity.this, R.string.phone_offline_failed,
                            android.widget.Toast.LENGTH_LONG).show();
                    mPlayer.setPlayWhenReady(false);
                }
            }

            @Override
            public void onPlayerStateChanged(boolean playing, int state) {
                if (state == Player.STATE_READY && mPlayer != null) {
                    showFileInfo(mPlayer.getDuration());
                } else if (state == Player.STATE_ENDED && mPlayer != null) {
                    if (mKind != Kind.MUSIC || !step(1)) {
                        mPlayer.setPlayWhenReady(false);
                        if (mControls != null) mControls.reveal();
                    }
                }
            }
        });
        updateRepeat();
        prepare();
        if (mControls != null) mControls.start();
        if (mMusic != null) mMusic.show(true);
    }

    private void prepare() {
        mPlayer.prepare(new ProgressiveMediaSource.Factory(new DefaultDataSourceFactory(this, "StreamTube"))
                .createMediaSource(getIntent().getData()));
        mPlayer.seekTo(mResumePosition);
        mPlayer.setPlaybackParameters(new PlaybackParameters(mSpeed));
        mPlayer.setPlayWhenReady(mPlaying);
    }

    @Override
    protected void onStop() {
        if (mControls != null) mControls.stop();
        if (mMusic != null) mMusic.show(false);
        super.onStop();
        if (mPlayer != null) {
            mResumePosition = mPlayer.getCurrentPosition();
            mPlaying = mPlayer.getPlayWhenReady();
            mPlayerView.setPlayer(null);
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_CURRENT, getIntent());
        outState.putLong(STATE_POSITION, mPlayer != null ? mPlayer.getCurrentPosition() : mResumePosition);
        outState.putBoolean(STATE_PLAYING, mPlayer != null ? mPlayer.getPlayWhenReady() : mPlaying);
        outState.putFloat(STATE_SPEED, mSpeed);
        outState.putBoolean(STATE_FULLSCREEN, mFullscreen);
    }

    @Override
    protected void onDestroy() {
        if (mMusic != null) mMusic.release();
        super.onDestroy();
    }

    private void setQueue(List<Item> items) {
        if (isFinishing() || isDestroyed()) return;
        mQueue.clear();
        mFiles.clear();
        mIndex = -1;
        for (Item item : items) {
            String id = item.uri.toString();
            Video video = Video.from(id);
            video.title = item.title;
            video.author = PhoneDownloadDetails.read(this, item.uri, item.title).author;
            if (id.equals(mCurrent.videoId)) mIndex = mQueue.size();
            mQueue.add(video);
            mFiles.put(id, item);
        }
        if (mIndex < 0) {
            mQueue.add(0, mCurrent);
            mIndex = 0;
        }
        if (mMusic != null) onModesChanged();
    }

    boolean canStep(int direction) {
        int next = mIndex + direction;
        return next >= 0 && next < mQueue.size();
    }

    boolean step(int direction) {
        if (!canStep(direction)) return false;
        play(mQueue.get(mIndex + direction));
        return true;
    }

    public void play(Video video) {
        Item item = mFiles.get(video.videoId);
        if (item == null || mPlayer == null) return;
        mIndex = mQueue.indexOf(video);
        setIntent(intent(this, item));
        mResumePosition = 0;
        mPlaying = true;
        bindItem();
        prepare();
        if (mMusic != null) mMusic.show(true);
    }

    public void togglePlay() {
        if (mPlayer == null) return;
        if (mPlayer.getPlaybackState() == Player.STATE_ENDED) mPlayer.seekTo(0);
        mPlayer.setPlayWhenReady(!mPlayer.getPlayWhenReady());
        if (mControls != null) mControls.reveal();
    }

    public SimpleExoPlayer exo() { return mPlayer; }
    public Video video() { return mCurrent; }
    public void next() { step(1); }
    public void previous() {
        if (!step(-1) && mPlayer != null) mPlayer.seekTo(0);
    }
    public void minimize() { finish(); }
    public void showVideo() { }

    public List<Video> upNext() {
        return mQueue.isEmpty() ? Collections.emptyList() : new ArrayList<>(mQueue.subList(mIndex + 1, mQueue.size()));
    }

    public List<Video> before() {
        return mQueue.isEmpty() ? Collections.emptyList() : new ArrayList<>(mQueue.subList(0, mIndex));
    }

    public void reorder(List<Video> upcoming) {
        if (mQueue.isEmpty()) return;
        mQueue.subList(mIndex + 1, mQueue.size()).clear();
        mQueue.addAll(upcoming);
    }

    private void updateRepeat() {
        if (mPlayer == null) return;
        boolean repeat = mKind == Kind.SHORT || mKind == Kind.MUSIC
                && PlayerData.instance(this).getPlaybackMode() == PlayerConstants.PLAYBACK_MODE_ONE;
        mPlayer.setRepeatMode(repeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    public void onModesChanged() {
        updateRepeat();
        List<Video> upcoming = upNext();
        if (PlayerData.instance(this).getPlaybackMode() == PlayerConstants.PLAYBACK_MODE_SHUFFLE) {
            Collections.shuffle(upcoming);
        } else {
            Collections.sort(upcoming, (a, b) -> PhoneDownloadsActivity.NEWEST_FIRST.compare(
                    mFiles.get(a.videoId), mFiles.get(b.videoId)));
        }
        reorder(upcoming);
    }
}
