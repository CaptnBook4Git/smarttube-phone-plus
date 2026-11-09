package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;

import com.liskovsoft.sharedutils.okhttp.OkHttpManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Channel header details SmartTube's channel rows don't carry (banner, handle, subscriber and video counts,
 * description), read from the channel's public page. Every field is optional: the header hides what's missing.
 */
final class PhoneChannelInfo {
    String bannerUrl;
    String avatarUrl;
    String handle;
    String subscribers;
    String videos;
    String description;

    interface Callback {
        void onInfo(PhoneChannelInfo info);
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, PhoneChannelInfo> sCache = new HashMap<>();
    private static final int MAX_PAGE_BYTES = 3 * 1024 * 1024;

    private static final Pattern BANNER = Pattern.compile("\"imageBannerViewModel\":\\{\"image\":\\{\"sources\":\\[\\{\"url\":\"([^\"]+)\"");
    private static final Pattern BANNER_OLD = Pattern.compile("\"banner\":\\{\"thumbnails\":\\[\\{\"url\":\"([^\"]+)\"");
    private static final Pattern HANDLE = Pattern.compile("\"vanityChannelUrl\":\"https?://www\\.youtube\\.com/(@[^\"/]+)\"");
    private static final Pattern HANDLE_PART = Pattern.compile("\"content\":\"(@[^\"\\s]{2,})\"");
    private static final Pattern SUBSCRIBERS = Pattern.compile("\"content\":\"([^\"]{1,30}? subscribers?)\"");
    private static final Pattern VIDEOS = Pattern.compile("\"content\":\"([^\"]{1,30}? videos?)\"");
    private static final Pattern OG_IMAGE = Pattern.compile("<meta property=\"og:image\" content=\"([^\"]+)\"");
    private static final Pattern OG_DESCRIPTION = Pattern.compile("<meta property=\"og:description\" content=\"([^\"]*)\"");

    private PhoneChannelInfo() {
    }

    static void fetch(String channelId, Callback callback) {
        if (TextUtils.isEmpty(channelId)) {
            return;
        }
        PhoneChannelInfo cached;
        synchronized (sCache) {
            cached = sCache.get(channelId);
        }
        if (cached != null) {
            callback.onInfo(cached);
            return;
        }
        POOL.execute(() -> {
            PhoneChannelInfo info = null;
            try {
                info = parse(readPage("https://www.youtube.com/channel/" + channelId));
            } catch (Throwable ignored) {
                // offline: header shows what SmartTube already knows
            }
            if (info == null) {
                return;
            }
            synchronized (sCache) {
                sCache.put(channelId, info);
            }
            final PhoneChannelInfo result = info;
            MAIN.post(() -> callback.onInfo(result));
        });
    }

    private static PhoneChannelInfo parse(String html) {
        if (html == null) {
            return null;
        }
        PhoneChannelInfo info = new PhoneChannelInfo();
        info.bannerUrl = first(html, BANNER, BANNER_OLD);
        if (info.bannerUrl != null) {
            info.bannerUrl = unescapeJson(info.bannerUrl);
            if (info.bannerUrl.startsWith("//")) {
                info.bannerUrl = "https:" + info.bannerUrl;
            }
        }
        info.handle = first(html, HANDLE, HANDLE_PART);
        info.subscribers = unescapeJson(first(html, SUBSCRIBERS));
        info.videos = unescapeJson(first(html, VIDEOS));
        String image = first(html, OG_IMAGE);
        if (image != null) {
            info.avatarUrl = image.replace("&amp;", "&").replaceAll("=s\\d+-", "=s240-");
        }
        String description = first(html, OG_DESCRIPTION);
        if (!TextUtils.isEmpty(description)) {
            info.description = Html.fromHtml(description).toString().trim();
        }
        return info;
    }

    private static String first(String text, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String unescapeJson(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\u0026", "&").replace("\\u003d", "=").replace("\\/", "/").replace("\\\"", "\"");
    }

    /** Reads the page until the header counts have been seen (they come early in the page data) */
    private static String readPage(String url) throws Exception {
        Response response = OkHttpManager.instance().doGetRequest(url, AvatarFetcher.headers());
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
                byte[] chunk = new byte[32 * 1024];
                int read;
                int checkedUpTo = 0;
                while ((read = in.read(chunk)) != -1 && buffer.size() < MAX_PAGE_BYTES) {
                    buffer.write(chunk, 0, read);
                    // Check only the newest part (plus some overlap) for the end of the header data
                    if (buffer.size() - checkedUpTo > 128 * 1024) {
                        byte[] bytes = buffer.toByteArray();
                        int from = Math.max(0, checkedUpTo - 4096);
                        String tail = new String(bytes, from, bytes.length - from, StandardCharsets.UTF_8);
                        checkedUpTo = bytes.length;
                        if (VIDEOS.matcher(tail).find() && SUBSCRIBERS.matcher(new String(bytes, StandardCharsets.UTF_8)).find()) {
                            break;
                        }
                    }
                }
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            response.close();
        }
    }
}
