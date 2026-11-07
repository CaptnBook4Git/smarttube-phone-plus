package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Phone home screen. Implements the same BrowseView the TV BrowseFragment does, so BrowsePresenter drives it unchanged.
 * SmartTube's sections are grouped into four bottom tabs, and the sections of a tab become chips.
 */
public class PhoneBrowseActivity extends PhoneActivity implements BrowseView, FeedAdapter.Listener {
    private static final int TAB_HOME = 0;
    private static final int TAB_SHORTS = 1;
    private static final int TAB_SUBS = 2;
    private static final int TAB_YOU = 3;
    private static final int TAB_COUNT = 4;

    private BrowsePresenter mPresenter;
    private final List<BrowseSection> mSections = new ArrayList<>();
    private final int[] mTabSectionIds = {-1, -1, -1, -1};
    private BrowseSection mCurrentSection;
    private int mCurrentTab = TAB_HOME;

    private LinearLayout mBottomBar;
    private LinearLayout mChips;
    private HorizontalScrollView mChipsScroll;
    private ImageView mAccount;
    private final View[] mTabViews = new View[TAB_COUNT];
    private boolean mIsFirstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_activity_browse);

        mBottomBar = findViewById(R.id.phone_bottom_bar);
        mChips = findViewById(R.id.phone_chips);
        mChipsScroll = findViewById(R.id.phone_chips_scroll);
        mAccount = findViewById(R.id.phone_account);

        applyInsets(findViewById(R.id.phone_root), findViewById(R.id.phone_top_bar), mBottomBar);
        setupList(this);
        setupTopBar();
        setupTabs();

        mPresenter = BrowsePresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mIsFirstResume) {
            mPresenter.onViewResumed();
        }
        mIsFirstResume = false;
        updateAccountIcon();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public void onBackPressed() {
        if (mCurrentTab != TAB_HOME && hasTab(TAB_HOME)) {
            selectTab(TAB_HOME);
            return;
        }
        super.onBackPressed();
    }

    // region UI setup

    private void setupTopBar() {
        findViewById(R.id.phone_search).setOnClickListener(v -> SearchPresenter.instance(this).startSearch(null));
        mAccount.setOnClickListener(v -> AccountSettingsPresenter.instance(this).show());
        mAccount.setOnLongClickListener(v -> {
            AccountSelectionPresenter.instance(this).nextAccountOrDialog();
            return true;
        });
        findViewById(R.id.phone_logo).setOnClickListener(v -> scrollToTopOrRefresh());
    }

    private void setupTabs() {
        int[] icons = {R.drawable.ic_phone_home, R.drawable.ic_phone_shorts, R.drawable.ic_phone_subs, R.drawable.ic_phone_library};
        String[] labels = {
                getString(R.string.header_home),
                getString(R.string.header_shorts),
                getString(R.string.header_subscriptions),
                getString(R.string.phone_tab_you)
        };

        for (int i = 0; i < TAB_COUNT; i++) {
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, dp(8), 0, dp(6));
            TypedValue ripple = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
            if (ripple.resourceId != 0) {
                tab.setBackgroundResource(ripple.resourceId);
            }

            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            tab.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(3);
            tab.addView(label, labelParams);

            final int index = i;
            tab.setOnClickListener(v -> {
                if (index == mCurrentTab) {
                    scrollToTopOrRefresh();
                } else {
                    selectTab(index);
                }
            });

            mTabViews[i] = tab;
            mBottomBar.addView(tab, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }

        updateTabs();
    }

    private void updateTabs() {
        for (int i = 0; i < TAB_COUNT; i++) {
            View tab = mTabViews[i];
            if (tab == null) {
                continue;
            }
            tab.setVisibility(hasTab(i) ? View.VISIBLE : View.GONE);
            boolean selected = i == mCurrentTab;
            int color = getResources().getColor(selected ? R.color.phone_text : R.color.phone_icon_inactive);
            LinearLayout layout = (LinearLayout) tab;
            ((ImageView) layout.getChildAt(0)).setColorFilter(color);
            TextView label = (TextView) layout.getChildAt(1);
            label.setTextColor(color);
            label.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void updateChips() {
        mChips.removeAllViews();
        List<BrowseSection> sections = sectionsOfTab(mCurrentTab);

        if (sections.size() <= 1) {
            mChipsScroll.setVisibility(View.GONE);
            return;
        }

        mChipsScroll.setVisibility(View.VISIBLE);

        for (BrowseSection section : sections) {
            TextView chip = new TextView(this);
            chip.setText(section.getTitle());
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), 0, dp(12), 0);
            chip.setBackgroundResource(R.drawable.phone_chip_bg);
            boolean selected = mCurrentSection != null && mCurrentSection.getId() == section.getId();
            chip.setSelected(selected);
            chip.setTextColor(getResources().getColor(selected ? R.color.phone_bg : R.color.phone_text));
            chip.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            chip.setOnClickListener(v -> {
                if (mCurrentSection == null || mCurrentSection.getId() != section.getId()) {
                    focusSection(section);
                } else {
                    scrollToTopOrRefresh();
                }
            });
            chip.setOnLongClickListener(v -> {
                mPresenter.onSectionLongPressed(section.getId());
                return true;
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
            params.setMarginStart(dp(4));
            params.setMarginEnd(dp(4));
            mChips.addView(chip, params);

            if (selected) {
                chip.post(() -> mChipsScroll.smoothScrollTo(Math.max(0, chip.getLeft() - dp(16)), 0));
            }
        }
    }

    private void scrollToTopOrRefresh() {
        if (mList.canScrollVertically(-1)) {
            mList.smoothScrollToPosition(0);
        } else {
            mPresenter.refresh(false);
        }
    }

    private void updateAccountIcon() {
        Account account = MediaServiceManager.instance().getSelectedAccount();
        if (account != null && account.getAvatarImageUrl() != null) {
            mAccount.setImageTintList(null);
            PhoneUi.loadAvatar(mAccount, account.getAvatarImageUrl());
        } else {
            mAccount.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.phone_text)));
            mAccount.setImageResource(R.drawable.ic_phone_account);
        }
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    // endregion

    // region Tabs and sections

    private static int tabOf(BrowseSection section) {
        switch (section.getId()) {
            case MediaGroup.TYPE_SHORTS:
                return TAB_SHORTS;
            case MediaGroup.TYPE_SUBSCRIPTIONS:
            case MediaGroup.TYPE_CHANNEL_UPLOADS:
                return TAB_SUBS;
            case MediaGroup.TYPE_HISTORY:
            case MediaGroup.TYPE_USER_PLAYLISTS:
            case MediaGroup.TYPE_MY_VIDEOS:
            case MediaGroup.TYPE_NOTIFICATIONS:
            case MediaGroup.TYPE_PLAYBACK_QUEUE:
            case MediaGroup.TYPE_BLOCKED_CHANNELS:
            case MediaGroup.TYPE_SETTINGS:
                return TAB_YOU;
            default:
                return TAB_HOME;
        }
    }

    private List<BrowseSection> sectionsOfTab(int tab) {
        List<BrowseSection> result = new ArrayList<>();
        for (BrowseSection section : mSections) {
            if (tabOf(section) == tab) {
                result.add(section);
            }
        }
        return result;
    }

    private boolean hasTab(int tab) {
        for (BrowseSection section : mSections) {
            if (tabOf(section) == tab) {
                return true;
            }
        }
        return false;
    }

    private void selectTab(int tab) {
        List<BrowseSection> sections = sectionsOfTab(tab);
        if (sections.isEmpty()) {
            return;
        }

        BrowseSection target = sections.get(0);
        for (BrowseSection section : sections) {
            if (section.getId() == mTabSectionIds[tab]) {
                target = section;
                break;
            }
        }

        focusSection(target);
    }

    private void focusSection(BrowseSection section) {
        mCurrentSection = section;
        mCurrentTab = tabOf(section);
        mTabSectionIds[mCurrentTab] = section.getId();

        updateTabs();
        updateChips();
        hideMessage();
        mAdapter.setMode(modeOf(section));
        mList.scrollToPosition(0);

        if (section.getType() == BrowseSection.TYPE_ERROR && section.getData() instanceof ErrorFragmentData) {
            showError((ErrorFragmentData) section.getData());
        }

        mPresenter.onSectionFocused(section.getId());
    }

    private static int modeOf(BrowseSection section) {
        switch (section.getType()) {
            case BrowseSection.TYPE_ROW:
                return FeedAdapter.MODE_SHELVES;
            case BrowseSection.TYPE_SHORTS_GRID:
                return FeedAdapter.MODE_SHORTS;
            case BrowseSection.TYPE_MULTI_GRID:
                return FeedAdapter.MODE_MULTI;
            case BrowseSection.TYPE_SETTINGS_GRID:
                return FeedAdapter.MODE_SETTINGS;
            default:
                return FeedAdapter.MODE_FLAT;
        }
    }

    private int indexOfSection(int id) {
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }

    // endregion

    // region BrowseView

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) {
            return;
        }

        int existing = indexOfSection(section.getId());
        if (existing != -1 && (index == -1 || existing == index)) {
            return;
        }
        if (existing != -1) {
            mSections.remove(existing);
        }

        if (index == -1 || index > mSections.size()) {
            mSections.add(section);
        } else {
            mSections.add(index, section);
        }

        updateTabs();
        if (mCurrentSection != null && tabOf(section) == mCurrentTab) {
            updateChips();
        }
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (section == null) {
            return;
        }
        int index = indexOfSection(section.getId());
        if (index != -1) {
            mSections.remove(index);
        }
        updateTabs();
        updateChips();
    }

    @Override
    public void removeAllSections() {
        mSections.clear();
        updateTabs();
        updateChips();
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (mSections.isEmpty() || index < 0) {
            return;
        }
        focusSection(mSections.get(Math.min(index, mSections.size() - 1)));
    }

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null) {
            return;
        }
        BrowseSection section = group.getSection();
        if (section != null && mCurrentSection != null && section.getId() != mCurrentSection.getId()) {
            return; // late result of a section the user already left
        }
        hideMessage();
        mAdapter.update(group);
        checkScrollEndLater();
    }

    @Override
    public void updateSection(SettingsGroup group) {
        if (group == null) {
            return;
        }
        hideMessage();
        mAdapter.setSettings(group.getItems());
    }

    @Override
    public void clearSection(BrowseSection section) {
        mAdapter.clear();
    }

    @Override
    public void selectSectionItem(int index) {
        if (index >= 0 && index < mAdapter.getItemCount()) {
            mList.scrollToPosition(index);
        }
    }

    @Override
    public void selectSectionItem(Video item) {
        if (item == null) {
            return;
        }
        int index = mAdapter.indexOf(item);
        if (index != -1) {
            mList.scrollToPosition(index);
        }
    }

    @Override
    public void showError(ErrorFragmentData data) {
        super.showError(data);
    }

    @Override
    public void showProgressBar(boolean show) {
        showProgress(show);
    }

    @Override
    public boolean isProgressBarShowing() {
        return isProgressShowing();
    }

    @Override
    public void focusOnContent() {
        // Touch UI, nothing to focus
    }

    @Override
    public boolean isEmpty() {
        return mAdapter == null || mAdapter.isEmpty();
    }

    @Override
    public void updateBadge() {
        updateAccountIcon();
    }

    // endregion

    // region FeedAdapter.Listener

    @Override
    public void onVideoClicked(Video video) {
        mPresenter.onVideoItemSelected(video);
        mPresenter.onVideoItemClicked(video);
    }

    @Override
    public void onVideoLongClicked(Video video) {
        mPresenter.onVideoItemSelected(video);
        mPresenter.onVideoItemLongClicked(video);
    }

    @Override
    public void onStripClicked(Video channel) {
        mAdapter.setSelectedStripItem(channel);
        // Selecting a channel loads its uploads below the strip (same as focusing it on TV).
        mPresenter.onVideoItemSelected(channel);
        if (!MainUIData.instance(this).isUploadsAutoLoadEnabled()) {
            mPresenter.onVideoItemClicked(channel);
        }
    }

    @Override
    public void onShelfScrollEnd(Video lastVideo) {
        if (!mPresenter.hasPendingActions()) {
            mPresenter.onScrollEnd(lastVideo);
        }
    }

    @Override
    protected boolean hasPendingActions() {
        return mPresenter.hasPendingActions();
    }

    @Override
    protected void onListScrollEnd(Video lastVideo) {
        mPresenter.onScrollEnd(lastVideo);
    }

    // endregion
}
