package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.pr0gramm.app.AndroidUtility;
import com.pr0gramm.app.R;
import com.pr0gramm.app.ui.MaterialProgressDrawable;

/**
 */
public class BusyIndicator extends ImageView {
    private MaterialProgressDrawable mpd;

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
        mpd = new MaterialProgressDrawable(getContext(), this);
        mpd.setColorSchemeColors(getResources().getColor(R.color.primary));
        mpd.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        mpd.updateSizes(MaterialProgressDrawable.LARGE);
        mpd.setAlpha(255);

        int size = AndroidUtility.dp(getContext(), 64);
        setLayoutParams(new ViewGroup.LayoutParams(size, size));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setImageDrawable(mpd);
        mpd.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        setImageDrawable(null);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            setImageDrawable(mpd);
            mpd.start();
        } else {
            setImageDrawable(null);
        }
    }
}
