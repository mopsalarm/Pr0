package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

/**
 */
public class VerticalScrollView extends ScrollView {
    private OnScrollListener onScrollListener;
    private TouchInterceptor touchInterceptor;

    public VerticalScrollView(Context context) {
        super(context);
    }

    public VerticalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VerticalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Sets the scroll listener for this scroll view.
     *
     * @param listener The new scroll listener that gets informed on scroll events.
     */
    public void setOnScrollListener(OnScrollListener listener) {
        this.onScrollListener = listener;
    }

    @Override
    protected void onScrollChanged(int left, int top, int oldLeft, int oldTop) {
        super.onScrollChanged(left, top, oldLeft, oldTop);

        if (onScrollListener != null)
            onScrollListener.onScrollChanged(oldTop, top);
    }

    public void setTouchInterceptor(TouchInterceptor touchInterceptor) {
        this.touchInterceptor = touchInterceptor;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent ev) {
        if (touchInterceptor == null || !touchInterceptor.shouldIntercept(ev))
            return super.onInterceptTouchEvent(ev);

        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                //Log.i("VerticalScrollView", "onInterceptTouchEvent: DOWN super false");
                super.onTouchEvent(ev);
                break;

            case MotionEvent.ACTION_MOVE:
                return false; // redirect MotionEvents to ourself

            case MotionEvent.ACTION_CANCEL:
                //Log.i("VerticalScrollView", "onInterceptTouchEvent: CANCEL super false");
                super.onTouchEvent(ev);
                break;

            case MotionEvent.ACTION_UP:
                //Log.i("VerticalScrollView", "onInterceptTouchEvent: UP super false");
                return false;

            default:
                //Log.i("VerticalScrollView", "onInterceptTouchEvent: " + action);
                break;
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        super.onTouchEvent(ev);
        return true;
    }

    public interface OnScrollListener {
        public void onScrollChanged(int oldTop, int top);
    }

    public interface TouchInterceptor {
        boolean shouldIntercept(MotionEvent event);
    }
}
