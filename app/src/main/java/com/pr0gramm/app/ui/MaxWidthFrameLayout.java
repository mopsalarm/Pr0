package com.pr0gramm.app.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.pr0gramm.app.R;

/**
 */
public class MaxWidthFrameLayout extends FrameLayout {
    private int maxWidth;

    public MaxWidthFrameLayout(Context context) {
        super(context);
    }

    public MaxWidthFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (attrs != null)
            init(attrs);
    }

    public MaxWidthFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        if (attrs != null)
            init(attrs);
    }

    private void init(AttributeSet attrs) {
        Resources.Theme theme = getContext().getTheme();
        TypedArray arr = theme.obtainStyledAttributes(attrs, R.styleable.MaxWidthFrameLayout, 0, 0);
        try {
            setMaxWidth(arr.getDimensionPixelSize(
                    R.styleable.MaxWidthFrameLayout_mwfl_maxWidth, Integer.MAX_VALUE));
        } finally {
            arr.recycle();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(widthMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);

        if (width > maxWidth) {
            if (mode == MeasureSpec.UNSPECIFIED || mode == MeasureSpec.AT_MOST) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setMaxWidth(int maxWidth) {
        if (this.maxWidth != maxWidth) {
            this.maxWidth = maxWidth;
            requestLayout();
        }
    }
}
