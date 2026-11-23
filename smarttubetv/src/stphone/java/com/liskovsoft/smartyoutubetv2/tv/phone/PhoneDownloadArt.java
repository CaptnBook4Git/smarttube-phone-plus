package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** A small persistent cover for each audio download, including when Glide's cache is cleared. */
final class PhoneDownloadArt {
    static File file(Context context, Uri uri) {
        UUID key = UUID.nameUUIDFromBytes(uri.toString().getBytes(StandardCharsets.UTF_8));
        return new File(new File(context.getFilesDir(), "download-covers"), key + ".jpg");
    }

    // Called on the download worker, before the completed track is announced.
    static void save(Context app, Uri uri, String videoId) {
        if (videoId == null) return;
        File cover = file(app, uri);
        File dir = cover.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        FutureTarget<Bitmap> request = Glide.with(app).asBitmap()
                .load("https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg").submit(512, 512);
        try {
            Bitmap bitmap = request.get(5, TimeUnit.SECONDS);
            try (FileOutputStream out = new FileOutputStream(cover)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
            }
        } catch (Exception error) {
            cover.delete();
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            Glide.with(app).clear(request);
        }
    }

    static boolean isLocal(String id) {
        return id != null && (id.startsWith("file://") || id.startsWith("content://"));
    }

    static void bind(ImageView view, Uri uri) {
        Glide.with(view).load(file(view.getContext(), uri)).centerCrop()
                .placeholder(R.drawable.phone_download_record).error(R.drawable.phone_download_record).into(view);
    }
}
