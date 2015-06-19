package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.ImageView;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.inject.Inject;
import com.pr0gramm.app.Pr0grammApplication;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import roboguice.inject.InjectView;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import static com.pr0gramm.app.AndroidUtility.checkNotMainThread;
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

    public MpegMediaView(Context context, Binder binder, String url, Runnable onViewListener) {
        super(context, binder, R.layout.player_image, url, onViewListener);
    }

    private void asyncLoadVideo() {
        if (loading != null || videoPlayer != null) {
            return;
        }

        Observable<VideoPlayer> loader = binder.bind(Async.fromCallable(() -> {
            Downloader.Response response = downloader.load(Uri.parse(url), 0);
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


    private static class MpegVideoDrawable extends Drawable implements Runnable {
        private final Object lock = new Object();

        private long delay = 1000 / 30;

        private volatile boolean running;
        private volatile Bitmap current;
        private final BlockingQueue<Bitmap> next = new SynchronousQueue<>();
        private final BlockingQueue<Bitmap> previous = new ArrayBlockingQueue<>(4);

        public void start() {
            running = true;
            scheduleSelf(this, SystemClock.uptimeMillis() + delay);
        }

        public void stop() {
            running = false;
            unscheduleSelf(this);
        }

        public boolean push(Bitmap frame) {
            checkNotMainThread();
            try {
                return next.offer(frame, delay, TimeUnit.MILLISECONDS);
            } catch (InterruptedException err) {
                return false;
            }
        }

        public Optional<Bitmap> pull() {
            checkNotMainThread();

            try {
                return Optional.fromNullable(previous.poll(100, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                return Optional.absent();
            }
        }

        public void setPictureDelay(long delay) {
            this.delay = delay;
        }

        @Override
        public void run() {
            moveToNextFrame();

            if (current != null) {
                invalidateSelf();
            }

            if (running) {
                start();
            }
        }

        private void moveToNextFrame() {
            Bitmap frame = this.next.poll();
            if (frame != null) {
                if (current != null) {
                    previous.add(current);
                }

                synchronized (lock) {
                    current = frame;
                }
            }
        }

        @Override
        public void draw(Canvas canvas) {
            synchronized (lock) {
                if (current != null) {
                    Rect bounds = getBounds();
                    canvas.drawBitmap(current, null, bounds, null);
                }
            }
        }

        @Override
        public void setAlpha(int alpha) {
            // do nothing
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            // do nothing
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    private static class VideoPlayer implements VideoConsumer {
        private static final Logger logger = LoggerFactory.getLogger(VideoPlayer.class);

        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean paused = new AtomicBoolean();
        private final WeakReference<OnSizeCallback> sizeCallback;

        private final InputStreamCache cache;
        private final MpegVideoDrawable drawable = new MpegVideoDrawable();

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

                drawable.start();
            }
        }

        private void playLoop() {
            try {
                while (running.get()) {
                    try {
                        playOnce();
                    } catch (IOException error) {
                        running.set(false);
                        break;
                    }
                }
            } finally {
                drawable.stop();
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
                do {
                    ensureStillRunning();
                    bitmap = drawable.pull().orNull();
                } while (bitmap == null);
            }

            // post information about the newly received size info
            OnSizeCallback callback = sizeCallback.get();
            if (callback != null) {
                callback.onSizeChanged(picture.width, picture.height);
            }

            bitmap.setPixels(picture.pixels, 0, picture.codedWidth, 0, 0,
                    picture.width, picture.height);

            if (!drawable.push(bitmap)) {
                // couldn't push this bitmap, drop that the frame.
                bitmapCount.decrementAndGet();
                bitmap.recycle();
            }
        }

        @Override
        public PictureBuffer fetchBuffer(VideoDecoder decoder) throws InterruptedException {
            ensureStillRunning();

            // set the current delay
            drawable.setPictureDelay((long) (1000 / decoder.getPictureRate()));

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
                drawable.stop();
                throw new VideoPlaybackStoppedException();
            }
        }

        private static class VideoPlaybackStoppedException extends RuntimeException {
        }
    }
}
