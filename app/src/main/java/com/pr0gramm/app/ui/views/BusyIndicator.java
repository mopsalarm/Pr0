package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;

import com.pnikosis.materialishprogress.ProgressWheel;
import com.pr0gramm.app.R;

/**
 */
public class BusyIndicator extends ProgressWheel {
    public BusyIndicator(Context context) {
        super(context);
        init();
    }

    public BusyIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBarColor(ContextCompat.getColor(getContext(), R.color.primary));
        spin();
    }
}
