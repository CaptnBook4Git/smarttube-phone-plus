package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Download button under the video: a sheet with the files that can be saved as they are, then a background download
 * with a progress notification into Download/SmartTube.
 * <ul>
 *     <li>Video 360p: the MP4 with sound that YouTube still serves as one file</li>
 *     <li>Video 480p to 1080p: YouTube serves the picture (H.264) and the sound (AAC) as separate files; both are
 *     downloaded and joined into one MP4 on the phone (MediaExtractor + MediaMuxer, no re-encoding)</li>
 *     <li>Audio: the AAC (M4A) track, rewritten from YouTube's streaming layout into a plain M4A file</li>
 * </ul>
 * Files are fetched in pieces with the player's user agent: YouTube cuts off or slows down one long request.
 */
final class PhoneDownloads {
    private static final String TAG = "PhoneDownloads";
    private static final String CHANNEL = "phone_downloads";
    private static final String FOLDER = "SmartTube";
    private static final long CHUNK = 4L * 1024 * 1024;
    private static final int RETRIES = 3;
    private static final int REQUEST_STORAGE = 4721;

    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(); // one download at a time
    private static final AtomicInteger NEXT_ID = new AtomicInteger(5100);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final class Option {
        final boolean audio;
        final String url;
        final long size; // bytes, -1 when unknown
        final String label;
        /** Higher qualities: the sound, joined to the picture in {@link #url} after both are downloaded */
        String audioUrl;
        long audioSize = -1;
        /** Audio taken out of the 360p video file (when YouTube gives no separate audio file to download) */
        boolean fromVideo;

        Option(boolean audio, String url, long size, String label) {
            this.audio = audio;
            this.url = url;
            this.size = size;
            this.label = label;
        }

        boolean isMerged() {
            return audioUrl != null;
        }

        long totalSize() {
            if (!isMerged()) {
                return size;
            }
            return size > 0 && audioSize > 0 ? size + audioSize : -1;
        }
    }

    /** Highest first; above 1080p YouTube has no H.264, which is what MP4 files on phones play */
    private static final int MAX_HEIGHT = 1080;

    private PhoneDownloads() {
    }

    // region Sheet

