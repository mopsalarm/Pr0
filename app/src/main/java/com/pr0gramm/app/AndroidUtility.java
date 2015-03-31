package com.pr0gramm.app;

import android.content.res.TypedArray;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.common.base.Throwables;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;

import static com.pr0gramm.app.Pr0grammApplication.GLOBAL_CONTEXT;
import static com.pr0gramm.app.Pr0grammApplication.tracker;

/**
 */
public class AndroidUtility {
    private AndroidUtility() {
    }

    /**
     * Gets the height of the action bar as definied in the style attribute
     * {@link R.attr#actionBarSize}.
     *
     * @param activity An activity to resolve the styled attribute value for
     * @return The height of the action bar in pixels.
     */
    public static int getActionBarSize(FragmentActivity activity) {
        TypedArray arr = activity.obtainStyledAttributes(new int[]{R.attr.actionBarSize});
        try {
            return arr.getDimensionPixelSize(0, -1);
        } finally {
            arr.recycle();
        }
    }

    public static String urlDecode(String value, Charset charset) {
        try {
            return URLDecoder.decode(value, charset.name());

        } catch (UnsupportedEncodingException e) {
            throw Throwables.propagate(e);
        }
    }

    public static void checkMainThread() {
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalStateException("Must be called from the main thread.");
    }

    public static void checkNotMainThread() {
        if (Looper.getMainLooper().getThread() == Thread.currentThread())
            throw new IllegalStateException("Must not be called from the main thread.");
    }

    public static void logToCrashlytics(Throwable error) {
        try {
            String description = new StandardExceptionParser(GLOBAL_CONTEXT, null)
                    .getDescription(Thread.currentThread().getName(), error);

            tracker().send(new HitBuilders.ExceptionBuilder()
                    .setDescription(description)
                    .setFatal(false)
                    .build());

            try {
                // log to crashlytics for fast error reporting.
                Crashlytics.logException(error);
            } catch (IllegalStateException ignored) {
                // most certainly crashlytics was not activated.
            }

        } catch (Exception err) {
            Log.i("Error", "Could not send error to google", err);
        }
    }
}
