package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Classic desktop YouTube search filters + saved custom search lists ("Eigene Listen") for the Home screen.
 */
public final class PhoneSearchFilters {
    private static final String PREFS_NAME = "phone_saved_search_lists";
    private static final String KEY_LISTS = "lists_json";

    // Extra filter flags beyond SearchOptions (encoded by patched SearchFilterHelper)
    public static final int TYPE_SHORTS = 1 << 19;
    public static final int FEATURE_HD = 1 << 20;
    public static final int FEATURE_SUBTITLES = 1 << 21;
    public static final int FEATURE_CREATIVE_COMMONS = 1 << 22;
    public static final int FEATURE_360 = 1 << 23;
    public static final int FEATURE_VR180 = 1 << 24;
    public static final int FEATURE_3D = 1 << 25;
    public static final int FEATURE_LOCATION = 1 << 26;
    public static final int FEATURE_PURCHASED = 1 << 27;

    public static final int MASK_UPLOAD_DATE =
            SearchOptions.UPLOAD_DATE_LAST_HOUR
                    | SearchOptions.UPLOAD_DATE_TODAY
                    | SearchOptions.UPLOAD_DATE_THIS_WEEK
                    | SearchOptions.UPLOAD_DATE_THIS_MONTH
                    | SearchOptions.UPLOAD_DATE_THIS_YEAR;

    public static final int MASK_DURATION =
            SearchOptions.DURATION_UNDER_4
                    | SearchOptions.DURATION_BETWEEN_4_20
                    | SearchOptions.DURATION_OVER_20;

    public static final int MASK_TYPE =
            SearchOptions.TYPE_VIDEO
                    | TYPE_SHORTS
                    | SearchOptions.TYPE_CHANNEL
                    | SearchOptions.TYPE_PLAYLIST
                    | SearchOptions.TYPE_MOVIE;

    public static final int MASK_FEATURES =
            SearchOptions.FEATURE_LIVE
                    | SearchOptions.FEATURE_4K
                    | FEATURE_HD
                    | FEATURE_SUBTITLES
                    | FEATURE_CREATIVE_COMMONS
                    | FEATURE_360
                    | FEATURE_VR180
                    | FEATURE_3D
                    | SearchOptions.FEATURE_HDR
                    | FEATURE_LOCATION
                    | FEATURE_PURCHASED;

    public static final int MASK_SORT =
            SearchOptions.SORT_BY_RELEVANCE
                    | SearchOptions.SORT_BY_UPLOAD_DATE
                    | SearchOptions.SORT_BY_VIEW_COUNT
                    | SearchOptions.SORT_BY_RATING;

    private static volatile int sListsVersion = 1;

    private PhoneSearchFilters() {
    }

    public interface IntCallback {
        void onOptionsChanged(int newOptions);
    }

    public interface ListDialogCallback {
        void onSavedPermanently(SavedSearchList list);
        void onAppliedTemporarily(String query, int options);
        void onDeleted(SavedSearchList list);
    }

    public static final class SavedSearchList {
        public final String id;
        public String name;
        public String query;
        public int options;

        public SavedSearchList(String id, String name, String query, int options) {
            this.id = id != null ? id : UUID.randomUUID().toString();
            this.name = name != null ? name : "";
            this.query = query != null ? query : "";
            this.options = options;
        }

        public String displayTitle() {
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
            return query;
        }
    }

