package com.pr0gramm.app.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import com.akodiakson.sdk.simple.Sdk;
import com.crashlytics.android.Crashlytics;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.ui.PrivateBrowserSpan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import butterknife.ButterKnife;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Action2;
import rx.functions.Func1;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static rx.Observable.empty;
import static rx.Observable.just;

/**
 * Place to put everything that belongs nowhere. Thanks Obama.
 */
public class AndroidUtility {
    private static final Logger logger = LoggerFactory.getLogger("AndroidUtility");

    private static final Pattern RE_USERNAME = Pattern.compile("@[A-Za-z0-9]+");
    private static final Pattern RE_GENERIC_LINK = Pattern.compile("(?:https?://)?(?:www\\.)?pr0gramm\\.com(/(?:new|top|user)/[^\\p{javaWhitespace}]*[0-9])");
    private static final Pattern RE_GENERIC_SHORT_LINK = Pattern.compile("/((?:new|top|user)/[^\\p{javaWhitespace}]*[0-9])");

    private static final Cache<String, Boolean> previousExceptions =
            CacheBuilder.<String, Boolean>newBuilder()
                    .expireAfterWrite(1, TimeUnit.MINUTES)
                    .maximumSize(32)
                    .build();

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
        if (Sdk.isAtLeastKitKat()) {
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
        if (error == null)
            return;

        try {
            // try to rate limit exceptions.
            String key = String.valueOf(error.toString());
            if (Boolean.TRUE.equals(previousExceptions.getIfPresent(key))) {
                return;
            } else {
                previousExceptions.put(key, true);
            }

            // log to crashlytics for fast error reporting.
            Crashlytics.getInstance().core.logException(error);

        } catch (IllegalStateException ignored) {
            // most certainly crashlytics was not activated.
            logger.warn("Looks like crashlytics was not activated. Here is the error:", error);

        } catch (Throwable err) {
            logger.info("Could not send error to crashlytics", err);
        }
    }

    public static Bundle bundle(String key, String value) {
        Bundle bundle = new Bundle();
        bundle.putString(key, value);
        return bundle;
    }

