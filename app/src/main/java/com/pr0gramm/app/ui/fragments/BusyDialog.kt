package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.content.Context
import android.support.annotation.StringRes
import android.support.v4.app.Fragment
import android.widget.TextView
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.showDialog
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import org.slf4j.LoggerFactory
import rx.*

/**
 */
class BusyDialog<T> private constructor(val context: Context, val text: String) : Observable.Operator<T, T> {
    private fun show(): Dialog {
        return showDialog(context) {
            layout(R.layout.progress_dialog)
            cancelable()

            onShow {
                val view = it.findViewById<TextView>(R.id.text)
                view?.text = text
            }
        }
    }

    private fun dismiss(dialog: Dialog) {
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

    override fun call(subscriber: Subscriber<in T>): Subscriber<in T> {
        val dialog = show()

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
            val dialog = show()

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
        private val logger = LoggerFactory.getLogger("BusyDialogFragment")

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

fun Completable.withBusyDialog(fragment: Fragment, text: Int = 0): Completable {
    return withBusyDialog(fragment.activity ?: fragment.context, text)
}

fun <T> Observable<T>.withBusyDialog(context: Context, text: Int = 0): Observable<T> {
    return lift(BusyDialog.busyDialog(context, text))
}

fun <T> Observable<T>.withBusyDialog(fragment: Fragment, text: Int = 0): Observable<T> {
    return withBusyDialog(fragment.activity ?: fragment.context, text)
}
