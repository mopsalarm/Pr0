package com.pr0gramm.app.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.pr0gramm.app.GifToWebmService;

import javax.inject.Inject;

import static com.pr0gramm.app.AndroidUtility.checkMainThread;

/**
 */
@SuppressLint("ViewConstructor")
public class Gif2WebmViewerFragment extends ProxyViewerFragment {
    @Inject
    private GifToWebmService gifToWebmService;

    public Gif2WebmViewerFragment(Context context, Binder binder, String url) {
        super(context, binder, url);

        Log.i("Gif2Webm", "Start converting gif to webm");
        binder.bind(gifToWebmService.convertToWebm(url)).subscribe(result -> {
            checkMainThread();

            // create the correct child-viewer
            ViewerFragment child;
            if (result.isWebm()) {
                Log.i("Gif2Webm", "Converted successfully, replace with webm player");
                child = ViewerFragment.newInstance(getContext(), binder, result.getUrl());

            } else {
                Log.i("Gif2Webm", "Conversion did not work, showing gif");
                child = new GifViewerFragment(getContext(), binder, result.getUrl());
            }

            setChild(child);
        });
    }
}
