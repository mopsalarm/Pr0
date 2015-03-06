package com.pr0gramm.app;

import android.content.Context;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;

/**
 */
public class CustomSwipeRefreshLayout extends SwipeRefreshLayout {
    private CanSwipeUpPredicate canSwipeUpPredicate;

    public CustomSwipeRefreshLayout(Context context) {
        super(context);
    }

    public CustomSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean canChildScrollUp() {
        return (canSwipeUpPredicate != null && canSwipeUpPredicate.canSwipeUp()) || super.canChildScrollUp();
    }

    public void setCanSwipeUpPredicate(CanSwipeUpPredicate canSwipeUpPredicate) {
        this.canSwipeUpPredicate = canSwipeUpPredicate;
    }

    public CanSwipeUpPredicate getCanSwipeUpPredicate() {
        return canSwipeUpPredicate;
    }

    public interface CanSwipeUpPredicate {
        boolean canSwipeUp();
    }
}
