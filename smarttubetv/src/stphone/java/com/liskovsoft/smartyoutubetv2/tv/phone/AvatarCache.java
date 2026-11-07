package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Video items carry no channel picture. Remember channel pictures whenever a channel item passes through the UI
 * (subscriptions strip, channel results, channel pages), and draw a colored initial until one is known.
 */
final class AvatarCache {
    private static final String PREFS = "phone_avatar_cache";
    private static final int MAX_ENTRIES = 3000;
    private static Map<String, String> sMap;
    private static SharedPreferences sPrefs;

    private AvatarCache() {
    }

    @SuppressWarnings("unchecked")
    private static void init(Context context) {
        if (sMap != null) {
            return;
        }
        sPrefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sMap = new HashMap<>();
        for (Map.Entry<String, ?> entry : sPrefs.getAll().entrySet()) {
            if (entry.getValue() instanceof String) {
                sMap.put(entry.getKey(), (String) entry.getValue());
            }
        }
    }

    private static String nameKey(String name) {
        return "n:" + name.trim().toLowerCase(Locale.ROOT);
    }

    static void remember(Context context, Video channel) {
        if (channel == null || TextUtils.isEmpty(channel.getCardImageUrl())) {
            return;
        }
        init(context);
        if (sMap.size() > MAX_ENTRIES) {
            return;
        }

        String url = channel.getCardImageUrl();
        SharedPreferences.Editor editor = null;

        if (!TextUtils.isEmpty(channel.channelId) && !url.equals(sMap.get("c:" + channel.channelId))) {
            sMap.put("c:" + channel.channelId, url);
            editor = sPrefs.edit();
            editor.putString("c:" + channel.channelId, url);
        }

        String name = !TextUtils.isEmpty(channel.getTitle()) ? channel.getTitle() : channel.getAuthor();
        if (!TextUtils.isEmpty(name) && !url.equals(sMap.get(nameKey(name)))) {
            sMap.put(nameKey(name), url);
            if (editor == null) {
                editor = sPrefs.edit();
            }
            editor.putString(nameKey(name), url);
        }

        if (editor != null) {
            editor.apply();
        }
    }

    @Nullable
    static String find(Context context, Video video) {
        if (video == null) {
            return null;
        }
        init(context);
        String url = null;
        if (!TextUtils.isEmpty(video.channelId)) {
            url = sMap.get("c:" + video.channelId);
        }
        if (url == null && !TextUtils.isEmpty(video.getAuthor())) {
            url = sMap.get(nameKey(video.getAuthor()));
        }
        return url;
    }

    /**
     * Colored circle with the first letter of the channel name.
     */
    static final class LetterDrawable extends Drawable {
        private static final int[] COLORS = {
                0xFF5C6BC0, 0xFF26A69A, 0xFFEF5350, 0xFFAB47BC, 0xFF42A5F5,
                0xFFFFA726, 0xFF66BB6A, 0xFF8D6E63, 0xFFEC407A, 0xFF7E57C2
        };
        private final Paint mCircle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String mLetter;

        LetterDrawable(String name) {
            String trimmed = name != null ? name.trim() : "";
            if (trimmed.startsWith("@") && trimmed.length() > 1) {
                trimmed = trimmed.substring(1);
            }
            mLetter = trimmed.isEmpty() ? "?" : trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
            mCircle.setColor(COLORS[Math.abs(trimmed.hashCode() % COLORS.length)]);
            mText.setColor(0xFFFFFFFF);
            mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            mText.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            float radius = Math.min(bounds.width(), bounds.height()) / 2f;
            canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, mCircle);
            mText.setTextSize(radius * 1.05f);
            float y = bounds.exactCenterY() - (mText.descent() + mText.ascent()) / 2f;
            canvas.drawText(mLetter, bounds.exactCenterX(), y, mText);
        }

        @Override
        public void setAlpha(int alpha) {
            mCircle.setAlpha(alpha);
            mText.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mCircle.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
