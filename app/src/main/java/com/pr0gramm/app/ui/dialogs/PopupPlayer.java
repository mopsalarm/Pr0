package com.pr0gramm.app.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.view.Window;

import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.ui.PreviewInfo;
import com.pr0gramm.app.ui.views.viewer.ImmutableConfig;
import com.pr0gramm.app.ui.views.viewer.MediaUri;
import com.pr0gramm.app.ui.views.viewer.MediaView;
import com.pr0gramm.app.ui.views.viewer.MediaViews;

/**
 */
public class PopupPlayer {
    private PopupPlayer() {
    }

    public static Dialog newInstance(Activity activity, FeedItem item) {
        MediaUri uri = MediaUri.of(activity, item);
        MediaView mediaView = MediaViews.newInstance(ImmutableConfig.of(activity, uri)
                .withAudio(item.getAudio())
                .withPreviewInfo(PreviewInfo.of(activity, item)));

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(mediaView);

        dialog.setOnShowListener(di -> {
            mediaView.onResume();
            mediaView.playMedia();
        });

        dialog.setOnDismissListener(di -> {
            mediaView.stopMedia();
            mediaView.onPause();
        });

        dialog.show();
        return dialog;
    }
}
