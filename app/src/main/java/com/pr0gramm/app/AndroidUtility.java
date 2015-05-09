package com.pr0gramm.app;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.v4.app.FragmentActivity;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.net.ConnectivityManagerCompat;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import roboguice.inject.InjectView;

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

    /**
     * Gets the color tinted hq-icon
     */
    public static Drawable getTintentDrawable(Context context, @DrawableRes int drawableId, @ColorRes int colorId) {
        Resources resources = context.getResources();
        Drawable icon = DrawableCompat.wrap(resources.getDrawable(drawableId));
        DrawableCompat.setTint(icon, resources.getColor(colorId));
        return icon;
    }

    public static void uninjectViews(Object object) {
        checkMainThread();

        Class<?> objectClass = object.getClass();
        List<Field> fields = VIEW_FIELDS_CACHE.get(objectClass);
        if (fields == null) {
            fields = ImmutableList.copyOf(queryInjectViewFields(objectClass));
            VIEW_FIELDS_CACHE.put(objectClass, fields);
        }

        int count = 0;
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                field.set(object, null);
                count++;
            } catch (IllegalAccessException ignored) {
            }
        }

        logger.info("Uninjected {} views out of {}", count, objectClass.getSimpleName());
    }

    private static List<Field> queryInjectViewFields(Class<?> currentClass) {
        List<Field> fields = new ArrayList<>();
        while (currentClass != Object.class) {
            if (currentClass.getName().startsWith("android."))
                break;

            FluentIterable.of(currentClass.getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(InjectView.class))
                    .copyInto(fields);

            currentClass = currentClass.getSuperclass();
        }
        return fields;
    }

    private static final Map<Class<?>, List<Field>> VIEW_FIELDS_CACHE = new HashMap<>();

    public static Logger logger(Object instance) {
        String suffix = Integer.toString(System.identityHashCode(instance), Character.MAX_RADIX);
        return LoggerFactory.getLogger(instance.getClass().getSimpleName() + "-" + suffix);
    }
}
