package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.phone.PhoneDownloads.Kind;

import java.util.ArrayList;
import java.util.List;

/** Offline playback uses only the saved file and its local download details. */
public class PhoneLocalPlayerActivity extends Activity {
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_KIND = "kind";
    private static final String EXTRA_SIZE = "size";
    private static final String EXTRA_ADDED = "added";
    private static final String EXTRA_DURATION = "duration";
    private static final String STATE_POSITION = "position";
    private static final String STATE_PLAYING = "playing";
    private static final String STATE_SPEED = "speed";
    private static final String STATE_FULLSCREEN = "fullscreen";
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

    private SimpleExoPlayer mPlayer;
    private PlayerView mPlayerView;
    private Kind mKind;
    private long mResumePosition;
    private float mSpeed = 1f;
    private boolean mPlaying = true;
    private boolean mFullscreen;

    static void open(Activity activity, PhoneDownloadsActivity.Item item) {
        PlaybackView streaming = PlaybackPresenter.instance(activity).getView();
        if (streaming != null) {
            streaming.setPlayWhenReady(false);
        }
        activity.startActivity(new Intent(activity, PhoneLocalPlayerActivity.class)
                .setData(item.uri)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_TITLE, item.title)
                .putExtra(EXTRA_KIND, item.kind.name())
                .putExtra(EXTRA_SIZE, item.size)
                .putExtra(EXTRA_ADDED, item.addedMs)
                .putExtra(EXTRA_DURATION, item.durationMs));
    }

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(PhoneTheme.wrap(context));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PhoneTheme.apply(this);
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
        mPlayerView.setRewindIncrementMs(10_000);
        mPlayerView.setFastForwardIncrementMs(10_000);
        mPlayerView.setErrorMessageProvider(error -> android.util.Pair.create(0,
                getString(R.string.phone_offline_failed)));
        findViewById(R.id.phone_offline_back).setOnClickListener(v -> finish());
        mPlayerView.findViewById(R.id.phone_offline_fullscreen).setOnClickListener(v -> setFullscreen(!mFullscreen));
        mPlayerView.findViewById(R.id.phone_offline_speed).setOnClickListener(this::showSpeed);

        if (mKind == Kind.MUSIC) {
            GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF5A1421, Color.BLACK});
            mPlayerView.setBackground(background);
            mPlayerView.setDefaultArtwork(getDrawable(R.drawable.ic_phone_headphones));
            mPlayerView.setControllerShowTimeoutMs(0);
            mPlayerView.setControllerHideOnTouch(false);
            mPlayerView.findViewById(R.id.phone_offline_fullscreen).setVisibility(View.GONE);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        PhoneDownloadDetails details = PhoneDownloadDetails.read(this, uri, getIntent().getStringExtra(EXTRA_TITLE));
        ((TextView) findViewById(R.id.phone_offline_title)).setText(details.title);
        TextView author = findViewById(R.id.phone_offline_author);
        author.setText(details.author);
        author.setVisibility(details.author.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.phone_offline_description)).setText(details.description.isEmpty()
                ? getText(R.string.phone_offline_no_description) : details.description);
        showFileInfo(getIntent().getLongExtra(EXTRA_DURATION, 0));
        setFullscreen(mFullscreen);
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
            setFullscreen(mKind != Kind.MUSIC && isLandscape());
        }
    }

    @SuppressWarnings("deprecation")
    private void setFullscreen(boolean fullscreen) {
        mFullscreen = fullscreen;
        findViewById(R.id.phone_offline_header).setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        findViewById(R.id.phone_offline_details).setVisibility(fullscreen ? View.GONE : View.VISIBLE);

        ViewGroup.LayoutParams layout = mPlayerView.getLayoutParams();
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        float aspect = mKind == Kind.SHORT ? 0.85f : 9f / 16;
        layout.height = fullscreen ? ViewGroup.LayoutParams.MATCH_PARENT
                : Math.min(Math.round(metrics.widthPixels * aspect), Math.round(metrics.heightPixels * 0.52f));
        mPlayerView.setLayoutParams(layout);

        ImageButton button = mPlayerView.findViewById(R.id.phone_offline_fullscreen);
        button.setImageResource(fullscreen ? R.drawable.ic_phone_fullscreen_exit : R.drawable.ic_phone_fullscreen);
        button.setContentDescription(getText(fullscreen ? R.string.phone_offline_exit_fullscreen : R.string.phone_fullscreen));
        getWindow().setStatusBarColor(fullscreen ? Color.BLACK : getColor(R.color.phone_bg));
        getWindow().setNavigationBarColor(fullscreen ? Color.BLACK : getColor(R.color.phone_bg));
        getWindow().getDecorView().setSystemUiVisibility(fullscreen
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                : PhoneTheme.isNight(this) ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        mPlayerView.showController();
    }

    private void showSpeed(View anchor) {
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
        if (mFullscreen) {
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
        mPlayerView.setPlayer(mPlayer);
        mPlayer.addListener(new com.google.android.exoplayer2.Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playing, int state) {
                if (state == com.google.android.exoplayer2.Player.STATE_READY && mPlayer != null) {
                    showFileInfo(mPlayer.getDuration());
                }
            }
        });
        mPlayer.prepare(new ProgressiveMediaSource.Factory(new DefaultDataSourceFactory(this, "StreamTube"))
                .createMediaSource(getIntent().getData()));
        mPlayer.seekTo(mResumePosition);
        mPlayer.setPlaybackParameters(new PlaybackParameters(mSpeed));
        mPlayer.setPlayWhenReady(mPlaying);
    }

    @Override
    protected void onStop() {
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
        outState.putLong(STATE_POSITION, mPlayer != null ? mPlayer.getCurrentPosition() : mResumePosition);
        outState.putBoolean(STATE_PLAYING, mPlayer != null ? mPlayer.getPlayWhenReady() : mPlaying);
        outState.putFloat(STATE_SPEED, mSpeed);
        outState.putBoolean(STATE_FULLSCREEN, mFullscreen);
    }
}
