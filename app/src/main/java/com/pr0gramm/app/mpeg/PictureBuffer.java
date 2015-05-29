package com.pr0gramm.app.mpeg;

import android.graphics.Bitmap;

/**
 */
public class PictureBuffer {
    public final int[] pixels;
    public final int width, height;
    public final int codedWidth;

    public int hours;
    public int minutes;
    public int seconds;
    public int pictures;
    public int pictureCounter;

    public PictureBuffer(int width, int height, int codedWidth, int codedHeight) {
        this.pixels = new int[codedWidth * codedHeight];
        this.width = width;
        this.height = height;
        this.codedWidth = codedWidth;
    }
}
