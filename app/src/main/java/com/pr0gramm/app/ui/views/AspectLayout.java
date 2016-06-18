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
    @SuppressWarnings("FieldCanBeLocal")
    private static float MAX_ASPECT_RATIO_DEFORMATION_FRACTION = 0.01f;

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
     * Sets the aspect of this image. This might trigger a relayout.
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
        if (aspect < 0) {
            // Aspect ratio not set.
            return;
        }

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        float viewAspectRatio = (float) width / height;
        float aspectDeformation = aspect / viewAspectRatio - 1;
        if (Math.abs(aspectDeformation) <= MAX_ASPECT_RATIO_DEFORMATION_FRACTION) {
            // We're within the allowed tolerance.
            return;
        }

        // always "fix" height, never change width.
        height = (int) (width / aspect);

        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}