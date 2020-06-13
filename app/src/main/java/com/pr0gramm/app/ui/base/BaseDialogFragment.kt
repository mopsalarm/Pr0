package com.pr0gramm.app.ui.base

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.dialogs.DialogDismissListener
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector

/**
 * A robo fragment that provides lifecycle events as an observable.
 */
abstract class BaseDialogFragment(name: String) : AppCompatDialogFragment(), LazyInjectorAware, HasViewCache {
    protected val logger = Logger(name)

    override val injector: PropertyInjector = PropertyInjector()

    override val viewCache: ViewCache = ViewCache { dialog?.findViewById(it) }

    protected val themedContext: Context
        get() = dialog?.context ?: requireContext()

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.time("Injecting services") { injector.inject(requireContext()) }
        super.onCreate(savedInstanceState)
    }

    final override fun onStart() {
        super.onStart()

        // bind dialog. It is only created in on start.
        dialog?.let { onDialogViewCreated() }
    }

    protected open fun onDialogViewCreated() {}

    override fun onDestroyView() {
        val dialog = this.dialog
        if (dialog != null && retainInstance)
            dialog.setDismissMessage(null)

        super.onDestroyView()

        viewCache.reset()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        val activity = activity
        if (activity is DialogDismissListener) {
            // propagate to fragment
            activity.onDialogDismissed(this)
        }
    }

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
