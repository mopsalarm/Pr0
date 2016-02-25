package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import com.pr0gramm.app.BuildConfig;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import rx.Observable;
import rx.subjects.BehaviorSubject;

import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;
import static com.pr0gramm.app.util.AndroidUtility.checkNotMainThread;

/**
 */
public class VideoDrawable extends Drawable {
    private final BlockingQueue<Bitmap> pending = new ArrayBlockingQueue<>(16);
    private final BlockingQueue<Bitmap> finished = new ArrayBlockingQueue<>(16);
    private final FrameCounter fpsCounter = new FrameCounter();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    private final BehaviorSubject<Void> frameAvailableSubject = BehaviorSubject.create();

    private long frameDelay = 1000L / 30;
    private Bitmap current;

    /**
     * Pushes a new frame to this drawable to be drawn later.
     *
     * @param bitmap The frame to be drawn.
     */
    public void push(Bitmap bitmap) throws InterruptedException {
        checkNotMainThread();
        pending.put(bitmap);
        ensureScheduled();
    }

    /**
     * Returns a previously drawn frame from this drawable or null, if no
     * drawable can be popped right now. This function has a timeout
     */
    public Bitmap pop(long timeout, TimeUnit unit) throws InterruptedException {
        checkNotMainThread();
        ensureScheduled();
        return finished.poll(timeout, unit);
    }

    private void ensureScheduled() {
        final Callback callback = getCallback();
        if (callback != null) {
            if (scheduled.compareAndSet(false, true)) {
                callback.scheduleDrawable(this, this::moveToNext, SystemClock.uptimeMillis() + getFrameDelay());
            }
        } else {
            scheduled.set(false);
        }
    }

    public void setFrameDelay(long frameDelay) {
        this.frameDelay = frameDelay;
    }

    public long getFrameDelay() {
        return this.frameDelay;
    }

    /**
     * Called to move to the next frame. This method will be called on
     * the ui thread and must not block!.
     */
    private void moveToNext() {
        checkMainThread();
        scheduled.set(false);

        Bitmap next = pending.poll();
        if (next != null) {
            if (current != null && !finished.offer(current))
                throw new RuntimeException("Could not add image to finished queue");

            current = next;
            invalidateSelf();
            ensureScheduled();

            frameAvailableSubject.onNext(null);

            fpsCounter.update();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (current == null) {
            // canvas.eraseColor(Color.TRANSPARENT);
            return;
        }

        // draw the current frame
        Rect bounds = getBounds();
        canvas.drawBitmap(current, null, bounds, null);

        if (BuildConfig.DEBUG) {
            drawCurrentFps(canvas, bounds);
        }
    }

    @SuppressLint("DefaultLocale")
    private void drawCurrentFps(Canvas canvas, Rect bounds) {
        String fpsString = String.format("%1.2ffps", fpsCounter.fps());

        Paint paint = new Paint();
        paint.setColor(Color.GREEN);

        int size = bounds.width() / 10;
        paint.setTextSize(size);
        canvas.drawText(fpsString, bounds.left + size, bounds.bottom - size, paint);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    public Observable<Void> frameAvailable() {
        return frameAvailableSubject.asObservable();
    }

    private static class FrameCounter {
        private final long[] durations = new long[50];
        private int position = 0;
        private long previousTimestamp;

        public void update() {
            long now = SystemClock.elapsedRealtime();
            durations[position++ % durations.length] = now - previousTimestamp;
            previousTimestamp = now;
        }

        public float fps() {
            long total = 0;
            for (long duration : durations)
                total += duration;

            return (1000 * durations.length) / (float) total;
        }
    }
}
