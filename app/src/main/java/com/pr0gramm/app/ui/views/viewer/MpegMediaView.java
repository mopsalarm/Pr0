package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.ImageView;

import com.google.common.util.concurrent.Uninterruptibles;
import com.google.inject.Inject;
import com.pr0gramm.app.R;
import com.pr0gramm.app.mpeg.InputStreamCache;
import com.pr0gramm.app.mpeg.PictureBuffer;
import com.pr0gramm.app.mpeg.VideoConsumer;
import com.pr0gramm.app.mpeg.VideoDecoder;
import com.squareup.picasso.Downloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import roboguice.inject.InjectView;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;

/**
 */
@SuppressLint("ViewConstructor")
public class MpegMediaView extends MediaView {
    @InjectView(R.id.image)
    private ImageView imageView;

    @Inject
    private Downloader downloader;

    private Mpeg mpeg;
    private Subscription loading;

    public MpegMediaView(Context context, Binder binder, String url, Runnable onViewListener) {
        super(context, binder, R.layout.player_image, url, onViewListener);
    }

    private void asyncLoadVideo() {
        if (loading != null || mpeg != null) {
            return;
        }

        WeakReference<ImageView> imageView = new WeakReference<>(this.imageView);
        Observable<Mpeg> loader = binder.bind(Async.fromCallable(() -> {
            Downloader.Response response = downloader.load(Uri.parse(url), 0);
            return new Mpeg(imageView, response.getInputStream());
        }, Schedulers.io()));

        loading = loader.finallyDo(() -> loading = null).subscribe(mpeg -> {
            hideBusyIndicator();

            this.mpeg = mpeg;
            if (isPlaying()) {
                this.mpeg.play();
            }

        }, defaultOnError());
    }

    private void loadAndPlay() {
        if (mpeg == null && loading == null) {
            asyncLoadVideo();
        } else if (mpeg != null) {
            mpeg.play();
        }
    }

    private void stopAndDestroy() {
        if (loading != null) {
            loading.unsubscribe();
            loading = null;
        }

        if (mpeg != null) {
            mpeg.stop();
            mpeg = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isPlaying()) {
            loadAndPlay();
            onViewListener.run();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mpeg != null && isPlaying())
            mpeg.pause();
    }

    @Override
    public void playMedia() {
        super.playMedia();
        if (isPlaying()) {
            loadAndPlay();
        }
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        stopAndDestroy();
    }

    @Override
    public void onDestroy() {
        imageView.setImageDrawable(null);
        stopAndDestroy();
        super.onDestroy();
    }


    private static class Mpeg implements VideoConsumer {
        private static final Logger logger = LoggerFactory.getLogger(Mpeg.class);

        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean paused = new AtomicBoolean();
        private final WeakReference<ImageView> imageView;

        private final InputStreamCache cache;
        private volatile Bitmap visible, backbuffer;
        private PictureBuffer buffer;

        public Mpeg(WeakReference<ImageView> imageView, InputStream inputStream) {
            this.imageView = imageView;
            this.cache = new InputStreamCache(inputStream);
        }

        public void play() {
            paused.set(false);
            if (running.compareAndSet(false, true)) {
                Thread thread = new Thread(this::playLoop);
                thread.setDaemon(true);
                thread.setName("MpegPlayerThread:" + this);
                thread.start();
            }
        }

        private void playLoop() {
            while (running.get()) {
                try {
                    playOnce();
                } catch (IOException error) {
                    running.set(false);
                    break;
                }
            }
        }

        @SuppressLint("NewApi")
        private void playOnce() throws IOException {
            try (InputStream stream = cache.get()) {
                logger.info("creating video decoder instance");
                VideoDecoder decoder = new VideoDecoder(this, stream);

                logger.info("start decoding mpeg file now");
                try {
                    decoder.decodeSequence();

                } catch (IOException error) {
                    logger.error("io error occurred during playback");

                } catch (InterruptedException | VideoPlaybackStoppedException ignored) {
                }

                logger.info("decoding mepg stream finished");
            }
        }

        public void stop() {
            running.set(false);
        }

        public void pause() {
            paused.set(true);
        }

        @Override
        public void sequenceStarted() {
            logger.info("sequence started");
        }

        @Override
        public void sequenceEnded() {
            logger.info("sequence ended");
        }

        @Override
        public void pictureDecoded(PictureBuffer picture) {
            if (!running.get()) {
                logger.info("got picture, but stop was requested");
                throw new VideoPlaybackStoppedException();
            }

            // logger.info("picture decoded, ready to show", picture);
            ImageView imageView = Mpeg.this.imageView.get();
            if (imageView != null) {
                if (backbuffer == null) {
                    backbuffer = Bitmap.createBitmap(
                            picture.width, picture.height, Bitmap.Config.ARGB_8888);
                }

                backbuffer.setPixels(picture.pixels, 0, picture.codedWidth, 0, 0,
                        picture.width, picture.height);

                imageView.post(() -> {
                    // logger.info("show picture in ui now");
                    if (imageView.getWindowToken() != null) {
                        imageView.setImageBitmap(backbuffer);

                        // switch back & front buffer
                        Bitmap previous = visible;
                        visible = backbuffer;
                        backbuffer = previous;
                    } else {
                        stop();
                    }
                });
            }
        }

        @Override
        public PictureBuffer fetchBuffer(VideoDecoder decoder) throws InterruptedException {
            // check again for end of video
            if (!running.get())
                throw new VideoPlaybackStoppedException();

            do {
                // wait a moment to play video in the correct speed.
                int delay = (int) (1000 / decoder.getPictureRate());
                Uninterruptibles.sleepUninterruptibly(delay, TimeUnit.MILLISECONDS);

                // check again
                if (!running.get())
                    throw new VideoPlaybackStoppedException();

                // if we are on pause, we'll loop
            } while (paused.get());

            if (buffer == null) {
                int width = decoder.getWidth();
                int height = decoder.getHeight();
                int codedWidth = decoder.getCodedWidth();
                int codedHeight = decoder.getCodedHeight();

                // logger.info("requesting buffer at {}x{} ({}x{})", width, height, codedWidth, codedHeight);
                buffer = new PictureBuffer(width, height, codedWidth, codedHeight);
            }

            return buffer;
        }

        private static class VideoPlaybackStoppedException extends RuntimeException {
        }
    }
}
