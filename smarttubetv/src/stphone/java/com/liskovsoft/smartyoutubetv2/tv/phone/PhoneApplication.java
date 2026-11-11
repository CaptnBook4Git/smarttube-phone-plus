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
        // Updates come from this project's GitHub releases: a manual check opens the update screen. The automatic
        // check at start is done by Home (PhoneUpdater.checkInBackground), so SmartTube's own one does nothing here.
        com.liskovsoft.smartyoutubetv2.common.misc.AppUpdateHook.setHandler((context, forceCheck) -> {
            if (forceCheck) {
                PhoneUpdater.open(context);
            }
        });
    }

    private void applyPhoneDefaults() {
        // Sheets open below the video on phones, so never shrink the video aside for them (a TV layout feature: the TV
        // settings panel covers the right part of the screen)
        com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData tweaks =
                com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData.instance(this);
        if (!tweaks.isDontResizeVideoToFitDialogEnabled()) {
            tweaks.setDontResizeVideoToFitDialogEnabled(true);
        }

        android.content.SharedPreferences prefs = getSharedPreferences("phone_defaults", MODE_PRIVATE);
        if (!prefs.getBoolean("background_playback_v1", false)) {
            com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData playerData =
                    com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData.instance(this);
            if (playerData.getBackgroundMode() == com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants.BACKGROUND_MODE_DEFAULT) {
                playerData.setBackgroundMode(com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants.BACKGROUND_MODE_SOUND);
            }
            prefs.edit().putBoolean("background_playback_v1", true).apply();
        }

        // YouTube notifications (new uploads from subscriptions, replies...) in the Notifications tab.
        // SmartTube for TV hides this section by default.
        if (!prefs.getBoolean("notifications_section_v1", false)) {
            com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService sidebar =
                    com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService.instance(this);
            if (sidebar != null) {
                sidebar.enableSection(com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_NOTIFICATIONS, true);
            }
            prefs.edit().putBoolean("notifications_section_v1", true).apply();
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

        // Search, channels and sign in open on top of the screen in front, in the app's own task. Without this they
        // could open inside the picture-in-picture player's window.
        viewManager.setOpenInCallerTask(PhoneSearchActivity.class, PhoneChannelActivity.class,
                PhoneChannelUploadsActivity.class, PhoneSignInActivity.class);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(android.app.Activity activity) {
                if (android.os.Build.VERSION.SDK_INT < 24 || !activity.isInPictureInPictureMode()) {
                    viewManager.setCallerActivity(activity);
                }
            }

            @Override
            public void onActivityDestroyed(android.app.Activity activity) {
                viewManager.clearCallerActivity(activity);
            }

            @Override
            public void onActivityCreated(android.app.Activity activity, android.os.Bundle state) {
            }

            @Override
            public void onActivityStarted(android.app.Activity activity) {
            }

            @Override
            public void onActivityPaused(android.app.Activity activity) {
            }

            @Override
            public void onActivityStopped(android.app.Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle state) {
            }
        });
    }
}
