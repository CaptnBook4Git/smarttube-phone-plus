package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

/**
 * Lets a build use its own updater instead of SmartTube's (the phone app updates from its own GitHub releases).
 * Kept free of the update checker library so app modules can set it.
 */
public final class AppUpdateHook {
    public interface Handler {
        void start(Context context, boolean forceCheck);
    }

    private static Handler sHandler;

    private AppUpdateHook() {
    }

    public static void setHandler(Handler handler) {
        sHandler = handler;
    }

    public static Handler getHandler() {
        return sHandler;
    }

    public static boolean isSet() {
        return sHandler != null;
    }
}
