package com.pr0gramm.app.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Window;

import com.f2prateek.dart.InjectExtra;
import com.google.common.util.concurrent.Runnables;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.services.InMemoryCacheService;
import com.pr0gramm.app.ui.PreviewInfo;
import com.pr0gramm.app.ui.base.BaseDialogFragment;
import com.pr0gramm.app.ui.views.viewer.MediaUri;
import com.pr0gramm.app.ui.views.viewer.MediaView;
import com.pr0gramm.app.ui.views.viewer.MediaViews;

import javax.inject.Inject;

import proguard.annotation.KeepName;
import proguard.annotation.KeepPublicClassMemberNames;

/**
 */
@KeepPublicClassMemberNames
public class PopupPlayer extends BaseDialogFragment {
    private static final String KEY_ITEM = "BaseDialogFragment.item";

    // @InjectExtra(KEY_ITEM)
    FeedItem item;

    @Inject
    InMemoryCacheService inMemoryCacheService;
    private MediaView mediaView;

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        item = getArguments().getParcelable(KEY_ITEM);

        MediaUri uri = MediaUri.of(getActivity(), item);
        mediaView = MediaViews.newInstance(getActivity(), uri, Runnables.doNothing());

        PreviewInfo previewInfo = inMemoryCacheService.getPreviewInfo(item);
        if (previewInfo != null) {
            mediaView.setPreviewImage(previewInfo, null);
        } else {
            mediaView.setViewAspect(16f / 9f);
        }

        mediaView.onTransitionEnds();
        mediaView.onStart();
        mediaView.onResume();
        mediaView.playMedia();

        Dialog dialog = new Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(mediaView);
        return dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

        mediaView.stopMedia();
        mediaView.onPause();
        mediaView.onStop();
        mediaView.onDestroy();
    }

    public static PopupPlayer newInstance(Context context, FeedItem item) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(KEY_ITEM, item);

        PopupPlayer instance = new PopupPlayer();
        instance.setArguments(arguments);
        return instance;
    }
}
