package com.pr0gramm.app.ui.base

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import com.github.salomonbrys.kodein.KodeinInjector
import com.github.salomonbrys.kodein.android.SupportFragmentInjector
import com.pr0gramm.app.ui.dialogs.DialogDismissListener
import com.pr0gramm.app.util.time
import com.trello.rxlifecycle.LifecycleTransformer
import com.trello.rxlifecycle.components.support.RxAppCompatDialogFragment
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A robo fragment that provides lifecycle events as an observable.
 */
abstract class BaseDialogFragment(name: String) : RxAppCompatDialogFragment(), SupportFragmentInjector, HasViewCache {
    @JvmField
    protected val logger: Logger = LoggerFactory.getLogger(name)

    final override val injector = KodeinInjector()
    final override val kodeinComponent = super.kodeinComponent
    final override val kodeinScope = super.kodeinScope

    final override fun initializeInjector() = super.initializeInjector()
    final override fun destroyInjector() = super.destroyInjector()

    override val viewCache: ViewCache = ViewCache { dialog.findViewById(it) }

    override fun getContext(): Context {
        return super.getContext()!!
    }

    fun <T> bindToLifecycleAsync(): LifecycleTransformer<T> {
        return AsyncLifecycleTransformer(bindToLifecycle<T>())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.time("Injecting services") { initializeInjector() }
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyInjector()
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
        get() = dialog?.context ?: context

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
