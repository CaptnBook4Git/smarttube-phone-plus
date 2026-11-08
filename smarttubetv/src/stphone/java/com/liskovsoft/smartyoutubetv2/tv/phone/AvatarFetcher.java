package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Feed items carry no channel picture. Look it up once per channel and keep it in {@link AvatarCache}.
 * Requests go through SmartTube's own OkHttp client (same DNS and network settings as the rest of the app).
 */
final class AvatarFetcher {
    interface Callback {
        void onAvatar(String url);
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, List<Callback>> sPending = new HashMap<>();
    private static final Map<String, Long> sFailedAt = new HashMap<>();
    private static final long RETRY_AFTER_MS = 30_000;
    private static final Pattern HANDLE = Pattern.compile("@[\\p{L}\\p{N}._\\-]{2,}");
    private static final Pattern OG_IMAGE = Pattern.compile("<meta property=\"og:image\" content=\"([^\"]+)\"");
    private static final Pattern AUTHOR_URL = Pattern.compile("\"author_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final int MAX_PAGE_BYTES = 700 * 1024;

    private AvatarFetcher() {
    }

    static String handleOf(Video video) {
        CharSequence[] sources = {video.getSecondTitle(), video.getAuthor()};
        for (CharSequence source : sources) {
            if (source == null) {
                continue;
            }
            Matcher matcher = HANDLE.matcher(source);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }

    /**
     * Key used to match a fetched picture with the view that asked for it.
     */
    static String keyOf(Video video) {
        if (!TextUtils.isEmpty(video.channelId)) {
            return "c:" + video.channelId;
        }
        String handle = handleOf(video);
        if (handle != null) {
            return "h:" + handle;
        }
        // No channel id or handle in the feed item: resolve the channel from the video itself
        if (!TextUtils.isEmpty(video.videoId)) {
            return !TextUtils.isEmpty(video.getAuthor())
                    ? "a:" + video.getAuthor().trim().toLowerCase(Locale.ROOT) : "v:" + video.videoId;
        }
        return null;
    }

    static void fetch(Context context, Video video, Callback callback) {
        final String key = keyOf(video);
        if (key == null) {
            return;
        }

        final String channelId = video.channelId;
        final String handle = handleOf(video);
        final String videoId = video.videoId;
        final String author = video.getAuthor();
        final Context app = context.getApplicationContext();

        synchronized (sPending) {
            Long failedAt = sFailedAt.get(key);
            if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_AFTER_MS) {
                return;
            }
            List<Callback> callbacks = sPending.get(key);
            if (callbacks != null) {
                callbacks.add(callback);
                return;
            }
            callbacks = new ArrayList<>();
            callbacks.add(callback);
            sPending.put(key, callbacks);
        }

        POOL.execute(() -> {
            String url = null;
            for (int attempt = 0; attempt < 2 && url == null; attempt++) {
                try {
                    String page;
                    if (!TextUtils.isEmpty(channelId)) {
                        page = "https://www.youtube.com/channel/" + channelId;
                    } else if (handle != null) {
                        page = "https://www.youtube.com/@" + URLEncoder.encode(handle.substring(1), "UTF-8");
                    } else {
                        page = readChannelUrl(videoId);
                    }
                    url = page != null ? readOgImage(page) : null;
                } catch (Throwable ignored) {
                    // Offline or flaky DNS: one more try, then wait before retrying this channel
                }
            }

            final String result = url;
            if (result != null) {
                AvatarCache.rememberUrl(app, channelId, author, handle, result);
            }

            List<Callback> callbacks;
            synchronized (sPending) {
                callbacks = sPending.remove(key);
                if (result == null) {
                    sFailedAt.put(key, System.currentTimeMillis());
                } else {
                    sFailedAt.remove(key);
                }
            }

            if (result != null && callbacks != null) {
                MAIN.post(() -> {
                    for (Callback cb : callbacks) {
                        cb.onAvatar(result);
                    }
                });
            }
        });
    }

    private static Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        headers.put("Accept-Language", "en-US,en;q=0.8");
        headers.put("Cookie", "CONSENT=YES+1; SOCS=CAI");
        return headers;
    }

    /**
     * oEmbed (a tiny JSON) tells which channel a video belongs to.
     */
    private static String readChannelUrl(String videoId) throws Exception {
        if (TextUtils.isEmpty(videoId)) {
            return null;
        }
        String api = "https://www.youtube.com/oembed?format=json&url="
                + URLEncoder.encode("https://www.youtube.com/watch?v=" + videoId, "UTF-8");
        String json = read(api, 64 * 1024, null);
        if (json == null) {
            return null;
        }
        Matcher matcher = AUTHOR_URL.matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\/", "/") : null;
    }

    private static String readOgImage(String pageUrl) throws Exception {
        String html = read(pageUrl, MAX_PAGE_BYTES, OG_IMAGE);
        if (html == null) {
            return null;
        }
        Matcher matcher = OG_IMAGE.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        // Channel pictures come at 900px; a small size is plenty for an avatar
        return matcher.group(1).replace("&amp;", "&").replaceAll("=s\\d+-", "=s96-");
    }

    /**
     * Reads the body up to maxBytes, stopping early once stopAt matches.
     */
    private static String read(String url, int maxBytes, Pattern stopAt) throws Exception {
        Response response = OkHttpManager.instance().doGetRequest(url, headers());
        if (response == null) {
            return null;
        }
        try {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                return null;
            }
            try (InputStream in = body.byteStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[16 * 1024];
                int read;
                while ((read = in.read(chunk)) != -1 && buffer.size() < maxBytes) {
                    buffer.write(chunk, 0, read);
                    if (stopAt != null && stopAt.matcher(new String(buffer.toByteArray(), StandardCharsets.UTF_8)).find()) {
                        break;
                    }
                }
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            response.close();
        }
    }
}
