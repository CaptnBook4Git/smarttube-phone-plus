package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/** Bookmarks belong to this installation and remain available while signed out. */
final class PhoneMoments extends SQLiteOpenHelper {
    static final class Moment {
        final String videoId;
        final String title;
        final long position;
        final String note;

        Moment(String videoId, String title, long position, String note) {
            this.videoId = videoId;
            this.title = title;
            this.position = position;
            this.note = note;
        }

        String time() {
            return PhonePlaybackActivity.formatTime(position);
        }

        private String[] key() {
            return new String[]{videoId, Long.toString(position / 1000)};
        }
    }

    private static PhoneMoments instance;
    private static final String KEY = "video_id = ? AND second = ?";
    private static final String[] COLUMNS = {"video_id", "title", "position", "note"};

    static synchronized PhoneMoments get(Context context) {
        if (instance == null) {
            instance = new PhoneMoments(context.getApplicationContext());
        }
        return instance;
    }

    private PhoneMoments(Context context) {
        super(context, "saved_moments.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE moments (video_id TEXT NOT NULL, title TEXT NOT NULL, "
                + "second INTEGER NOT NULL, position INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '', "
                + "UNIQUE(video_id, second))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int from, int to) {
        throw new IllegalStateException("Unknown saved moments schema " + to);
    }

    Moment save(Moment moment) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("video_id", moment.videoId);
        values.put("title", moment.title);
        values.put("position", moment.position);
        values.put("second", moment.position / 1000);
        db.insertWithOnConflict("moments", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        // Saving the same displayed timestamp again keeps the original position and note.
        try (Cursor result = db.query("moments", COLUMNS, KEY, moment.key(), null, null, null)) {
            if (!result.moveToFirst()) {
                throw new android.database.SQLException("Could not save moment");
            }
            return read(result);
        }
    }

    List<Moment> list() {
        List<Moment> moments = new ArrayList<>();
        // Recently bookmarked videos first, each video's timestamps in playback order.
        String sql = "SELECT m.video_id, m.title, m.position, m.note FROM moments m JOIN "
                + "(SELECT video_id, MAX(rowid) AS latest FROM moments GROUP BY video_id) g "
                + "ON m.video_id = g.video_id ORDER BY g.latest DESC, m.position";
        try (Cursor result = getReadableDatabase().rawQuery(sql, null)) {
            while (result.moveToNext()) {
                moments.add(read(result));
            }
        }
        return moments;
    }

    private static Moment read(Cursor result) {
        return new Moment(result.getString(0), result.getString(1), result.getLong(2), result.getString(3));
    }

    void remove(Moment moment) {
        getWritableDatabase().delete("moments", KEY, moment.key());
    }

    static void editNote(Activity activity, Moment moment, Runnable onSaved) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, PhoneTheme.isNight(activity)
                ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert);
        EditText input = new EditText(builder.getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        input.setHint(R.string.phone_moment_note_hint);
        input.setText(moment.note);
        input.setSelection(input.length());
        FrameLayout frame = new FrameLayout(builder.getContext());
        int margin = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        frame.setPadding(margin, 0, margin, 0);
        frame.addView(input);
        AlertDialog dialog = builder.setTitle(activity.getString(R.string.phone_moment_note_at, moment.time()))
                .setView(frame).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.phone_save, (d, which) -> {
                    ContentValues values = new ContentValues();
                    values.put("note", input.getText().toString().trim());
                    get(activity).getWritableDatabase().update("moments", values, KEY, moment.key());
                    onSaved.run();
                }).create();
        input.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
    }
}
