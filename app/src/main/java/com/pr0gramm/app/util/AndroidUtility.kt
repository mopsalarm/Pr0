package com.pr0gramm.app.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.util.LruCache
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.app.TaskStackBuilder
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.ConnectivityManagerCompat
import com.crashlytics.android.Crashlytics
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Debug
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.truss
import okhttp3.HttpUrl
import rx.Completable
import rx.util.async.Async
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Place to put everything that belongs nowhere. Thanks Obama.
 */
object AndroidUtility {
    private val logger = logger("AndroidUtility")

    private val EXCEPTION_BLACKLIST = listOf("MediaCodec", "dequeueInputBuffer", "dequeueOutputBuffer", "releaseOutputBuffer", "native_")

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
    fun getActionBarHeight(context: Context): Int {
        context.obtainStyledAttributes(intArrayOf(R.attr.actionBarSize)).use {
            return it.getDimensionPixelSize(it.getIndex(0), -1)
        }
    }

    /**
     * Gets the height of the statusbar.
     */
    fun getStatusBarHeight(context: Context): Int {
        var result = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val resources = context.resources
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                result = resources.getDimensionPixelSize(resourceId)
            }
        }

        return result
    }

    fun logToCrashlytics(error: Throwable?) {
        if (error == null)
            return

        try {
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            if (EXCEPTION_BLACKLIST.any { it in trace }) {
                logger.warn("Ignoring exception", error)
                return
            }

            val errorStr = error.toString()
            if ("connect timed out" in errorStr)
                return

            // try to rate limit exceptions.
            val key = System.identityHashCode(error)
            if (cache.get(key) != null) {
                return
            } else {
                cache.put(key, Unit)
            }

            // log to crashlytics for fast error reporting.
            Crashlytics.getInstance().core.logException(error)

        } catch (_: IllegalStateException) {
            // most certainly crashlytics was not activated.
            logger.warn("Looks like crashlytics was not activated. Here is the error:", error)

        } catch (err: Throwable) {
            logger.info("Could not send error to crashlytics", err)
        }

    }

    fun dp(context: Context, dpValue: Int): Int {
        return context.dip2px(dpValue.toFloat()).toInt()
    }

    fun isOnMobile(context: Context?): Boolean {
        context ?: return false

        val cm = context.getSystemService(
                Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return ConnectivityManagerCompat.isActiveNetworkMetered(cm)
    }

    /**
     * Gets the color tinted hq-icon
     */
    fun getTintentDrawable(context: Context, @DrawableRes drawableId: Int, @ColorRes colorId: Int): Drawable {
        val resources = context.resources
        val icon = DrawableCompat.wrap(ResourcesCompat.getDrawable(resources, drawableId, null)!!)
        DrawableCompat.setTint(icon, ResourcesCompat.getColor(resources, colorId, null))
        return icon
    }

    /**
     * Returns a CharSequence containing a bulleted and properly indented list.

     * @param leadingMargin In pixels, the space between the left edge of the bullet and the left edge of the text.
     * *
     * @param lines         An array of CharSequences. Each CharSequences will be a separate line/bullet-point.
     */
    fun makeBulletList(leadingMargin: Int, lines: List<CharSequence>): CharSequence {
        return truss {
            for (idx in lines.indices) {
                val last = idx == lines.size - 1
                val line = lines[idx]

                append(line,
                        BulletSpan(leadingMargin / 3),
                        LeadingMarginSpan.Standard(leadingMargin))

                append(if (last) "" else "\n")
            }
        }
    }

    fun toFile(uri: Uri): File {
        require("file" == uri.scheme) { "not a file:// uri" }
        return File(uri.path)
    }

    fun buildVersionCode(): Int {
        return Debug.versionOverride.takeIf { BuildConfig.DEBUG }
                ?: BuildConfig.VERSION_CODE
    }

    fun hideSoftKeyboard(view: View?) {
        if (view != null) {
            try {
                val imm = view.context
                        .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

                imm.hideSoftInputFromWindow(view.windowToken, 0)
            } catch (ignored: Exception) {
            }
        }
    }

    fun showSoftKeyboard(view: EditText?) {
        if (view != null) {
            try {
                view.requestFocus()

                val imm = view.context
                        .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            } catch (ignored: Exception) {
            }
        }
    }

    @ColorInt
    fun darken(@ColorInt color: Int, amount: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] *= 1f - amount
        return Color.HSVToColor(hsv)
    }

    fun recreateActivity(activity: Activity) {
        val intent = activity.intent
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        TaskStackBuilder.create(activity)
                .addNextIntentWithParentStack(intent)
                .startActivities()
    }

    fun applyWindowFullscreen(activity: Activity, fullscreen: Boolean) {
        var flags = 0
        if (fullscreen) {
            flags = flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                flags = flags or (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        val decorView = activity.window.decorView
        decorView.systemUiVisibility = flags
    }

    fun screenSize(activity: Activity): Point {
        val screenSize = Point()
        val display = activity.windowManager.defaultDisplay

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            display.getRealSize(screenSize)
        } else {
            display.getSize(screenSize)
        }

        return screenSize
    }

    fun screenIsLandscape(activity: Activity?): Boolean {
        if (activity == null) {
            return false
        }

        val size = screenSize(activity)
        return size.x > size.y
    }

    fun screenIsPortrait(activity: Activity): Boolean {
        return !screenIsLandscape(activity)
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

    fun checkMainThread() {
        if (Looper.getMainLooper().thread !== Thread.currentThread()) {
            logger.error(
                    "Expected to be in main thread, current thread is: {}",
                    Thread.currentThread().name)

            throw IllegalStateException("Must be called from the main thread.")
        }
    }

    fun checkNotMainThread(msg: String? = null) {
        if (Looper.getMainLooper().thread === Thread.currentThread()) {
            logger.error("Expected not to be on main thread: {}", msg)
            throw IllegalStateException("Must not be called from the main thread: $msg")
        }
    }
}

fun doInBackground(action: () -> Unit): Completable {
    val o = Async.start<Any>({
        try {
            action()
        } catch (thr: Throwable) {
            // log it
            AndroidUtility.logToCrashlytics(BackgroundThreadException(thr))

            // forward the exception
            throw thr
        }

        null
    }, BackgroundScheduler)

    return o.toCompletable()
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
    val hasMessage = !message.isBlank() && (
            !hasCause || !message.contains(cause!!.javaClass.simpleName))

    if (hasMessage) {
        if (hasCause) {
            return String.format("%s(%s), caused by %s",
                    type, error.message, cause!!.getMessageWithCauses())
        } else {
            return String.format("%s(%s)", type, error.message)
        }
    } else {
        if (hasCause) {
            return String.format("%s, caused by %s", type, cause!!.getMessageWithCauses())
        } else {
            return type
        }
    }
}

fun endAction(action: () -> Unit): Animator.AnimatorListener {
    return object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            action()
        }
    }
}

inline fun <T : View> endAction(view: T, crossinline action: (T) -> Unit): Animator.AnimatorListener {
    return object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator?) {
            action(view)
        }
    }
}

fun hideViewEndAction(view: View): Animator.AnimatorListener {
    return object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator?) {
            view.visible = false
        }
    }
}

fun Uri.toHttpUrl(): HttpUrl = HttpUrl.parse(this.toString())!!