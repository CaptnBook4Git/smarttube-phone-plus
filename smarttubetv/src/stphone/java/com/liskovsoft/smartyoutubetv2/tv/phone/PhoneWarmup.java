package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

/**
 * Once per app start, after Home's first feed is on screen: one throwaway format request for a public video, so the
 * one-time work of a YouTube session (visitor identity, the player script that unscrambles links, picking a working
 * client) is done before the first video the user opens instead of during it.
 */
final class PhoneWarmup {
    private static final String TAG = "PhoneWarmup";
    /** Big Buck Bunny (Blender Foundation): public, stable, available everywhere */
    private static final String WARMUP_VIDEO_ID = "aqz-KE-bpKQ";
    /** Let Home's thumbnails load first */
    private static final long DELAY_MS = 1_500;
    private static boolean sStarted;

    private PhoneWarmup() {
    }

    static void startOnce() {
        if (sStarted) {
            return;
        }
        sStarted = true;
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                RxHelper.execute(YouTubeServiceManager.instance().getMediaItemService().getFormatInfoObserve(WARMUP_VIDEO_ID),
                        formatInfo -> Log.d(TAG, "Session warm"),
                        error -> Log.w(TAG, "Warmup failed: " + error.getMessage())), DELAY_MS);
    }
}
