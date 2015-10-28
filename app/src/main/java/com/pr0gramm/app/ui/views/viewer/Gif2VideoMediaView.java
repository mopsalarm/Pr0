package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.services.gif.GifToVideoService;
import com.pr0gramm.app.services.proxy.ProxyService;

import javax.inject.Inject;

import rx.Subscription;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;

/**
 */
@SuppressLint("ViewConstructor")
public class Gif2VideoMediaView extends ProxyMediaView {
    private Subscription conversion;

    @Inject
    GifToVideoService gifToVideoService;

    @Inject
    ProxyService proxyService;

    public Gif2VideoMediaView(Activity context, Binder binder, MediaUri url, Runnable onViewListener) {
        super(context, binder, url, onViewListener);
        startWebmConversion(binder, url);
    }

    private void startWebmConversion(Binder binder, MediaUri url) {
        logger.info("Start converting gif to webm");

        // normalize to http://
        String gifUrl = url.toString().replace("https://", "http://");

        // and start conversion!
        conversion = gifToVideoService.toVideo(gifUrl).compose(binder.get()).subscribe(result -> {
            checkMainThread();

            // create the correct child-viewer
            MediaView mediaView;
            if (result.getVideoUrl().isPresent()) {
                logger.info("Converted successfully, replace with video player");
                MediaUri webm = url.withUri(Uri.parse(result.getVideoUrl().get()), MediaUri.MediaType.VIDEO);
                mediaView = MediaViews.newInstance((Activity) getContext(), binder, webm, this::onMediaShown);

            } else {
                logger.info("Conversion did not work, showing gif");
                mediaView = new GifMediaView((Activity) getContext(), binder, url, this::onMediaShown);
            }

            mediaView.removePreviewImage();
            setChild(mediaView);

        }, defaultOnError());
    }

    @Override
    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void onDestroy() {
        if (conversion != null)
            conversion.unsubscribe();

        super.onDestroy();
    }
}
