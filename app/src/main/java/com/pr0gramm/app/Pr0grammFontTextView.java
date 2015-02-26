package com.pr0gramm.app;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Text view with a custom font.
 */
public class Pr0grammFontTextView extends TextView {
    public Pr0grammFontTextView(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (!isInEditMode()) {
            Typeface customTypeface = Typeface.createFromAsset(
                    context.getAssets(), "fonts/pict0gramm-v3.ttf");

            setTypeface(customTypeface);
        }
    }
}
