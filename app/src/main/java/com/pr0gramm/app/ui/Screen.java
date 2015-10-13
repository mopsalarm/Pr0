package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.Surface;


public class Screen {
    private Screen() {
    }

    public static class Orientation {

        public static final int LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        public static final int PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        public static final int REVERSE_LANDSCAPE = 8; // ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        public static final int REVERSE_PORTRAIT = 9; // ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        public static final int UNSPECIFIED = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

    }

    @SuppressLint("NewApi")
    @SuppressWarnings({"deprecation", "ResourceType"})
    public static void lockOrientation(final Activity activity) {
        final Display display = activity.getWindowManager().getDefaultDisplay();
        final int rotation = display.getRotation();

        final int width, height;
        if (Build.VERSION.SDK_INT >= 13) {
            Point size = new Point();
            display.getSize(size);
            width = size.x;
            height = size.y;
        } else {
            width = display.getWidth();
            height = display.getHeight();
        }

        switch (rotation) {
            case Surface.ROTATION_90:
                if (width > height) {
                    activity.setRequestedOrientation(Orientation.LANDSCAPE);
                } else {
                    activity.setRequestedOrientation(Orientation.REVERSE_PORTRAIT);
                }
                break;
            case Surface.ROTATION_180:
                if (height > width) {
                    activity.setRequestedOrientation(Orientation.REVERSE_PORTRAIT);
                } else {
                    activity.setRequestedOrientation(Orientation.REVERSE_LANDSCAPE);
                }
                break;
            case Surface.ROTATION_270:
                if (width > height) {
                    activity.setRequestedOrientation(Orientation.REVERSE_LANDSCAPE);
                } else {
                    activity.setRequestedOrientation(Orientation.PORTRAIT);
                }
                break;
            default:
                if (height > width) {
                    activity.setRequestedOrientation(Orientation.PORTRAIT);
                } else {
                    activity.setRequestedOrientation(Orientation.LANDSCAPE);
                }
        }
    }

    public static void unlockOrientation(final Activity activity) {
        activity.setRequestedOrientation(Orientation.UNSPECIFIED);
    }
}
