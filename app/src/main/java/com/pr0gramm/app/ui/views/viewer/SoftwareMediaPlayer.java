package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.google.common.util.concurrent.Uninterruptibles;
import com.pr0gramm.app.VideoDrawable;
import com.pr0gramm.app.mpeg.InputStreamCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import rx.Observable;
import rx.subjects.BehaviorSubject;

/**
 */
public abstract class SoftwareMediaPlayer {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean paused = new AtomicBoolean();
    private final InputStreamCache inputCache;

    private final BehaviorSubject<Size> videoSize = BehaviorSubject.create();
    private final BehaviorSubject<Throwable> errors = BehaviorSubject.create();
    private final VideoDrawable drawable = new VideoDrawable();
    private final AtomicInteger bitmapCount = new AtomicInteger();

    private Queue<Bitmap> returned = new LinkedList<>();

    public SoftwareMediaPlayer(InputStream inputStream) {
        this.inputCache = new InputStreamCache(new BufferedInputStream(inputStream));
    }

    public void start() {
        paused.set(false);
        if (running.compareAndSet(false, true)) {
            Thread thread = new Thread(this::run);
            thread.setDaemon(true);
            thread.setName(getClass().getSimpleName() + ":" + this);
            thread.setPriority(3);
            thread.start();
        }
    }

    public void stop() {
        paused.set(false);
        running.set(false);
    }

    public void pause() {
        paused.set(true);
    }

    public Drawable drawable() {
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

            } catch (VideoPlaybackStoppedException ignored) {
                // ignore this one

            } catch (Throwable error) {
                errors.onNext(error);
                stop();
                break;
            }
        }
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
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

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
        Bitmap bitmap = returned.poll();

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
        if (!returned.offer(bitmap)) {
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
