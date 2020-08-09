package com.pr0gramm.app.ui.base

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.view.ContextThemeWrapper
import androidx.viewbinding.ViewBinding
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.dialogs.DialogDismissListener
import com.pr0gramm.app.ui.resolveDialogTheme
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector

/**
 * A robo fragment that provides lifecycle events as an observable.
 */
abstract class BaseDialogFragment(name: String) : AppCompatDialogFragment(), LazyInjectorAware {
    protected val logger = Logger(name)

    override val injector: PropertyInjector = PropertyInjector()

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


abstract class ViewBindingDialogFragment<T : ViewBinding>(
        name: String, private val inflate: (inflater: LayoutInflater) -> T,
) : BaseDialogFragment(name) {

    protected lateinit var views: T

    final override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // apply default dialog theme to context before inflating the
        // bindings, so that the dialog theme is applied to the view.
        val themedContext = ContextThemeWrapper(
                requireContext(), resolveDialogTheme(requireContext(), theme),
        )

        views = inflate(LayoutInflater.from(themedContext))
        return onCreateDialog(views.root)
    }

    abstract fun onCreateDialog(contentView: View): Dialog
}