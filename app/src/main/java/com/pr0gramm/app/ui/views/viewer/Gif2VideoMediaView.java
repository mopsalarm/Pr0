package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.services.gif.GifToVideoService;
import com.pr0gramm.app.services.proxy.ProxyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import rx.Subscription;
import rx.subscriptions.Subscriptions;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;

/**
 */
@SuppressLint("ViewConstructor")
public class Gif2VideoMediaView extends ProxyMediaView {
    private static final Logger logger = LoggerFactory.getLogger("Gif2VideoMediaView");

    private Subscription conversion = Subscriptions.unsubscribed();

    @Inject
    GifToVideoService gifToVideoService;

    @Inject
    ProxyService proxyService;

    public Gif2VideoMediaView(Activity context, MediaUri url, Runnable onViewListener) {
        super(context, url, onViewListener);
        startWebmConversion(url);
    }

    private void startWebmConversion(MediaUri url) {
        logger.info("Start converting gif to webm");

        // normalize to http://
        String gifUrl = url.toString().replace("https://", "http://");

        // and start conversion!
        conversion = gifToVideoService.toVideo(gifUrl).compose(bindView()).subscribe(result -> {
            checkMainThread();

            // create the correct child-viewer
            MediaView mediaView;
            if (result.getVideoUrl().isPresent()) {
                logger.info("Converted successfully, replace with video player");
                MediaUri webm = url.withUri(Uri.parse(result.getVideoUrl().get()), MediaUri.MediaType.VIDEO);
                mediaView = MediaViews.newInstance((Activity) getContext(), webm, this::onMediaShown);

            } else {
                logger.info("Conversion did not work, showing gif");
                mediaView = new GifMediaView((Activity) getContext(), url, this::onMediaShown);
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
        conversion.unsubscribe();
        super.onDestroy();
    }
}
