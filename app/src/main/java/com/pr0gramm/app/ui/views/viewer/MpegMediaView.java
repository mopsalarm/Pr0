package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.google.common.util.concurrent.Uninterruptibles;
import com.google.inject.Inject;
import com.pr0gramm.app.Pr0grammApplication;
import com.pr0gramm.app.R;
import com.pr0gramm.app.VideoDrawable;
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
import java.util.concurrent.atomic.AtomicInteger;

import roboguice.inject.InjectView;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;

/**
 */
@SuppressLint("ViewConstructor")
public class MpegMediaView extends MediaView implements OnSizeCallback {
    @InjectView(R.id.image)
    private ImageView imageView;

    @Inject
    private Downloader downloader;

    private VideoPlayer videoPlayer;
    private Subscription loading;

    public MpegMediaView(Context context, Binder binder, MediaUri url, Runnable onViewListener) {
        super(context, binder, R.layout.player_image, url, onViewListener);
    }

    private void asyncLoadVideo() {
        if (loading != null || videoPlayer != null) {
            return;
        }

        Observable<VideoPlayer> loader = binder.bind(Async.fromCallable(() -> {
            Downloader.Response response = downloader.load(getEffectiveUri(), 0);
            return new VideoPlayer(response.getInputStream(), this);
        }, Schedulers.io()));

        loading = loader.finallyDo(() -> loading = null).subscribe(mpeg -> {
            hideBusyIndicator();

            this.videoPlayer = mpeg;
            imageView.setImageDrawable(this.videoPlayer.getVideoDrawable());

            if (isPlaying()) {
                this.videoPlayer.play();
                onMediaShown();
            }

        }, defaultOnError());
    }


    @Override
    public void onSizeChanged(int width, int height) {
        float viewAspect = (float) width / height;
        post(() -> setViewAspect(viewAspect));
    }

    private void loadAndPlay() {
        if (videoPlayer == null && loading == null) {
            asyncLoadVideo();
        } else if (videoPlayer != null) {
            videoPlayer.play();
            onMediaShown();
        }
    }

    private void stopAndDestroy() {
        if (loading != null) {
            loading.unsubscribe();
            loading = null;
        }

        if (videoPlayer != null) {
            videoPlayer.stop();

            Pr0grammApplication.getRefWatcher().watch(videoPlayer);
            videoPlayer = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isPlaying()) {
            loadAndPlay();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (videoPlayer != null && isPlaying())
            videoPlayer.pause();
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

    private static class VideoPlayer implements VideoConsumer {
        private static final Logger logger = LoggerFactory.getLogger(VideoPlayer.class);

        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean paused = new AtomicBoolean();
        private final WeakReference<OnSizeCallback> sizeCallback;

        private final InputStreamCache cache;
        private final VideoDrawable drawable = new VideoDrawable();

        private PictureBuffer buffer;
        private AtomicInteger bitmapCount = new AtomicInteger();

        public VideoPlayer(InputStream inputStream, OnSizeCallback sizeCallback) {
            this.sizeCallback = new WeakReference<>(sizeCallback);
            this.cache = new InputStreamCache(inputStream);
        }

        public Drawable getVideoDrawable() {
            return drawable;
        }

        public void play() {
            paused.set(false);
            if (running.compareAndSet(false, true)) {
                Thread thread = new Thread(this::playLoop);
                thread.setDaemon(true);
                thread.setName("MpegPlayerThread:" + this);
                thread.setPriority(3);
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
            Bitmap bitmap;
            if (bitmapCount.get() <= 2) {
                bitmapCount.incrementAndGet();

                ensureStillRunning();
                bitmap = Bitmap.createBitmap(
                        picture.width, picture.height, Bitmap.Config.ARGB_8888);

            } else {
                bitmap = null;
                do {
                    ensureStillRunning();

                    try {
                        bitmap = drawable.pop(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignored) {
                    }
                } while (bitmap == null);
            }

            // post information about the newly received size info
            OnSizeCallback callback = sizeCallback.get();
            if (callback != null) {
                callback.onSizeChanged(picture.width, picture.height);
            }

            bitmap.setPixels(picture.pixels, 0, picture.codedWidth, 0, 0,
                    picture.width, picture.height);

            try {
                drawable.push(bitmap);
            } catch (InterruptedException ignored) {
            }
        }

        @Override
        public PictureBuffer fetchBuffer(VideoDecoder decoder) throws InterruptedException {
            ensureStillRunning();

            // set the current delay
            drawable.setFrameDelay((long) (1000 / decoder.getPictureRate()));

            // do nothing while paused.
            while (paused.get()) {
                ensureStillRunning();
                Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            }

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

        private void ensureStillRunning() {
            if (!running.get()) {
                throw new VideoPlaybackStoppedException();
            }
        }

        private static class VideoPlaybackStoppedException extends RuntimeException {
        }
    }
}
