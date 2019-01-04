package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.content.Context
import android.widget.TextView
import androidx.annotation.StringRes
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.base.AndroidCoroutineScope
import com.pr0gramm.app.ui.base.Main
import com.pr0gramm.app.ui.fragments.BusyDialogHelper.dismiss
import com.pr0gramm.app.ui.showDialog
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.checkMainThread
import kotlinx.coroutines.withContext
import rx.*

private object BusyDialogHelper {
    private val logger = Logger("BusyDialog")

    fun show(context: Context, text: String): Dialog {
        return showDialog(context) {
            layout(R.layout.progress_dialog)
            cancelable()

            onShow {
                val view = it.findViewById<TextView>(R.id.text)
                view?.text = text
            }
        }
    }

    fun dismiss(dialog: Dialog) {
        try {
            checkMainThread()
            dialog.dismiss()
        } catch (err: Throwable) {
            logger.warn("Could not dismiss busy dialog:", err)

            if (BuildConfig.DEBUG) {
                throw err
            }
        }
    }
}

/**
 */
class BusyDialog<T>(val context: Context, val text: String) : Observable.Operator<T, T> {
    override fun call(subscriber: Subscriber<in T>): Subscriber<in T> {
        val dialog = BusyDialogHelper.show(context, text)

        return object : Subscriber<T>() {
            override fun onCompleted() {
                dismiss(dialog)
                subscriber.onCompleted()
            }

            override fun onError(e: Throwable) {
                dismiss(dialog)
                subscriber.onError(e)
            }

            override fun onNext(value: T) {
                subscriber.onNext(value)
            }
        }
    }

    fun forCompletable(): Completable.Operator {
        return Completable.Operator { subscriber ->
            val dialog = BusyDialogHelper.show(context, text)

            object : CompletableSubscriber {
                override fun onCompleted() {
                    dismiss(dialog)
                    subscriber.onCompleted()
                }

                override fun onError(throwable: Throwable) {
                    dismiss(dialog)
                    subscriber.onError(throwable)
                }

                override fun onSubscribe(subscription: Subscription) {
                    subscriber.onSubscribe(subscription)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun <T> busyDialog(context: Context, @StringRes textRes: Int = 0): BusyDialog<T> {
            val text = if (textRes != 0) context.getString(textRes) else context.getString(R.string.please_wait)

            return BusyDialog(context, text)
        }
    }
}

fun Completable.withBusyDialog(context: Context, text: Int = 0): Completable {
    return lift(BusyDialog.busyDialog<Any>(context, text).forCompletable())
}

fun Completable.withBusyDialog(fragment: androidx.fragment.app.Fragment, text: Int = 0): Completable {
    val context = fragment.activity ?: fragment.context
    return context?.let { withBusyDialog(it, text) } ?: this
}

fun <T> Observable<T>.withBusyDialog(context: Context, text: Int = 0): Observable<T> {
    return lift(BusyDialog.busyDialog(context, text))
}

fun <T> Observable<T>.withBusyDialog(fragment: androidx.fragment.app.Fragment, text: Int = 0): Observable<T> {
    val context = fragment.activity ?: fragment.context
    return context?.let { withBusyDialog(it, text) } ?: this
}

suspend fun <T> AndroidCoroutineScope.withBusyDialog(@StringRes textId: Int? = null, block: suspend () -> T): T {
    val dialog = withContext(Main) {
        val text = androidContext.getString(textId ?: R.string.please_wait)
        BusyDialogHelper.show(androidContext, text)
    }

    try {
        return block()
    } finally {
        // even run after cancellation, do we want this?
        withContext(Main) {
            BusyDialogHelper.dismiss(dialog)
        }
    }
}
