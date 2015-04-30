package com.pr0gramm.app;

import android.content.Context;
import android.content.res.TypedArray;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.support.v4.net.ConnectivityManagerCompat;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Throwables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import roboguice.inject.InjectView;

import static java.util.Arrays.asList;

/**
 */
public class AndroidUtility {
    private static final Logger logger = LoggerFactory.getLogger(AndroidUtility.class);

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
            // log to crashlytics for fast error reporting.
            Crashlytics.logException(error);

        } catch (IllegalStateException ignored) {
            // most certainly crashlytics was not activated.

        } catch (Exception err) {
            logger.info("Could not send error to google", err);
        }
    }

    public static Bundle bundle(String key, String value) {
        Bundle bundle = new Bundle();
        bundle.putString(key, value);
        return bundle;
    }

    public static int dp(Context context, int pixel) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (pixel * density);
    }

    public static boolean isOnMobile(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        return ConnectivityManagerCompat.isActiveNetworkMetered(cm);
    }

    public static void uninjectViews(Object object) {
        List<Field> fields = new ArrayList<>();

        Class<?> currentClass = object.getClass();
        while (currentClass != Object.class) {
            if (currentClass.getName().startsWith("android."))
                break;

            fields.addAll(asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }

        int count = 0;
        for (Field field : fields) {
            if (field.isAnnotationPresent(InjectView.class)) {
                try {
                    field.set(object, null);
                    count++;
                } catch (IllegalAccessException ignored) {
                }
            }
        }

        logger.info("Uninjected {} views from {}", count, object.getClass().getSimpleName());
    }
}
