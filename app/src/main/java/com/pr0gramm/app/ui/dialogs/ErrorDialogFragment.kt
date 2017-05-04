package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import com.pr0gramm.app.ui.dialog
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import com.pr0gramm.app.util.AndroidUtility.logToCrashlytics
import com.pr0gramm.app.util.ErrorFormatting
import org.slf4j.LoggerFactory
import rx.functions.Action1
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException

/**
 * This dialog fragment shows and error to the user.
 */
class ErrorDialogFragment : DialogFragment() {
    interface OnErrorDialogHandler {
        fun showErrorDialog(error: Throwable, formatter: ErrorFormatting.Formatter)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialog(activity) {
            content(arguments.getString("content") ?: "no content :(")
            positive()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("ErrorDialogFragment")

        private var GlobalErrorDialogHandler: WeakReference<OnErrorDialogHandler?> = WeakReference(null)
        private var PreviousError = WeakReference<Throwable>(null)

        fun unsetGlobalErrorDialogHandler(handler: OnErrorDialogHandler) {
            checkMainThread()

            GlobalErrorDialogHandler.get()?.let { oldHandler ->
                if (oldHandler === handler) {
                    GlobalErrorDialogHandler = WeakReference(null)
                }
            }
        }

        @JvmStatic
        var globalErrorDialogHandler: OnErrorDialogHandler?
            get() = GlobalErrorDialogHandler.get()
            set(handler) {
                checkMainThread()
                GlobalErrorDialogHandler = WeakReference(handler)
            }

        private fun processError(error: Throwable, handler: OnErrorDialogHandler?) {
            logger.error("An error occurred", error)

            if (error is CancellationException) {
                logger.warn("Ignoring cancellation exception.")
                return
            }

            try {
                // do some checking so we don't log this exception twice
                val sendToCrashlytics = PreviousError.get() !== error
                PreviousError = WeakReference<Throwable>(error)

                // format and log
                val formatter = ErrorFormatting.getFormatter(error)
                if (sendToCrashlytics && formatter.shouldSendToCrashlytics())
                    logToCrashlytics(error)

                handler?.showErrorDialog(error, formatter)

            } catch (thr: Throwable) {
                // there was an error handling the error. oops.
                logToCrashlytics(thr)
            }
        }

        @JvmStatic
        fun showErrorString(fragmentManager: FragmentManager, message: String) {
            logger.info(message)

            try {
                val arguments = Bundle()
                arguments.putString("content", message)

                // remove previous dialog, if any
                dismissErrorDialog(fragmentManager)

                val dialog = ErrorDialogFragment()
                dialog.arguments = arguments
                dialog.show(fragmentManager, "ErrorDialog")

            } catch (error: Exception) {
                logger.error("Could not show error dialog", error)
            }
        }

        /**
         * Dismisses any previously shown error dialog.
         */
        private fun dismissErrorDialog(fm: FragmentManager) {
            try {
                val previousFragment = fm.findFragmentByTag("ErrorDialog")
                (previousFragment as? DialogFragment)?.dismissAllowingStateLoss()

            } catch (error: Throwable) {
                logger.warn("Error removing previous dialog", error)
            }

        }

        /**
         * Creates the default error callback [rx.functions.Action1]
         */
        @JvmStatic
        fun defaultOnError(): Action1<Throwable> {
            return Action1 { error -> processError(error, globalErrorDialogHandler) }
        }
    }
}
