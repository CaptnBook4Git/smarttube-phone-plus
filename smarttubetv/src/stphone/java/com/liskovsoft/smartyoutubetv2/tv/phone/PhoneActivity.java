package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Base for the portrait phone screens. Keeps SmartTube's activity plumbing (locale, dpi, view manager stack)
 * but shows the system bars and lays the content out edge to edge.
 */
public abstract class PhoneActivity extends MotherActivity {
    protected RecyclerView mList;
    protected FeedAdapter mAdapter;
    private View mProgressBar;
    private View mEmptyView;
    private TextView mEmptyText;
    private TextView mEmptyAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // MotherActivity may switch to immersive mode (TV fullscreen setting). Phones keep their bars.
        setupSystemBars();
        getViewManager().addTop(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupSystemBars();
        }
    }

    private void setupSystemBars() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }

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
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    /**
     * Pads the top bar below the status bar and the bottom view (or the list) above the navigation bar.
     */
    @SuppressWarnings("deprecation")
    protected void applyInsets(View root, View topBar, View bottomBar) {
        final int topBase = topBar != null ? topBar.getPaddingTop() : 0;
        final int bottomBase = bottomBar != null ? bottomBar.getPaddingBottom() : 0;

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            int left = insets.getSystemWindowInsetLeft();
            int right = insets.getSystemWindowInsetRight();

            v.setPadding(left, 0, right, 0);

            if (topBar != null) {
                topBar.setPadding(topBar.getPaddingLeft(), topBase + top, topBar.getPaddingRight(), topBar.getPaddingBottom());
            }

            if (bottomBar != null) {
                bottomBar.setPadding(bottomBar.getPaddingLeft(), bottomBar.getPaddingTop(), bottomBar.getPaddingRight(), bottomBase + bottom);
            }

            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
    }

    // region Shared list plumbing

    protected void setupList(FeedAdapter.Listener listener) {
        mList = findViewById(R.id.phone_list);
        mProgressBar = findViewById(R.id.phone_progress_bar);
        mEmptyView = findViewById(R.id.phone_empty);
        mEmptyText = findViewById(R.id.phone_empty_text);
        mEmptyAction = findViewById(R.id.phone_empty_action);

        mAdapter = new FeedAdapter(listener);
        GridLayoutManager manager = new GridLayoutManager(this, 2);
        manager.setSpanSizeLookup(mAdapter.getSpanSizeLookup());
        mList.setLayoutManager(manager);
        mList.setAdapter(mAdapter);
        mList.setItemAnimator(null);
        mList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                checkScrollEnd();
            }
        });
    }

    protected void checkScrollEnd() {
        if (mList == null || mAdapter == null) {
            return;
        }
        GridLayoutManager manager = (GridLayoutManager) mList.getLayoutManager();
        if (manager == null) {
            return;
        }
        int last = manager.findLastVisibleItemPosition();
        if (last >= mAdapter.getItemCount() - 6) {
            Video video = mAdapter.getScrollEndVideo();
            if (video != null && !hasPendingActions()) {
                onListScrollEnd(video);
            }
        }
    }

    /**
     * Short lists never scroll, so ask for more right after an update.
     */
    protected void checkScrollEndLater() {
        if (mList != null) {
            mList.post(this::checkScrollEnd);
        }
    }

    protected abstract boolean hasPendingActions();

    protected abstract void onListScrollEnd(Video lastVideo);

    protected void showProgress(boolean show) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (!show) {
            updateEmptyState();
        }
    }

    protected boolean isProgressShowing() {
        return mProgressBar != null && mProgressBar.getVisibility() == View.VISIBLE;
    }

    protected void updateEmptyState() {
        if (mEmptyView == null) {
            return;
        }
        boolean empty = mAdapter == null || mAdapter.isEmpty();
        if (!empty) {
            mEmptyView.setVisibility(View.GONE);
        } else if (!isProgressShowing() && mEmptyView.getVisibility() != View.VISIBLE) {
            showMessage(getString(R.string.phone_empty), null, null);
        }
    }

    protected void hideMessage() {
        if (mEmptyView != null) {
            mEmptyView.setVisibility(View.GONE);
        }
    }

    protected void showMessage(String message, String action, Runnable onAction) {
        if (mEmptyView == null) {
            return;
        }
        mEmptyText.setText(message);
        if (action != null && onAction != null) {
            mEmptyAction.setText(action);
            mEmptyAction.setVisibility(View.VISIBLE);
            mEmptyAction.setOnClickListener(v -> onAction.run());
        } else {
            mEmptyAction.setVisibility(View.GONE);
        }
        mEmptyView.setVisibility(View.VISIBLE);
    }

    protected void showError(ErrorFragmentData data) {
        if (data == null) {
            return;
        }
        if (mAdapter != null) {
            mAdapter.clear();
        }
        showMessage(data.getMessage(), data.getActionText(), data::onAction);
    }

    // endregion

    // region Navigation

    @Override
    public void finish() {
        // Back on the root screen leaves the app, otherwise return to the previous screen.
        if (!getViewManager().hasParentView(this)) {
            Utils.properlyFinishTheApp(this);
        } else {
            finishReally();
        }
    }

    @Override
    public void finishReally() {
        // Mandatory, keeps SmartTube's view stack in order (same as LeanbackActivity).
        getViewManager().startParentView(this);
        super.finishReally();
    }

    // endregion
}
