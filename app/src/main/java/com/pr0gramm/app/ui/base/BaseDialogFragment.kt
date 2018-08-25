package com.pr0gramm.app.ui.base

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import com.pr0gramm.app.ui.dialogs.DialogDismissListener
import com.pr0gramm.app.util.kodein
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.time
import com.trello.rxlifecycle.LifecycleTransformer
import com.trello.rxlifecycle.components.support.RxAppCompatDialogFragment
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.KodeinTrigger
import org.slf4j.Logger
import rx.Observable

/**
 * A robo fragment that provides lifecycle events as an observable.
 */
abstract class BaseDialogFragment(name: String) : RxAppCompatDialogFragment(), KodeinAware, HasViewCache {
    @JvmField
    protected val logger: Logger = logger(name)

    override val kodein: Kodein by lazy { requireContext().kodein }
    override val kodeinTrigger = KodeinTrigger()

    override val viewCache: ViewCache = ViewCache { dialog.findViewById(it) }

    fun <T> bindToLifecycleAsync(): LifecycleTransformer<T> {
        return AsyncLifecycleTransformer(bindToLifecycle<T>())
    }

    fun <T> Observable<T>.bindToLifecycle(): Observable<T> = compose(this@BaseDialogFragment.bindToLifecycle())

    fun <T> Observable<T>.bindToLifecycleAsync(): Observable<T> = compose(this@BaseDialogFragment.bindToLifecycleAsync())

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.time("Injecting services") { kodeinTrigger.trigger() }
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        // bind dialog. It is only created in on start.
        dialog?.let {
            onDialogViewCreated()
        }
    }

    protected open fun onDialogViewCreated() {}

    override fun onDestroyView() {
        if (dialog != null && retainInstance)
            dialog.setDismissMessage(null)

        super.onDestroyView()
        viewCache.reset()
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)

        val activity = activity
        if (activity is DialogDismissListener) {
            // propagate to fragment
            activity.onDialogDismissed(this)
        }
    }

    protected val themedContext: Context
        get() = dialog?.context ?: requireContext()

    override fun dismiss() {
        try {
            super.dismiss()
        } catch (ignored: Exception) {
            // i never want that!
        }
    }

    override fun dismissAllowingStateLoss() {
        try {
            super.dismissAllowingStateLoss()
        } catch (ignored: Exception) {
            // i never want that!
        }
    }
}
