package com.pr0gramm.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

/**
 */
public class MaximizeImageView extends ImageView {
    public MaximizeImageView(Context context) {
        super(context);
    }

    public MaximizeImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MaximizeImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = getMeasuredWidth();
        int height = getMinimumHeight();
        Drawable drawable = getDrawable();
        if (drawable != null) {
            int orgWidth = drawable.getIntrinsicWidth();
            int orgHeight = drawable.getIntrinsicHeight();

            // calculate height from width
            float aspect = (float) orgWidth / orgHeight;
            height = (int) (width / aspect);

            // check if height is still okay, if not,
            // scale down height (and width as such too)
            if (height > getMaxHeight()) {
                Log.i("Image", "Would be too heigh, scale down width");
                height = getMaxHeight();
                width = (int) (height * aspect);
            }

            if (height < getMinimumHeight())
                height = getMinimumHeight();
        }

        Log.i("Image", "Size is now " + width + "x" + height);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }
}