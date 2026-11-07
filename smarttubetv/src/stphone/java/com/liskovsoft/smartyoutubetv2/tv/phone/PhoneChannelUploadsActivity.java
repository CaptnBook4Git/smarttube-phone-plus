package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;

/**
 * A single list of videos: channel uploads, a playlist, a mix.
 */
public class PhoneChannelUploadsActivity extends PhoneListActivity implements ChannelUploadsView {
    private ChannelUploadsPresenter mPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter.setMode(FeedAdapter.MODE_FLAT);

        mPresenter = ChannelUploadsPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public void finishReally() {
        super.finishReally();
        mPresenter.onFinish();
    }

    @Override
    protected Video getChannel() {
        return mPresenter != null ? mPresenter.getChannel() : null;
    }

    // region ChannelUploadsView

    @Override
    public void update(VideoGroup videoGroup) {
        hideMessage();
        mAdapter.update(videoGroup);
        updateHeader();
        checkScrollEndLater();
    }

    @Override
    public void showProgressBar(boolean show) {
        showProgress(show);
    }

    @Override
    public void clear() {
        mAdapter.setMode(FeedAdapter.MODE_FLAT);
    }

    // endregion

    // region Listener

    @Override
    public void onVideoClicked(Video video) {
        mPresenter.onVideoItemSelected(video);
        mPresenter.onVideoItemClicked(video);
    }

    @Override
    public void onVideoLongClicked(Video video) {
        mPresenter.onVideoItemSelected(video);
        mPresenter.onVideoItemLongClicked(video);
    }

    @Override
    public void onStripClicked(Video channel) {
        onVideoClicked(channel);
    }

    @Override
    public void onShelfScrollEnd(Video lastVideo) {
        if (!mPresenter.hasPendingActions()) {
            mPresenter.onScrollEnd(lastVideo);
        }
    }

    @Override
    protected boolean hasPendingActions() {
        return mPresenter.hasPendingActions();
    }

    @Override
    protected void onListScrollEnd(Video lastVideo) {
        mPresenter.onScrollEnd(lastVideo);
    }

    // endregion
}
