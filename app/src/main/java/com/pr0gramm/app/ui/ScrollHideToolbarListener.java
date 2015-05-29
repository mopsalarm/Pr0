package com.pr0gramm.app.ui;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewPropertyAnimator;

/**
 */
public class ScrollHideToolbarListener {
    private final View toolbar;
    private int toolbarMarginOffset;
    private ViewPropertyAnimator animation;

    public ScrollHideToolbarListener(View toolbar) {
        this.toolbar = toolbar;
    }

    private void applyToolbarPosition(boolean animated) {
        int y = -toolbarMarginOffset;
        if (animated) {
            animation = toolbar.animate().translationY(y).setDuration(250);
            animation.start();
        } else {
            if (animation != null) {
                animation.cancel();
                animation = null;
            }

            toolbar.setTranslationY(y);
        }
    }

    public void onScrolled(int dy) {
        int abHeight = toolbar.getHeight();
        if (abHeight == 0)
            return;

        toolbarMarginOffset += dy;
        if (toolbarMarginOffset > abHeight)
            toolbarMarginOffset = abHeight;

        if (toolbarMarginOffset < 0)
            toolbarMarginOffset = 0;

        applyToolbarPosition(false);
    }

    public void onScrollFinished(int y) {
        int abHeight = toolbar.getHeight();
        if (abHeight == 0)
            return;

        if (y < abHeight) {
            reset();
        } else {
            toolbarMarginOffset = (toolbarMarginOffset > abHeight / 2) ? abHeight : 0;
            applyToolbarPosition(true);
        }
    }

    public void reset() {
        if (toolbarMarginOffset != 0) {
            toolbarMarginOffset = 0;
            applyToolbarPosition(true);
        }
    }

    public interface ToolbarActivity {
        ScrollHideToolbarListener getScrollHideToolbarListener();
    }

    /**
     * This method estimates scrolling based on y value of the first element
     * in this recycler view. If scrolling could not be estimated, it will
     * return {@link Integer#MAX_VALUE} as estimate.
     *
     * @param recyclerView The recycler view to estimate scrolling of
     */
    public static int estimateRecyclerViewScrollY(RecyclerView recyclerView) {
        int scrollY = Integer.MAX_VALUE;
        View view = recyclerView.getLayoutManager().findViewByPosition(0);
        if (view != null) {
            scrollY = -(int) view.getY();
        }

        return scrollY;
    }
}
