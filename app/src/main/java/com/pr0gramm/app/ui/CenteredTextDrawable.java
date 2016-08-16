package com.pr0gramm.app.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import com.google.common.base.Strings;

/**
 */
public class CenteredTextDrawable extends Drawable {
    private String text;
    private float textSize;
    private int textColor;

    public CenteredTextDrawable() {
        this("");
    }

    public CenteredTextDrawable(String text) {
        textSize = 16f;
        this.text = text;
    }

    @Override
    public int getIntrinsicWidth() {
        return 96;
    }

    @Override
    public int getIntrinsicHeight() {
        return 96;
    }

    public void setTextSize(float pixels) {
        this.textSize = pixels;
    }

    public float getTextSize() {
        return textSize;
    }

    public void setText(String text) {
        text = Strings.nullToEmpty(text);
        if (!text.equals(this.text)) {
            this.text = text;
            invalidateSelf();
        }
    }

    public String getText() {
        return text;
    }

    public void setTextColor(int color) {
        this.textColor = color;
    }

    public int getTextColor() {
        return textColor;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect rect = getBounds();

        TextPaint tp = new TextPaint();
        tp.setColor(textColor);
        tp.setTextSize(textSize);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setAntiAlias(true);

        StaticLayout layout = new StaticLayout(text, tp, canvas.getWidth(),
                Layout.Alignment.ALIGN_CENTER, 0.8f, 0, false);

        canvas.save();

        // center the text on the icon
        canvas.translate(
                0.5f * (rect.width() - layout.getWidth()),
                0.5f * (rect.height() - layout.getHeight()));

        layout.draw(canvas);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        // do nothing
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        // yea, just do nothing..
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
