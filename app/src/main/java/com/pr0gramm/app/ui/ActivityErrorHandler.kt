package com.pr0gramm.app.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.support.v4.app.FragmentActivity

import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment
import com.pr0gramm.app.util.ErrorFormatting
import java.io.PrintWriter
import java.io.StringWriter

import java.lang.ref.WeakReference

/**
 */
class ActivityErrorHandler(application: Application) : ErrorDialogFragment.OnErrorDialogHandler, Application.ActivityLifecycleCallbacks {
    private var current = NULL

    private var pendingError: Throwable? = null
    private var pendingFormatter: ErrorFormatting.Formatter? = null

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun showErrorDialog(error: Throwable, formatter: ErrorFormatting.Formatter) {
        val activity = current.get()
        if (activity != null) {
            val message = if (formatter.handles(error)) {
                formatter.getMessage(activity, error)
            } else {
                StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            }

            ErrorDialogFragment.showErrorString(activity.supportFragmentManager, message)

            // reset any pending errors
            pendingError = null
            pendingFormatter = null

        } else {
            this.pendingError = error
            this.pendingFormatter = formatter
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is FragmentActivity) {
            current = WeakReference(activity)

            if (pendingError != null && pendingFormatter != null) {
                showErrorDialog(pendingError!!, pendingFormatter!!)
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (current.get() === activity) {
            current = NULL
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (current.get() === activity) {
            current = NULL
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current.get() === activity) {
            current = NULL
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {}

    companion object {
        private val NULL = WeakReference<FragmentActivity>(null)
    }

}
