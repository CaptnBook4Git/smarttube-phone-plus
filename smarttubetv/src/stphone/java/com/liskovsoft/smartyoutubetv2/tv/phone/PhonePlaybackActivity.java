package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.phone.widget.RatioFrameLayout;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

/**
 * Phone player built around SmartTube's playback engine:
 * <ul>
 *     <li>Portrait: video on top with phone controls, video details below</li>
 *     <li>Landscape: full screen video with the same minimal controls (no TV overlay)</li>
 *     <li>Shorts: full screen vertical video, swipe up/down for next/previous, tap to pause</li>
 * </ul>
 */
public class PhonePlaybackActivity extends PlaybackActivity implements PhonePlaybackFragment.Host, FeedAdapter.Listener {
    private static final long HIDE_CONTROLS_MS = 3_500;
    private static final long TICK_MS = 500;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private PhonePlaybackFragment mPlayer;
    private View mRoot;
    private RatioFrameLayout mVideoBox;
    private RecyclerView mDetails;
    private FeedAdapter mAdapter;
    private View mControls;
    private ImageView mPlayButton;
    private ImageView mFullscreenButton;
    private TextView mControlsTitle;
    private TextView mTime;
    private SeekBar mSeek;
    private ProgressBar mMiniProgress;
    private View mHeader;
    private View mShorts;
    private View mShortsPaused;
    private boolean mPortrait = true;
    private boolean mShortsMode;
    private boolean mIsSeeking;
    private Boolean mRouteTouchToPlayer;
    private static final int LEVEL_NONE = 0;
    private static final int LEVEL_BRIGHTNESS = 1;
    private static final int LEVEL_VOLUME = 2;
    private int mLevelMode = LEVEL_NONE;
    private float mLevelStartX;
    private float mLevelStartY;
    private float mLevelStartValue;
    private View mLevelView;
    private ImageView mLevelIcon;
    private ProgressBar mLevelBar;
    private TextView mLevelText;
    private final Runnable mHideLevel = () -> {
        if (mLevelView != null) {
            mLevelView.animate().alpha(0f).setDuration(200).withEndAction(() -> mLevelView.setVisibility(View.GONE)).start();
        }
    };
    private boolean mTouchStartedOnVideo;
    private GestureDetector mCloseDetector;
    private int mStatusInset;
    private boolean mDescriptionExpanded;

