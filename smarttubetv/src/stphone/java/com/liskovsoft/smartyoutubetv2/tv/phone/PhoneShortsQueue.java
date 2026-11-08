package com.liskovsoft.smartyoutubetv2.tv.phone;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * The Shorts the user is swiping through (the row or grid the first Short came from).
 * Swiping only ever moves inside this list, so it never lands on a regular video.
 */
final class PhoneShortsQueue {
    private static VideoGroup sGroup;

    private PhoneShortsQueue() {
    }

    static void set(VideoGroup group) {
        sGroup = group;
    }

    static List<Video> videos() {
        List<Video> result = new ArrayList<>();
        if (sGroup != null && sGroup.getVideos() != null) {
            for (Video video : sGroup.getVideos()) {
                // The group comes from the Shorts section or a Shorts row: every item is a Short
                // (items there don't always carry the isShorts flag)
                if (video != null && video.videoId != null) {
                    result.add(video);
                }
            }
        }
        return result;
    }

    static boolean contains(Video video) {
        if (video == null || video.videoId == null) {
            return false;
        }
        for (Video item : videos()) {
            if (video.videoId.equals(item.videoId)) {
                return true;
            }
        }
        return false;
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
