package com.pr0gramm.app;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * A image that measures it height to the same value
 * as its width.
 */
public class SquareImageView extends ImageView {
    public SquareImageView(Context context) {
        super(context);
    }

    public SquareImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = getMeasuredWidth();

        //noinspection SuspiciousNameCombination
        setMeasuredDimension(width, width);
    }
}