package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;

import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Seek actions in Android's media controls, which use session actions on Android 13 and later. */
final class PhoneMediaSeek implements MediaSessionConnector.CustomActionProvider {
    static final String BACK = "org.smarttube.phone.SEEK_BACK";
    static final String AHEAD = "org.smarttube.phone.SEEK_AHEAD";
    private static final long INCREMENT_MS = 10_000;

    private final boolean forward;
    private final PlaybackStateCompat.CustomAction action;

    PhoneMediaSeek(Context context, boolean forward) {
        this.forward = forward;
        action = new PlaybackStateCompat.CustomAction.Builder(forward ? AHEAD : BACK,
                context.getString(forward ? R.string.phone_seek_ahead_10 : R.string.phone_seek_back_10),
                forward ? R.drawable.ic_phone_seek_ahead_10 : R.drawable.ic_phone_seek_back_10).build();
    }

    @Override
    public PlaybackStateCompat.CustomAction getCustomAction(Player player) {
        return canSeek(player) ? action : null;
    }

    @Override
    public void onCustomAction(Player player, ControlDispatcher dispatcher, String name, Bundle extras) {
        if (canSeek(player)) {
            dispatcher.dispatchSeekTo(player, player.getCurrentWindowIndex(), target(player, forward));
        }
    }

    static boolean canSeek(Player player) {
        return player != null && player.isCurrentWindowSeekable()
                && !player.isCurrentWindowDynamic() && player.getDuration() > 0;
    }

    static void jump(Player player, boolean forward) {
        if (canSeek(player)) {
            player.seekTo(target(player, forward));
        }
    }

    private static long target(Player player, boolean forward) {
        long next = player.getCurrentPosition() + (forward ? INCREMENT_MS : -INCREMENT_MS);
        return Math.max(0, Math.min(player.getDuration(), next));
    }
}
