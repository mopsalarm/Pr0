package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.pr0gramm.app.services.GifToWebmService;

import javax.inject.Inject;

import rx.Subscription;

import static com.pr0gramm.app.AndroidUtility.checkMainThread;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;

/**
 */
@SuppressLint("ViewConstructor")
public class Gif2WebmMediaView extends ProxyMediaView {
    private Subscription conversion;

    @Inject
    private GifToWebmService gifToWebmService;

    public Gif2WebmMediaView(Context context, Binder binder, String url) {
        super(context, binder, url);
        startWebmConversion(binder, url);
    }

    private void startWebmConversion(Binder binder, String url) {
        Log.i("Gif2Webm", "Start converting gif to webm");
        conversion = binder.bind(gifToWebmService.convertToWebm(url)).subscribe(result -> {
            checkMainThread();

            // create the correct child-viewer
            MediaView child;
            if (result.isWebm()) {
                Log.i("Gif2Webm", "Converted successfully, replace with webm player");
                child = MediaView.newInstance(getContext(), binder, result.getUrl());

            } else {
                Log.i("Gif2Webm", "Conversion did not work, showing gif");
                child = new GifMediaView(getContext(), binder, result.getUrl());
            }

            setChild(child);
        }, defaultOnError());
    }

    @Override
    public void onDestroy() {
        if (conversion != null)
            conversion.unsubscribe();

        super.onDestroy();
    }
}
