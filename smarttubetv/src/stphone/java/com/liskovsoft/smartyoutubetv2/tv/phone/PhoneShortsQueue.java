package com.liskovsoft.smartyoutubetv2.tv.phone;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * The Shorts the user is swiping through (the row or grid the first Short came from, plus every batch loaded after it).
 * Swiping only ever moves inside this list, so it never lands on a regular video.
 */
final class PhoneShortsQueue {
    private static final List<Video> sVideos = new ArrayList<>();

    private PhoneShortsQueue() {
    }

    static synchronized void set(VideoGroup group) {
        sVideos.clear();
        append(group);
    }

    /** A further batch of the same Shorts list (loaded while the user swipes) */
    static synchronized void append(VideoGroup group) {
        if (group == null || group.getVideos() == null) {
            return;
        }
        // The group comes from the Shorts section or a Shorts row: every item is a Short
        // (items there don't always carry the isShorts flag)
        for (Video video : new ArrayList<>(group.getVideos())) {
            if (video != null && video.videoId != null && indexOf(sVideos, video) < 0) {
                sVideos.add(video);
            }
        }
    }

    static synchronized boolean isActive() {
        return !sVideos.isEmpty();
    }

    static synchronized List<Video> videos() {
        return new ArrayList<>(sVideos);
    }

    static boolean contains(Video video) {
        return indexOf(videos(), video) >= 0;
    }

    static Video next(Video current) {
        List<Video> list = videos();
        int index = indexOf(list, current);
        if (index >= 0 && index + 1 < list.size()) {
            return list.get(index + 1);
        }
        return null;
    }

    static Video previous(Video current) {
        List<Video> list = videos();
        int index = indexOf(list, current);
        return index > 0 ? list.get(index - 1) : null;
    }

    static boolean isNearEnd(Video current) {
        List<Video> list = videos();
        int index = indexOf(list, current);
        return index >= 0 && index >= list.size() - 3;
    }

    static Video last() {
        List<Video> list = videos();
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    private static int indexOf(List<Video> list, Video video) {
        if (video == null || video.videoId == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (video.videoId.equals(list.get(i).videoId)) {
                return i;
            }
        }
        return -1;
    }
}
