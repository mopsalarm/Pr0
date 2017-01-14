package com.pr0gramm.app.ui.views.viewer;

import com.pr0gramm.app.Settings;
import com.trello.rxlifecycle.android.FragmentEvent;

import rx.Observable;

import static com.trello.rxlifecycle.android.RxLifecycleAndroid.bindView;

/**
 * This class provides static methods to create a new
 * {@link com.pr0gramm.app.ui.views.viewer.MediaView} for some url.
 */
public class MediaViews {
    private MediaViews() {
    }

    /**
     * Instantiates one of the viewer subclasses depending on the provided configuration.
     *
     * @return A new {@link MediaView} instance.
     */
    public static MediaView newInstance(MediaView.Config config_) {
        ImmutableConfig config = ImmutableConfig.copyOf(config_);

        Settings settings = Settings.of(config.activity());

        if (config.audio() && settings.disableAudio())
            config = config.withAudio(false);

        // handle delay urls first.
        if (config.mediaUri().hasDelayFlag()) {
            return new DelayedMediaView(config.withMediaUri(config.mediaUri().withDelay(false)));
        }

//        if (!config.mediaUri().isLocal()) {
//            config = config.withMediaUri(config.mediaUri().withProxy(true));
//        }

        final MediaUri uri = config.mediaUri();
        if (uri.getMediaType() == MediaUri.MediaType.VIDEO) {
            return new VideoMediaView(config);

        } else if (uri.getMediaType() == MediaUri.MediaType.GIF) {
            if (shouldUseGifToWebm(uri, settings)) {
                return new Gif2VideoMediaView(config);
            } else {
                return new GifMediaView(config);
            }

        } else {
            return new ImageMediaView(config);
        }
    }


    private static boolean shouldUseGifToWebm(MediaUri uri, Settings settings) {
        return !uri.isLocal() && settings.convertGifToWebm();
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