    public static int listsVersion() {
        return sListsVersion;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static List<SavedSearchList> getSavedLists(Context context) {
        List<SavedSearchList> result = new ArrayList<>();
        String raw = prefs(context).getString(KEY_LISTS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String query = obj.optString("query", "").trim();
                if (query.isEmpty()) {
                    continue;
                }
                String id = obj.optString("id", UUID.randomUUID().toString());
                String name = obj.optString("name", query).trim();
                int options = obj.optInt("options", 0);
                result.add(new SavedSearchList(id, name, query, options));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void saveListsInternal(Context context, List<SavedSearchList> lists) {
        JSONArray arr = new JSONArray();
        try {
            for (SavedSearchList item : lists) {
                if (item == null || TextUtils.isEmpty(item.query)) {
                    continue;
                }
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("name", TextUtils.isEmpty(item.name) ? item.query : item.name);
                obj.put("query", item.query);
                obj.put("options", item.options);
                arr.put(obj);
            }
        } catch (Exception ignored) {
        }
        prefs(context).edit().putString(KEY_LISTS, arr.toString()).apply();
        sListsVersion++;
    }

    public static SavedSearchList saveOrUpdateList(Context context, @Nullable String id, String name, String query, int options) {
        String cleanQuery = query != null ? query.trim() : "";
        String cleanName = name != null && !name.trim().isEmpty() ? name.trim() : cleanQuery;
        List<SavedSearchList> lists = getSavedLists(context);
        SavedSearchList target = null;
        if (!TextUtils.isEmpty(id)) {
            for (SavedSearchList item : lists) {
                if (id.equals(item.id)) {
                    item.name = cleanName;
                    item.query = cleanQuery;
                    item.options = options;
                    target = item;
                    break;
                }
            }
        }
        if (target == null) {
            target = new SavedSearchList(id, cleanName, cleanQuery, options);
            lists.add(target);
        }
        saveListsInternal(context, lists);
        return target;
    }

    public static void deleteList(Context context, String id) {
        if (TextUtils.isEmpty(id)) {
            return;
        }
        List<SavedSearchList> lists = getSavedLists(context);
        for (int i = lists.size() - 1; i >= 0; i--) {
            if (id.equals(lists.get(i).id)) {
                lists.remove(i);
            }
        }
        saveListsInternal(context, lists);
    }

    @Nullable
    public static SavedSearchList findById(Context context, @Nullable String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        for (SavedSearchList item : getSavedLists(context)) {
            if (id.equals(item.id)) {
                return item;
            }
        }
        return null;
    }

    public static final class ActiveChip {
        public final int flag;
        public final String label;

        public ActiveChip(int flag, String label) {
            this.flag = flag;
            this.label = label;
        }
    }

    public static List<ActiveChip> getActiveChips(Context context, int options) {
        List<ActiveChip> chips = new ArrayList<>();
        if (options == 0) {
            return chips;
        }
        int[][] allPairs = {
                {SearchOptions.TYPE_VIDEO, R.string.phone_filter_type_videos},
                {TYPE_SHORTS, R.string.phone_filter_type_shorts},
                {SearchOptions.TYPE_CHANNEL, R.string.phone_filter_type_channels},
                {SearchOptions.TYPE_PLAYLIST, R.string.phone_filter_type_playlists},
                {SearchOptions.TYPE_MOVIE, R.string.phone_filter_type_movies},
                {SearchOptions.DURATION_UNDER_4, R.string.phone_filter_dur_under_3},
                {SearchOptions.DURATION_BETWEEN_4_20, R.string.phone_filter_dur_3_20},
                {SearchOptions.DURATION_OVER_20, R.string.phone_filter_dur_over_20},
                {SearchOptions.UPLOAD_DATE_LAST_HOUR, R.string.phone_filter_date_hour},
                {SearchOptions.UPLOAD_DATE_TODAY, R.string.phone_filter_date_today},
                {SearchOptions.UPLOAD_DATE_THIS_WEEK, R.string.phone_filter_date_week},
                {SearchOptions.UPLOAD_DATE_THIS_MONTH, R.string.phone_filter_date_month},
                {SearchOptions.UPLOAD_DATE_THIS_YEAR, R.string.phone_filter_date_year},
                {SearchOptions.FEATURE_LIVE, R.string.phone_filter_feat_live},
                {SearchOptions.FEATURE_4K, R.string.phone_filter_feat_4k},
                {FEATURE_HD, R.string.phone_filter_feat_hd},
                {FEATURE_SUBTITLES, R.string.phone_filter_feat_subtitles},
                {FEATURE_CREATIVE_COMMONS, R.string.phone_filter_feat_cc},
                {FEATURE_360, R.string.phone_filter_feat_360},
                {FEATURE_VR180, R.string.phone_filter_feat_vr180},
                {FEATURE_3D, R.string.phone_filter_feat_3d},
                {SearchOptions.FEATURE_HDR, R.string.phone_filter_feat_hdr},
                {FEATURE_LOCATION, R.string.phone_filter_feat_location},
                {FEATURE_PURCHASED, R.string.phone_filter_feat_purchased},
                {SearchOptions.SORT_BY_VIEW_COUNT, R.string.phone_filter_sort_popularity},
                {SearchOptions.SORT_BY_UPLOAD_DATE, R.string.phone_filter_sort_date},
                {SearchOptions.SORT_BY_RATING, R.string.phone_filter_sort_rating}
        };
        for (int[] pair : allPairs) {
            if ((options & pair[0]) == pair[0]) {
                chips.add(new ActiveChip(pair[0], context.getString(pair[1])));
            }
        }
        return chips;
    }

    public static String formatSummary(Context context, int options) {
        List<ActiveChip> chips = getActiveChips(context, options);
        if (chips.isEmpty()) {
            return context.getString(R.string.phone_filter_any);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chips.size(); i++) {
            if (i > 0) {
                sb.append(" · ");
            }
            sb.append(chips.get(i).label);
        }
        return sb.toString();
    }

    /**
     * Opens the classic 5-category YouTube search filter sheet (TYP, DAUER, HOCHLADEDATUM, EIGENSCHAFTEN, PRIORISIEREN).
     */
    public static void showFilterDialog(Context context, int initialOptions, IntCallback onChanged) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        final int[] state = {initialOptions};

        if (initialOptions != 0) {
            dialog.appendSingleButton(UiOptionItem.from(
                    context.getString(R.string.phone_search_filter_reset),
                    item -> {
                        state[0] = 0;
                        if (onChanged != null) {
                            onChanged.onOptionsChanged(0);
                        }
                        dialog.closeDialog();
                    }
            ));
        }

        // 1. TYP
        List<OptionItem> typeOpts = new ArrayList<>();
        int currentType = state[0] & MASK_TYPE;
        int[][] typePairs = {
                {0, R.string.phone_filter_any},
                {SearchOptions.TYPE_VIDEO, R.string.phone_filter_type_videos},
                {TYPE_SHORTS, R.string.phone_filter_type_shorts},
                {SearchOptions.TYPE_CHANNEL, R.string.phone_filter_type_channels},
                {SearchOptions.TYPE_PLAYLIST, R.string.phone_filter_type_playlists},
                {SearchOptions.TYPE_MOVIE, R.string.phone_filter_type_movies}
        };
        for (int[] pair : typePairs) {
            typeOpts.add(UiOptionItem.from(
                    context.getString(pair[1]),
                    item -> {
                        state[0] = (state[0] & ~MASK_TYPE) | pair[0];
                        if (onChanged != null) {
                            onChanged.onOptionsChanged(state[0]);
                        }
                    },
                    currentType == pair[0]
            ));
        }
        dialog.appendRadioCategory(context.getString(R.string.phone_filter_cat_type), typeOpts);

        // 2. DAUER
        List<OptionItem> durOpts = new ArrayList<>();
        int currentDur = state[0] & MASK_DURATION;
        int[][] durPairs = {
                {0, R.string.phone_filter_any},
                {SearchOptions.DURATION_UNDER_4, R.string.phone_filter_dur_under_3},
                {SearchOptions.DURATION_BETWEEN_4_20, R.string.phone_filter_dur_3_20},
                {SearchOptions.DURATION_OVER_20, R.string.phone_filter_dur_over_20}
        };
        for (int[] pair : durPairs) {
            durOpts.add(UiOptionItem.from(
                    context.getString(pair[1]),
                    item -> {
                        state[0] = (state[0] & ~MASK_DURATION) | pair[0];
                        if (onChanged != null) {
                            onChanged.onOptionsChanged(state[0]);
                        }
                    },
                    currentDur == pair[0]
            ));
        }
        dialog.appendRadioCategory(context.getString(R.string.phone_filter_cat_duration), durOpts);

        // 3. HOCHLADEDATUM
        List<OptionItem> dateOpts = new ArrayList<>();
        int currentDate = state[0] & MASK_UPLOAD_DATE;
        int[][] datePairs = {
                {0, R.string.phone_filter_any},
                {SearchOptions.UPLOAD_DATE_LAST_HOUR, R.string.phone_filter_date_hour},
                {SearchOptions.UPLOAD_DATE_TODAY, R.string.phone_filter_date_today},
                {SearchOptions.UPLOAD_DATE_THIS_WEEK, R.string.phone_filter_date_week},
                {SearchOptions.UPLOAD_DATE_THIS_MONTH, R.string.phone_filter_date_month},
                {SearchOptions.UPLOAD_DATE_THIS_YEAR, R.string.phone_filter_date_year}
        };
        for (int[] pair : datePairs) {
            dateOpts.add(UiOptionItem.from(
                    context.getString(pair[1]),
                    item -> {
                        state[0] = (state[0] & ~MASK_UPLOAD_DATE) | pair[0];
                        if (onChanged != null) {
                            onChanged.onOptionsChanged(state[0]);
                        }
                    },
                    currentDate == pair[0]
            ));
        }
        dialog.appendRadioCategory(context.getString(R.string.phone_filter_cat_upload_date), dateOpts);

        // 4. EIGENSCHAFTEN (Multi-select)
        List<OptionItem> featOpts = new ArrayList<>();
        int[][] featPairs = {
                {SearchOptions.FEATURE_LIVE, R.string.phone_filter_feat_live},
                {SearchOptions.FEATURE_4K, R.string.phone_filter_feat_4k},
                {FEATURE_HD, R.string.phone_filter_feat_hd},
                {FEATURE_SUBTITLES, R.string.phone_filter_feat_subtitles},
                {FEATURE_CREATIVE_COMMONS, R.string.phone_filter_feat_cc},
                {FEATURE_360, R.string.phone_filter_feat_360},
                {FEATURE_VR180, R.string.phone_filter_feat_vr180},
                {FEATURE_3D, R.string.phone_filter_feat_3d},
                {SearchOptions.FEATURE_HDR, R.string.phone_filter_feat_hdr},
                {FEATURE_LOCATION, R.string.phone_filter_feat_location},
                {FEATURE_PURCHASED, R.string.phone_filter_feat_purchased}
        };
        for (int[] pair : featPairs) {
            featOpts.add(UiOptionItem.from(
                    context.getString(pair[1]),
                    item -> {
                        if (item.isSelected()) {
                            state[0] |= pair[0];
                        } else {
                            state[0] &= ~pair[0];
                        }
                        if (onChanged != null) {
                            onChanged.onOptionsChanged(state[0]);
                        }
                    },
                    (state[0] & pair[0]) == pair[0]
            ));
        }
        dialog.appendCheckedCategory(context.getString(R.string.phone_filter_cat_features), featOpts);

        // 5. PRIORISIEREN / SORTIEREN
        List<OptionItem> sortOpts = new ArrayList<>();
        int currentSort = state[0] & MASK_SORT;
        int[][] sortPairs = {
                {0, R.string.phone_filter_sort_relevance},
                {SearchOptions.SORT_BY_VIEW_COUNT, R.string.phone_filter_sort_popularity},
                {SearchOptions.SORT_BY_UPLOAD_DATE, R.string.phone_filter_sort_date},
                {SearchOptions.SORT_BY_RATING, R.string.phone_filter_sort_rating}
        };
        for (int[] pair : sortPairs) {
            sortOpts.add(UiOptionItem.from(
                    context.getString(pair[1]),
                    item -> {
                        state[0] = (state[0] & ~MASK_SORT) | pair[0];
                        if (onChanged != null) {
                            onChanged.onOptionsChanged(state[0]);
                        }
                    },
                    currentSort == pair[0]
            ));
        }
        dialog.appendRadioCategory(context.getString(R.string.phone_filter_cat_sort), sortOpts);

        dialog.showDialog(context.getString(R.string.phone_search_filters_title));
    }

    /**
     * Dialog to create or edit an "Eigene Liste" as a native SmartTube bottom sheet in the same design as all dialogs.
     */
    public static void showSaveOrEditListDialog(
            Context context,
            @Nullable SavedSearchList existing,
            String initialQuery,
            int initialOptions,
            ListDialogCallback callback) {
        final int[] workingOptions = {existing != null && initialOptions == existing.options ? existing.options : initialOptions};

        android.app.Dialog dialog = new android.app.Dialog(context, R.style.PhoneSheetTheme);
        android.view.View sheet = android.view.LayoutInflater.from(context).inflate(R.layout.phone_sheet_saved_list, null);
        dialog.setContentView(sheet);

        TextView titleView = sheet.findViewById(R.id.phone_sheet_title);
        titleView.setText(existing != null ? R.string.phone_list_dialog_edit_title : R.string.phone_list_dialog_new_title);

        EditText nameInput = sheet.findViewById(R.id.phone_list_name_input);
        nameInput.setText(existing != null ? existing.name : (initialQuery != null ? initialQuery : ""));

        EditText queryInput = sheet.findViewById(R.id.phone_list_query_input);
        queryInput.setText(initialQuery != null && !initialQuery.isEmpty() ? initialQuery : (existing != null ? existing.query : ""));

        TextView filterSummary = sheet.findViewById(R.id.phone_list_filter_summary);
        Runnable updateFilterLabel = () -> filterSummary.setText(formatSummary(context, workingOptions[0]));
        updateFilterLabel.run();

        android.view.View filterRow = sheet.findViewById(R.id.phone_list_filter_btn_row);
        filterRow.setOnClickListener(v -> showFilterDialog(context, workingOptions[0], newOpts -> {
            workingOptions[0] = newOpts;
            updateFilterLabel.run();
        }));

        TextView saveBtn = sheet.findViewById(R.id.phone_list_btn_save);
        saveBtn.setOnClickListener(v -> {
            String q = queryInput.getText().toString().trim();
            if (q.isEmpty()) {
                com.liskovsoft.sharedutils.helpers.MessageHelpers.showMessage(context, R.string.phone_list_empty_query);
                return;
            }
            String n = nameInput.getText().toString().trim();
            SavedSearchList saved = saveOrUpdateList(context, existing != null ? existing.id : null, n, q, workingOptions[0]);
            com.liskovsoft.sharedutils.helpers.MessageHelpers.showMessage(
                    context,
                    existing != null ? R.string.phone_list_updated_toast : R.string.phone_list_saved_toast
            );
            dialog.dismiss();
            if (callback != null) {
                callback.onSavedPermanently(saved);
            }
        });

        TextView tempBtn = sheet.findViewById(R.id.phone_list_btn_temp);
        tempBtn.setOnClickListener(v -> {
            String q = queryInput.getText().toString().trim();
            if (q.isEmpty()) {
                com.liskovsoft.sharedutils.helpers.MessageHelpers.showMessage(context, R.string.phone_list_empty_query);
                return;
            }
            com.liskovsoft.sharedutils.helpers.MessageHelpers.showMessage(context, R.string.phone_list_temp_toast);
            dialog.dismiss();
            if (callback != null) {
                callback.onAppliedTemporarily(q, workingOptions[0]);
            }
        });

        TextView deleteBtn = sheet.findViewById(R.id.phone_list_btn_delete);
        if (existing != null) {
            deleteBtn.setVisibility(android.view.View.VISIBLE);
            deleteBtn.setOnClickListener(v -> {
                deleteList(context, existing.id);
                com.liskovsoft.sharedutils.helpers.MessageHelpers.showMessage(context, R.string.phone_list_deleted_toast);
                dialog.dismiss();
                if (callback != null) {
                    callback.onDeleted(existing);
                }
            });
        } else {
            deleteBtn.setVisibility(android.view.View.GONE);
        }

        sheet.findViewById(R.id.phone_dialog_root).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public static TextView createPillChip(Context context, String text, boolean highlighted, boolean accent) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        int hPad = dp(context, 14);
        chip.setPadding(hPad, 0, hPad, 0);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (accent) {
            chip.setBackgroundResource(R.drawable.phone_pill_bg);
            chip.setTextColor(context.getResources().getColor(R.color.phone_link));
        } else {
            chip.setBackgroundResource(R.drawable.phone_chip_bg);
            chip.setSelected(highlighted);
            chip.setTextColor(context.getResources().getColor(highlighted ? R.color.phone_bg : R.color.phone_text));
        }
        return chip;
    }

    public static TextView createCustomListChip(Context context, String text, boolean selected) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        int hPad = dp(context, 14);
        chip.setPadding(hPad, 0, hPad, 0);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setBackgroundResource(R.drawable.phone_custom_chip_bg);
        chip.setSelected(selected);
        chip.setTextColor(context.getResources().getColor(selected ? R.color.phone_bg : R.color.phone_text));
        return chip;
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }
}
