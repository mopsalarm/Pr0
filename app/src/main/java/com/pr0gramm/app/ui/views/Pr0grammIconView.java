package com.pr0gramm.app.ui.views;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.text.BoringLayout;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import com.google.common.base.Objects;
import com.pr0gramm.app.R;

import static android.util.TypedValue.applyDimension;
import static com.google.common.base.MoreObjects.firstNonNull;

/**
 * Text view with a custom font.
 */
public class Pr0grammIconView extends View {
    @ColorInt
    private int textColor = Color.RED;
    private float textSize = 16;
    private String text = "+";

    private Layout cachedLayout;

    public Pr0grammIconView(Context context) {
        super(context);
    }

    public Pr0grammIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    private void init(@NonNull AttributeSet attrs) {
        TypedArray a = getContext().getTheme().obtainStyledAttributes(
                attrs, R.styleable.IconView, 0, 0);

        try {
            text = firstNonNull(a.getString(R.styleable.IconView_iv_text), "");
            textSize = a.getDimensionPixelSize(R.styleable.IconView_iv_textSize, 16);
            textColor = a.getColor(R.styleable.IconView_iv_textColor, Color.RED);
        } finally {
            a.recycle();
        }
    }

    private Layout buildLayout() {
        TextPaint paint = new TextPaint();
        paint.setColor(textColor);
        paint.setTypeface(getFontfaceInstance(this));
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);

        BoringLayout.Metrics metrics = BoringLayout.isBoring(text, paint);
        if (metrics != null) {
            return new BoringLayout(text, paint, metrics.width,
                    Layout.Alignment.ALIGN_NORMAL, 1, 0, metrics, true);
        } else {
            return new StaticLayout(text, paint, 0,
                    Layout.Alignment.ALIGN_NORMAL, 1, 0, true);
        }
    }

    @Override
    public void invalidate() {
        cachedLayout = null;
        super.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (cachedLayout == null)
            cachedLayout = buildLayout();

        cachedLayout.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (cachedLayout == null)
            cachedLayout = buildLayout();

        setMeasuredDimension(cachedLayout.getWidth(), cachedLayout.getHeight());
    }

    public void setText(String text) {
        if (!Objects.equal(this.text, text)) {
            this.text = text;
            invalidate();
            requestLayout();
        }
    }

    public void setTextSize(int unit, int textSize) {
        float newTextSize = applyDimension(unit, textSize, getResources().getDisplayMetrics());
        if (newTextSize != this.textSize) {
            this.textSize = newTextSize;
            invalidate();
            requestLayout();
        }
    }

    public void setTextColor(@ColorInt int color) {
        if (this.textColor != color) {
            this.textColor = color;
            invalidate();
        }
    }

    public void setTextColor(ColorStateList textColor) {
        setTextColor(textColor.getDefaultColor());
    }

    /**
     * Loads the pr0gramm icon typeface. The font is only loaded once. This method
     * will then return the same instance on every further call.
     *
     * @param view A view that will be used to get the context and to check if we
     *             are in edit mode or not.
     */
    private static Typeface getFontfaceInstance(View view) {
        if (TYPEFACE == null) {
            if (view.isInEditMode()) {
                TYPEFACE = Typeface.DEFAULT;
            } else {
                AssetManager assets = view.getContext().getAssets();
                TYPEFACE = Typeface.createFromAsset(assets, "fonts/pict0gramm-v3.ttf");
            }
        }

        return TYPEFACE;
    }

    private static Typeface TYPEFACE;

}