    private final Runnable mTicker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            mHandler.postDelayed(this, TICK_MS);
        }
    };

    private final Runnable mAutoHide = () -> {
        if (mPlayer != null && mPlayer.getPlayWhenReady() && !mIsSeeking) {
            showPhoneControls(false);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // inflates the phone fragment_playback layout (TV player keeps TV density)
        PhoneUi.applyPhoneDensity(this); // phone views use the phone's real density

        Fragment fragment = getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        if (fragment instanceof PhonePlaybackFragment) {
            mPlayer = (PhonePlaybackFragment) fragment;
        }

        // Open/close like a sheet sliding up from the app, not a new page
        getWindow().setWindowAnimations(R.style.PhonePlayerAnimation);

        mRoot = findViewById(R.id.phone_player_root);
        mVideoBox = findViewById(R.id.phone_video_box);
        mDetails = findViewById(R.id.phone_player_details);

        // Inflated here (not in the layout) so they get the phone density, not the TV one
        LayoutInflater inflater = LayoutInflater.from(this);
        inflater.inflate(R.layout.phone_mini_progress, mVideoBox, true);
        inflater.inflate(R.layout.phone_player_controls, mVideoBox, true);
        inflater.inflate(R.layout.phone_shorts_overlay, mVideoBox, true);
        createLevelIndicator();

        mControls = findViewById(R.id.phone_controls);
        mPlayButton = findViewById(R.id.phone_ctl_play);
        mFullscreenButton = findViewById(R.id.phone_ctl_fullscreen);
        mControlsTitle = findViewById(R.id.phone_ctl_title);
        mTime = findViewById(R.id.phone_ctl_time);
        mSeek = findViewById(R.id.phone_ctl_seek);
        mMiniProgress = findViewById(R.id.phone_mini_progress);
        mShorts = findViewById(R.id.phone_shorts);
        mShortsPaused = findViewById(R.id.phone_shorts_paused);

        setupControls();
        setupDetails();
        setupShorts();

        mCloseDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null && velocityY > 1200 && Math.abs(velocityY) > Math.abs(velocityX) * 1.5f
                        && e2.getY() - e1.getY() > dp(80)) {
                    enterMiniPlayer();
                    return true;
                }
                return false;
            }
        });

        mRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            mStatusInset = insets.getSystemWindowInsetTop();
            applyLayoutMode();
            return insets;
        });

        mPortrait = getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
        Video video = presenter().getVideo();
        mShortsMode = isShort(video);
        applyLayoutMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PhoneUi.applyPhoneDensity(this);
        applySystemBars();
        mHandler.removeCallbacks(mTicker);
        mHandler.post(mTicker);
        refreshDetails();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mTicker);
        mHandler.removeCallbacks(mAutoHide);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applySystemBars();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        PhoneUi.applyPhoneDensity(this);
        boolean portrait = newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE;
        if (portrait != mPortrait) {
            mPortrait = portrait;
            applyLayoutMode();
            applySystemBars();
        }
    }

    @Override
    public void onBackPressed() {
        if (!mPortrait && !isPip()) {
            // Leave full screen first, like the YouTube app
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            showPhoneControls(false);
        }
        applyLayoutMode();
    }

    private boolean isPip() {
        return Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode();
    }

    private static boolean isShort(Video video) {
        return video != null && (video.isShorts || video.belongsToShorts());
    }

    // region Layout modes

    private void applyLayoutMode() {
        if (mVideoBox == null) {
            return;
        }

        boolean pip = isPip();
        boolean shorts = mShortsMode && !pip;
        boolean withDetails = mPortrait && !shorts && !pip;

        mVideoBox.setRatioEnabled(withDetails);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mVideoBox.getLayoutParams();
        params.height = withDetails ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
        params.weight = withDetails ? 0 : 1;
        mVideoBox.setLayoutParams(params);

        mDetails.setVisibility(withDetails ? View.VISIBLE : View.GONE);
        mMiniProgress.setVisibility(mPortrait && !pip ? View.VISIBLE : View.GONE);
        mRoot.setPadding(0, withDetails ? mStatusInset : 0, 0, 0);

        mShorts.setVisibility(shorts ? View.VISIBLE : View.GONE);
        View back = findViewById(R.id.phone_shorts_back);
        FrameLayout.LayoutParams backParams = (FrameLayout.LayoutParams) back.getLayoutParams();
        backParams.topMargin = dp(6) + mStatusInset;
        back.setLayoutParams(backParams);

        mControlsTitle.setVisibility(mPortrait ? View.GONE : View.VISIBLE);
        // Full screen: keep the seek bar and time clear of the screen edges
        View bottom = findViewById(R.id.phone_ctl_bottom);
        int side = mPortrait ? 0 : dp(28);
        bottom.setPadding(side, 0, side, mPortrait ? 0 : dp(48));
        mFullscreenButton.setImageResource(mPortrait ? R.drawable.ic_phone_fullscreen : R.drawable.ic_phone_fullscreen_exit);

        if (shorts || pip) {
            showPhoneControls(false);
        }

        if (mPlayer != null && !pip) {
            mPlayer.hideTvOverlay();
        }
    }

    @SuppressWarnings("deprecation")
    private void applySystemBars() {
        Window window = getWindow();
        boolean fullscreen = !mPortrait;

        if (!fullscreen) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(mShortsMode ? Color.TRANSPARENT : Color.BLACK);
            window.setNavigationBarColor(mShortsMode ? Color.BLACK : getResources().getColor(R.color.phone_bg));
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.systemBars());
                    controller.setSystemBarsAppearance(0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                }
            } else {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
        } else {
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        }
        mRoot.requestApplyInsets();
    }

    private void toggleFullscreen() {
        showPhoneControls(false);
        setRequestedOrientation(mPortrait
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    // endregion

    // region Touch routing

    /**
     * The TV player listens to every touch on the screen. Only touches on the bare video reach it (tap shows the
     * controls, double tap seeks); the details list, the phone controls and the Shorts overlay handle their own.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mVideoBox == null || isPip()) {
            return super.dispatchTouchEvent(event);
        }

        // Full screen: swipe up/down on the left for brightness, on the right for volume
        if (!mPortrait && !mShortsMode && handleLevelGesture(event)) {
            return true;
        }

        return routeTouch(event);
    }

    private boolean routeTouch(MotionEvent event) {

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN || mRouteTouchToPlayer == null) {
            Rect box = new Rect();
            mVideoBox.getGlobalVisibleRect(box);
            boolean onVideo = box.contains((int) event.getRawX(), (int) event.getRawY());
            mTouchStartedOnVideo = onVideo;
            mRouteTouchToPlayer = onVideo && !mShortsMode && mControls.getVisibility() != View.VISIBLE;
        }

        // Swipe the video down to close the player (portrait, regular videos)
        if (mTouchStartedOnVideo && mPortrait && !mShortsMode && mCloseDetector != null) {
            mCloseDetector.onTouchEvent(event);
        }

        boolean result;
        if (mRouteTouchToPlayer) {
            result = super.dispatchTouchEvent(event);
        } else {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                onUserInteraction();
            }
            result = getWindow().superDispatchTouchEvent(event) || onTouchEvent(event);
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mRouteTouchToPlayer = null;
        }

        return result;
    }

    // endregion

    // region Picture-in-picture

    /**
     * Drag the player down: keep watching in a floating window and go back to the app.
     */
    private void enterMiniPlayer() {
        if (Build.VERSION.SDK_INT < 26) {
            finish();
            return;
        }
        try {
            android.app.PictureInPictureParams params = new android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(mShortsMode ? new android.util.Rational(9, 16) : new android.util.Rational(16, 9))
                    .build();
            showPhoneControls(false);
            if (enterPictureInPictureMode(params)) {
                getViewManager().startParentView(this);
            } else {
                finish();
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            finish();
        }
    }

    // endregion

    // region Brightness and volume gestures

    private void createLevelIndicator() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackgroundResource(R.drawable.phone_level_bg);
        box.setPadding(dp(22), dp(16), dp(22), dp(16));
        box.setVisibility(View.GONE);

        mLevelIcon = new ImageView(this);
        mLevelIcon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        box.addView(mLevelIcon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        mLevelBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mLevelBar.setMax(100);
        mLevelBar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        mLevelBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x55FFFFFF));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(120), dp(4));
        barParams.topMargin = dp(12);
        box.addView(mLevelBar, barParams);

        mLevelText = new TextView(this);
        mLevelText.setTextColor(Color.WHITE);
        mLevelText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mLevelText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(8);
        box.addView(mLevelText, textParams);

        mVideoBox.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        mLevelView = box;
    }

    private boolean handleLevelGesture(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLevelMode = LEVEL_NONE;
                mLevelStartX = event.getRawX();
                mLevelStartY = event.getRawY();
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (mLevelMode == LEVEL_NONE) {
                    float dx = event.getRawX() - mLevelStartX;
                    float dy = event.getRawY() - mLevelStartY;
                    int slop = android.view.ViewConfiguration.get(this).getScaledTouchSlop() * 2;
                    if (Math.abs(dy) < slop || Math.abs(dy) < Math.abs(dx) * 1.5f) {
                        return false;
                    }
                    Rect box = new Rect();
                    mVideoBox.getGlobalVisibleRect(box);
                    mLevelMode = mLevelStartX < box.centerX() ? LEVEL_BRIGHTNESS : LEVEL_VOLUME;
                    mLevelStartValue = mLevelMode == LEVEL_BRIGHTNESS ? currentBrightness() : currentVolume();
                    mLevelStartY = event.getRawY();

                    // Whatever got the touch so far (player, controls) must not see a tap
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    routeTouch(cancel);
                    cancel.recycle();
                }
                float height = Math.max(1, mVideoBox.getHeight() * 0.7f);
                float value = Math.max(0f, Math.min(1f, mLevelStartValue + (mLevelStartY - event.getRawY()) / height));
                if (mLevelMode == LEVEL_BRIGHTNESS) {
                    setBrightness(value);
                } else {
                    setVolume(value);
                }
                showLevel(value);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mLevelMode != LEVEL_NONE) {
                    mLevelMode = LEVEL_NONE;
                    mHandler.removeCallbacks(mHideLevel);
                    mHandler.postDelayed(mHideLevel, 700);
                    return true;
                }
                return false;
            default:
                return mLevelMode != LEVEL_NONE;
        }
    }

    private void showLevel(float value) {
        if (mLevelView == null) {
            return;
        }
        mHandler.removeCallbacks(mHideLevel);
        mLevelView.animate().cancel();
        mLevelView.setAlpha(1f);
        mLevelView.setVisibility(View.VISIBLE);
        mLevelIcon.setImageResource(mLevelMode == LEVEL_BRIGHTNESS ? R.drawable.ic_phone_brightness
                : value <= 0f ? R.drawable.ic_phone_volume_off : R.drawable.ic_phone_volume);
        int percent = Math.round(value * 100);
        mLevelBar.setProgress(percent);
        mLevelText.setText(percent + "%");
    }

    private float currentBrightness() {
        float brightness = getWindow().getAttributes().screenBrightness;
        if (brightness >= 0) {
            return brightness;
        }
        try {
            return android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    private void setBrightness(float value) {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.screenBrightness = Math.max(0.01f, value);
        getWindow().setAttributes(attrs);
    }

    private float currentVolume() {
        android.media.AudioManager audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) {
            return 0.5f;
        }
        int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        return max > 0 ? audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / (float) max : 0.5f;
    }

    private void setVolume(float value) {
        android.media.AudioManager audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) {
            return;
        }
        int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        try {
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Math.round(value * max), 0);
        } catch (SecurityException ignored) {
            // Do Not Disturb can refuse volume changes
        }
    }

    // endregion

    // region Phone controls

    private void setupControls() {
        mControls.setOnClickListener(v -> showPhoneControls(false));
        mPlayButton.setOnClickListener(v -> {
            togglePlay();
            scheduleAutoHide();
        });
        findViewById(R.id.phone_ctl_next).setOnClickListener(v -> {
            presenter().onNextClicked();
            scheduleAutoHide();
        });
        findViewById(R.id.phone_ctl_prev).setOnClickListener(v -> {
            presenter().onPreviousClicked();
            scheduleAutoHide();
        });
        mFullscreenButton.setOnClickListener(v -> toggleFullscreen());
        findViewById(R.id.phone_ctl_collapse).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.phone_ctl_settings).setOnClickListener(v -> clickButton(R.id.lb_control_high_quality));
        findViewById(R.id.phone_ctl_cc).setOnClickListener(v -> clickButton(R.id.lb_control_closed_captioning));

        mSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mPlayer != null) {
                    long duration = mPlayer.getDurationMs();
                    updateTimeText(duration * progress / 1000, duration);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsSeeking = true;
                mHandler.removeCallbacks(mAutoHide);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mIsSeeking = false;
                if (mPlayer != null) {
                    long duration = mPlayer.getDurationMs();
                    if (duration > 0) {
                        mPlayer.setPositionMs(duration * seekBar.getProgress() / 1000);
                    }
                }
                scheduleAutoHide();
            }
        });
    }

    private void togglePlay() {
        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(!mPlayer.getPlayWhenReady());
            updatePlayButton();
        }
    }

    private void showPhoneControls(boolean show) {
        if (mControls == null) {
            return;
        }
        boolean visible = show && !mShortsMode && !isPip();
        mControls.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            updatePlayButton();
            updateProgress();
            scheduleAutoHide();
        } else {
            mHandler.removeCallbacks(mAutoHide);
        }
    }

    private void scheduleAutoHide() {
        mHandler.removeCallbacks(mAutoHide);
        mHandler.postDelayed(mAutoHide, HIDE_CONTROLS_MS);
    }

    private void updatePlayButton() {
        if (mPlayer == null) {
            return;
        }
        boolean playing = mPlayer.getPlayWhenReady();
        mPlayButton.setImageResource(playing ? R.drawable.ic_phone_pause : R.drawable.ic_phone_play);
        if (mShortsPaused != null) {
            mShortsPaused.setVisibility(mShortsMode && !playing ? View.VISIBLE : View.GONE);
        }
    }

    private void updateProgress() {
        if (mPlayer == null) {
            return;
        }
        long duration = mPlayer.getDurationMs();
        long position = mPlayer.getPositionMs();
        int progress = duration > 0 ? (int) Math.max(0, Math.min(1000, position * 1000 / duration)) : 0;

        mMiniProgress.setProgress(progress);

        if (mControls.getVisibility() == View.VISIBLE && !mIsSeeking) {
            mSeek.setProgress(progress);
            updateTimeText(position, duration);
        }
        updatePlayButton();
    }

    private void updateTimeText(long position, long duration) {
        mTime.setText(duration > 0 ? formatTime(position) + " / " + formatTime(duration) : formatTime(position));
    }

    private static String formatTime(long ms) {
        long total = Math.max(0, ms / 1000);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, seconds) : String.format("%d:%02d", minutes, seconds);
    }

    // endregion

    // region Shorts

    private void setupShorts() {
        findViewById(R.id.phone_shorts_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.phone_shorts_channel).setOnClickListener(v -> clickButton(R.id.action_channel));
        findViewById(R.id.phone_shorts_subscribe).setOnClickListener(v -> clickButton(R.id.action_subscribe));

        LinearLayout actions = findViewById(R.id.phone_shorts_actions);
        addShortsAction(actions, R.id.action_thumbs_up, R.drawable.ic_phone_like, getString(R.string.phone_like));
        addShortsAction(actions, R.id.action_thumbs_down, R.drawable.ic_phone_dislike, getString(R.string.phone_dislike));
        addShortsAction(actions, R.id.action_chat, R.drawable.ic_phone_comments, getString(R.string.phone_comments));
        addShortsAction(actions, R.id.action_share, R.drawable.ic_phone_share, getString(R.string.phone_share));
        addShortsAction(actions, R.id.action_playlist_add, R.drawable.ic_phone_save, getString(R.string.phone_save));
        addShortsAction(actions, R.id.lb_control_high_quality, R.drawable.ic_phone_more, null);

        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                togglePlay();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || Math.abs(velocityY) < Math.abs(velocityX) || Math.abs(velocityY) < 600) {
                    return false;
                }
                float distance = e2.getY() - e1.getY();
                if (Math.abs(distance) < dp(60)) {
                    return false;
                }
                swipeTo(distance < 0);
                return true;
            }
        });
        mShorts.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    private void swipeTo(boolean next) {
        float height = mShorts.getHeight();
        mShorts.animate()
                .translationY(next ? -height * 0.15f : height * 0.15f)
                .alpha(0f)
                .setDuration(140)
                .withEndAction(() -> {
                    if (next) {
                        presenter().onNextClicked();
                    } else {
                        presenter().onPreviousClicked();
                    }
                    mShorts.setTranslationY(next ? height * 0.15f : -height * 0.15f);
                    mShorts.animate().translationY(0).alpha(1f).setDuration(180).start();
                })
                .start();
    }

    private void addShortsAction(LinearLayout container, int buttonId, int icon, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setTag(buttonId);
        item.setPadding(dp(6), dp(8), dp(6), dp(8));

        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setBackgroundResource(R.drawable.phone_play_bg);
        image.setPadding(dp(11), dp(11), dp(11), dp(11));
        image.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        item.addView(image, new LinearLayout.LayoutParams(dp(50), dp(50)));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        text.setShadowLayer(4, 0, 1, 0x99000000);
        text.setGravity(Gravity.CENTER);
        text.setVisibility(label != null ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(4);
        item.addView(text, textParams);

        item.setOnClickListener(v -> clickButton(buttonId));
        container.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void refreshShorts(Video video) {
        if (mShorts == null || video == null) {
            return;
        }
        ((TextView) findViewById(R.id.phone_shorts_title)).setText(video.getTitle());
        ((TextView) findViewById(R.id.phone_shorts_author)).setText(video.getAuthor());
        PhoneUi.bindAuthorAvatar(findViewById(R.id.phone_shorts_avatar), video);

        boolean subscribed = getButtonState(R.id.action_subscribe) == PlayerUI.BUTTON_ON;
        TextView subscribe = findViewById(R.id.phone_shorts_subscribe);
        subscribe.setText(subscribed ? R.string.phone_subscribed : R.string.phone_subscribe);
        subscribe.setBackgroundResource(subscribed ? R.drawable.phone_pill_bg : R.drawable.phone_button_bg);
        subscribe.setTextColor(subscribed ? Color.WHITE : getResources().getColor(R.color.phone_bg));

        LinearLayout actions = findViewById(R.id.phone_shorts_actions);
        for (int i = 0; i < actions.getChildCount(); i++) {
            LinearLayout item = (LinearLayout) actions.getChildAt(i);
            int buttonId = (Integer) item.getTag();
            boolean on = getButtonState(buttonId) == PlayerUI.BUTTON_ON;
            boolean isRating = buttonId == R.id.action_thumbs_up || buttonId == R.id.action_thumbs_down;
            ((ImageView) item.getChildAt(0)).setImageTintList(ColorStateList.valueOf(
                    isRating && on ? getResources().getColor(R.color.phone_accent) : Color.WHITE));
            if (buttonId == R.id.action_thumbs_up) {
                ((TextView) item.getChildAt(1)).setText(
                        !TextUtils.isEmpty(video.likeCount) ? video.likeCount : getString(R.string.phone_like));
            }
        }
    }

    // endregion

    // region Details

    private PlaybackPresenter presenter() {
        return PlaybackPresenter.instance(this);
    }

    private int getButtonState(int buttonId) {
        return mPlayer != null ? mPlayer.getButtonState(buttonId) : PlayerUI.BUTTON_OFF;
    }

    private void clickButton(int buttonId) {
        presenter().onButtonClicked(buttonId, getButtonState(buttonId));
    }

    private void setupDetails() {
        // The layout manager must exist before anything is inflated against the RecyclerView
        mDetails.setLayoutManager(new LinearLayoutManager(this));
        mHeader = LayoutInflater.from(this).inflate(R.layout.phone_player_header, mDetails, false);
        mHeader.findViewById(R.id.phone_player_title).setOnClickListener(v -> {
            mDescriptionExpanded = !mDescriptionExpanded;
            refreshDetails();
        });
        mHeader.findViewById(R.id.phone_player_channel).setOnClickListener(v -> clickButton(R.id.action_channel));
        mHeader.findViewById(R.id.phone_player_subscribe).setOnClickListener(v -> clickButton(R.id.action_subscribe));

        LinearLayout actions = mHeader.findViewById(R.id.phone_player_actions);
        addAction(actions, R.id.action_thumbs_up, R.drawable.ic_phone_like, getString(R.string.phone_like));
        addAction(actions, R.id.action_thumbs_down, R.drawable.ic_phone_dislike, null);
        addAction(actions, R.id.action_chat, R.drawable.ic_phone_comments, getString(R.string.phone_comments));
        addAction(actions, R.id.action_share, R.drawable.ic_phone_share, getString(R.string.phone_share));
        addAction(actions, R.id.action_playlist_add, R.drawable.ic_phone_save, getString(R.string.phone_save));
        addAction(actions, R.id.lb_control_high_quality, R.drawable.ic_phone_settings, getString(R.string.phone_quality));
        addAction(actions, R.id.action_video_speed, R.drawable.ic_phone_speed, getString(R.string.phone_speed));

        // Details only: no suggested videos under the player
        mAdapter = new FeedAdapter(this);
        mAdapter.setHeaderView(mHeader);
        mDetails.setItemAnimator(null);
        mDetails.setAdapter(mAdapter);
    }

    private void addAction(LinearLayout container, int buttonId, int icon, String label) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackgroundResource(R.drawable.phone_pill_bg);
        pill.setPadding(dp(14), 0, label != null ? dp(16) : dp(14), 0);
        pill.setTag(buttonId);

        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.phone_text)));
        pill.addView(image, new LinearLayout.LayoutParams(dp(20), dp(20)));

        if (label != null) {
            TextView text = new TextView(this);
            text.setText(label);
            text.setTextColor(getResources().getColor(R.color.phone_text));
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.setMarginStart(dp(8));
            pill.addView(text, textParams);
        }

        pill.setOnClickListener(v -> clickButton(buttonId));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        params.setMarginStart(dp(4));
        params.setMarginEnd(dp(4));
        container.addView(pill, params);
    }

    private Video currentVideo() {
        Video video = presenter().getVideo();
        if (video == null && mPlayer != null) {
            video = mPlayer.getVideo();
        }
        return video;
    }

    private void refreshDetails() {
        Video video = currentVideo();
        if (video == null || mHeader == null) {
            return;
        }

        mControlsTitle.setText(video.getTitle());
        refreshShorts(video);

        TextView title = mHeader.findViewById(R.id.phone_player_title);
        TextView meta = mHeader.findViewById(R.id.phone_player_meta);
        TextView description = mHeader.findViewById(R.id.phone_player_description);
        TextView author = mHeader.findViewById(R.id.phone_player_author);
        TextView subs = mHeader.findViewById(R.id.phone_player_subs);
        TextView subscribe = mHeader.findViewById(R.id.phone_player_subscribe);
        ImageView avatar = mHeader.findViewById(R.id.phone_avatar);

        title.setText(video.getTitle());
        title.setMaxLines(mDescriptionExpanded ? 10 : 2);

        CharSequence metaText = PhoneUi.meta(video);
        meta.setText(metaText);
        meta.setVisibility(TextUtils.isEmpty(metaText) ? View.GONE : View.VISIBLE);

        boolean showDescription = mDescriptionExpanded && !TextUtils.isEmpty(video.description);
        description.setText(showDescription ? video.description : null);
        description.setVisibility(showDescription ? View.VISIBLE : View.GONE);

        author.setText(video.getAuthor());
        subs.setText(video.subscriberCount);
        subs.setVisibility(TextUtils.isEmpty(video.subscriberCount) ? View.GONE : View.VISIBLE);
        PhoneUi.bindAuthorAvatar(avatar, video);
        avatar.setOnClickListener(v -> clickButton(R.id.action_channel));

        boolean subscribed = getButtonState(R.id.action_subscribe) == PlayerUI.BUTTON_ON;
        subscribe.setText(subscribed ? R.string.phone_subscribed : R.string.phone_subscribe);
        subscribe.setBackgroundResource(subscribed ? R.drawable.phone_pill_bg : R.drawable.phone_button_bg);
        subscribe.setTextColor(getResources().getColor(subscribed ? R.color.phone_text : R.color.phone_bg));

        LinearLayout actions = mHeader.findViewById(R.id.phone_player_actions);
        for (int i = 0; i < actions.getChildCount(); i++) {
            LinearLayout pill = (LinearLayout) actions.getChildAt(i);
            int buttonId = (Integer) pill.getTag();
            boolean on = getButtonState(buttonId) == PlayerUI.BUTTON_ON;
            boolean isRating = buttonId == R.id.action_thumbs_up || buttonId == R.id.action_thumbs_down;
            ((ImageView) pill.getChildAt(0)).setImageTintList(ColorStateList.valueOf(
                    getResources().getColor(isRating && on ? R.color.phone_accent : R.color.phone_text)));
            if (buttonId == R.id.action_thumbs_up && pill.getChildCount() > 1) {
                ((TextView) pill.getChildAt(1)).setText(
                        !TextUtils.isEmpty(video.likeCount) ? video.likeCount : getString(R.string.phone_like));
            }
        }
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    // endregion

    // region PhonePlaybackFragment.Host

    @Override
    public boolean isCompactPlayer() {
        // Phone controls everywhere; the TV overlay is never shown (except picture-in-picture)
        return !isPip();
    }

    @Override
    public void onPlayerControlsVisible(boolean visible) {
        showPhoneControls(visible);
    }

    @Override
    public void onPlayerVideo(Video video) {
        boolean shorts = isShort(video);
        if (shorts != mShortsMode) {
            mShortsMode = shorts;
            applyLayoutMode();
            applySystemBars();
        }
        mDescriptionExpanded = false;
        refreshDetails();
        if (mDetails != null) {
            mDetails.scrollToPosition(0);
        }
    }

    @Override
    public void onPlayerSuggestions(VideoGroup group) {
        // Suggested videos are not shown on the phone player
    }

    @Override
    public void onPlayerSuggestionsCleared() {
    }

    @Override
    public void onPlayerButtonState(int buttonId, int buttonState) {
        refreshDetails();
    }

    @Override
    public void onPlayerChannelIcon(String iconUrl) {
        Video video = currentVideo();
        if (video != null && !TextUtils.isEmpty(iconUrl)) {
            AvatarCache.rememberUrl(this, video.channelId, video.getAuthor(), AvatarFetcher.handleOf(video), iconUrl);
            refreshDetails();
        }
    }

    // endregion

    // region FeedAdapter.Listener (header only, no items)

    @Override
    public void onVideoClicked(Video video) {
        presenter().onSuggestionItemClicked(video);
    }

    @Override
    public void onVideoLongClicked(Video video) {
        presenter().onSuggestionItemLongClicked(video);
    }

    @Override
    public void onStripClicked(Video channel) {
        onVideoClicked(channel);
    }

    @Override
    public void onShelfScrollEnd(Video lastVideo) {
    }

    // endregion
}
