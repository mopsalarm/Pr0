package com.pr0gramm.app.ui.base

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import butterknife.ButterKnife
import butterknife.Unbinder
import com.f2prateek.dart.Dart
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.Dagger
import com.pr0gramm.app.ui.dialogs.DialogDismissListener
import com.trello.rxlifecycle.LifecycleTransformer
import com.trello.rxlifecycle.components.support.RxAppCompatDialogFragment

/**
 * A robo fragment that provides lifecycle events as an observable.
 */
abstract class BaseDialogFragment : RxAppCompatDialogFragment() {
    private var unbinder: Unbinder? = null

    fun <T> bindToLifecycleAsync(): LifecycleTransformer<T> {
        return AsyncLifecycleTransformer(bindToLifecycle<T>())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        injectComponent(Dagger.activityComponent(activity))
        arguments?.let { Dart.inject(this, it) }
        super.onCreate(savedInstanceState)
    }

    protected abstract fun injectComponent(activityComponent: ActivityComponent)

    override fun onStart() {
        super.onStart()

        // bind dialog. It is only created in on start.
        dialog?.let {
            unbinder = ButterKnife.bind(this, it)
            onDialogViewCreated()
        }
    }

    protected open fun onDialogViewCreated() {}

    override fun onDestroyView() {
        if (dialog != null && retainInstance)
            dialog.setDismissMessage(null)

        super.onDestroyView()

        unbinder?.unbind()
        unbinder = null
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
