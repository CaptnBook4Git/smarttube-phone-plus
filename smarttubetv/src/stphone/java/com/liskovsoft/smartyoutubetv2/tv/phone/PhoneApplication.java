package com.liskovsoft.smartyoutubetv2.tv.phone;


import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.smartyoutubetv2.tv.ui.adddevice.AddDeviceActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;
import com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser.WebBrowserActivity;

/**
 * Phone build entry point: same app, but home, search and channel screens use the phone UI.
 */
public class PhoneApplication extends MainApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReport.install(this);
        setupPhoneViews();
        applyPhoneDefaults();
    }

    private void applyPhoneDefaults() {
        android.content.SharedPreferences prefs = getSharedPreferences("phone_defaults", MODE_PRIVATE);
        if (!prefs.getBoolean("background_playback_v1", false)) {
            com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData playerData =
                    com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData.instance(this);
            if (playerData.getBackgroundMode() == com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants.BACKGROUND_MODE_DEFAULT) {
                playerData.setBackgroundMode(com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants.BACKGROUND_MODE_SOUND);
            }
            prefs.edit().putBoolean("background_playback_v1", true).apply();
        }
    }

    private void setupPhoneViews() {
        ViewManager viewManager = ViewManager.instance(this);

        viewManager.setRoot(PhoneBrowseActivity.class);
        viewManager.register(BrowseView.class, PhoneBrowseActivity.class); // root, no parent
        viewManager.register(SearchView.class, PhoneSearchActivity.class, PhoneBrowseActivity.class);
        viewManager.register(ChannelView.class, PhoneChannelActivity.class, PhoneBrowseActivity.class);
        viewManager.register(ChannelUploadsView.class, PhoneChannelUploadsActivity.class, PhoneBrowseActivity.class);

        // Remaining TV screens now return to the phone home
        viewManager.register(PlaybackView.class, PhonePlaybackActivity.class, PhoneBrowseActivity.class);
        viewManager.register(AppDialogView.class, PhoneDialogActivity.class, PhoneBrowseActivity.class);
        viewManager.register(SignInView.class, PhoneSignInActivity.class, PhoneBrowseActivity.class);
        viewManager.register(AddDeviceView.class, AddDeviceActivity.class, PhoneBrowseActivity.class);
        viewManager.register(WebBrowserView.class, WebBrowserActivity.class, PhoneBrowseActivity.class);
    }
}
