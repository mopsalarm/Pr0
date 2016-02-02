package com.pr0gramm.app.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.view.Window;

import com.google.common.util.concurrent.Runnables;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.services.InMemoryCacheService;
import com.pr0gramm.app.ui.PreviewInfo;
import com.pr0gramm.app.ui.views.viewer.MediaUri;
import com.pr0gramm.app.ui.views.viewer.MediaView;
import com.pr0gramm.app.ui.views.viewer.MediaViews;

/**
 */
public class PopupPlayer {
    private PopupPlayer() {
    }

    public static Dialog newInstance(Activity activity, InMemoryCacheService cacheService, FeedItem item) {
        MediaUri uri = MediaUri.of(activity, item);
        MediaView mediaView = MediaViews.newInstance(activity, uri, Runnables.doNothing());

        PreviewInfo previewInfo = cacheService.getPreviewInfo(item);
        if (previewInfo != null) {
            mediaView.setPreviewImage(previewInfo, null);
        } else {
            mediaView.setViewAspect(16f / 9f);
        }

        mediaView.onTransitionEnds();
        mediaView.onResume();
        mediaView.playMedia();

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(mediaView);
        dialog.setOnDismissListener(di -> {
            mediaView.stopMedia();
            mediaView.onPause();
        });

        dialog.show();
        return dialog;
    }
}