    public static int dp(Context context, int dpValue) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * density);
    }

    public static boolean isOnMobile(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null
                && info.getType() != ConnectivityManager.TYPE_WIFI
                && info.getType() != ConnectivityManager.TYPE_ETHERNET;
    }

    /**
     * Gets the color tinted hq-icon
     */
    public static Drawable getTintentDrawable(Context context, @DrawableRes int drawableId, @ColorRes int colorId) {
        Resources resources = context.getResources();
        Drawable icon = DrawableCompat.wrap(ResourcesCompat.getDrawable(resources, drawableId, null));
        DrawableCompat.setTint(icon, ResourcesCompat.getColor(resources, colorId, null));
        return icon;
    }

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
    public static CharSequence makeBulletList(int leadingMargin, List<? extends CharSequence> lines) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int idx = 0; idx < lines.size(); idx++) {
            boolean last = idx == lines.size() - 1;
            CharSequence line = lines.get(idx);

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

    public static <T, U> void ifPresent(Optional<T> first, Optional<U> second, Action2<T, U> action) {
        if (first.isPresent() && second.isPresent()) {
            action.call(first.get(), second.get());
        }
    }

    /**
     * Measures the runtime of the given runnable and log it to the provided logger.
     */
    public static void time(Logger logger, String name, Runnable runnable) {
        if (BuildConfig.DEBUG) {
            time(logger, name, () -> {
                runnable.run();
                return null;
            });
        } else {
            runnable.run();
        }
    }

    public static <T> T time(Logger logger, String name, Supplier<T> supplier) {
        if (BuildConfig.DEBUG) {
            Stopwatch watch = Stopwatch.createStarted();
            try {
                return supplier.get();
            } finally {
                logger.info("{} took {}", name, watch);
            }
        } else {
            return supplier.get();
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

    public static <R> Observable<R> toObservable(Optional<R> optional) {
        if (optional.isPresent()) {
            return just(optional.get());
        } else {
            return empty();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void setViewBackground(View view, Drawable drawable) {
        if (Sdk.isAtLeastJellyBean()) {
            view.setBackground(drawable);
        } else {
            view.setBackgroundDrawable(drawable);
        }
    }

    public static File toFile(Uri uri) {
        checkArgument("file".equals(uri.getScheme()), "not a file:// uri");
        return new File(uri.getPath());
    }

    private static final Pattern MALICIOUS_COMMENT_CHARS = Pattern.compile("([\\p{Mn}\\p{Mc}\\p{Me}])[\\p{Mn}\\p{Mc}\\p{Me}]+");

    public static void linkify(TextView view, String content) {
        content = MALICIOUS_COMMENT_CHARS.matcher(content).replaceAll("$1");

        // fix amazon links
        content = new AmazonAffiliate().affiliateLinks(content);

        Uri base = UriHelper.of(view.getContext()).base();
        String scheme = base.getScheme() + "://";

        SpannableStringBuilder text = SpannableStringBuilder.valueOf(
                RE_GENERIC_LINK.matcher(content).replaceAll("$1"));

        Linkify.addLinks(text, Linkify.WEB_URLS);

        Linkify.addLinks(text, RE_USERNAME, scheme, null, (match, url) -> {
            String user = match.group().substring(1);
            return base.buildUpon().path("/user").appendEncodedPath(user).toString();
        });

        Linkify.addLinks(text, RE_GENERIC_SHORT_LINK, scheme, null,
                (match, url) -> base.buildUpon().appendEncodedPath(match.group(1)).toString());

        Settings settings = Settings.of(view.getContext());
        if (settings.useIncognitoBrowser()) {
            URLSpan[] spans = text.getSpans(0, text.length(), URLSpan.class);
            for (URLSpan span : spans) {
                String url = span.getURL();
                if (url.contains("://pr0gramm.com/"))
                    continue;

                int start = text.getSpanStart(span);
                int end = text.getSpanEnd(span);
                int flags = text.getSpanFlags(span);
                text.removeSpan(span);

                text.setSpan(new PrivateBrowserSpan(url), start, end, flags);
            }
        }

        view.setText(text);

        view.setMovementMethod(new NonCrashingLinkMovementMethod());
    }

    /**
     * Find one view. Raise a {@link NullPointerException} if the view can not be found.
     *
     * @param view The view to search in
     * @param id   The id of the view to search
     */
    public static <T extends View> T findView(View view, int id) {
        //noinspection unchecked
        return checkNotNull(ButterKnife.findById(view, id));
    }

    public static PackageInfo getPackageInfo(Context context) {
        PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getPackageInfo(context.getPackageName(), 0);

        } catch (PackageManager.NameNotFoundException err) {
            throw Throwables.propagate(err);
        }
    }

    public static int getPackageVersionCode(Context context) {
        try {
            if (BuildConfig.DEBUG) {
                return 100;
            } else {
                return getPackageInfo(context).versionCode;
            }
        } catch (Exception error) {
            return -1;
        }
    }

    public static Animator.AnimatorListener endAction(Runnable action) {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                action.run();
            }
        };
    }

    public static Func1<Boolean, Boolean> isTrue() {
        return val -> val != null && val;
    }

    public static Func1<Boolean, Boolean> isFalse() {
        return val -> val == null || !val;
    }

    public static <T> Func1<T, Boolean> isNotNull() {
        return val -> val != null;
    }

    public static void hideSoftKeyboard(View view) {
        try {
            InputMethodManager imm = (InputMethodManager) view.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);

            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    @ColorInt
    public static int darken(@ColorInt int color, float amount) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= (1.f - amount);
        return Color.HSVToColor(hsv);
    }
}
