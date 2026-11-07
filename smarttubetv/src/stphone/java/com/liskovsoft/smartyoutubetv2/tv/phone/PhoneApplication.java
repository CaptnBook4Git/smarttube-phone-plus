package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.os.Build;

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
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivityOpaque;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.signin.SignInActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser.WebBrowserActivity;

/**
 * Phone build entry point: same app, but home, search and channel screens use the phone UI.
 */
public class PhoneApplication extends MainApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        setupPhoneViews();
    }

    private void setupPhoneViews() {
        ViewManager viewManager = ViewManager.instance(this);
        Class<? extends AppDialogActivity> dialogClass = Build.VERSION.SDK_INT == 26 ? AppDialogActivityOpaque.class : AppDialogActivity.class;

        viewManager.setRoot(PhoneBrowseActivity.class);
        viewManager.register(BrowseView.class, PhoneBrowseActivity.class); // root, no parent
        viewManager.register(SearchView.class, PhoneSearchActivity.class, PhoneBrowseActivity.class);
        viewManager.register(ChannelView.class, PhoneChannelActivity.class, PhoneBrowseActivity.class);
        viewManager.register(ChannelUploadsView.class, PhoneChannelUploadsActivity.class, PhoneBrowseActivity.class);

        // Remaining TV screens now return to the phone home
        viewManager.register(PlaybackView.class, PlaybackActivity.class, PhoneBrowseActivity.class);
        viewManager.register(AppDialogView.class, dialogClass, PhoneBrowseActivity.class);
        viewManager.register(SignInView.class, SignInActivity.class, PhoneBrowseActivity.class);
        viewManager.register(AddDeviceView.class, AddDeviceActivity.class, PhoneBrowseActivity.class);
        viewManager.register(WebBrowserView.class, WebBrowserActivity.class, PhoneBrowseActivity.class);
    }
}
