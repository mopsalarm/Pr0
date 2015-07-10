package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;

import com.google.common.util.concurrent.Uninterruptibles;
import com.pr0gramm.app.VideoDrawable;
import com.pr0gramm.app.mpeg.InputStreamCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import rx.Observable;
import rx.subjects.BehaviorSubject;

/**
 */
public abstract class SoftwareMediaPlayer {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean paused = new AtomicBoolean();
    private final InputStreamCache inputCache;

    protected final BehaviorSubject<Size> videoSize = BehaviorSubject.create();
    protected final VideoDrawable drawable = new VideoDrawable();

    public SoftwareMediaPlayer(InputStream inputStream) {
        this.inputCache = new InputStreamCache(inputStream);
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

    protected void reportSize(int width, int height) {
        videoSize.onNext(new Size(width, height));

    }

    protected void blockWhilePaused() {
        while (paused.get()) {
            ensureStillRunning();
            Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        }
    }

    protected void ensureStillRunning() {
        if (!running.get()) {
            throw new VideoPlaybackStoppedException();
        }
    }

    @SuppressLint("NewApi")
    private void run() {
        while (running.get()) {
            try (InputStream stream = inputCache.get()) {
                playOnce(stream);
            } catch (VideoPlaybackStoppedException | InterruptedException ignored) {
            } catch (Exception error) {
                logger.warn("io error occurred during playback", error);
                stop();
                break;
            }
        }
    }

    protected abstract void playOnce(InputStream stream) throws Exception;

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
