package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * All of SmartTube's menus (video menu, quality, speed, accounts, settings...) as a phone bottom sheet.
 * Same OptionCategory/OptionItem model and click semantics as the TV dialog (AppPreferenceManager).
 */
public class PhoneDialogActivity extends MotherActivity implements AppDialogView {
    private static final int ROW_CATEGORY = 0;
    private static final int ROW_BUTTON = 1;
    private static final int ROW_SWITCH = 2;
    private static final int ROW_RADIO = 3;
    private static final int ROW_CHECK = 4;
    private static final int ROW_STRING = 5;
    private static final int ROW_TEXT = 6;

    private static final class Row {
        final int kind;
        final CharSequence title;
        final CharSequence description;
        final OptionItem item;
        final OptionCategory category;
        boolean selected;

        Row(int kind, CharSequence title, CharSequence description, OptionItem item, OptionCategory category, boolean selected) {
            this.kind = kind;
            this.title = title;
            this.description = description;
            this.item = item;
            this.category = category;
            this.selected = selected;
        }
    }

    private static final class Page {
        final CharSequence title;
        final List<Row> rows;

        Page(CharSequence title, List<Row> rows) {
            this.title = title;
            this.rows = rows;
        }
    }

    private AppDialogPresenter mPresenter;
    private final List<Page> mStack = new ArrayList<>();
    private final RowsAdapter mAdapter = new RowsAdapter();
    private TextView mTitle;
    private View mBack;
    private boolean mIsTransparent;
    private boolean mIsOverlay;
    private boolean mIsPaused;
    private boolean mIsDelegated;
    private boolean mIsFinishing;
    private int mId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PhoneUi.applyPhoneDensity(this);
        setContentView(R.layout.phone_dialog);

        mTitle = findViewById(R.id.phone_sheet_title);
        mBack = findViewById(R.id.phone_sheet_back);
        RecyclerView list = findViewById(R.id.phone_sheet_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setItemAnimator(null);
        list.setAdapter(mAdapter);

        findViewById(R.id.phone_dialog_root).setOnClickListener(v -> finish());
        mBack.setOnClickListener(v -> goBack());

        View sheet = findViewById(R.id.phone_sheet);
        final int sheetBottom = sheet.getPaddingBottom();
        findViewById(R.id.phone_dialog_root).setOnApplyWindowInsetsListener((v, insets) -> {
            sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(), sheet.getPaddingRight(),
                    sheetBottom + insets.getSystemWindowInsetBottom());
            return insets;
        });

        mPresenter = AppDialogPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void initTheme() {
        // Keep the translucent sheet theme from the manifest (no TV settings theme)
    }

    @Override
    protected void onResume() {
        super.onResume();
        PhoneUi.applyPhoneDensity(this);
        mIsPaused = false;
        showSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsPaused = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!mIsDelegated && mPresenter.getView() == this) {
            mPresenter.onViewDestroyed();
        }
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    @SuppressWarnings("deprecation")
    private void showSystemBars() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(getResources().getColor(R.color.phone_sheet));
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    // region AppDialogView

    @Override
    public void show(List<OptionCategory> categories, CharSequence title, boolean isExpandable, boolean isTransparent, boolean isOverlay, int id) {
        if (isFinishing()) {
            return;
        }

        mIsTransparent = mStack.isEmpty() ? isTransparent : mIsTransparent;
        mIsOverlay = isOverlay;
        mId = id;

        if (categories != null) {
            for (OptionCategory category : categories) {
                if (category.type == OptionCategory.TYPE_CHAT || category.type == OptionCategory.TYPE_COMMENTS) {
                    delegateToTvDialog();
                    return;
                }
            }
        }

        Page page;
        if (isExpandable && categories != null && categories.size() == 1 && categories.get(0).options != null) {
            OptionCategory category = categories.get(0);
            page = categoryPage(category, category.title != null ? category.title : title);
        } else {
            page = rootPage(categories, title);
        }

        mStack.add(page);
        render();
    }

    @Override
    public void finish() {
        if (mIsFinishing) {
            return;
        }
        mIsFinishing = true;
        if (!mIsDelegated) {
            mPresenter.onFinish();
        }
        finishReally();
    }

    @Override
    public void goBack() {
        if (canGoBack()) {
            mStack.remove(mStack.size() - 1);
            render();
        } else {
            finish();
        }
    }

    @Override
    public void clearBackstack() {
        mStack.clear();
    }

    @Override
    public boolean canGoBack() {
        return mStack.size() > 1;
    }

    @Override
    public boolean isShown() {
        return !isFinishing() && !mIsPaused;
    }

    @Override
    public boolean isTransparent() {
        return mIsTransparent;
    }

    @Override
    public boolean isOverlay() {
        return mIsOverlay;
    }

    @Override
    public boolean isPaused() {
        return mIsPaused;
    }

    @Override
    public int getViewId() {
        return mId;
    }

    // endregion

