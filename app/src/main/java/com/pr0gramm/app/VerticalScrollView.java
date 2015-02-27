package com.pr0gramm.app;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 */
public class VerticalScrollView extends ScrollView {
    private OnScrollListener onScrollListener;

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

    public interface OnScrollListener {
        public void onScrollChanged(int oldTop, int top);
    }
}
