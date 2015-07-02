package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.pr0gramm.app.R;

/**
 * A {@link FrameLayout} that keeps a aspect ratio and calculates it height
 * from the width.
 */
public class AspectLayout extends FrameLayout {
    private float aspect;

    public AspectLayout(Context context) {
        this(context, null, 0);
    }

    public AspectLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AspectLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // apply attributes
        Resources.Theme theme = context.getTheme();
        TypedArray arr = theme.obtainStyledAttributes(attrs, R.styleable.AspectView, 0, 0);
        try {
            aspect = arr.getFloat(R.styleable.AspectView_aspect, 1.f);
        } finally {
            arr.recycle();
        }
    }

    /**
     * Sets the aspect of this image. This will not trigger a relayout.
     *
     * @param aspect Sets the aspect of the image.
     */
    public void setAspect(float aspect) {
        if (this.aspect != aspect) {
            this.aspect = aspect;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = getMeasuredWidth();
        setMeasuredDimension(width, (int) (width / aspect));
    }
}