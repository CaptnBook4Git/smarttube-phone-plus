package com.liskovsoft.smartyoutubetv2.common.misc;

/**
 * Phone builds: open videos as fast as possible. Off on TV (SmartTube's own behavior is unchanged there).
 * <ul>
 *     <li>the video's formats are requested the moment it is tapped, not after the player screen and engine exist</li>
 *     <li>playback starts after half a second of buffered media instead of two and a half</li>
 *     <li>recommendations load next to the video instead of after it starts</li>
 * </ul>
 */
public final class FastStart {
    private static boolean sEnabled;

    private FastStart() {
    }

    public static void enable() {
        sEnabled = true;
    }

    public static boolean isEnabled() {
        return sEnabled;
    }
}
