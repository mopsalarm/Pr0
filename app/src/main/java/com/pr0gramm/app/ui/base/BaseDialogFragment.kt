package com.pr0gramm.app.ui.base

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.dialogs.DialogDismissListener
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import com.trello.rxlifecycle.components.support.RxAppCompatDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A robo fragment that provides lifecycle events as an observable.
 */
abstract class BaseDialogFragment(name: String) : RxAppCompatDialogFragment(), LazyInjectorAware, HasViewCache, AndroidCoroutineScope {
    protected val logger = Logger(name)

    override val injector: PropertyInjector = PropertyInjector()

    override val viewCache: ViewCache = ViewCache { dialog?.findViewById(it) }

    override lateinit var job: Job
    private lateinit var onStartScope: AndroidCoroutineScope

    override val androidContext: Context
        get() = requireContext()

    override fun onCreate(savedInstanceState: Bundle?) {
        logger.time("Injecting services") { injector.inject(requireContext()) }

        job = SupervisorJob()
        super.onCreate(savedInstanceState)
    }

    final override fun onStart() {
        super.onStart()

        onStartScope = newChild()

        // bind dialog. It is only created in on start.
        dialog?.let {
            onStartScope.launch(Main.immediate) {
                onDialogViewCreated()
            }
        }
    }

    protected open suspend fun onDialogViewCreated() {}

    override fun onStop() {
        super.onStop()
        onStartScope.cancelScope()
    }

    override fun onDestroyView() {
        val dialog = this.dialog
        if (dialog != null && retainInstance)
            dialog.setDismissMessage(null)

        super.onDestroyView()
        job.cancel()

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
