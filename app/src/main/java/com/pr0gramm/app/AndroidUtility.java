package com.pr0gramm.app;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.net.ConnectivityManagerCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
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
import rx.Observable;
import rx.functions.Action1;

/**
 */
public class AndroidUtility {
    private static final Logger logger = LoggerFactory.getLogger(AndroidUtility.class);

    private AndroidUtility() {
    }

    /**
     * Gets the height of the action bar as definied in the style attribute
     * {@link R.attr#actionBarSize} plus the height of the status bar on android
     * Kitkat and above.
     *
     * @param context A context to resolve the styled attribute value for
     */
    public static int getActionBarContentOffset(Context context) {
        int offset = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            offset = getStatusBarHeight(context);
        }

        return getActionBarHeight(context) + offset;
    }

    /**
     * Gets the height of the actionbar.
     */
    public static int getActionBarHeight(Context context) {
        TypedArray arr = context.obtainStyledAttributes(new int[]{R.attr.actionBarSize});
        try {
            return arr.getDimensionPixelSize(0, -1);
        } finally {
            arr.recycle();
        }
    }

    /**
     * Gets the height of the statusbar.
     */
    public static int getStatusBarHeight(Context context) {
        int result = 0;
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId);
        }

        return result;
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
            Crashlytics.getInstance().core.logException(error);

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
        Drawable icon = DrawableCompat.wrap(ResourcesCompat.getDrawable(resources, drawableId, null));
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
                Object view = field.get(object);
                if (view instanceof RecyclerView) {
                    ((RecyclerView) view).setAdapter(null);
                }

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

    /**
     * Returns a CharSequence containing a bulleted and properly indented list.
     *
     * @param leadingMargin In pixels, the space between the left edge of the bullet and the left edge of the text.
     * @param lines         An array of CharSequences. Each CharSequences will be a separate line/bullet-point.
     */
    public static CharSequence makeBulletList(int leadingMargin, CharSequence... lines) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int idx = 0; idx < lines.length; idx++) {
            boolean last = idx == lines.length - 1;
            CharSequence line = lines[idx];

            Spannable spannable = new SpannableString(line + (last ? "" : "\n"));
            spannable.setSpan(new BulletSpan(leadingMargin), 0, spannable.length(),
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

            sb.append(spannable);
        }
        return sb;
    }

    /**
     * Converts the observable into an optional.
     */
    public static <T> Optional<T> toOptional(Observable<T> observable) {
        T element = observable.toBlocking().singleOrDefault(null);
        return Optional.fromNullable(element);
    }

    /**
     * Calls the given action with the value from the optional, if there it is present.
     */
    public static <T> void ifPresent(Optional<T> optional, Action1<T> action) {
        if (optional.isPresent()) {
            action.call(optional.get());
        }
    }

    /**
     * Measures the runtime of the given runnable and log it to the provided logger.
     */
    public static void time(Logger logger, String name, Runnable runnable) {
        time(logger, name, () -> {
            runnable.run();
            return null;
        });
    }

    public static <T> T time(Logger logger, String name, Supplier<T> supplier) {
        Stopwatch watch = Stopwatch.createStarted();
        try {
            return supplier.get();
        } finally {
            logger.info("{} took {}", name, watch);
        }
    }

    public static void removeView(View view) {
        if (view != null) {
            ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(view);
            }
        }
    }
}