    /**
     * Comments and live chat need the streaming TV panel for now.
     */
    private void delegateToTvDialog() {
        mIsDelegated = true;
        Intent intent = new Intent(this, AppDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finishReally();
    }

    // region Pages

    private Page rootPage(List<OptionCategory> categories, CharSequence title) {
        List<Row> rows = new ArrayList<>();
        if (categories != null) {
            for (OptionCategory category : categories) {
                if (category.options == null || category.options.isEmpty()) {
                    continue;
                }
                OptionItem first = category.options.get(0);
                switch (category.type) {
                    case OptionCategory.TYPE_SINGLE_BUTTON:
                        rows.add(new Row(ROW_BUTTON, first.getTitle(), first.getDescription(), first, category, false));
                        break;
                    case OptionCategory.TYPE_SINGLE_SWITCH:
                        rows.add(new Row(ROW_SWITCH, first.getTitle(), first.getDescription(), first, category, first.isSelected()));
                        break;
                    default:
                        rows.add(new Row(ROW_CATEGORY, category.title, selectedSummary(category), null, category, false));
                        break;
                }
            }
        }
        return new Page(title, rows);
    }

    private static CharSequence selectedSummary(OptionCategory category) {
        if (category.type != OptionCategory.TYPE_RADIO_LIST || category.options == null) {
            return null;
        }
        for (OptionItem item : category.options) {
            if (item.isSelected()) {
                return item.getTitle();
            }
        }
        return null;
    }

    private Page categoryPage(OptionCategory category, CharSequence title) {
        List<Row> rows = new ArrayList<>();
        for (OptionItem item : category.options) {
            switch (category.type) {
                case OptionCategory.TYPE_RADIO_LIST:
                    rows.add(new Row(ROW_RADIO, item.getTitle(), item.getDescription(), item, category, item.isSelected()));
                    break;
                case OptionCategory.TYPE_CHECKBOX_LIST:
                    rows.add(new Row(ROW_CHECK, item.getTitle(), item.getDescription(), item, category, item.isSelected()));
                    break;
                case OptionCategory.TYPE_LONG_TEXT:
                    rows.add(new Row(ROW_TEXT, item.getTitle(), null, item, category, false));
                    break;
                case OptionCategory.TYPE_SINGLE_SWITCH:
                    rows.add(new Row(ROW_SWITCH, item.getTitle(), item.getDescription(), item, category, item.isSelected()));
                    break;
                case OptionCategory.TYPE_SINGLE_BUTTON:
                    rows.add(new Row(ROW_BUTTON, item.getTitle(), item.getDescription(), item, category, false));
                    break;
                default:
                    rows.add(new Row(ROW_STRING, item.getTitle(), item.getDescription(), item, category, item.isSelected()));
                    break;
            }
        }
        return new Page(title, rows);
    }

    private void render() {
        if (mStack.isEmpty()) {
            return;
        }
        Page page = mStack.get(mStack.size() - 1);
        mTitle.setText(page.title);
        mTitle.setVisibility(TextUtils.isEmpty(page.title) ? View.INVISIBLE : View.VISIBLE);
        mBack.setVisibility(mStack.size() > 1 ? View.VISIBLE : View.GONE);
        mAdapter.setRows(page.rows);
    }

    private void onRowClicked(Row row) {
        int depth = mStack.size();

        switch (row.kind) {
            case ROW_CATEGORY:
                mStack.add(categoryPage(row.category, row.category.title));
                render();
                return;
            case ROW_BUTTON:
            case ROW_STRING:
                row.item.onSelect(true);
                return;
            case ROW_SWITCH:
                row.selected = !row.selected;
                row.item.onSelect(row.selected);
                mAdapter.notifyDataSetChanged();
                return;
            case ROW_RADIO: {
                Page page = mStack.get(mStack.size() - 1);
                for (Row other : page.rows) {
                    other.selected = other == row;
                }
                mAdapter.notifyDataSetChanged();
                row.item.onSelect(true);
                // Like the TV list dialog: a choice closes this list (unless the app already opened something else)
                if (!isFinishing() && mStack.size() == depth) {
                    goBack();
                }
                return;
            }
            case ROW_CHECK: {
                boolean selected = !row.selected;
                if (selected) {
                    OptionItem[] required = row.item.getRequired();
                    if (required != null) {
                        for (OptionItem item : required) {
                            if (!item.isSelected()) {
                                MessageHelpers.showMessage(this, getString(R.string.require_checked, item.getTitle()));
                            }
                        }
                    }
                    OptionItem[] radio = row.item.getRadio();
                    if (radio != null) {
                        Page page = mStack.get(mStack.size() - 1);
                        for (OptionItem item : radio) {
                            item.onSelect(false);
                            for (Row other : page.rows) {
                                if (other.item == item) {
                                    other.selected = false;
                                }
                            }
                        }
                    }
                }
                row.selected = selected;
                row.item.onSelect(selected);
                mAdapter.notifyDataSetChanged();
                return;
            }
            default:
                // Long text: nothing to do
        }
    }

    // endregion

    private final class RowsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Row> mRows = new ArrayList<>();

        void setRows(List<Row> rows) {
            mRows.clear();
            mRows.addAll(rows);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mRows.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.phone_dialog_row, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = mRows.get(position);
            View view = holder.itemView;
            TextView title = view.findViewById(R.id.phone_title);
            TextView meta = view.findViewById(R.id.phone_meta);
            View check = view.findViewById(R.id.phone_row_check);
            Switch toggle = view.findViewById(R.id.phone_row_switch);
            View chevron = view.findViewById(R.id.phone_row_chevron);

            title.setText(row.title);
            title.setTextSize(row.kind == ROW_TEXT ? 15 : 16);
            title.setMaxLines(row.kind == ROW_TEXT ? Integer.MAX_VALUE : 3);
            meta.setText(row.description);
            meta.setVisibility(TextUtils.isEmpty(row.description) ? View.GONE : View.VISIBLE);

            boolean isList = row.kind == ROW_RADIO || row.kind == ROW_CHECK;
            check.setVisibility(isList && row.selected ? View.VISIBLE : isList ? View.INVISIBLE : View.GONE);
            toggle.setVisibility(row.kind == ROW_SWITCH ? View.VISIBLE : View.GONE);
            toggle.setChecked(row.selected);
            chevron.setVisibility(row.kind == ROW_CATEGORY ? View.VISIBLE : View.GONE);

            view.setOnClickListener(row.kind == ROW_TEXT ? null : v -> onRowClicked(row));
            view.setClickable(row.kind != ROW_TEXT);
        }
    }
}
