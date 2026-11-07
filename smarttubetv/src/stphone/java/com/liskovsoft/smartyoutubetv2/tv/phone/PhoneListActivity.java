package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Shared screen for the channel page and the channel uploads list: back arrow, channel name, feed.
 */
public abstract class PhoneListActivity extends PhoneActivity implements FeedAdapter.Listener {
    private TextView mTitle;
    private ImageView mAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_activity_list);

        applyInsets(findViewById(R.id.phone_root), findViewById(R.id.phone_top_bar), mListBottomInsetTarget());
        setupList(this);

        mTitle = findViewById(R.id.phone_screen_title);
        mAvatar = findViewById(R.id.phone_avatar);
        findViewById(R.id.phone_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.phone_search).setOnClickListener(v -> SearchPresenter.instance(this).startSearch(null));
    }

    private View mListBottomInsetTarget() {
        return findViewById(R.id.phone_list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHeader();
    }

    protected abstract Video getChannel();

    protected void updateHeader() {
        Video channel = getChannel();
        if (channel == null) {
            mTitle.setText(R.string.phone_channel);
            mAvatar.setVisibility(View.GONE);
            return;
        }

        String name = !TextUtils.isEmpty(channel.getAuthor()) ? channel.getAuthor() : channel.getTitle();
        mTitle.setText(!TextUtils.isEmpty(name) ? name : getString(R.string.phone_channel));

        if (FeedAdapter.isChannelItem(channel) && !TextUtils.isEmpty(channel.getCardImageUrl())) {
            mAvatar.setVisibility(View.VISIBLE);
            PhoneUi.loadAvatar(mAvatar, channel.getCardImageUrl());
        } else {
            mAvatar.setVisibility(View.GONE);
        }
    }
}
