package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
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
 * Portrait player: video on top, phone controls on the video, details and suggestions below.
 * Rotating the phone (or the full screen button) switches to the regular full screen TV player.
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
    private TextView mTime;
    private SeekBar mSeek;
    private ProgressBar mMiniProgress;
    private View mHeader;
    private boolean mCompact = true;
    private boolean mIsSeeking;
    private Boolean mRouteTouchToPlayer;
    private Video mLastScrollEndVideo;
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
        PhoneUi.applyPhoneDensity(this); // details panel and suggestions use the phone's real density

        Fragment fragment = getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        if (fragment instanceof PhonePlaybackFragment) {
            mPlayer = (PhonePlaybackFragment) fragment;
        }

        mRoot = findViewById(R.id.phone_player_root);
        mVideoBox = findViewById(R.id.phone_video_box);
        mDetails = findViewById(R.id.phone_player_details);
        mControls = findViewById(R.id.phone_controls);
        mPlayButton = findViewById(R.id.phone_ctl_play);
        mTime = findViewById(R.id.phone_ctl_time);
        mSeek = findViewById(R.id.phone_ctl_seek);
        mMiniProgress = findViewById(R.id.phone_mini_progress);

        setupControls();
        setupDetails();

        mRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            mStatusInset = insets.getSystemWindowInsetTop();
            applyLayoutMode();
            return insets;
        });

        mCompact = getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
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
        boolean compact = newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE;
        if (compact != mCompact) {
            mCompact = compact;
            applyLayoutMode();
            applySystemBars();
        }
    }

    @Override
    public void onBackPressed() {
        if (!mCompact && !isInPictureInPictureMode()) {
            // Leave full screen first, like the YouTube app
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        super.onBackPressed();
    }

    // region Layout modes

    private void applyLayoutMode() {
        if (mVideoBox == null) {
            return;
        }

        boolean pip = Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode();
        boolean compact = mCompact && !pip;

        mVideoBox.setRatioEnabled(compact);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mVideoBox.getLayoutParams();
        params.height = compact ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
        params.weight = compact ? 0 : 1;
        mVideoBox.setLayoutParams(params);

        mDetails.setVisibility(compact ? View.VISIBLE : View.GONE);
        mMiniProgress.setVisibility(compact ? View.VISIBLE : View.GONE);
        mRoot.setPadding(0, compact ? mStatusInset : 0, 0, 0);

        if (compact) {
            if (mPlayer != null) {
                mPlayer.hideTvOverlay();
            }
        } else {
            showPhoneControls(false);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            showPhoneControls(false);
        }
        applyLayoutMode();
    }

    @SuppressWarnings("deprecation")
    private void applySystemBars() {
        Window window = getWindow();
        if (mCompact) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(getResources().getColor(R.color.phone_bg));
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

    private void enterFullscreen() {
        showPhoneControls(false);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    // endregion

    // region Touch routing

    /**
     * The TV player listens to every touch on the screen. In portrait only touches on the video should reach it
     * (tap shows the controls, double tap seeks); the details below scroll like a normal list.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!mCompact || mVideoBox == null) {
            return super.dispatchTouchEvent(event);
        }

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN || mRouteTouchToPlayer == null) {
            Rect box = new Rect();
            mVideoBox.getGlobalVisibleRect(box);
            boolean onVideo = box.contains((int) event.getRawX(), (int) event.getRawY());
            mRouteTouchToPlayer = onVideo && mControls.getVisibility() != View.VISIBLE;
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

    // region Phone controls

    private void setupControls() {
        mControls.setOnClickListener(v -> showPhoneControls(false));
        mPlayButton.setOnClickListener(v -> {
            if (mPlayer != null) {
                mPlayer.setPlayWhenReady(!mPlayer.getPlayWhenReady());
                updatePlayButton();
                scheduleAutoHide();
            }
        });
        findViewById(R.id.phone_ctl_next).setOnClickListener(v -> {
            presenter().onNextClicked();
            scheduleAutoHide();
        });
        findViewById(R.id.phone_ctl_prev).setOnClickListener(v -> {
            presenter().onPreviousClicked();
            scheduleAutoHide();
        });
        findViewById(R.id.phone_ctl_fullscreen).setOnClickListener(v -> enterFullscreen());
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

    private void showPhoneControls(boolean show) {
        if (mControls == null) {
            return;
        }
        boolean visible = show && mCompact;
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
        if (mPlayer != null) {
            mPlayButton.setImageResource(mPlayer.getPlayWhenReady() ? R.drawable.ic_phone_pause : R.drawable.ic_phone_play);
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
            updatePlayButton();
        }
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

    // region Details

    private PlaybackPresenter presenter() {
        return PlaybackPresenter.instance(this);
    }

    private void clickButton(int buttonId) {
        int state = mPlayer != null ? mPlayer.getButtonState(buttonId) : PlayerUI.BUTTON_OFF;
        presenter().onButtonClicked(buttonId, state);
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
        addAction(actions, R.id.action_share, R.drawable.ic_phone_share, getString(R.string.phone_share));
        addAction(actions, R.id.action_playlist_add, R.drawable.ic_phone_save, getString(R.string.phone_save));
        addAction(actions, R.id.lb_control_high_quality, R.drawable.ic_phone_settings, getString(R.string.phone_quality));
        addAction(actions, R.id.lb_control_closed_captioning, R.drawable.ic_phone_cc, getString(R.string.phone_captions));
        addAction(actions, R.id.action_video_speed, R.drawable.ic_phone_speed, getString(R.string.phone_speed));

        mAdapter = new FeedAdapter(this);
        mAdapter.setHeaderView(mHeader);
        mDetails.setItemAnimator(null);
        mDetails.setAdapter(mAdapter);
        mDetails.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager != null && manager.findLastVisibleItemPosition() >= mAdapter.getItemCount() - 4) {
                    Video last = mAdapter.getScrollEndVideo();
                    if (last != null && last != mLastScrollEndVideo) {
                        mLastScrollEndVideo = last;
                        presenter().onScrollEnd(last);
                    }
                }
            }
        });
    }

    private void addAction(LinearLayout container, int buttonId, int icon, String label) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackgroundResource(R.drawable.phone_pill_bg);
        pill.setPadding(dp(14), 0, dp(16), 0);
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
            text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.setMarginStart(dp(8));
            pill.addView(text, textParams);
        } else {
            pill.setPadding(dp(14), 0, dp(14), 0);
        }

        pill.setOnClickListener(v -> clickButton(buttonId));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        params.setMarginStart(dp(4));
        params.setMarginEnd(dp(4));
        container.addView(pill, params);
    }

    private void refreshDetails() {
        if (mHeader == null) {
            return;
        }
        Video video = presenter().getVideo();
        if (video == null && mPlayer != null) {
            video = mPlayer.getVideo();
        }
        if (video == null) {
            return;
        }

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

        String authorName = video.getAuthor();
        author.setText(!TextUtils.isEmpty(authorName) ? authorName : "");
        subs.setText(video.subscriberCount);
        subs.setVisibility(TextUtils.isEmpty(video.subscriberCount) ? View.GONE : View.VISIBLE);
        PhoneUi.bindAuthorAvatar(avatar, video);
        avatar.setOnClickListener(v -> clickButton(R.id.action_channel));

        boolean subscribed = mPlayer != null && mPlayer.getButtonState(R.id.action_subscribe) == PlayerUI.BUTTON_ON;
        subscribe.setText(subscribed ? R.string.phone_subscribed : R.string.phone_subscribe);
        subscribe.setBackgroundResource(subscribed ? R.drawable.phone_pill_bg : R.drawable.phone_button_bg);
        subscribe.setTextColor(getResources().getColor(subscribed ? R.color.phone_text : R.color.phone_bg));

        LinearLayout actions = mHeader.findViewById(R.id.phone_player_actions);
        for (int i = 0; i < actions.getChildCount(); i++) {
            View pill = actions.getChildAt(i);
            int buttonId = (Integer) pill.getTag();
            boolean on = mPlayer != null && mPlayer.getButtonState(buttonId) == PlayerUI.BUTTON_ON;
            boolean isRating = buttonId == R.id.action_thumbs_up || buttonId == R.id.action_thumbs_down;
            pill.setAlpha(1f);
            ImageView icon = (ImageView) ((LinearLayout) pill).getChildAt(0);
            icon.setImageTintList(ColorStateList.valueOf(getResources().getColor(isRating && on ? R.color.phone_accent : R.color.phone_text)));
            if (buttonId == R.id.action_thumbs_up && ((LinearLayout) pill).getChildCount() > 1) {
                ((TextView) ((LinearLayout) pill).getChildAt(1)).setText(
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
        return mCompact && !(Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode());
    }

    @Override
    public void onPlayerControlsVisible(boolean visible) {
        showPhoneControls(visible);
    }

    @Override
    public void onPlayerVideo(Video video) {
        mDescriptionExpanded = false;
        refreshDetails();
        if (mDetails != null) {
            mDetails.scrollToPosition(0);
        }
    }

    @Override
    public void onPlayerSuggestions(VideoGroup group) {
        if (mAdapter == null || group == null || group.isChapters()) {
            return;
        }
        mAdapter.update(group);
        refreshDetails();
    }

    @Override
    public void onPlayerSuggestionsCleared() {
        if (mAdapter != null) {
            mAdapter.clear();
        }
        mLastScrollEndVideo = null;
    }

    @Override
    public void onPlayerButtonState(int buttonId, int buttonState) {
        refreshDetails();
    }

    // endregion

    // region FeedAdapter.Listener

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
        if (lastVideo != mLastScrollEndVideo) {
            mLastScrollEndVideo = lastVideo;
            presenter().onScrollEnd(lastVideo);
        }
    }

    // endregion
}
