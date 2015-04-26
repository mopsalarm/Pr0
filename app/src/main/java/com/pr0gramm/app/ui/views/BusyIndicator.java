package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.R;
import com.pr0gramm.app.ui.MaterialProgressDrawable;

/**
 */
public class BusyIndicator extends FrameLayout {
    private MaterialProgressDrawable mpd;
    private ImageView imageView;

    public BusyIndicator(Context context) {
        super(context);
        init();
    }

    public BusyIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BusyIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // set size of the container
        int size = AndroidUtility.dp(getContext(), 64);
        setLayoutParams(new ViewGroup.LayoutParams(size, size));

        // and create the image-view holding the drawable
        imageView = new ImageView(getContext());
        imageView.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        addView(imageView);

        mpd = new MaterialProgressDrawable(getContext(), imageView);
        mpd.setColorSchemeColors(getResources().getColor(R.color.primary));
        mpd.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        mpd.updateSizes(MaterialProgressDrawable.LARGE);
        mpd.setAlpha(255);

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        imageView.setImageDrawable(mpd);
        mpd.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageView.setImageDrawable(null);
    }
}
