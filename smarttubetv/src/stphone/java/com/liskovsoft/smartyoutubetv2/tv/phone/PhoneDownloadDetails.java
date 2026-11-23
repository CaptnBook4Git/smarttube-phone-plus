package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/** Optional metadata lives beside the app's download index. The media files remain independently playable. */
final class PhoneDownloadDetails {
    final String title;
    final String author;
    final String description;

    PhoneDownloadDetails(String title, String author, String description) {
        this.title = bounded(title, 500);
        this.author = bounded(author, 500);
        this.description = bounded(description, 16000);
    }

    void save(Context context, Uri uri) {
        try {
            JSONObject data = new JSONObject().put("title", title).put("author", author).put("description", description);
            prefs(context).edit().putString(uri.toString(), data.toString()).apply();
        } catch (JSONException e) {
            Log.w("PhoneDownloads", "Could not record download details", e);
        }
    }

    static PhoneDownloadDetails read(Context context, Uri uri, String fallbackTitle) {
        String value = prefs(context).getString(uri.toString(), null);
        if (value != null) {
            try {
                JSONObject data = new JSONObject(value);
                return new PhoneDownloadDetails(data.optString("title", fallbackTitle),
                        data.optString("author"), data.optString("description"));
            } catch (JSONException ignored) {
                // The file still opens using its name.
            }
        }
        return new PhoneDownloadDetails(fallbackTitle, null, null);
    }

    static void remove(Context context, Uri uri) {
        prefs(context).edit().remove(uri.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("phone_download_details", Context.MODE_PRIVATE);
    }

    private static String bounded(String value, int limit) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), limit));
    }
}
