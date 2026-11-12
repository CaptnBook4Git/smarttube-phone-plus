package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Settings, About StreamTube: the build, the project StreamTube is built on, and its source code.
 */
final class PhoneAbout {
    private static final String SMARTTUBE_URL = "https://github.com/yuliskov/SmartTube";
    private static final String SOURCE_URL = "https://github.com/" + PhoneUpdater.REPO;

    private PhoneAbout() {
    }

    static void show(Activity activity) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(activity);

        String version = PhoneUpdater.currentVersionName(activity);
        int build = PhoneUpdater.currentBuild(activity);
        String buildText = build > 0 ? activity.getString(R.string.phone_about_build, build) : version;
        dialog.appendSingleButton(UiOptionItem.from(activity.getString(R.string.phone_brand_name), buildText,
                item -> AppDialogPresenter.instance(activity).closeDialog()));
        dialog.appendSingleButton(UiOptionItem.from(activity.getString(R.string.phone_about_built_on),
                activity.getString(R.string.phone_about_license), item -> open(activity, SMARTTUBE_URL)));
        dialog.appendSingleButton(UiOptionItem.from(activity.getString(R.string.phone_about_source),
                SOURCE_URL.replace("https://", ""), item -> open(activity, SOURCE_URL)));

        dialog.showDialog(activity.getString(R.string.phone_about_title));
    }

    private static void open(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(activity, url, Toast.LENGTH_LONG).show();
        }
    }
}
