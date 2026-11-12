package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Plays a downloaded video (or audio) file in the app: full screen, the usual controls, any rotation. No network, no
 * YouTube: the file from Download/StreamTube.
 */
public class PhoneLocalPlayerActivity extends Activity {
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_AUDIO = "audio";
    private static final String STATE_POSITION = "position";

    private SimpleExoPlayer mPlayer;
    private PlayerView mPlayerView;
    private long mResumePosition;

    static void open(Activity activity, Uri uri, String title, boolean audio) {
        // One thing playing at a time: the streaming player (e.g. in the mini player) pauses
        com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView streaming =
                com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter.instance(activity).getView();
        if (streaming != null) {
            streaming.setPlayWhenReady(false);
        }
        activity.startActivity(new Intent(activity, PhoneLocalPlayerActivity.class)
                .setData(uri)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_AUDIO, audio));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        mResumePosition = savedInstanceState != null ? savedInstanceState.getLong(STATE_POSITION) : 0;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        mPlayerView = new PlayerView(this);
        mPlayerView.setKeepContentOnPlayerReset(true);
        root.addView(mPlayerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        float density = getResources().getDisplayMetrics().density;
        if (getIntent().getBooleanExtra(EXTRA_AUDIO, false)) {
            // Audio: a headphones picture instead of an empty black screen
            ImageView art = new ImageView(this);
            art.setImageResource(R.drawable.ic_phone_headphones);
            art.setImageTintList(android.content.res.ColorStateList.valueOf(0x99FFFFFF));
            root.addView(art, 0, new FrameLayout.LayoutParams((int) (96 * density), (int) (96 * density), Gravity.CENTER));
            mPlayerView.setControllerShowTimeoutMs(0); // controls stay: there's nothing to watch
            mPlayerView.setControllerHideOnTouch(false);
        }

        // Title and back at the top, shown and hidden with the controls
        android.widget.LinearLayout top = new android.widget.LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundResource(R.drawable.phone_scrim_top);
        int pad = (int) (8 * density);
        top.setPadding(pad, pad, pad * 2, pad);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_phone_back);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        back.setPadding(pad + pad / 2, pad + pad / 2, pad + pad / 2, pad + pad / 2);
        back.setContentDescription(getString(R.string.phone_back));
        back.setOnClickListener(v -> finish());
        top.addView(back, new android.widget.LinearLayout.LayoutParams((int) (48 * density), (int) (48 * density)));
        TextView title = new TextView(this);
        title.setText(getIntent().getStringExtra(EXTRA_TITLE));
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        mPlayerView.setControllerVisibilityListener(visibility -> {
            top.setVisibility(visibility);
            if (visibility != View.VISIBLE) {
                hideSystemBars();
            }
        });

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            top.setPadding(pad + insets.getSystemWindowInsetLeft(), pad + insets.getSystemWindowInsetTop(),
                    pad * 2 + insets.getSystemWindowInsetRight(), pad);
            return insets;
        });
        setContentView(root);
        hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }
        mPlayer = ExoPlayerFactory.newSimpleInstance(this);
        mPlayerView.setPlayer(mPlayer);
        mPlayer.prepare(new ProgressiveMediaSource.Factory(new DefaultDataSourceFactory(this, "StreamTube"))
                .createMediaSource(uri));
        mPlayer.seekTo(mResumePosition);
        mPlayer.setPlayWhenReady(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mPlayer != null) {
            mResumePosition = mPlayer.getCurrentPosition();
            mPlayerView.setPlayer(null);
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_POSITION, mPlayer != null ? mPlayer.getCurrentPosition() : mResumePosition);
    }
}
