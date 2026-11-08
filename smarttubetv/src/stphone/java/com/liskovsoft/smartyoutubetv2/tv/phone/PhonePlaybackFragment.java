package com.liskovsoft.smartyoutubetv2.tv.phone;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;

/**
 * SmartTube's player with a phone hook: in portrait the TV overlay (title, control rows, suggestion rows) stays hidden
 * and the phone screen shows its own controls and details instead. In landscape it is the normal TV player.
 */
public class PhonePlaybackFragment extends PlaybackFragment {
    public interface Host {
        boolean isCompactPlayer();

        void onPlayerControlsVisible(boolean visible);

        void onPlayerVideo(Video video);

        void onPlayerSuggestions(VideoGroup group);

        void onPlayerSuggestionsCleared();

        void onPlayerButtonState(int buttonId, int buttonState);

        void onPlayerChannelIcon(String iconUrl);
    }

    @Override
    public void setChannelIcon(String iconUrl) {
        super.setChannelIcon(iconUrl);
        Host host = getPhoneHost();
        if (host != null) {
            host.onPlayerChannelIcon(iconUrl);
        }
    }

    @Override
    public void onViewCreated(@androidx.annotation.NonNull android.view.View view, @androidx.annotation.Nullable android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        hideTvLayers(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        hideTvLayers(getView());
    }

    /**
     * The TV overlay (title, control rows, Recommended, Chapters) and its dim layer never show on the phone.
     */
    private static void hideTvLayers(android.view.View root) {
        if (root == null) {
            return;
        }
        android.view.View dock = root.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.playback_controls_dock);
        if (dock != null) {
            dock.setVisibility(android.view.View.GONE);
        }
        android.view.View background = root.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.playback_fragment_background);
        if (background != null) {
            background.setVisibility(android.view.View.GONE);
        }
    }

    /**
     * SmartTube's own media session (connected to the player): used for the background playback notification.
     */
    public android.support.v4.media.session.MediaSessionCompat.Token getMediaSessionToken() {
        try {
            java.lang.reflect.Field field = com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment.class.getDeclaredField("mMediaSession");
            field.setAccessible(true);
            Object session = field.get(this);
            return session instanceof android.support.v4.media.session.MediaSessionCompat
                    ? ((android.support.v4.media.session.MediaSessionCompat) session).getSessionToken() : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private Host getPhoneHost() {
        return getActivity() instanceof Host ? (Host) getActivity() : null;
    }

    private boolean isCompact() {
        Host host = getPhoneHost();
        return host != null && host.isCompactPlayer();
    }

    @Override
    public void showControlsOverlay(boolean runAnimation) {
        if (isCompact()) {
            Host host = getPhoneHost();
            if (host != null) {
                host.onPlayerControlsVisible(true);
            }
            return;
        }
        super.showControlsOverlay(runAnimation);
    }

    @Override
    public void hideControlsOverlay(boolean runAnimation) {
        super.hideControlsOverlay(runAnimation);
        Host host = getPhoneHost();
        if (host != null && isCompact()) {
            host.onPlayerControlsVisible(false);
        }
    }

    @Override
    public void setVideo(Video video) {
        super.setVideo(video);
        Host host = getPhoneHost();
        if (host != null) {
            host.onPlayerVideo(video);
        }
    }

    @Override
    public void updateSuggestions(VideoGroup group) {
        super.updateSuggestions(group);
        Host host = getPhoneHost();
        if (host != null) {
            host.onPlayerSuggestions(group);
        }
    }

    @Override
    public void clearSuggestions() {
        super.clearSuggestions();
        Host host = getPhoneHost();
        if (host != null) {
            host.onPlayerSuggestionsCleared();
        }
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        super.setButtonState(buttonId, buttonState);
        Host host = getPhoneHost();
        if (host != null) {
            host.onPlayerButtonState(buttonId, buttonState);
        }
    }

    /**
     * Hide the TV overlay right away (used when switching back to the portrait player).
     */
    public void hideTvOverlay() {
        super.hideControlsOverlay(false);
    }
}
