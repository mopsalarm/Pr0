package com.pr0gramm.app;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import pl.droidsonroids.gif.GifImageView;

/**
 */
public class MaximizeImageView extends GifImageView {
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
        int height = ViewCompat.getMinimumHeight(this);
        Drawable drawable = getDrawable();
        if (drawable != null) {
            int orgWidth = drawable.getIntrinsicWidth();
            int orgHeight = drawable.getIntrinsicHeight();

            // calculate height from width
            float aspect = (float) orgWidth / orgHeight;
            height = (int) (width / aspect);

            // check if height is still okay, if not,
            // scale down height (and width as such too)
            if (height > getMaximumHeightCompat()) {
                Log.i("Image", "Would be too heigh, scale down width");
                height = getMaximumHeightCompat();
                width = (int) (height * aspect);
            }

            if (height < ViewCompat.getMinimumHeight(this))
                height = ViewCompat.getMinimumHeight(this);
        }

        Log.i("Image", "Size is now " + width + "x" + height);
        setMeasuredDimension(width, height);
    }

    private int getMaximumHeightCompat() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            return getMaxHeight();

        return getContext().getResources().getDimensionPixelSize(R.dimen.max_image_view_height);
    }
}