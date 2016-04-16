package com.pr0gramm.app.ui.views.viewer;

import android.app.Activity;

import com.pr0gramm.app.Settings;
import com.pr0gramm.app.vpx.WebmMediaPlayer;
import com.trello.rxlifecycle.FragmentEvent;

import rx.Observable;

import static com.trello.rxlifecycle.RxLifecycle.bindView;

/**
 * This class provides static methods to create a new
 * {@link com.pr0gramm.app.ui.views.viewer.MediaView} for some url.
 */
public class MediaViews {
    private MediaViews() {
    }

    /**
     * Instantiates one of the viewer fragments subclasses depending
     * on the provided url.
     *
     * @param uri The url that should be displayed.
     * @return A new {@link MediaView} instance.
     */
    public static MediaView newInstance(Activity activity, MediaUri uri, Runnable onViewListener) {
        Settings settings = Settings.of(activity);

        // handle delay urls first.
        if (uri.hasDelayFlag()) {
            return new DelayedMediaView(activity, uri.withDelay(false), onViewListener);
        }

        if (!uri.isLocal() && settings.useProxy()) {
            uri = uri.withProxy(true);
        }

        MediaView result;
        if (uri.getMediaType() == MediaUri.MediaType.VIDEO) {
            if (shouldUseSoftwareDecoder(uri, settings)) {
                result = new SoftwareVideoMediaView(activity,
                        uri.withProxy(uri.hasProxyFlag()),
                        onViewListener);
            } else {
                result = new VideoMediaView(activity, uri, onViewListener);
            }

        } else if (uri.getMediaType() == MediaUri.MediaType.GIF) {
            if (shouldUseGifToWebm(uri, settings)) {
                result = new Gif2VideoMediaView(activity, uri, onViewListener);
            } else {
                result = new GifMediaView(activity, uri, onViewListener);
            }

        } else {
            result = new ImageMediaView(activity, uri, onViewListener);
        }

        return result;
    }

    private static boolean canUseWebmDecoder(MediaUri uri, Settings settings) {
        return uri.getBaseUri().getPath().endsWith(".webm")
                && WebmMediaPlayer.isAvailable();
    }

    private static boolean shouldUseGifToWebm(MediaUri uri, Settings settings) {
        return !uri.isLocal() && settings.convertGifToWebm();
    }

    private static boolean shouldUseSoftwareDecoder(MediaUri uri, Settings settings) {
        return settings.useSoftwareDecoder() && canUseWebmDecoder(uri, settings);

    }

    public static void adaptFragmentLifecycle(Observable<FragmentEvent> lifecycle, MediaView view) {
        lifecycle.compose(bindView(view)).subscribe(event -> {
            if (event == FragmentEvent.RESUME)
                view.onResume();

            if (event == FragmentEvent.PAUSE)
                view.onPause();
        });
    }
}
