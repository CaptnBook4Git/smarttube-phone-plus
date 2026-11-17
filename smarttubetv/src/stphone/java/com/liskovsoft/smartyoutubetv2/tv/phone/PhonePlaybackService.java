package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Tells Android a video is playing (a "media playback" foreground service, like music apps have). Without it Android
 * treats the app as idle once it is in the background or the screen is off and cuts its network: the next video
 * couldn't load ("Unable to resolve host") and playback stopped.
 *
 * The player screen starts it while a regular video plays (never Shorts) and shows its notification (the playback
 * controls). While the video plays it keeps the processor and Wi-Fi awake; it lets go of them when the video is
 * paused, and ends itself when the player is closed or stays paused for a long time.
 */
public class PhonePlaybackService extends Service {
    private static final String TAG = "PhonePlayback";
    static final int NOTIFICATION_ID = 4711;
    static final String NOTIFICATION_CHANNEL = "phone_playback";
    private static final long POLL_MS = 5_000;
    private static final long PAUSED_LIMIT_MS = 10 * 60_000;

    private static boolean sRunning;
    private static Notification sNotification;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;
    private long mIdleSince;

    static boolean isRunning() {
        return sRunning;
    }

    /** The notification the service shows (the player screen builds it: title, picture, play / pause) */
    static void setNotification(Notification notification) {
        sNotification = notification;
    }

    /** Only works while the app is on screen (Android doesn't let a background app start it): false otherwise */
    static boolean start(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return false;
        }
        try {
            context.getApplicationContext().startForegroundService(new Intent(context.getApplicationContext(), PhonePlaybackService.class));
            return true;
        } catch (RuntimeException e) {
            Log.d(TAG, "not started: " + e);
            return false;
        }
    }

    static void stop(Context context) {
        if (sRunning) {
            context.getApplicationContext().stopService(new Intent(context.getApplicationContext(), PhonePlaybackService.class));
        }
    }

    private final Runnable mPoll = new Runnable() {
        @Override
        public void run() {
            SimpleExoPlayer exo = PhonePlaybackActivity.currentExoPlayer();
            boolean playing = exo != null && exo.getPlayWhenReady();
            PhonePlaybackActivity.syncBackgroundVideo(); // also after an engine restart in the background
            holdLocks(playing);
            long now = android.os.SystemClock.elapsedRealtime();
            if (playing) {
                mIdleSince = 0;
            } else if (mIdleSince == 0) {
                mIdleSince = now;
            }
            // No player for a moment is normal (it restarts between videos); gone for good or paused for long: done
            long limit = exo == null ? 30_000 : PAUSED_LIMIT_MS;
            if (mIdleSince != 0 && now - mIdleSince > limit) {
                Log.d(TAG, "ending: " + (exo == null ? "player closed" : "paused for long"));
                stopSelf();
                return;
            }
            mHandler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = sNotification;
        if (notification == null) {
            notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
                    .setSmallIcon(R.drawable.ic_phone_play)
                    .setContentTitle(getString(R.string.app_name))
                    .setSilent(true)
                    .build();
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException e) {
            Log.d(TAG, "foreground refused: " + e);
            stopSelf();
            return START_NOT_STICKY;
        }
        mHandler.removeCallbacks(mPoll);
        mIdleSince = 0;
        mPoll.run();
        return START_NOT_STICKY;
    }

    private void holdLocks(boolean hold) {
        try {
            if (mWakeLock == null) {
                PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
                mWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StreamTube:playback");
                mWakeLock.setReferenceCounted(false);
                WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wifi != null) {
                    mWifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "StreamTube:playback");
                    mWifiLock.setReferenceCounted(false);
                }
            }
            if (hold) {
                if (!mWakeLock.isHeld()) {
                    mWakeLock.acquire();
                    Log.d(TAG, "playing: processor and Wi-Fi kept awake");
                }
                if (mWifiLock != null && !mWifiLock.isHeld()) {
                    mWifiLock.acquire();
                }
            } else {
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                    Log.d(TAG, "not playing: locks let go");
                }
                if (mWifiLock != null && mWifiLock.isHeld()) {
                    mWifiLock.release();
                }
            }
        } catch (RuntimeException e) {
            Log.d(TAG, "locks: " + e);
        }
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mPoll);
        holdLocks(false);
        sRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
