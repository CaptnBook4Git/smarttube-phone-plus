package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Portrait sign-in. Same device code flow as the TV screen, but one tap opens the activation page with the code
 * already filled in, and the app picks up the new account by itself when the user comes back.
 */
public class PhoneSignInActivity extends PhoneActivity implements SignInView {
    @Override
    protected boolean hostsMiniPlayer() {
        return false;
    }

    private SignInPresenter mPresenter;
    private TextView mCode;
    private TextView mCodeHint;
    private TextView mButton;
    private TextView mAlt;
    private View mWaiting;
    private String mUserCode;
    private String mOpenUrl;
    private boolean mIsError;
    private static final int MAX_AUTO_RETRIES = 4;
    private int mAutoRetries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_activity_signin);

        applyInsets(findViewById(R.id.phone_root), findViewById(R.id.phone_top_bar), findViewById(R.id.phone_scroll));

        mCode = findViewById(R.id.phone_code);
        mCodeHint = findViewById(R.id.phone_code_hint);
        mButton = findViewById(R.id.phone_signin_button);
        mAlt = findViewById(R.id.phone_signin_alt);
        mWaiting = findViewById(R.id.phone_waiting);

        findViewById(R.id.phone_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.phone_code_card).setOnClickListener(v -> copyCode(true));
        mButton.setOnClickListener(v -> onButtonClicked());

        mPresenter = SignInPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    private void onButtonClicked() {
        if (mIsError) {
            mAutoRetries = 0;
            restartSignIn();
            return;
        }

        if (mOpenUrl == null) {
            return;
        }

        copyCode(false);
        mWaiting.setVisibility(View.VISIBLE);
        Utils.openLinkExt(this, mOpenUrl);
    }

    private void restartSignIn() {
        if (isFinishing()) {
            return;
        }
        mIsError = false;
        setButtonEnabled(false);
        mCode.setText(R.string.phone_signin_loading);
        mPresenter = SignInPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    private void copyCode(boolean notify) {
        if (TextUtils.isEmpty(mUserCode)) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("code", mUserCode));
            if (notify) {
                Toast.makeText(this, R.string.phone_signin_copied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setButtonEnabled(boolean enabled) {
        mButton.setEnabled(enabled);
        mButton.setAlpha(enabled ? 1f : 0.5f);
    }

    // region SignInView

    @Override
    public void showCode(String userCode, String signInUrl) {
        showCode(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        runOnUiThread(() -> {
            if (TextUtils.isEmpty(signInUrl)) {
                // The presenter reports errors as a code without url
                mIsError = true;
                mUserCode = null;
                mOpenUrl = null;
                boolean network = userCode != null && (userCode.contains("UnknownHost") || userCode.contains("resolve host")
                        || userCode.contains("timeout") || userCode.contains("Unable to"));

                // DNS lookups sometimes fail on the first tries: retry quietly before showing an error
                if (network && mAutoRetries < MAX_AUTO_RETRIES) {
                    mAutoRetries++;
                    mCode.setTextSize(16);
                    mCode.setText(R.string.phone_signin_connecting);
                    mCode.postDelayed(this::restartSignIn, 1500L * mAutoRetries);
                    return;
                }

                mCode.setTextSize(16);
                mCode.setTypeface(android.graphics.Typeface.DEFAULT);
                mCode.setGravity(android.view.Gravity.CENTER);
                mCode.setPadding(dp(16), 0, dp(16), 0);
                mCode.setText(network ? R.string.phone_signin_error_network : R.string.phone_signin_error_generic);
                mCodeHint.setVisibility(View.GONE);
                mWaiting.setVisibility(View.INVISIBLE);
                mButton.setText(R.string.phone_signin_retry);
                setButtonEnabled(true);
                return;
            }

            mIsError = false;
            mAutoRetries = 0;
            mUserCode = userCode;
            mOpenUrl = fullSignInUrl != null ? fullSignInUrl : signInUrl;
            mCode.setTextSize(34);
            mCode.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            mCode.setPadding(0, 0, 0, 0);
            mCode.setText(userCode);
            mCodeHint.setVisibility(View.VISIBLE);
            mButton.setText(R.string.phone_signin_continue);
            setButtonEnabled(true);
            mAlt.setText(getString(R.string.phone_signin_alt, signInUrl.replace("https://", "")));
        });
    }

    @Override
    public void close() {
        runOnUiThread(this::finish);
    }

    // endregion

    private int dp(float value) {
        return (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    @Override
    protected boolean hasPendingActions() {
        return false;
    }

    @Override
    protected void onListScrollEnd(Video lastVideo) {
        // No list on this screen
    }
}
