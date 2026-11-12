package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.smartyoutubetv2.common.R;

public abstract class AbstractCommentsReceiver implements CommentsReceiver {
    private final Context mContext;
    private Callback mCallback;

    public AbstractCommentsReceiver(Context context) {
        mContext = context.getApplicationContext(); // Mem leak fix (holds PlaybackActivity)
    }

    @Override
    public void addCommentGroup(CommentGroup commentGroup) {
        if (mCallback != null) {
            mCallback.onCommentGroup(commentGroup);
        }
    }

    @Override
    public void loadBackup(Backup backup) {
        if (mCallback != null) {
            mCallback.onBackup(backup);
        }
    }

    @Override
    public void sync(CommentItem commentItem) {
        if (mCallback != null) {
            mCallback.onSync(commentItem);
        }
    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    /** Phone: the comments total to show next to the title ("24", "2.4K"), or null */
    public String getCommentsCount() {
        return null;
    }

    /** Phone: whether these comments can also be listed Newest first */
    public boolean canSortByNewest() {
        return false;
    }

    public boolean isSortedByNewest() {
        return false;
    }

    /** Phone: Top (false) or Newest (true) order for the next {@link #onStart()} */
    public void setSortedByNewest(boolean newest) {
    }

    /** Phone: the video a new comment goes to; null when these comments can't be written to */
    public String getVideoId() {
        return null;
    }

    /** Phone: the top-level comment a new reply goes to; null for the video's own comments */
    public String getParentCommentId() {
        return null;
    }

    @Override
    public void onLoadMore(CommentGroup commentGroup) {

    }

    @Override
    public void onCommentClicked(CommentItem commentItem) {

    }

    @Override
    public void onCommentLongClicked(CommentItem commentItem) {
        
    }

    @Override
    public void onFinish(Backup backup) {
        setCallback(null); // Mem leak fix (holds AppDialogActivity)
    }

    @Override
    public String getLoadingMessage() {
        if (mContext == null) {
            return null;
        }

        return mContext.getString(R.string.loading);
    }

    @Override
    public String getErrorMessage() {
        return mContext.getString(R.string.section_is_empty);
    }
}