    /** Download tapped: look up the files of this video and offer the ones that can be saved */
    static void showOptions(Activity activity, Video video) {
        if (video == null || TextUtils.isEmpty(video.videoId)) {
            return;
        }
        if (Build.VERSION.SDK_INT < 29 && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Android 9 and older: saving into Download needs the storage permission
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
            Toast.makeText(activity, R.string.phone_download_storage, Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(activity, R.string.phone_download_checking, Toast.LENGTH_SHORT).show();
        String title = !TextUtils.isEmpty(video.getTitle()) ? video.getTitle() : video.videoId;
        RxHelper.execute(YouTubeServiceManager.instance().getMediaItemService().getFormatInfoObserve(video.videoId),
                formatInfo -> {
                    if (PhoneUi.isAlive(activity)) {
                        showSheet(activity, formatInfo, title);
                    }
                },
                error -> {
                    Log.e(TAG, "Format info: " + error.getMessage());
                    if (PhoneUi.isAlive(activity)) {
                        Toast.makeText(activity, R.string.phone_download_unavailable, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private static void showSheet(Activity activity, MediaItemFormatInfo formatInfo, String title) {
        if (formatInfo == null || formatInfo.isLive() || formatInfo.isUnplayable()) {
            Toast.makeText(activity, R.string.phone_download_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        Option video = videoOption(activity, formatInfo);
        MediaFormat audioFormat = audioFormat(formatInfo);
        Option audio = null;
        if (audioFormat != null) {
            audio = new Option(true, audioFormat.getUrl(), parseSize(audioFormat.getClen()), activity.getString(R.string.phone_download_audio));
        } else if (video != null) {
            // No separate audio file with a link: the sound of the 360p file (same AAC sound, a bit lower bitrate)
            audio = new Option(true, video.url, -1, activity.getString(R.string.phone_download_audio));
            audio.fromVideo = true;
        }

        java.util.List<Option> options = new java.util.ArrayList<>();
        if (audioFormat != null) {
            options.addAll(mergedOptions(activity, formatInfo, audioFormat));
        }
        if (video != null) {
            options.add(video);
        }
        if (audio != null) {
            options.add(audio);
        }
        if (options.isEmpty()) {
            Toast.makeText(activity, R.string.phone_download_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        Context app = activity.getApplicationContext();
        AppDialogPresenter dialog = AppDialogPresenter.instance(activity);
        if (!hasMergedOptions(options) && hasHigherQualities(formatInfo)) {
            // Higher qualities exist, but YouTube streams them without a file link (SABR): say so instead of hiding them
            dialog.appendSingleButton(UiOptionItem.from(activity.getString(R.string.phone_download_no_hd),
                    activity.getString(R.string.phone_download_no_hd_reason), item -> AppDialogPresenter.instance(activity).closeDialog()));
        }
        for (Option option : options) {
            long total = option.totalSize();
            String description = option.fromVideo ? activity.getString(R.string.phone_download_audio_from_video)
                    : total > 0 ? sizeText(total) : null;
            dialog.appendSingleButton(UiOptionItem.from(option.label, description, item -> {
                AppDialogPresenter.instance(activity).closeDialog();
                enqueue(app, option, title);
            }));
        }
        dialog.showDialog(activity.getString(R.string.phone_download));
    }

    private static boolean hasMergedOptions(java.util.List<Option> options) {
        for (Option option : options) {
            if (option.isMerged()) {
                return true;
            }
        }
        return false;
    }

    /** The video has qualities above 360p, with or without a file link */
    private static boolean hasHigherQualities(MediaItemFormatInfo formatInfo) {
        if (formatInfo.getAdaptiveFormats() == null) {
            return false;
        }
        for (MediaFormat format : formatInfo.getAdaptiveFormats()) {
            if (format != null && format.getMimeType() != null && format.getMimeType().startsWith("video/") && lines(format) > 360) {
                return true;
            }
        }
        return false;
    }

    /** The video with sound in one file: 360p (YouTube's only remaining one) */
    private static Option videoOption(Context context, MediaItemFormatInfo formatInfo) {
        if (formatInfo.getUrlFormats() == null) {
            return null;
        }
        MediaFormat best = null;
        for (MediaFormat format : formatInfo.getUrlFormats()) {
            if (format == null || TextUtils.isEmpty(format.getUrl()) || format.getMimeType() == null
                    || !format.getMimeType().startsWith("video/mp4")) {
                continue;
            }
            if (best == null || "18".equals(format.getITag()) || (!"18".equals(best.getITag()) && format.getHeight() > best.getHeight())) {
                best = format;
            }
        }
        if (best == null) {
            return null;
        }
        String quality = !TextUtils.isEmpty(best.getQualityLabel()) ? best.getQualityLabel()
                : best.getHeight() > 0 ? best.getHeight() + "p" : "360p";
        return new Option(false, best.getUrl(), parseSize(best.getClen()),
                context.getString(R.string.phone_download_video, quality));
    }

    /**
     * 480p to 1080p: the H.264 picture of each height (the highest frame rate, then bitrate), to join with the sound.
     * Heights the single 360p file already covers are left out.
     */
    private static java.util.List<Option> mergedOptions(Context context, MediaItemFormatInfo formatInfo, MediaFormat audio) {
        java.util.List<Option> result = new java.util.ArrayList<>();
        if (formatInfo.getAdaptiveFormats() == null) {
            return result;
        }
        java.util.Map<Integer, MediaFormat> byHeight = new java.util.TreeMap<>(java.util.Collections.reverseOrder());
        for (MediaFormat format : formatInfo.getAdaptiveFormats()) {
            String mime = format != null ? format.getMimeType() : null;
            if (mime == null || TextUtils.isEmpty(format.getUrl()) || !mime.startsWith("video/mp4") || !mime.contains("avc1")
                    || format.isOtf() || lines(format) <= 360 || lines(format) > MAX_HEIGHT) {
                continue;
            }
            MediaFormat current = byHeight.get(lines(format));
            if (current == null || fps(format) > fps(current) || (fps(format) == fps(current) && bitrate(format) > bitrate(current))) {
                byHeight.put(lines(format), format);
            }
        }
        for (MediaFormat format : byHeight.values()) {
            String quality = !TextUtils.isEmpty(format.getQualityLabel()) ? format.getQualityLabel() : lines(format) + "p";
            Option option = new Option(false, format.getUrl(), parseSize(format.getClen()),
                    context.getString(R.string.phone_download_video, quality));
            option.audioUrl = audio.getUrl();
            option.audioSize = parseSize(audio.getClen());
            result.add(option);
        }
        return result;
    }

    /** The AAC audio track (M4A), the original language when the video has several */
    private static MediaFormat audioFormat(MediaItemFormatInfo formatInfo) {
        if (formatInfo.getAdaptiveFormats() == null) {
            return null;
        }
        MediaFormat best = null;
        for (MediaFormat format : formatInfo.getAdaptiveFormats()) {
            if (format == null || TextUtils.isEmpty(format.getUrl()) || format.getMimeType() == null
                    || !format.getMimeType().startsWith("audio/mp4") || format.isDrc() || format.isOtf()) {
                continue;
            }
            String track = format.getAudioTrackId();
            boolean original = track == null || format.getXtags() == null || format.getXtags().contains("original");
            if (!original) {
                continue;
            }
            if (best == null || "140".equals(format.getITag()) || (!"140".equals(best.getITag()) && bitrate(format) > bitrate(best))) {
                best = format;
            }
        }
        return best;
    }

    // endregion

    // region Download

    private static void enqueue(Context app, Option option, String title) {
        int id = NEXT_ID.getAndIncrement();
        createChannel(app);
        NotificationCompat.Builder progress = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(app.getString(R.string.phone_download_waiting))
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSilent(true)
                .setProgress(0, 0, true);
        notify(app, id, progress);
        Toast.makeText(app, R.string.phone_download_started, Toast.LENGTH_SHORT).show();

        QUEUE.execute(() -> {
            File temp = null;
            File soundTemp = null;
            File remuxed = null;
            try {
                temp = new File(app.getCacheDir(), "download_" + id + ".part");
                long grandTotal = option.totalSize();
                long downloaded = fetch(app, option.url, option.size, temp, id, progress, 0, grandTotal);

                File result = temp;
                if (option.isMerged()) {
                    soundTemp = new File(app.getCacheDir(), "download_" + id + ".audio.part");
                    fetch(app, option.audioUrl, option.audioSize, soundTemp, id, progress, downloaded, grandTotal);
                    progress.setProgress(0, 0, true).setContentText(app.getString(R.string.phone_download_joining));
                    notify(app, id, progress);
                    remuxed = new File(app.getCacheDir(), "download_" + id + ".mp4");
                    merge(temp, soundTemp, remuxed);
                    result = remuxed;
                } else if (option.audio) {
                    remuxed = new File(app.getCacheDir(), "download_" + id + ".m4a");
                    if (remuxAudio(temp, remuxed)) {
                        result = remuxed;
                    } else if (option.fromVideo) {
                        throw new IOException("Couldn't take the sound out of the video file");
                    }
                }

                String name = fileName(title) + (option.audio ? ".m4a" : ".mp4");
                String mime = option.audio ? "audio/mp4" : "video/mp4";
                Uri saved = save(app, result, name, mime);
                done(app, id, title, saved, mime);
            } catch (Exception e) {
                Log.e(TAG, "Download failed: " + e);
                failed(app, id, title);
            } finally {
                if (temp != null) {
                    temp.delete();
                }
                if (soundTemp != null) {
                    soundTemp.delete();
                }
                if (remuxed != null) {
                    remuxed.delete();
                }
            }
        });
    }

    /**
     * The file in pieces (ranged requests), a few retries per piece. The progress counts from offset of grandTotal
     * (picture then sound for the joined qualities). Returns the bytes downloaded.
     */
    private static long fetch(Context app, String url, long size, File target, int id, NotificationCompat.Builder progress,
                              long offset, long grandTotal) throws IOException {
        OkHttpClient client = OkHttpManager.instance().getClient();
        long total = size;
        long position = 0;
        long lastUpdate = 0;
        byte[] buffer = new byte[64 * 1024];

        try (OutputStream out = new FileOutputStream(target)) {
            while (total <= 0 || position < total) {
                long end = position + CHUNK - 1;
                if (total > 0) {
                    end = Math.min(end, total - 1);
                }
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", DefaultHeaders.APP_USER_AGENT)
                        .header("Range", "bytes=" + position + "-" + end)
                        .build();

                long received = 0;
                boolean wholeFile = false;
                IOException lastError = null;
                for (int attempt = 0; attempt < RETRIES; attempt++) {
                    try (Response response = client.newCall(request).execute()) {
                        if (response.code() == 416) {
                            return position; // past the end: the size was unknown and the file is complete
                        }
                        if (!response.isSuccessful() || response.body() == null) {
                            throw new IOException("HTTP " + response.code());
                        }
                        if (total <= 0) {
                            total = totalFromContentRange(response.header("Content-Range"));
                        }
                        wholeFile = response.code() == 200; // the range was ignored: this is the whole file
                        ResponseBody body = response.body();
                        try (InputStream in = body.byteStream()) {
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                                received += read;
                                long now = SystemClock.uptimeMillis();
                                if (now - lastUpdate > 700) {
                                    lastUpdate = now;
                                    showProgress(app, id, progress, offset + position + received, grandTotal > 0 ? grandTotal : -1);
                                }
                            }
                        }
                        lastError = null;
                        break;
                    } catch (IOException e) {
                        lastError = e;
                        if (received > 0) {
                            throw e; // part of the piece is already written: start over would corrupt the file
                        }
                        SystemClock.sleep(1000L * (attempt + 1));
                    }
                }
                if (lastError != null) {
                    throw lastError;
                }
                position += received;
                if (wholeFile || received == 0) {
                    break;
                }
            }
        }
        if (total > 0 && position < total) {
            throw new IOException("Incomplete: " + position + " of " + total);
        }
        return position;
    }

    /**
     * Picture and sound into one MP4, samples copied as they are (no re-encoding), written in time order so players
     * can stream the file.
     */
    private static void merge(File video, File audio, File target) throws IOException {
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean started = false;
        try {
            videoExtractor.setDataSource(video.getPath());
            audioExtractor.setDataSource(audio.getPath());
            int videoTrack = findTrack(videoExtractor, "video/");
            int audioTrack = findTrack(audioExtractor, "audio/");
            if (videoTrack == -1 || audioTrack == -1) {
                throw new IOException("Missing track: video " + videoTrack + ", audio " + audioTrack);
            }
            videoExtractor.selectTrack(videoTrack);
            audioExtractor.selectTrack(audioTrack);

            muxer = new MediaMuxer(target.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoOut = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack));
            int audioOut = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack));
            muxer.start();
            started = true;

            ByteBuffer videoBuffer = ByteBuffer.allocate(bufferSize(videoExtractor.getTrackFormat(videoTrack), 4 * 1024 * 1024));
            ByteBuffer audioBuffer = ByteBuffer.allocate(bufferSize(audioExtractor.getTrackFormat(audioTrack), 256 * 1024));
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean videoDone = false;
            boolean audioDone = false;
            while (!videoDone || !audioDone) {
                long videoTime = videoDone ? Long.MAX_VALUE : videoExtractor.getSampleTime();
                long audioTime = audioDone ? Long.MAX_VALUE : audioExtractor.getSampleTime();
                if (videoTime < 0) {
                    videoDone = true;
                    continue;
                }
                if (audioTime < 0) {
                    audioDone = true;
                    continue;
                }
                boolean takeVideo = videoTime <= audioTime;
                MediaExtractor extractor = takeVideo ? videoExtractor : audioExtractor;
                ByteBuffer buffer = takeVideo ? videoBuffer : audioBuffer;
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    if (takeVideo) {
                        videoDone = true;
                    } else {
                        audioDone = true;
                    }
                    continue;
                }
                int flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                info.set(0, sampleSize, extractor.getSampleTime(), flags);
                muxer.writeSampleData(takeVideo ? videoOut : audioOut, buffer, info);
                extractor.advance();
            }
            muxer.stop();
            started = false;
        } catch (RuntimeException e) {
            throw new IOException("Join failed: " + e, e);
        } finally {
            videoExtractor.release();
            audioExtractor.release();
            if (muxer != null) {
                try {
                    if (started) {
                        muxer.stop();
                    }
                } catch (RuntimeException ignored) {
                    // already failed
                }
                try {
                    muxer.release();
                } catch (RuntimeException ignored) {
                    // not started
                }
            }
        }
        if (target.length() == 0) {
            throw new IOException("Join produced an empty file");
        }
    }

    private static int findTrack(MediaExtractor extractor, String mimePrefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(mimePrefix)) {
                return i;
            }
        }
        return -1;
    }

    private static int bufferSize(android.media.MediaFormat format, int fallback) {
        return format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)
                ? Math.max(format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE), fallback) : fallback;
    }

    /**
     * YouTube's audio files are laid out for streaming (fragmented MP4), which some players and editors don't open.
     * Rewritten as a plain M4A; false keeps the original.
     */
    private static boolean remuxAudio(File source, File target) {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            extractor.setDataSource(source.getPath());
            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    break;
                }
            }
            if (track == -1) {
                return false;
            }
            extractor.selectTrack(track);
            android.media.MediaFormat format = extractor.getTrackFormat(track);
            int bufferSize = format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)
                    ? Math.max(format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE), 256 * 1024) : 1024 * 1024;

            muxer = new MediaMuxer(target.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outTrack = muxer.addTrack(format);
            muxer.start();

            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int size;
            while ((size = extractor.readSampleData(buffer, 0)) >= 0) {
                int flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                info.set(0, size, extractor.getSampleTime(), flags);
                muxer.writeSampleData(outTrack, buffer, info);
                extractor.advance();
            }
            muxer.stop();
            return target.length() > 0;
        } catch (Exception e) {
            Log.e(TAG, "Audio remux failed, keeping the original: " + e);
            return false;
        } finally {
            extractor.release();
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (Exception ignored) {
                    // not started
                }
            }
        }
    }

    /** Into Download/SmartTube, where the Files app and players find it */
    private static Uri save(Context app, File file, String name, String mime) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver resolver = app.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("Can't create the file in Download");
            }
            try (InputStream in = new FileInputStream(file); OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) {
                    throw new IOException("Can't write the file in Download");
                }
                copy(in, out);
            } catch (IOException e) {
                resolver.delete(uri, null, null);
                throw e;
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return uri;
        }

        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Can't create " + folder);
        }
        File target = new File(folder, name);
        for (int i = 1; target.exists(); i++) {
            int dot = name.lastIndexOf('.');
            target = new File(folder, name.substring(0, dot) + " (" + i + ")" + name.substring(dot));
        }
        try (InputStream in = new FileInputStream(file); OutputStream out = new FileOutputStream(target)) {
            copy(in, out);
        }
        MediaScannerConnection.scanFile(app, new String[]{target.getPath()}, new String[]{mime}, null);
        return null; // no shareable link for a plain file on old Android: the notification just says where it is
    }

    // endregion

    // region Notifications

    private static void createChannel(Context app) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL, app.getString(R.string.phone_download_channel),
                        NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
        }
    }

    private static void showProgress(Context app, int id, NotificationCompat.Builder progress, long done, long total) {
        if (total > 0) {
            int percent = (int) (done * 100 / total);
            progress.setProgress(100, percent, false)
                    .setContentText(app.getString(R.string.phone_download_progress, sizeText(done), sizeText(total)));
        } else {
            progress.setProgress(0, 0, true).setContentText(sizeText(done));
        }
        notify(app, id, progress);
    }

    private static void done(Context app, int id, String title, Uri saved, String mime) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(app.getString(R.string.phone_download_done))
                .setAutoCancel(true);
        if (saved != null) {
            Intent open = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(saved, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            builder.setContentIntent(PendingIntent.getActivity(app, id, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        notify(app, id, builder);
        MAIN.post(() -> Toast.makeText(app, app.getString(R.string.phone_download_done), Toast.LENGTH_SHORT).show());
    }

    private static void failed(Context app, int id, String title) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(app.getString(R.string.phone_download_failed))
                .setAutoCancel(true);
        notify(app, id, builder);
        MAIN.post(() -> Toast.makeText(app, app.getString(R.string.phone_download_failed), Toast.LENGTH_LONG).show());
    }

    private static void notify(Context app, int id, NotificationCompat.Builder builder) {
        if (Build.VERSION.SDK_INT >= 33
                && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return; // the toasts still say when it starts and ends
        }
        try {
            NotificationManagerCompat.from(app).notify(id, builder.build());
        } catch (SecurityException ignored) {
            // notifications blocked
        }
    }

    // endregion

    // region Helpers

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /** "bytes 0-1023/34567" → 34567 */
    private static long totalFromContentRange(String contentRange) {
        if (contentRange == null) {
            return -1;
        }
        int slash = contentRange.lastIndexOf('/');
        return slash != -1 ? parseSize(contentRange.substring(slash + 1).trim()) : -1;
    }

    private static long parseSize(String value) {
        try {
            return value != null ? Long.parseLong(value) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long bitrate(MediaFormat format) {
        return parseSize(format.getBitrate());
    }

    /** The "p" of the quality: the shorter side, so a portrait 1080x1920 video counts as 1080p */
    private static int lines(MediaFormat format) {
        int width = format.getWidth();
        int height = format.getHeight();
        return width > 0 && height > 0 ? Math.min(width, height) : height;
    }

    private static long fps(MediaFormat format) {
        return parseSize(format.getFps());
    }

    private static String sizeText(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.2f GB", bytes / (1024f * 1024 * 1024));
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024));
    }

    /** The video title as a file name (no characters file systems refuse) */
    private static String fileName(String title) {
        String name = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.length() > 100) {
            name = name.substring(0, 100).trim();
        }
        return name.isEmpty() ? "video" : name;
    }

    // endregion
}
