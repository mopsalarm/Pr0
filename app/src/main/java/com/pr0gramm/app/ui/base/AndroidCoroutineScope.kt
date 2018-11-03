package com.pr0gramm.app.ui.base

import android.content.Context
import android.view.View
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment
import com.pr0gramm.app.ui.fragments.withBusyDialog
import com.pr0gramm.app.util.AndroidUtility
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


interface AndroidCoroutineScope : CoroutineScope {
    var job: Job

    val androidContext: Context

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main


    fun launchWithErrorHandler(
            context: CoroutineContext = EmptyCoroutineContext,
            start: CoroutineStart = CoroutineStart.DEFAULT,
            useBusyIndicator: Boolean = false,
            block: suspend CoroutineScope.() -> Unit
    ): Job {
        return launch(context, start) {
            try {
                if (useBusyIndicator) {
                    withBusyDialog {
                        block()
                    }
                } else {
                    block()
                }
            } catch (err: Throwable) {
                ErrorDialogFragment.defaultOnError().call(err)
            }
        }
    }
}

suspend fun <T> withViewDisabled(view: View, block: suspend () -> T): T {
    AndroidUtility.checkMainThread()

    view.isEnabled = false
    try {
        return block()
    } finally {
        view.isEnabled = true
    }
}
