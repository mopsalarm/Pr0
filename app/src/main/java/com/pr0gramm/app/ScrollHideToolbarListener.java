package com.pr0gramm.app;

import android.support.v7.widget.Toolbar;
import android.view.ViewPropertyAnimator;

/**
 */
public class ScrollHideToolbarListener {
    private final Toolbar toolbar;
    private int toolbarMarginOffset;
    private ViewPropertyAnimator animation;

    public ScrollHideToolbarListener(Toolbar toolbar) {
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
        // int abHeight = AndroidUtility.getActionBarSize(MainActivity.this);
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

    public void reset() {
        if (toolbarMarginOffset != 0) {
            toolbarMarginOffset = 0;
            applyToolbarPosition(true);
        }
    }

    public interface ToolbarActivity {
        ScrollHideToolbarListener getScrollHideToolbarListener();
    }
}
