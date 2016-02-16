package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;

import com.google.common.util.concurrent.Uninterruptibles;
import com.pr0gramm.app.mpeg.InputStreamCache;
import com.pr0gramm.app.ui.VideoDrawable;
import com.pr0gramm.app.util.AndroidUtility;

import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

import static com.pr0gramm.app.util.AndroidUtility.checkNotMainThread;

/**
 */
public abstract class SoftwareMediaPlayer {
    private final Logger logger;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean paused = new AtomicBoolean();

    private final InputStreamCache inputCache;
    private final BehaviorSubject<Size> videoSize = BehaviorSubject.create();
    private final BehaviorSubject<Throwable> errors = BehaviorSubject.create();
    private final VideoDrawable drawable = new VideoDrawable();
    private final AtomicInteger bitmapCount = new AtomicInteger();

    private final Queue<WeakReference<Bitmap>> returned = new LinkedList<>();
    private final AtomicReference<Thread> thread = new AtomicReference<>();

    private final BehaviorSubject<Boolean> bufferingSubject = BehaviorSubject.create();

    protected final Context context;
    protected long duration = -1;
    protected long currentPosition = -0;

    public SoftwareMediaPlayer(Logger logger, Context context, InputStream inputStream) {
        this.logger = logger;
        this.context = context;
        this.inputCache = new InputStreamCache(context, new BufferedInputStream(inputStream));
    }

    public Observable<Boolean> buffering() {
        return bufferingSubject
                .filter(AndroidUtility.isFalse())
                .mergeWith(bufferingSubject
                        .filter(AndroidUtility.isTrue())
                        .debounce(500, TimeUnit.MILLISECONDS, Schedulers.io()))
                .distinctUntilChanged();
    }

    protected void publishIsBuffering(boolean buffering) {
        bufferingSubject.onNext(buffering);
    }

    /**
     * Destroys the play. This will block until the backend thread stopped.
     */
    public void destroy() {
        checkNotMainThread();

        stop();

        // wait for playback to stop
        Thread thread = this.thread.get();
        if (thread != null)
            Uninterruptibles.joinUninterruptibly(thread);

        try {
            inputCache.close();
        } catch (IOException error) {
            logger.warn("couldnt close cache");
        }
    }

    public void start() {
        paused.set(false);
        if (running.compareAndSet(false, true)) {
            Thread thread = new Thread(this::run);
            thread.setDaemon(true);
            thread.setName(getClass().getSimpleName() + ":" + this);
            thread.setPriority(3);

            if (this.thread.getAndSet(thread) != null)
                logger.warn("A thread was already set");

            thread.start();
        }
    }

    public void stop() {
        paused.set(false);
        running.set(false);
    }

    public void pause() {
        paused.set(true);

        WeakReference<Bitmap> ref;
        while ((ref = returned.poll()) != null) {
            logger.info("Clear bitmap in video pause mode, remaining: {}",
                    bitmapCount.decrementAndGet());

            Bitmap bitmap = ref.get();
            if (bitmap != null)
                bitmap.recycle();
        }

    }

    public VideoDrawable drawable() {
        return drawable;
    }

    public final Observable<Size> videoSize() {
        return videoSize.asObservable();
    }

    public final Observable<Throwable> errors() {
        return errors.asObservable();
    }

    protected void reportSize(int width, int height) {
        videoSize.onNext(new Size(width, height));
    }

    protected void blockWhilePaused() {
        while (paused.get()) {
            ensureStillRunning();
            Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        }
    }

    protected boolean ensureStillRunning() {
        if (!running.get()) {
            throw new VideoPlaybackStoppedException();
        }

        return true;
    }

    @SuppressLint("NewApi")
    private void run() {
        while (running.get()) {
            try (InputStream stream = inputCache.get()) {
                playOnce(stream);

            } catch (VideoPlaybackStoppedException | InterruptedException | InterruptedIOException ignored) {
                // ignore this one

            } catch (Throwable error) {
                if (running.get() && !paused.get())
                    errors.onNext(error);

                stop();
                break;
            }
        }

        if (!thread.compareAndSet(Thread.currentThread(), null))
            logger.warn("Could not reset thread, a different thread was already set");
    }

    /**
     * Play the video from the given input stream.
     */
    protected abstract void playOnce(InputStream stream) throws Exception;

    /**
     * Gets an image that has the provided size.
     */
    protected Bitmap requestBitmap(int width, int height) {
        if (bitmapCount.get() < 3) {
            ensureStillRunning();
            bitmapCount.incrementAndGet();
            return Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        } else {
            Bitmap bitmap = dequeBitmap();
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                // remove this bitmap and try again!
                bitmap.recycle();
                bitmapCount.decrementAndGet();
                return requestBitmap(width, height);
            }

            return bitmap;
        }
    }

    /**
     * Gets a bitmap from the queue. This method will first look for returned bitmaps.
     * If no bitmap was returned, it will ask the drawable for a previously painted image.
     */
    private Bitmap dequeBitmap() {
        Bitmap bitmap = null;

        // poll weak refs until we found one alive or until there are no more.
        WeakReference<Bitmap> bitmapRef;
        while ((bitmapRef = returned.poll()) != null) {
            bitmap = bitmapRef.get();
            if (bitmap == null) {
                bitmapCount.decrementAndGet();
            } else {
                break;
            }
        }

        while (bitmap == null) {
            ensureStillRunning();

            try {
                bitmap = drawable.pop(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        return bitmap;
    }

    /**
     * Returns the bitmap to the returned-queue. If this is not possible,
     * the image will be recycled.
     */
    protected void returnBitmap(Bitmap bitmap) {
        if (!returned.offer(new WeakReference<>(bitmap))) {
            bitmap.recycle();
            bitmapCount.decrementAndGet();
        }
    }

    /**
     * Publishes an image to be drawn later. If the player has already
     * stopped playback, the bitmap will be forwarded to {@link #returnBitmap(Bitmap)}
     * and not be drawn.
     */
    protected void publishBitmap(Bitmap bitmap) {
        while (running.get()) {
            try {
                drawable.push(bitmap);
                return;
            } catch (InterruptedException ignored) {
            }
        }

        returnBitmap(bitmap);
        ensureStillRunning();
    }

    public long getCurrentPosition() {
        return currentPosition;
    }

    public long getDuration() {
        return duration;
    }

    protected void publishFrameDelay(long delay) {
        drawable.setFrameDelay(delay);
    }

    private static class VideoPlaybackStoppedException extends RuntimeException {
    }

    public static class Size {
        private final int width, height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public float getAspectRatio() {
            return (float) width / height;
        }
    }
}
