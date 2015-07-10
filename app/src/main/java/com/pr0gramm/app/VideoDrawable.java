package com.pr0gramm.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.pr0gramm.app.AndroidUtility.checkMainThread;
import static com.pr0gramm.app.AndroidUtility.checkNotMainThread;

/**
 */
public class VideoDrawable extends Drawable {
    private final BlockingQueue<Bitmap> pending = new ArrayBlockingQueue<>(16);
    private final BlockingQueue<Bitmap> finished = new ArrayBlockingQueue<>(16);

    private final AtomicBoolean scheduled = new AtomicBoolean();

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
     * drawable can be popped right now. This function has a tim
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
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (current == null) {
            canvas.drawColor(Color.TRANSPARENT);
            return;
        }

        // draw the current frame
        Rect bounds = getBounds();
        canvas.drawBitmap(current, null, bounds, null);
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
}
