package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Text view with a custom font.
 */
public class Pr0grammFontTextView extends TextView {
    public Pr0grammFontTextView(Context context) {
        this(context, null);
    }

    public Pr0grammFontTextView(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (!isInEditMode()) {
            Typeface font = getFontfaceInstance(context);
            setTypeface(font);
        }
    }

    /**
     * Loads a the pr0gramm typeface. The font is only loaded once. This method
     * will then return the same instance on every further call.
     *
     * @param context The context to use for loading.
     */
    private static Typeface getFontfaceInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Typeface.createFromAsset(
                    context.getAssets(), "fonts/pict0gramm-v3.ttf");
        }

        return INSTANCE;
    }

    private static Typeface INSTANCE;
}
