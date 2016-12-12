package com.pr0gramm.app.ui;

import android.view.MotionEvent;
import android.view.View;

import rx.functions.Action0;

/**
 * Detects single taps.
 */
public class DetectTapTouchListener implements View.OnTouchListener {
    private final Action0 consumer;
    private boolean moveOccurred;
    private float firstX;
    private float firstY;

    private DetectTapTouchListener(Action0 consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getActionIndex() == 0) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                moveOccurred = false;
                firstX = event.getX();
                firstY = event.getY();
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                moveOccurred = moveOccurred
                        || Math.abs(event.getX() - firstX) > 32
                        || Math.abs(event.getY() - firstY) > 32;
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!moveOccurred) {
                    consumer.call();
                    return true;
                }
            }
        }

        return false;
    }

    public static DetectTapTouchListener withConsumer(Action0 consumer) {
        return new DetectTapTouchListener(consumer);
    }
}
