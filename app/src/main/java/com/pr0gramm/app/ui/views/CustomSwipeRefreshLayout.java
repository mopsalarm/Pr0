package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;

import com.pr0gramm.app.AndroidUtility;

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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        try {
            super.onLayout(changed, left, top, right, bottom);
        } catch (Exception err) {
            // I found crashlytics reports during layout,
            // lets just catch everything inside this layout.
            AndroidUtility.logToCrashlytics(err);
        }
    }

    public interface CanSwipeUpPredicate {
        boolean canSwipeUp();
    }
}
