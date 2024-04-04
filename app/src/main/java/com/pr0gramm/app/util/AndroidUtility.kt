package com.pr0gramm.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.sqlite.SQLiteFullException
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Build
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.util.LruCache
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.TaskStackBuilder
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.use
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.ConnectivityManagerCompat
import androidx.core.text.inSpans
import androidx.core.view.postDelayed
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.debugConfig
import com.pr0gramm.app.ui.PermissionHelperDelegate
import com.pr0gramm.app.ui.base.AsyncScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Place to put everything that belongs nowhere. Thanks Obama.
 */
object AndroidUtility {
    private val logger = Logger("AndroidUtility")

    private val EXCEPTION_BLACKLIST =
        listOf("MediaCodec", "dequeueInputBuffer", "dequeueOutputBuffer", "releaseOutputBuffer", "native_")

    private val cache = LruCache<Int, Unit>(6)

    /**
     * Gets the height of the action bar as definied in the style attribute
     * [R.attr.actionBarSize] plus the height of the status bar on android
     * Kitkat and above.

     * @param context A context to resolve the styled attribute value for
     */
    fun getActionBarContentOffset(context: Context): Int {
        return getStatusBarHeight(context) + getActionBarHeight(context)
    }

    /**
     * Gets the height of the actionbar.
     */
    private fun getActionBarHeight(context: Context): Int {
        context.obtainStyledAttributes(intArrayOf(R.attr.actionBarSize)).use { ta ->
            return ta.getDimensionPixelSize(ta.getIndex(0), -1)
        }
    }

