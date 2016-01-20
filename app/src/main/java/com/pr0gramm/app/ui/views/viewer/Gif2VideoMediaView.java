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

import rx.Observable;

import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;

/**
 */
@SuppressLint("ViewConstructor")
public class Gif2VideoMediaView extends ProxyMediaView {
    private static final Logger logger = LoggerFactory.getLogger("Gif2VideoMediaView");

    @Inject
    GifToVideoService gifToVideoService;

    @Inject
    ProxyService proxyService;

    public Gif2VideoMediaView(Activity context, MediaUri url, Runnable onViewListener) {
        super(context, url, onViewListener);
        startWebmConversion();
    }

    private void startWebmConversion() {
        logger.info("Start converting gif to webm");

        // normalize to http://
        String gifUrl = getMediaUri().toString().replace("https://", "http://");

        // and start conversion!
        gifToVideoService.toVideo(gifUrl)
                .compose(backgroundBindView())
                .onErrorResumeNext(Observable.just(new GifToVideoService.Result(gifUrl)))
                .limit(1)
                .subscribe(this::handleConversionResult);
    }

    private void handleConversionResult(GifToVideoService.Result result) {
        checkMainThread();

        // create the correct child-viewer
        MediaView mediaView;
        if (result.getVideoUrl().isPresent()) {
            logger.info("Converted successfully, replace with video player");
            Uri videoUri = Uri.parse(result.getVideoUrl().get());
            MediaUri webm = getMediaUri().withUri(videoUri, MediaUri.MediaType.VIDEO);
            mediaView = MediaViews.newInstance((Activity) getContext(), webm, this::onMediaShown);

        } else {
            logger.info("Conversion did not work, showing gif");
            mediaView = new GifMediaView((Activity) getContext(), getMediaUri(), this::onMediaShown);
        }

        mediaView.removePreviewImage();
        setChild(mediaView);
    }

    @Override
    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }
}
