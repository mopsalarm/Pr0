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

    private DetectTapTouchListener(Action0 consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getActionIndex() == 0) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                moveOccurred = false;
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                moveOccurred = true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!moveOccurred) {
                    consumer.call();
                    return true;
                }

                moveOccurred = false;
            }
        }

        return false;
    }

    public static DetectTapTouchListener withConsumer(Action0 consumer) {
        return new DetectTapTouchListener(consumer);
    }
}