    /**
     * Gets the height of the statusbar.
     */
    fun getStatusBarHeight(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val window = activityFromContext(context)?.window
            val rootWindowInsets = window?.decorView?.rootWindowInsets
            if (rootWindowInsets != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // this seems to be always correct on android 11 and better.
                    return rootWindowInsets
                        .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                        .top
                }

                // this works for android 6 and above
                return rootWindowInsets.stableInsetTop
            }
        }

        // use the old code as fallback in case we have a really old android
        // or if we don't have a window or decorView

        var result = 0

        val resources = context.resources
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }

        return result
    }

    fun logToCrashlytics(error: Throwable?, force: Boolean = false) {
        val causalChain = error?.causalChain ?: return

        if (causalChain.containsType<CancellationException>()) {
            return
        }

        if (!force) {
            if (causalChain.containsType<PermissionHelperDelegate.PermissionNotGranted>()) {
                return
            }

            if (causalChain.containsType<IOException>() || causalChain.containsType<HttpException>()) {
                logger.warn(error) { "Ignoring network exception" }
                return
            }

            if (causalChain.containsType<SQLiteFullException>()) {
                logger.warn { "Database is full: $error" }
                return
            }
        }

        try {
            val trace = StringWriter().also { w -> error.printStackTrace(PrintWriter(w)) }.toString()
            if (EXCEPTION_BLACKLIST.any { word -> word in trace }) {
                logger.warn("Ignoring exception", error)
                return
            }

            val errorStr = error.toString()
            if ("connect timed out" in errorStr) {
                return
            }

            // try to rate limit exceptions.
            val key = System.identityHashCode(error)
            if (cache.get(key) != null) {
                return
            } else {
                cache.put(key, Unit)
            }

            ignoreAllExceptions {
                FirebaseCrashlytics.getInstance().recordException(error)
            }

        } catch (err: Throwable) {
            logger.warn(err) { "Could not send error $error to crash tool" }
        }
    }

    fun isOnMobile(context: Context?): Boolean {
        context ?: return false

        debugOnly {
            return true
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return ConnectivityManagerCompat.isActiveNetworkMetered(cm)
    }

    /**
     * Returns a CharSequence containing a bulleted and properly indented list.

     * @param leadingMargin In pixels, the space between the left edge of the bullet and the left edge of the text.
     * *
     * @param lines         An array of CharSequences. Each CharSequences will be a separate line/bullet-point.
     */
    fun makeBulletList(leadingMargin: Int, lines: List<CharSequence>): CharSequence {
        return SpannableStringBuilder().apply {
            for (idx in lines.indices) {
                inSpans(BulletSpan(leadingMargin / 3)) {
                    inSpans(LeadingMarginSpan.Standard(leadingMargin)) {
                        append(lines[idx])
                    }
                }

                val last = idx == lines.lastIndex
                append(if (last) "" else "\n")
            }
        }
    }

    fun buildVersionCode(): Int {
        return if (BuildConfig.DEBUG) {
            debugConfig.versionOverride ?: BuildConfig.VERSION_CODE
        } else {
            BuildConfig.VERSION_CODE
        }
    }

    fun showSoftKeyboard(view: EditText?) {
        view?.postDelayed(100) {
            try {
                view.requestFocus()

                val imm = view.context.getSystemService<InputMethodManager>()
                imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)

            } catch (err: Exception) {
                logger.warn(err) { "Failed to show soft keyboard" }
            }
        }
    }

    fun hideSoftKeyboard(view: View?) {
        if (view != null) {
            try {
                val imm = view.context.getSystemService<InputMethodManager>()
                imm?.hideSoftInputFromWindow(view.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

            } catch (err: Exception) {
                logger.warn(err) { "Failed to hide soft keyboard" }
            }
        }
    }

    fun recreateActivity(activity: Activity) {
        val intent = Intent(activity.intent)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        TaskStackBuilder.create(activity)
            .addNextIntentWithParentStack(intent)
            .startActivities()
    }

    fun applyWindowFullscreen(activity: Activity, fullscreen: Boolean) {
        var flags = 0

        if (fullscreen) {
            flags = flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

            flags = flags or (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)

            flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        val decorView = activity.window.decorView
        decorView.systemUiVisibility = flags
    }

    fun screenSize(activity: Activity): Point {
        val screenSize = Point()
        val display = activity.windowManager.defaultDisplay

        display.getRealSize(screenSize)

        return screenSize
    }

    fun screenIsLandscape(activity: Activity?): Boolean {
        if (activity == null) {
            return false
        }

        val size = screenSize(activity)
        return size.x > size.y
    }

    /**
     * Tries to get a basic activity from the given context. Returns an empty observable,
     * if no activity could be found.
     */
    fun activityFromContext(context: Context): Activity? {
        if (context is Activity)
            return context

        if (context is ContextWrapper)
            return activityFromContext(context.baseContext)

        return null
    }

    @ColorInt
    fun resolveColorAttribute(context: Context, attr: Int): Int {
        val arr = context.obtainStyledAttributes(intArrayOf(attr))
        try {
            return arr.getColor(arr.getIndex(0), 0)
        } finally {
            arr.recycle()
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun checkMainThread() = debugOnly {
    if (Looper.getMainLooper().thread !== Thread.currentThread()) {
        Logger("AndroidUtility").error { "Expected to be in main thread but was: ${Thread.currentThread().name}" }
        throw IllegalStateException("Must be called from the main thread.")
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun checkNotMainThread(msg: String? = null) = debugOnly {
    if (Looper.getMainLooper().thread === Thread.currentThread()) {
        Logger("AndroidUtility").error { "Expected not to be on main thread: $msg" }
        throw IllegalStateException("Must not be called from the main thread: $msg")
    }
}

inline fun <T> doInBackground(crossinline action: suspend () -> T): Job {
    return AsyncScope.launch {
        try {
            action()
        } catch (thr: Throwable) {
            // log it
            AndroidUtility.logToCrashlytics(BackgroundThreadException(thr))
        }
    }
}

class BackgroundThreadException(cause: Throwable) : RuntimeException(cause)

fun Throwable.getMessageWithCauses(): String {
    val error = this
    val type = javaClass.name
        .replaceFirst(".+\\.".toRegex(), "")
        .replace('$', '.')

    val cause = error.cause

    val hasCause = cause != null && error !== cause
    val message = error.message ?: ""
    val hasMessage = message.isNotBlank() && (
            !hasCause || (cause != null && cause.javaClass.directName !in message))

    return if (hasMessage) {
        if (hasCause && cause != null) {
            "$type(${error.message}), caused by ${cause.getMessageWithCauses()}"
        } else {
            "$type(${error.message})"
        }
    } else {
        if (hasCause && cause != null) {
            "$type, caused by ${cause.getMessageWithCauses()}"
        } else {
            type
        }
    }
}

/**
 * Gets the color tinted hq-icon
 */
fun Context.getTintedDrawable(@DrawableRes drawableId: Int, @ColorRes colorId: Int): Drawable {
    val icon = DrawableCompat.wrap(AppCompatResources.getDrawable(this, drawableId)!!.mutate())
    DrawableCompat.setTint(icon, ResourcesCompat.getColor(resources, colorId, null))
    return icon
}

fun ImageView.setImageResource(@DrawableRes drawableId: Int, @ColorRes colorId: Int) {
    setImageDrawable(context.getTintedDrawable(drawableId, colorId))
}
