package com.pr0gramm.app;

import android.content.res.TypedArray;
import android.support.v4.app.FragmentActivity;
import android.test.ActivityUnitTestCase;

/**
 */
public class AndroidUtility {
    private AndroidUtility() {
    }

    /**
     * Gets the height of the action bar as definied in the style attribute
     * {@link R.attr#actionBarSize}.
     *
     * @param activity An activity to resolve the styled attribute value for
     * @return The height of the action bar in pixels.
     */
    public static int getActionBarSize(FragmentActivity activity) {
        TypedArray arr = activity.obtainStyledAttributes(new int[]{R.attr.actionBarSize});
        try {
            return arr.getDimensionPixelSize(0, -1);
        } finally {
            arr.recycle();
        }
    }
}
