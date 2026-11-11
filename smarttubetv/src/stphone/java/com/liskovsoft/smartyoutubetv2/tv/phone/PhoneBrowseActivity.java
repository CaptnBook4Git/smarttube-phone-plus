package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
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
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
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
 * SmartTube's sections are grouped into five bottom tabs, and the sections of a tab become chips.
 */
public class PhoneBrowseActivity extends PhoneActivity implements BrowseView, FeedAdapter.Listener,
        MediaServiceManager.AccountChangeListener {
    private static final int TAB_HOME = 0;
    private static final int TAB_SHORTS = 1;
    private static final int TAB_SUBS = 2;
    private static final int TAB_NOTIFICATIONS = 3;
    private static final int TAB_YOU = 4;
    private static final int TAB_COUNT = 5;

    private BrowsePresenter mPresenter;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout mRefresh;
    private boolean mUserRefreshing;
    private final List<BrowseSection> mSections = new ArrayList<>();
    private final int[] mTabSectionIds = {-1, -1, -1, -1, -1};
    private BrowseSection mCurrentSection;
    private int mCurrentTab = TAB_HOME;

    private LinearLayout mBottomBar;
    private LinearLayout mChips;
    private HorizontalScrollView mChipsScroll;
    private View mAccountHeader;
    private final View[] mTabViews = new View[TAB_COUNT];
    private boolean mIsFirstResume = true;
    private boolean mIsInitialSelection = true;
    private boolean mShortsAutoplay;
    private BrowseSection mShortsReturnSection;
    private BrowseSection mLoadedSection; // what the presenter actually loads (Home falls back when signed out)
    private View mSignInCard;
    private PhoneSubscriptionsHeader mSubsHeader; // channels row and filter chips above the Subscriptions feed
    private VideoGroup mSubsChannelGroup; // the selected channel's videos, shown instead of the feed of all channels
    private Object mSubsAllContent; // the feed of all channels, put back when the channel is unselected
    private io.reactivex.disposables.Disposable mSubsChannelAction;
    private View mListFilterScroll;
    private LinearLayout mListFilterRow;
    private PhoneSearchFilters.SavedSearchList mActiveSavedList;
    private String mActiveSavedListQuery;
    private int mActiveSavedListOptions;
    private Object mHomeAllContent;
    private io.reactivex.disposables.Disposable mSavedListAction;
    private io.reactivex.disposables.Disposable mSavedListContinueAction;
    private int mLastListsVersion = -1;
    private ImageView mTopAvatar;
    private SettingsGroup mSettingsGroup; // last Settings content, rebuilt when the notification setting changes

    /** The task Home lives in: the player belongs in the same one (one card in recents) */
    static int sTaskId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sTaskId = getTaskId();
        setContentView(R.layout.phone_activity_browse);

        mBottomBar = findViewById(R.id.phone_bottom_bar);
        mChips = findViewById(R.id.phone_chips);
        mChipsScroll = findViewById(R.id.phone_chips_scroll);
        mListFilterScroll = findViewById(R.id.phone_list_filter_scroll);
        mListFilterRow = findViewById(R.id.phone_list_filter_row);
        mAccountHeader = findViewById(R.id.phone_account_header);

        applyInsets(findViewById(R.id.phone_root), findViewById(R.id.phone_top_bar), mBottomBar);
        setupList(this);
        setupPullToRefresh();
        setupBars();
        setupTopBar();
        setupTabs();

        MediaServiceManager.instance().addAccountListener(this);

        mPresenter = BrowsePresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();

        CrashReport.showIfAny(this);
        mList.postDelayed(this::askNotificationPermission, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sTaskId = getTaskId();
        // New build waiting? A small Update card (can be turned off on the update screen)
        getWindow().getDecorView().postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                PhoneUpdater.checkInBackground(this);
            }
        }, 2500);
        if (!mIsFirstResume) {
            mPresenter.onViewResumed();
        }
        mIsFirstResume = false;
        updateAccountViews();
        refreshSettingsRows(); // back from Android's notification settings
        if (mAdapter != null) {
            mAdapter.refreshQuickActions();
        }
        if (mLastListsVersion != PhoneSearchFilters.listsVersion()) {
            mLastListsVersion = PhoneSearchFilters.listsVersion();
            updateChips();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PhoneNotifications.REQUEST_FROM_SETTINGS) {
            PhoneNotifications.onPermissionResult(this);
        }
        refreshSettingsRows();
    }

    private void refreshSettingsRows() {
        if (mSettingsGroup != null && mCurrentSection != null && mCurrentSection.getId() == MediaGroup.TYPE_SETTINGS) {
            updateSection(mSettingsGroup);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MediaServiceManager.instance().removeAccountListener(this);
        if (mSubsHeader != null) {
            mSubsHeader.release();
        }
        com.liskovsoft.sharedutils.rx.RxHelper.disposeActions(mSubsChannelAction, mSavedListAction, mSavedListContinueAction);
        mPresenter.onViewDestroyed();
    }

    @Override
    public void onBackPressed() {
        if (mActiveSavedList != null && mCurrentTab == TAB_HOME) {
            showSavedSearchList(null);
            return;
        }
        if (isSection(MediaGroup.TYPE_CHANNEL_UPLOADS) && sectionById(MediaGroup.TYPE_SUBSCRIPTIONS) != null) {
            // All channels was opened from the Subscriptions feed: go back to it
            focusSection(sectionById(MediaGroup.TYPE_SUBSCRIPTIONS));
            return;
        }
        if (mCurrentTab != TAB_HOME && hasTab(TAB_HOME)) {
            selectTab(TAB_HOME);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onAccountChanged(Account account) {
        runOnUiThread(() -> {
            updateAccountViews();
            if (mCurrentSection != null && mCurrentSection.getId() == MediaGroup.TYPE_HOME) {
                focusSection(mCurrentSection);
            }
        });
    }

    private static boolean isSignedIn() {
        Account account = MediaServiceManager.instance().getSelectedAccount();
        return account != null && !account.isEmpty();
    }

    private BrowseSection sectionById(int id) {
        int index = indexOfSection(id);
        return index != -1 ? mSections.get(index) : null;
    }

    /**
     * Signed out, the Home feed is empty: show popular videos there instead, with a sign in card on top.
     */
    private BrowseSection signedOutHomeSource() {
        int[] candidates = {MediaGroup.TYPE_TRENDING, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_GAMING, MediaGroup.TYPE_NEWS, MediaGroup.TYPE_SPORTS};
        for (int id : candidates) {
            BrowseSection section = sectionById(id);
            if (section != null) {
                return section;
            }
        }
        return null;
    }

    // region UI setup

    /**
     * The feed scrolls under the top and bottom bars. The top bar slides away while scrolling down
     * (like the YouTube app); in Liquid Glass mode both bars are translucent.
     */
    private void setupBars() {
        View topBar = findViewById(R.id.phone_top_bar);
        topBar.setBackgroundResource(R.color.phone_bg);
        mBottomBar.setBackgroundResource(R.drawable.phone_bottom_bar_bg);

        View.OnLayoutChangeListener sync = (v, l, t, r, b, ol, ot, or, ob) -> v.post(this::syncBarPadding);
        topBar.addOnLayoutChangeListener(sync);
        mBottomBar.addOnLayoutChangeListener(sync);

        mList.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                int hideable = topBar.getHeight() - topBar.getPaddingTop(); // keep the status bar area
                float y = Math.max(-hideable, Math.min(0, topBar.getTranslationY() - dy));
                if (!recyclerView.canScrollVertically(-1)) {
                    y = 0;
                }
                topBar.setTranslationY(y);
                // The loading line sits under the top bar: move it with the bar
                View progress = findViewById(R.id.phone_progress_bar);
                if (progress != null) {
                    progress.setTranslationY(y);
                }
            }
        });
    }

    private void setupPullToRefresh() {
        mRefresh = findViewById(R.id.phone_refresh);
        if (mRefresh == null) {
            return;
        }
        mRefresh.setColorSchemeColors(getResources().getColor(R.color.phone_accent));
        mRefresh.setProgressBackgroundColorSchemeColor(getResources().getColor(R.color.phone_surface_2));
        mRefresh.setOnRefreshListener(() -> {
            if (mCurrentSection == null || mCurrentSection.getId() == MediaGroup.TYPE_SETTINGS) {
                mRefresh.setRefreshing(false);
                return;
            }
            if (mCurrentTab == TAB_HOME && mActiveSavedList != null) {
                mRefresh.setRefreshing(false);
                loadActiveSavedSearchList();
                return;
            }
            mUserRefreshing = true;
            if (isSubscriptionsFeed()) {
                mSubsHeader.onShown(true);
            }
            showLoadingPlaceholders();
            mRefresh.setRefreshing(false); // the pulsing placeholders take over from the spinner (like YouTube)
            mPresenter.refresh(false);
            mRefresh.postDelayed(this::stopRefreshing, 15_000); // never spin forever
        });
    }

    private void stopRefreshing() {
        mUserRefreshing = false;
        if (mRefresh != null && mRefresh.isRefreshing()) {
            mRefresh.setRefreshing(false);
        }
    }

    @Override
    protected boolean wantsSkeleton() {
        // Settings isn't a video feed
        return mCurrentSection == null || mCurrentSection.getId() != MediaGroup.TYPE_SETTINGS;
    }

    private void syncBarPadding() {
        View topBar = findViewById(R.id.phone_top_bar);
        int top = topBar.getHeight();
        // The status bar area stays covered while the top bar slides away underneath
        View scrim = findViewById(R.id.phone_status_scrim);
        android.view.ViewGroup.LayoutParams scrimParams = scrim.getLayoutParams();
        if (scrimParams.height != topBar.getPaddingTop()) {
            scrimParams.height = topBar.getPaddingTop();
            scrim.setLayoutParams(scrimParams);
        }
        if (mRefresh != null && mRefresh.getProgressViewEndOffset() != top + dp(24)) {
            mRefresh.setProgressViewOffset(false, top - dp(40), top + dp(24)); // the spinner comes out under the top bar
        }
        int bottom = mBottomBar.getHeight();
        if (mList.getPaddingTop() != top || mList.getPaddingBottom() != bottom + dp(8)) {
            mList.setPadding(mList.getPaddingLeft(), top, mList.getPaddingRight(), bottom + dp(8));
        }
        View progress = findViewById(R.id.phone_progress_bar);
        android.widget.FrameLayout.LayoutParams params = (android.widget.FrameLayout.LayoutParams) progress.getLayoutParams();
        if (params.topMargin != top) {
            params.topMargin = top;
            progress.setLayoutParams(params);
        }
    }

    private void setupTopBar() {
        findViewById(R.id.phone_search).setOnClickListener(v -> SearchPresenter.instance(this).startSearch(null));
        View settingsBtn = findViewById(R.id.phone_settings_btn);
        if (settingsBtn != null) {
            settingsBtn.setOnClickListener(v -> openAppSettings());
        }
        View accountSettingsBtn = findViewById(R.id.phone_account_settings);
        if (accountSettingsBtn != null) {
            accountSettingsBtn.setOnClickListener(v -> openAppSettings());
        }
        mTopAvatar = findViewById(R.id.phone_top_avatar);
        mTopAvatar.setOnClickListener(v -> selectTab(TAB_YOU));
        mSignInCard = getLayoutInflater().inflate(R.layout.phone_signin_card, mList, false);
        mSignInCard.findViewById(R.id.phone_signin_card_button).setOnClickListener(v -> YTSignInPresenter.instance(this).start());
        mSubsHeader = new PhoneSubscriptionsHeader(this, mList, new PhoneSubscriptionsHeader.Callback() {
            @Override
            public void onChannelSelected(Video channel) {
                showSubscriptionsChannel(channel);
            }

            @Override
            public void onChannelLongClicked(Video channel) {
                onVideoLongClicked(channel);
            }

            @Override
            public void onFilterChanged(FeedAdapter.VideoFilter filter) {
                mAdapter.setFilter(filter);
                mList.scrollToPosition(0);
                checkScrollEndLater(); // few matches: load more of the feed
            }
        });
        findViewById(R.id.phone_logo).setOnClickListener(v -> scrollToTopOrRefresh());
    }

    private void openAppSettings() {
        BrowseSection settings = sectionById(MediaGroup.TYPE_SETTINGS);
        if (settings == null) {
            com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService.instance(this)
                    .enableSection(MediaGroup.TYPE_SETTINGS, true);
            mPresenter.updateSections();
            settings = sectionById(MediaGroup.TYPE_SETTINGS);
        }
        if (settings != null) {
            focusSection(settings);
        } else {
            PhoneQuickActions.openSettings(this, () -> {
                if (mAdapter != null) {
                    mAdapter.refreshQuickActions();
                }
            });
        }
    }

    private void setupTabs() {
        int[] icons = {R.drawable.ic_phone_home, R.drawable.ic_phone_shorts, R.drawable.ic_phone_subs,
                R.drawable.ic_phone_bell, R.drawable.ic_phone_account};
        String[] labels = {
                getString(R.string.header_home),
                getString(R.string.header_shorts),
                getString(R.string.header_subscriptions),
                getString(R.string.phone_tab_notifications),
                getString(R.string.phone_tab_you)
        };

        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);

        for (int i = 0; i < TAB_COUNT; i++) {
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, dp(9), 0, dp(7));
            if (ripple.resourceId != 0) {
                tab.setBackgroundResource(ripple.resourceId);
            }

            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            tab.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(4);
            tab.addView(label, labelParams);

            final int index = i;
            tab.setOnClickListener(v -> {
                if (index == TAB_SHORTS) {
                    playShorts();
                } else if (index == TAB_SUBS && mCurrentTab == TAB_SUBS && !isSubscriptionsFeed()) {
                    selectTab(TAB_SUBS); // from All channels back to the feed
                } else if (index == mCurrentTab) {
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
            // No Notifications tab for now (YouTube doesn't give SmartTube the notification inbox)
            boolean visible = i == TAB_YOU || (hasTab(i) && i != TAB_NOTIFICATIONS);
            tab.setVisibility(visible ? View.VISIBLE : View.GONE);
            boolean selected = i == mCurrentTab;
            int color = getResources().getColor(selected ? R.color.phone_text : R.color.phone_icon_inactive);
            LinearLayout layout = (LinearLayout) tab;
            ImageView icon = (ImageView) layout.getChildAt(0);
            // The You tab shows the account picture untinted
            icon.setImageTintList(i == TAB_YOU && icon.getTag() != null ? null : ColorStateList.valueOf(color));
            TextView label = (TextView) layout.getChildAt(1);
            label.setTextColor(color);
            label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void updateChips() {
        mChips.removeAllViews();
        List<BrowseSection> sections = sectionsOfTab(mCurrentTab);

        if (isSubscriptionsFeed()) {
            mChipsScroll.setVisibility(View.GONE);
            updateSavedListFilterBar();
            return;
        }

        if (mCurrentTab != TAB_HOME && mCurrentTab != TAB_YOU && sections.size() <= 1) {
            mChipsScroll.setVisibility(View.GONE);
            updateSavedListFilterBar();
            return;
        }
        if (mCurrentTab == TAB_YOU && sections.isEmpty()) {
            mChipsScroll.setVisibility(View.GONE);
            updateSavedListFilterBar();
            return;
        }

        mChipsScroll.setVisibility(View.VISIBLE);

        if (mCurrentTab == TAB_HOME) {
            // First: Home section chip ("Start" / "Alle")
            BrowseSection homeSection = sectionById(MediaGroup.TYPE_HOME);
            if (homeSection == null && !sections.isEmpty()) {
                homeSection = sections.get(0);
            }
            if (homeSection != null) {
                BrowseSection finalHomeSection = homeSection;
                TextView chip = new TextView(this);
                chip.setText(homeSection.getTitle());
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(14), 0, dp(14), 0);
                chip.setBackgroundResource(R.drawable.phone_chip_bg);
                boolean selected = mActiveSavedList == null
                        && mCurrentSection != null
                        && mCurrentSection.getId() == homeSection.getId();
                chip.setSelected(selected);
                chip.setTextColor(getResources().getColor(selected ? R.color.phone_bg : R.color.phone_text));
                chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                chip.setOnClickListener(v -> {
                    if (mActiveSavedList != null) {
                        showSavedSearchList(null);
                        focusSection(finalHomeSection);
                    } else if (mCurrentSection == null || mCurrentSection.getId() != finalHomeSection.getId()) {
                        focusSection(finalHomeSection);
                    } else {
                        scrollToTopOrRefresh();
                    }
                });
                chip.setOnLongClickListener(v -> {
                    mPresenter.onSectionLongPressed(finalHomeSection.getId());
                    return true;
                });

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
                params.setMarginStart(dp(4));
                params.setMarginEnd(dp(4));
                mChips.addView(chip, params);

                if (selected) {
                    chip.post(() -> mChipsScroll.smoothScrollTo(Math.max(0, chip.getLeft() - dp(16)), 0));
                }
            }

            // Immediately after Start: custom saved search lists with subtle highlight
            List<PhoneSearchFilters.SavedSearchList> savedLists = PhoneSearchFilters.getSavedLists(this);
            for (PhoneSearchFilters.SavedSearchList saved : savedLists) {
                boolean selected = mActiveSavedList != null && saved.id.equals(mActiveSavedList.id);
                TextView chip = PhoneSearchFilters.createCustomListChip(this, saved.displayTitle(), selected);
                chip.setOnClickListener(v -> {
                    if (!selected) {
                        showSavedSearchList(saved);
                    } else {
                        openEditSavedListDialog(saved);
                    }
                });
                chip.setOnLongClickListener(v -> {
                    openEditSavedListDialog(saved);
                    return true;
                });

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
                params.setMarginStart(dp(4));
                params.setMarginEnd(dp(4));
                mChips.addView(chip, params);

                if (selected) {
                    chip.post(() -> mChipsScroll.smoothScrollTo(Math.max(0, chip.getLeft() - dp(16)), 0));
                }
            }

            // + Eigene Liste button
            TextView addChip = new TextView(this);
            addChip.setText(R.string.phone_list_add_btn);
            addChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            addChip.setGravity(Gravity.CENTER);
            addChip.setPadding(dp(14), 0, dp(14), 0);
            addChip.setBackgroundResource(R.drawable.phone_pill_bg);
            addChip.setTextColor(getResources().getColor(R.color.phone_link));
            addChip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            addChip.setOnClickListener(v -> openEditSavedListDialog(null));
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
            addParams.setMarginStart(dp(4));
            addParams.setMarginEnd(dp(4));
            mChips.addView(addChip, addParams);

            // Other Home categories (Gaming, News, etc.) after the custom lists
            for (BrowseSection section : sections) {
                if (homeSection != null && section.getId() == homeSection.getId()) {
                    continue;
                }
                TextView chip = new TextView(this);
                chip.setText(section.getTitle());
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(14), 0, dp(14), 0);
                chip.setBackgroundResource(R.drawable.phone_chip_bg);
                boolean selected = mActiveSavedList == null
                        && mCurrentSection != null
                        && mCurrentSection.getId() == section.getId();
                chip.setSelected(selected);
                chip.setTextColor(getResources().getColor(selected ? R.color.phone_bg : R.color.phone_text));
                chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                chip.setOnClickListener(v -> {
                    if (mActiveSavedList != null) {
                        showSavedSearchList(null);
                        focusSection(section);
                    } else if (mCurrentSection == null || mCurrentSection.getId() != section.getId()) {
                        focusSection(section);
                    } else {
                        scrollToTopOrRefresh();
                    }
                });
                chip.setOnLongClickListener(v -> {
                    mPresenter.onSectionLongPressed(section.getId());
                    return true;
                });

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
                params.setMarginStart(dp(4));
                params.setMarginEnd(dp(4));
                mChips.addView(chip, params);

                if (selected) {
                    chip.post(() -> mChipsScroll.smoothScrollTo(Math.max(0, chip.getLeft() - dp(16)), 0));
                }
            }
        } else {
            // Other tabs (You, etc.)
            for (BrowseSection section : sections) {
                TextView chip = new TextView(this);
                chip.setText(section.getTitle());
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(14), 0, dp(14), 0);
                chip.setBackgroundResource(R.drawable.phone_chip_bg);
                boolean selected = mCurrentSection != null && mCurrentSection.getId() == section.getId();
                chip.setSelected(selected);
                chip.setTextColor(getResources().getColor(selected ? R.color.phone_bg : R.color.phone_text));
                chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
                params.setMarginStart(dp(4));
                params.setMarginEnd(dp(4));
                mChips.addView(chip, params);

                if (selected) {
                    chip.post(() -> mChipsScroll.smoothScrollTo(Math.max(0, chip.getLeft() - dp(16)), 0));
                }
            }
        }

        updateSavedListFilterBar();
    }

    /**
     * Shorts tab: open the first Short full screen (swipe for more) and stay on the current tab underneath.
     */
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || isFinishing()
                || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("phone_defaults", MODE_PRIVATE);
        if (!prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply();
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 4712);
        }
    }

    private void playShorts() {
        BrowseSection shorts = sectionById(MediaGroup.TYPE_SHORTS);
        if (shorts == null) {
            return;
        }
        // Come back to where the user was (Home if they were already on Shorts), never to an empty Shorts tab
        BrowseSection back = mCurrentSection;
        if (back == null || back.getId() == MediaGroup.TYPE_SHORTS) {
            back = sectionById(MediaGroup.TYPE_HOME);
        }
        mShortsReturnSection = back;
        mShortsAutoplay = true;
        focusSection(shorts);
    }

    private void scrollToTopOrRefresh() {
        if (mList.canScrollVertically(-1)) {
            mList.smoothScrollToPosition(0);
        } else {
            mPresenter.refresh(false);
        }
    }

    private void updateAccountViews() {
        Account account = MediaServiceManager.instance().getSelectedAccount();
        boolean signedIn = account != null && !account.isEmpty();

        if (mTopAvatar != null) {
            if (signedIn && account.getAvatarImageUrl() != null) {
                mTopAvatar.setImageTintList(null);
                PhoneUi.loadAvatar(mTopAvatar, account.getAvatarImageUrl());
            } else {
                mTopAvatar.setImageResource(R.drawable.ic_phone_account);
                mTopAvatar.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.phone_text)));
            }
        }

        // "You" tab icon becomes the account picture
        View youTab = mTabViews[TAB_YOU];
        if (youTab != null) {
            ImageView icon = (ImageView) ((LinearLayout) youTab).getChildAt(0);
            if (signedIn && account.getAvatarImageUrl() != null) {
                icon.setTag("avatar");
                icon.setImageTintList(null);
                PhoneUi.loadAvatar(icon, account.getAvatarImageUrl());
            } else {
                icon.setTag(null);
                icon.setImageResource(R.drawable.ic_phone_account);
            }
        }
        updateTabs();

        // Account card on the You tab
        ImageView avatar = findViewById(R.id.phone_account_avatar);
        TextView name = findViewById(R.id.phone_account_name);
        TextView email = findViewById(R.id.phone_account_email);
        TextView primary = findViewById(R.id.phone_account_primary);
        TextView secondary = findViewById(R.id.phone_account_secondary);

        if (signedIn) {
            avatar.setImageTintList(null);
            if (account.getAvatarImageUrl() != null) {
                PhoneUi.loadAvatar(avatar, account.getAvatarImageUrl());
            } else {
                avatar.setImageDrawable(new AvatarCache.LetterDrawable(account.getName()));
            }
            name.setText(!TextUtils.isEmpty(account.getName()) ? account.getName() : account.getEmail());
            email.setText(account.getEmail());
            email.setVisibility(TextUtils.isEmpty(account.getEmail()) ? View.GONE : View.VISIBLE);
            primary.setText(R.string.phone_switch_account);
            primary.setOnClickListener(v -> AccountSelectionPresenter.instance(this).show(true));
            secondary.setText(R.string.phone_manage_accounts);
            secondary.setOnClickListener(v -> AccountSettingsPresenter.instance(this).show());
        } else {
            avatar.setImageResource(R.drawable.ic_phone_account);
            avatar.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.phone_text_2)));
            name.setText(R.string.phone_not_signed_in);
            email.setText(R.string.phone_sign_in_prompt);
            email.setVisibility(View.VISIBLE);
            primary.setText(R.string.phone_sign_in);
            primary.setOnClickListener(v -> YTSignInPresenter.instance(this).start());
            secondary.setText(R.string.phone_manage_accounts);
            secondary.setOnClickListener(v -> AccountSettingsPresenter.instance(this).show());
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
            case MediaGroup.TYPE_NOTIFICATIONS:
                return TAB_NOTIFICATIONS;
            case MediaGroup.TYPE_HISTORY:
            case MediaGroup.TYPE_USER_PLAYLISTS:
            case MediaGroup.TYPE_MY_VIDEOS:
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
            if (tab == TAB_YOU) {
                // No library sections enabled: still show the account card
                mCurrentTab = TAB_YOU;
                mCurrentSection = null;
                updateTabs();
                updateChips();
                mAccountHeader.setVisibility(View.VISIBLE);
                mAdapter.setMode(FeedAdapter.MODE_FLAT);
                hideMessage();
            }
            return;
        }

        BrowseSection target = sections.get(0);
        if (tab == TAB_SUBS && sectionById(MediaGroup.TYPE_SUBSCRIPTIONS) != null) {
            // The Subscriptions tab always opens on the feed, like the YouTube app
            focusSection(sectionById(MediaGroup.TYPE_SUBSCRIPTIONS));
            return;
        }
        for (BrowseSection section : sections) {
            if (section.getId() == mTabSectionIds[tab]) {
                target = section;
                break;
            }
        }

        focusSection(target);
    }

    private void focusSection(BrowseSection section) {
        clearSubscriptionsChannel();
        clearSavedSearchList();
        mCurrentSection = section;
        mCurrentTab = tabOf(section);
        mTabSectionIds[mCurrentTab] = section.getId();

        updateTabs();
        updateChips();
        mAccountHeader.setVisibility(mCurrentTab == TAB_YOU ? View.VISIBLE : View.GONE);
        hideMessage();

        BrowseSection load = section;
        if (section.getId() == MediaGroup.TYPE_HOME && !isSignedIn()) {
            BrowseSection fallback = signedOutHomeSource();
            if (fallback != null) {
                load = fallback;
            }
        }
        mLoadedSection = load;

        boolean subsFeed = isSubscriptionsFeed() && isSignedIn();
        mAdapter.setFilter(null);
        mAdapter.setHeaderView(load != section ? mSignInCard : subsFeed ? mSubsHeader.getView() : null);
        if (subsFeed) {
            mSubsHeader.onShown(false);
        }
        mAdapter.setMode(load != section && load.getType() == BrowseSection.TYPE_ROW ? FeedAdapter.MODE_FEED : modeOf(load));
        mList.scrollToPosition(0);

        if (load.getType() == BrowseSection.TYPE_ERROR && load.getData() instanceof ErrorFragmentData) {
            showError((ErrorFragmentData) load.getData());
        }

        mPresenter.onSectionFocused(load.getId());
    }

    // region Subscriptions: one channel

    /**
     * Subscriptions: a channel tapped in the channels row. The feed shows only its videos and Shorts (more load while
     * scrolling, like any feed); null puts the feed of all channels back as it was.
     */
    private void showSubscriptionsChannel(Video channel) {
        com.liskovsoft.sharedutils.rx.RxHelper.disposeActions(mSubsChannelAction);
        View progress = findViewById(R.id.phone_progress_bar);

        if (channel == null) {
            progress.setVisibility(View.GONE);
            mSubsChannelGroup = null;
            if (mSubsAllContent != null) {
                mAdapter.restoreContent(mSubsAllContent);
                mSubsAllContent = null;
                mList.scrollToPosition(0);
            }
            return;
        }

        if (mSubsAllContent == null) {
            mSubsAllContent = mAdapter.saveContent();
        }
        BrowseSection section = mCurrentSection;
        VideoGroup placeholder = VideoGroup.from(section);
        mSubsChannelGroup = placeholder; // until the videos arrive, late results of the full feed stay out
        mAdapter.restoreContent(null);
        progress.setVisibility(View.VISIBLE);

        com.liskovsoft.mediaserviceinterfaces.ContentService service =
                com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().getContentService();
        io.reactivex.Observable<com.liskovsoft.mediaserviceinterfaces.data.MediaGroup> uploads = channel.hasReloadPageKey()
                ? service.getGroupObserve(channel.getReloadPageKey())
                : service.getGroupObserve(channel.mediaItem != null ? channel.mediaItem
                        : com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem.from(channel));

        mSubsChannelAction = com.liskovsoft.sharedutils.rx.RxHelper.execute(uploads, mediaGroup -> {
            progress.setVisibility(View.GONE);
            if (mSubsChannelGroup != placeholder || !isSubscriptionsFeed()) {
                return; // another channel was picked meanwhile, or the user left
            }
            VideoGroup group = VideoGroup.from(mediaGroup, section);
            group.setAction(VideoGroup.ACTION_REPLACE);
            mSubsChannelGroup = group; // scrolling continues this group through the presenter, like the feed
            mAdapter.update(group);
            mList.scrollToPosition(0);
            checkScrollEndLater();
        }, error -> {
            progress.setVisibility(View.GONE);
            if (mSubsChannelGroup == placeholder) {
                android.widget.Toast.makeText(this, R.string.phone_subs_channel_error, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Leaving or reloading the Subscriptions feed: forget the shown channel (the feed is loaded anew) */
    private void clearSubscriptionsChannel() {
        com.liskovsoft.sharedutils.rx.RxHelper.disposeActions(mSubsChannelAction);
        mSubsChannelGroup = null;
        mSubsAllContent = null;
    }

    private void clearSavedSearchList() {
        com.liskovsoft.sharedutils.rx.RxHelper.disposeActions(mSavedListAction, mSavedListContinueAction);
        mActiveSavedList = null;
        mActiveSavedListQuery = null;
        mActiveSavedListOptions = 0;
        mHomeAllContent = null;
    }

    private void showSavedSearchList(PhoneSearchFilters.SavedSearchList list) {
        com.liskovsoft.sharedutils.rx.RxHelper.disposeActions(mSavedListAction, mSavedListContinueAction);
        View progress = findViewById(R.id.phone_progress_bar);

        if (list == null) {
            if (progress != null) {
                progress.setVisibility(View.GONE);
            }
            mActiveSavedList = null;
            mActiveSavedListQuery = null;
            mActiveSavedListOptions = 0;
            updateChips();
            if (mHomeAllContent != null) {
                mAdapter.restoreContent(mHomeAllContent);
                mHomeAllContent = null;
                mList.scrollToPosition(0);
            } else if (mCurrentSection != null) {
                focusSection(mCurrentSection);
            }
            return;
        }

        if (mHomeAllContent == null && mActiveSavedList == null) {
            mHomeAllContent = mAdapter.saveContent();
        }
        mActiveSavedList = list;
        mActiveSavedListQuery = list.query;
        mActiveSavedListOptions = list.options;
        updateChips();
        loadActiveSavedSearchList();
    }

    private void loadActiveSavedSearchList() {
        if (mActiveSavedList == null || TextUtils.isEmpty(mActiveSavedListQuery)) {
            return;
        }
        com.liskovsoft.sharedutils.rx.RxHelper.disposeActions(mSavedListAction, mSavedListContinueAction);
        View progress = findViewById(R.id.phone_progress_bar);
        if (progress != null) {
            progress.setVisibility(View.VISIBLE);
        }
        hideMessage();
        mAdapter.restoreContent(null);
        mAdapter.setMode(FeedAdapter.MODE_FEED);

        final String expectedId = mActiveSavedList.id;
        final BrowseSection section = mCurrentSection;
        com.liskovsoft.mediaserviceinterfaces.ContentService service =
                com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().getContentService();

        mSavedListAction = service.getSearchObserve(mActiveSavedListQuery, mActiveSavedListOptions)
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(
                        mediaGroups -> {
                            if (mActiveSavedList == null || !expectedId.equals(mActiveSavedList.id)) {
                                return;
                            }
                            if (mediaGroups != null) {
                                for (com.liskovsoft.mediaserviceinterfaces.data.MediaGroup mg : mediaGroups) {
                                    VideoGroup vg = VideoGroup.from(mg, section);
                                    mAdapter.update(vg);
                                }
                            }
                            checkScrollEndLater();
                        },
                        error -> {
                            if (progress != null) {
                                progress.setVisibility(View.GONE);
                            }
                        },
                        () -> {
                            if (progress != null) {
                                progress.setVisibility(View.GONE);
                            }
                        }
                );
    }

    private void updateSavedListFilterBar() {
        if (mListFilterScroll == null || mListFilterRow == null) {
            return;
        }
        if (mCurrentTab != TAB_HOME || mActiveSavedList == null) {
            mListFilterScroll.setVisibility(View.GONE);
            return;
        }
        mListFilterScroll.setVisibility(View.VISIBLE);
        mListFilterRow.removeAllViews();

        List<PhoneSearchFilters.ActiveChip> active = PhoneSearchFilters.getActiveChips(this, mActiveSavedListOptions);
        int dp8 = dp(8);
        int dp36 = dp(36);

        String filterLabel = active.isEmpty()
                ? getString(R.string.phone_search_filter_btn)
                : getString(R.string.phone_search_filter_btn) + " (" + active.size() + ")";
        TextView filterChip = PhoneSearchFilters.createPillChip(this, filterLabel, !active.isEmpty(), false);
        filterChip.setOnClickListener(v -> PhoneSearchFilters.showFilterDialog(this, mActiveSavedListOptions, newOpts -> {
            mActiveSavedListOptions = newOpts;
            updateSavedListFilterBar();
            loadActiveSavedSearchList();
        }));
        LinearLayout.LayoutParams filterLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp36);
        filterLp.setMarginEnd(dp8);
        mListFilterRow.addView(filterChip, filterLp);

        for (PhoneSearchFilters.ActiveChip item : active) {
            TextView chip = PhoneSearchFilters.createPillChip(this, item.label + " ×", true, false);
            chip.setOnClickListener(v -> {
                mActiveSavedListOptions &= ~item.flag;
                updateSavedListFilterBar();
                loadActiveSavedSearchList();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp36);
            lp.setMarginEnd(dp8);
            mListFilterRow.addView(chip, lp);
        }

        boolean isModifiedTemp = mActiveSavedListOptions != mActiveSavedList.options
                || !TextUtils.equals(mActiveSavedListQuery, mActiveSavedList.query);
        if (isModifiedTemp) {
            TextView savePermChip = PhoneSearchFilters.createPillChip(
                    this, getString(R.string.phone_list_save_perm_btn), false, true);
            savePermChip.setOnClickListener(v -> {
                mActiveSavedList = PhoneSearchFilters.saveOrUpdateList(
                        this, mActiveSavedList.id, mActiveSavedList.name, mActiveSavedListQuery, mActiveSavedListOptions);
                mLastListsVersion = PhoneSearchFilters.listsVersion();
                com.liskovsoft.sharedutils.helpers.MessageHelpers.showMessage(this, R.string.phone_list_updated_toast);
                updateChips();
            });
            LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp36);
            saveLp.setMarginEnd(dp8);
            mListFilterRow.addView(savePermChip, saveLp);

            TextView resetChip = PhoneSearchFilters.createPillChip(
                    this, getString(R.string.phone_list_reset_temp_btn), false, false);
            resetChip.setOnClickListener(v -> {
                mActiveSavedListQuery = mActiveSavedList.query;
                mActiveSavedListOptions = mActiveSavedList.options;
                updateSavedListFilterBar();
                loadActiveSavedSearchList();
            });
            LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp36);
            resetLp.setMarginEnd(dp8);
            mListFilterRow.addView(resetChip, resetLp);
        }

        TextView editChip = PhoneSearchFilters.createPillChip(
                this, getString(R.string.phone_list_edit_btn), false, false);
        editChip.setOnClickListener(v -> openEditSavedListDialog(mActiveSavedList));
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp36);
        mListFilterRow.addView(editChip, editLp);
    }

    private void openEditSavedListDialog(PhoneSearchFilters.SavedSearchList target) {
        String initialQuery = target != null
                ? (mActiveSavedList != null && target.id.equals(mActiveSavedList.id) ? mActiveSavedListQuery : target.query)
                : "";
        int initialOpts = target != null
                ? (mActiveSavedList != null && target.id.equals(mActiveSavedList.id) ? mActiveSavedListOptions : target.options)
                : 0;
        PhoneSearchFilters.showSaveOrEditListDialog(this, target, initialQuery, initialOpts, new PhoneSearchFilters.ListDialogCallback() {
            @Override
            public void onSavedPermanently(PhoneSearchFilters.SavedSearchList saved) {
                mLastListsVersion = PhoneSearchFilters.listsVersion();
                showSavedSearchList(saved);
            }

            @Override
            public void onAppliedTemporarily(String query, int options) {
                if (target != null) {
                    if (mActiveSavedList == null || !target.id.equals(mActiveSavedList.id)) {
                        if (mHomeAllContent == null && mActiveSavedList == null) {
                            mHomeAllContent = mAdapter.saveContent();
                        }
                        mActiveSavedList = target;
                    }
                    mActiveSavedListQuery = query;
                    mActiveSavedListOptions = options;
                    updateChips();
                    loadActiveSavedSearchList();
                } else {
                    SearchPresenter.instance(PhoneBrowseActivity.this).startSearch(query, options);
                }
            }

            @Override
            public void onDeleted(PhoneSearchFilters.SavedSearchList deleted) {
                mLastListsVersion = PhoneSearchFilters.listsVersion();
                if (mActiveSavedList != null && deleted != null && deleted.id.equals(mActiveSavedList.id)) {
                    showSavedSearchList(null);
                } else {
                    updateChips();
                }
            }
        });
    }

    // endregion

    private boolean isSection(int id) {
        return mCurrentSection != null && mCurrentSection.getId() == id;
    }

    private boolean isSubscriptionsFeed() {
        return isSection(MediaGroup.TYPE_SUBSCRIPTIONS);
    }

    private static int modeOf(BrowseSection section) {
        switch (section.getType()) {
            case BrowseSection.TYPE_ROW:
                return section.getId() == MediaGroup.TYPE_HOME ? FeedAdapter.MODE_FEED : FeedAdapter.MODE_SHELVES;
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
        BrowseSection target = mSections.get(Math.min(index, mSections.size() - 1));

        if (mIsInitialSelection) {
            // Phone app always starts on Home (SmartTube boots signed-out users into Music)
            mIsInitialSelection = false;
            BrowseSection home = sectionById(MediaGroup.TYPE_HOME);
            if (home != null) {
                focusSection(home);
                return;
            }
        }

        // The presenter restores the section it last loaded. When Home shows a stand-in feed (signed out),
        // that is the stand-in: keep Home selected instead of jumping to it.
        if (mLoadedSection != null && mCurrentSection != null && target.getId() == mLoadedSection.getId()
                && mLoadedSection != mCurrentSection) {
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
        if (section != null && section.getId() == MediaGroup.TYPE_SHORTS && !mShortsAutoplay && PhoneShortsQueue.isActive()) {
            PhoneShortsQueue.append(group); // next batch for the Shorts player, even though Home is behind it
        }
        BrowseSection expected = mLoadedSection != null ? mLoadedSection : mCurrentSection;
        if (section != null && expected != null && section.getId() != expected.getId()) {
            return; // late result of a section the user already left
        }
        if (mSubsChannelGroup != null && group.getId() != mSubsChannelGroup.getId()) {
            return; // the feed of all channels, while one channel is shown (it comes back when the channel is unselected)
        }
        if (mActiveSavedList != null && mCurrentTab == TAB_HOME) {
            return; // a saved search list is shown on Home instead of the default Home feed
        }
        hideMessage();

        if (mShortsAutoplay && group.getVideos() != null && !group.getVideos().isEmpty()
                && mCurrentSection != null && mCurrentSection.getId() == MediaGroup.TYPE_SHORTS) {
            mShortsAutoplay = false;
            PhoneShortsQueue.set(group);
            Video first = group.getVideos().get(0);
            PhonePlaybackActivity.releaseDetachedPlayer();
            mPresenter.onVideoItemSelected(first);
            mPresenter.onVideoItemClicked(first);
            Video last = group.getVideos().get(group.getVideos().size() - 1);
            boolean fetchMore = group.getVideos().size() < 5; // only the first Short came in (so it starts fast)
            BrowseSection back = mShortsReturnSection;
            if (back != null && back.getId() != MediaGroup.TYPE_SHORTS) {
                mList.postDelayed(() -> focusSection(back), 400);
            }
            if (fetchMore) {
                // After Home is back in place (switching sections cancels pending loads): the next Shorts
                mList.postDelayed(() -> mPresenter.onScrollEnd(last), 900);
            }
            return;
        }

        mAdapter.update(group);
        checkScrollEndLater();
        if (group.getVideos() != null && !group.getVideos().isEmpty()) {
            PhoneWarmup.startOnce(); // the first feed is on screen: get YouTube's session ready for the first video
        }
    }

    @Override
    public void updateSection(SettingsGroup group) {
        if (group == null) {
            return;
        }
        hideMessage();
        mSettingsGroup = group;
        // Phone only setting on top of SmartTube's settings
        java.util.List<com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem> items = new ArrayList<>();
        int appearance = PhoneTheme.get(this);
        items.add(new com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem(
                getString(R.string.phone_quick_actions_title),
                () -> PhoneQuickActions.openSettings(this, () -> {
                    if (mAdapter != null) {
                        mAdapter.refreshQuickActions();
                    }
                }),
                R.drawable.ic_phone_not_interested));
        items.add(new com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem(
                getString(PhoneTheme.titleRes(appearance)), () -> {
                    PhoneTheme.set(this, PhoneTheme.next(appearance));
                    recreate();
                }, R.drawable.ic_phone_brightness));
        items.add(new com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem(
                PhoneNotifications.settingsTitle(this), () -> PhoneNotifications.open(this), R.drawable.ic_phone_bell));
        items.add(new com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem(
                PhoneUpdater.settingsTitle(this), () -> PhoneUpdater.open(this), R.drawable.ic_phone_update));
        if (group.getItems() != null) {
            items.addAll(group.getItems());
        }
        mAdapter.setSettings(items);
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
        if (!show) {
            stopRefreshing();
        }
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
        updateAccountViews();
    }

    // endregion

    // region FeedAdapter.Listener

    @Override
    public void onVideoClicked(Video video) {
        PhoneUi.prepareExpandLaunch(video);
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
        if (mActiveSavedList != null && mCurrentTab == TAB_HOME) {
            return com.liskovsoft.sharedutils.rx.RxHelper.isAnyActionRunning(mSavedListAction, mSavedListContinueAction);
        }
        return mPresenter.hasPendingActions();
    }

    @Override
    protected void onListScrollEnd(Video lastVideo) {
        if (mActiveSavedList != null && mCurrentTab == TAB_HOME) {
            continueSavedList(lastVideo);
            return;
        }
        mPresenter.onScrollEnd(lastVideo);
    }

    private void continueSavedList(Video lastVideo) {
        if (lastVideo == null || lastVideo.getGroup() == null || lastVideo.getGroup().getMediaGroup() == null) {
            return;
        }
        if (com.liskovsoft.sharedutils.rx.RxHelper.isAnyActionRunning(mSavedListAction, mSavedListContinueAction)) {
            return;
        }
        final String expectedId = mActiveSavedList != null ? mActiveSavedList.id : null;
        if (expectedId == null) {
            return;
        }
        final VideoGroup prevGroup = lastVideo.getGroup();
        View progress = findViewById(R.id.phone_progress_bar);
        if (progress != null) {
            progress.setVisibility(View.VISIBLE);
        }
        com.liskovsoft.mediaserviceinterfaces.ContentService service =
                com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().getContentService();
        mSavedListContinueAction = service.continueGroupObserve(prevGroup.getMediaGroup())
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(
                        nextMediaGroup -> {
                            if (mActiveSavedList == null || !expectedId.equals(mActiveSavedList.id)) {
                                return;
                            }
                            VideoGroup nextGroup = VideoGroup.from(prevGroup, nextMediaGroup);
                            mAdapter.update(nextGroup);
                            checkScrollEndLater();
                        },
                        error -> {
                            if (progress != null) {
                                progress.setVisibility(View.GONE);
                            }
                        },
                        () -> {
                            if (progress != null) {
                                progress.setVisibility(View.GONE);
                            }
                        }
                );
    }

    // endregion
}
