package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.Locale;

/**
 * Settings > Check for updates: a dedicated screen that checks GitHub, shows what's new, downloads with a progress
 * bar and hands the APK to Android's installer.
 */
public class PhoneUpdateActivity extends PhoneActivity implements PhoneUpdater.InstallListener {
    private static final int STATE_CHECKING = 0;
    private static final int STATE_UP_TO_DATE = 1;
    private static final int STATE_AVAILABLE = 2;
    private static final int STATE_DOWNLOADING = 3;
    private static final int STATE_INSTALLING = 4;
    private static final int STATE_ERROR = 5;
    private static final int STATE_PERMISSION = 6;
    private static final int GREEN = 0xFF2BA640;

    private int mState = STATE_CHECKING;
    private PhoneUpdater.Release mRelease;
    private PhoneUpdater.Download mDownload;
    private String mError;
    private long mDone;
    private long mTotal;

    private ProgressBar mSpinner;
    private ImageView mIcon;
    private View mBadge;
    private TextView mTitle;
    private TextView mSubtitle;
    private View mProgressBox;
    private ProgressBar mProgress;
    private TextView mProgressText;
    private TextView mProgressPercent;
    private View mNotesBox;
    private LinearLayout mNotes;
    private TextView mPrimary;
    private TextView mSecondary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_activity_update);
        applyInsets(findViewById(R.id.phone_root), findViewById(R.id.phone_top_bar), findViewById(R.id.phone_update_actions));

        mSpinner = findViewById(R.id.phone_update_spinner);
        mIcon = findViewById(R.id.phone_update_icon);
        mBadge = findViewById(R.id.phone_update_badge);
        mTitle = findViewById(R.id.phone_update_title);
        mSubtitle = findViewById(R.id.phone_update_subtitle);
        mProgressBox = findViewById(R.id.phone_update_progress_box);
        mProgress = findViewById(R.id.phone_update_progress);
        mProgressText = findViewById(R.id.phone_update_progress_text);
        mProgressPercent = findViewById(R.id.phone_update_progress_percent);
        mNotesBox = findViewById(R.id.phone_update_notes_box);
        mNotes = findViewById(R.id.phone_update_notes);
        mPrimary = findViewById(R.id.phone_update_primary);
        mSecondary = findViewById(R.id.phone_update_secondary);

        findViewById(R.id.phone_back).setOnClickListener(v -> finish());
        mPrimary.setOnClickListener(v -> onPrimary());
        mSecondary.setOnClickListener(v -> onSecondary());

        String version = PhoneUpdater.currentVersionName(this);
        int build = PhoneUpdater.currentBuild(this);
        ((TextView) findViewById(R.id.phone_update_current)).setText(build > 0
                ? getString(R.string.phone_update_current, build, version) : version);

        Switch notify = findViewById(R.id.phone_update_notify);
        notify.setChecked(PhoneUpdater.isNotifyEnabled(this));
        notify.setOnCheckedChangeListener((button, checked) -> PhoneUpdater.setNotifyEnabled(this, checked));
        findViewById(R.id.phone_update_notify_row).setOnClickListener(v -> notify.toggle());

        PhoneUpdater.setInstallListener(this);
        check();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from Android's "install unknown apps" setting: carry on with the update
        if (mState == STATE_PERMISSION && canInstall()) {
            startDownload();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PhoneUpdater.setInstallListener(null);
        if (mDownload != null && mState == STATE_DOWNLOADING) {
            mDownload.cancel();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    // region Actions

    private void check() {
        mState = STATE_CHECKING;
        render();
        PhoneUpdater.checkAsync(this, release -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            mRelease = release;
            if (release == null) {
                mState = STATE_ERROR;
                mError = getString(R.string.phone_update_error_network);
            } else {
                mState = PhoneUpdater.isNewer(this, release) ? STATE_AVAILABLE : STATE_UP_TO_DATE;
            }
            render();
        });
    }

    private boolean canInstall() {
        return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
    }

    private void startDownload() {
        if (mRelease == null) {
            check();
            return;
        }
        if (!canInstall()) {
            mState = STATE_PERMISSION;
            render();
            return;
        }
        mState = STATE_DOWNLOADING;
        mDone = 0;
        mTotal = mRelease.size;
        render();
        mDownload = PhoneUpdater.download(this, mRelease, new PhoneUpdater.DownloadListener() {
            @Override
            public void onProgress(long done, long total) {
                mDone = done;
                mTotal = total;
                if (mState == STATE_DOWNLOADING) {
                    renderProgress();
                }
            }

            @Override
            public void onHandedToInstaller() {
                mState = STATE_INSTALLING;
                render();
            }

            @Override
            public void onFailed(String error) {
                if (TextUtils.isEmpty(error)) {
                    mState = STATE_AVAILABLE; // cancelled by the user
                } else {
                    mState = STATE_ERROR;
                    mError = getString(R.string.phone_update_failed, error);
                }
                render();
            }
        });
    }

    private void onPrimary() {
        switch (mState) {
            case STATE_AVAILABLE:
                startDownload();
                break;
            case STATE_PERMISSION:
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                } catch (Throwable e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
                break;
            case STATE_UP_TO_DATE:
            case STATE_ERROR:
                if (mRelease != null && PhoneUpdater.isNewer(this, mRelease)) {
                    startDownload(); // a failed download: try it again
                } else {
                    check();
                }
                break;
            case STATE_INSTALLING:
                startDownload(); // the installer was closed: start over
                break;
            default:
                break;
        }
    }

    private void onSecondary() {
        if (mState == STATE_DOWNLOADING && mDownload != null) {
            mDownload.cancel();
        } else if (mState == STATE_AVAILABLE || mState == STATE_PERMISSION) {
            finish();
        }
    }

    @Override
    public void onInstallFailed(String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        mState = STATE_ERROR;
        mError = message;
        render();
    }

    // endregion

    // region Rendering

    private void render() {
        boolean spinner = mState == STATE_CHECKING || mState == STATE_DOWNLOADING || mState == STATE_INSTALLING;
        mSpinner.setVisibility(spinner ? View.VISIBLE : View.GONE);
        mIcon.setVisibility(spinner ? View.GONE : View.VISIBLE);
        mProgressBox.setVisibility(mState == STATE_DOWNLOADING || mState == STATE_INSTALLING ? View.VISIBLE : View.GONE);

        int accent = getResources().getColor(R.color.phone_accent);
        int neutral = getResources().getColor(R.color.phone_text_2);
        String size = mRelease != null && mRelease.size > 0 ? formatMb(mRelease.size) : null;

        switch (mState) {
            case STATE_CHECKING:
                setBadge(accent, 0);
                mTitle.setText(R.string.phone_update_checking_title);
                mSubtitle.setText(R.string.phone_update_checking_subtitle);
                setPrimary(getString(R.string.phone_update_checking_button), false);
                setSecondary(null);
                break;
            case STATE_UP_TO_DATE:
                setBadge(GREEN, R.drawable.ic_phone_check);
                mTitle.setText(R.string.phone_update_none_title);
                mSubtitle.setText(getString(R.string.phone_update_none_subtitle, mRelease != null ? mRelease.build : PhoneUpdater.currentBuild(this)));
                setPrimary(getString(R.string.phone_update_check_again), true);
                setSecondary(null);
                break;
            case STATE_AVAILABLE:
                setBadge(accent, R.drawable.ic_phone_update);
                mTitle.setText(R.string.phone_update_available_title);
                mSubtitle.setText(releaseLine(size));
                setPrimary(size != null ? getString(R.string.phone_update_install_size, size) : getString(R.string.phone_update_install), true);
                setSecondary(getString(R.string.phone_update_later));
                break;
            case STATE_DOWNLOADING:
                setBadge(accent, 0);
                mTitle.setText(getString(R.string.phone_update_downloading_build, mRelease != null ? mRelease.build : 0));
                mSubtitle.setText(R.string.phone_update_downloading_subtitle);
                setPrimary(null, false);
                setSecondary(getString(android.R.string.cancel));
                renderProgress();
                break;
            case STATE_INSTALLING:
                setBadge(accent, 0);
                mTitle.setText(R.string.phone_update_installing_title);
                mSubtitle.setText(R.string.phone_update_installing_subtitle);
                mProgress.setProgress(1000);
                setPrimary(getString(R.string.phone_update_install_again), true);
                setSecondary(null);
                break;
            case STATE_PERMISSION:
                setBadge(accent, R.drawable.ic_phone_settings);
                mTitle.setText(R.string.phone_update_permission_title);
                mSubtitle.setText(R.string.phone_update_permission);
                setPrimary(getString(R.string.phone_update_open_settings), true);
                setSecondary(getString(R.string.phone_update_later));
                break;
            case STATE_ERROR:
            default:
                setBadge(neutral, R.drawable.ic_phone_close);
                mTitle.setText(R.string.phone_update_error_title);
                mSubtitle.setText(mError);
                setPrimary(getString(R.string.phone_update_retry), true);
                setSecondary(null);
                break;
        }

        renderNotes();
    }

    private String releaseLine(String size) {
        StringBuilder line = new StringBuilder(getString(R.string.phone_update_build, mRelease.build));
        if (mRelease.publishedAt > 0) {
            line.append("  ·  ").append(DateUtils.getRelativeTimeSpanString(mRelease.publishedAt,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        }
        if (size != null) {
            line.append("  ·  ").append(size);
        }
        return line.toString();
    }

    private void renderProgress() {
        int permille = mTotal > 0 ? (int) Math.min(1000, mDone * 1000 / mTotal) : 0;
        mProgress.setIndeterminate(mTotal <= 0);
        mProgress.setProgress(permille);
        mProgressText.setText(mTotal > 0 ? getString(R.string.phone_update_progress, formatMb(mDone), formatMb(mTotal)) : formatMb(mDone));
        mProgressPercent.setText(mTotal > 0 ? (permille / 10) + "%" : "");
    }

    private void renderNotes() {
        boolean show = mRelease != null && !mRelease.notes.isEmpty()
                && (mState == STATE_AVAILABLE || mState == STATE_DOWNLOADING || mState == STATE_INSTALLING || mState == STATE_PERMISSION);
        mNotesBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show || mNotes.getChildCount() == mRelease.notes.size()) {
            return;
        }
        mNotes.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (String note : mRelease.notes) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, (int) (8 * density), 0, 0);

            View dot = new View(this);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(getResources().getColor(R.color.phone_accent));
            dot.setBackground(circle);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams((int) (6 * density), (int) (6 * density));
            dotParams.topMargin = (int) (8 * density);
            dotParams.rightMargin = (int) (12 * density);
            row.addView(dot, dotParams);

            TextView text = new TextView(this);
            text.setText(note);
            text.setTextSize(14);
            text.setLineSpacing(2 * density, 1f);
            text.setTextColor(getResources().getColor(R.color.phone_text));
            row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            mNotes.addView(row);
        }
    }

    private void setBadge(int color, int iconRes) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)));
        mBadge.setBackground(circle);
        if (iconRes != 0) {
            mIcon.setImageResource(iconRes);
            mIcon.setImageTintList(ColorStateList.valueOf(color));
        }
    }

    private void setPrimary(String text, boolean enabled) {
        mPrimary.setVisibility(text != null ? View.VISIBLE : View.GONE);
        mPrimary.setText(text);
        mPrimary.setEnabled(enabled);
        mPrimary.setAlpha(enabled ? 1f : 0.5f);
    }

    private void setSecondary(String text) {
        mSecondary.setVisibility(text != null ? View.VISIBLE : View.GONE);
        mSecondary.setText(text);
    }

    private static String formatMb(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / 1048576f);
    }

    // endregion

    // region PhoneActivity (no feed on this screen)

    @Override
    protected boolean hasPendingActions() {
        return false;
    }

    @Override
    protected void onListScrollEnd(Video lastVideo) {
    }

    // endregion
}
