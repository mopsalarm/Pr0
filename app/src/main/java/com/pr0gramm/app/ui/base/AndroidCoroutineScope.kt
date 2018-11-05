package com.pr0gramm.app.ui.base

import android.content.Context
import android.view.View
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment
import com.pr0gramm.app.ui.fragments.withBusyDialog
import com.pr0gramm.app.util.AndroidUtility
import kotlinx.coroutines.*
import rx.Observable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.properties.Delegates


interface AndroidCoroutineScope : CoroutineScope {
    var job: Job

    val androidContext: Context

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main + LoggingCoroutineExceptionHandler


    fun launchWithErrorHandler(
            context: CoroutineContext = EmptyCoroutineContext,
            start: CoroutineStart = CoroutineStart.DEFAULT,
            busyDialog: Boolean = false,
            block: suspend CoroutineScope.() -> Unit
    ): Job {
        return launch(context, start) {
            try {
                if (busyDialog) {
                    withBusyDialog { block() }
                } else {
                    block()
                }
            } catch (err: Throwable) {
                ErrorDialogFragment.defaultOnError().call(err)
            }
        }
    }
}

inline fun <T> withViewDisabled(view: View, block: () -> T): T {
    AndroidUtility.checkMainThread()

    view.isEnabled = false
    try {
        return block()
    } finally {
        view.isEnabled = true
    }
}

suspend inline fun <T> withAsyncContext(
        context: CoroutineContext? = null,
        noinline block: suspend CoroutineScope.() -> T): T {

    val newContext = if (context != null) context + Async else Async
    return withContext(newContext, block)
}

val LoggingCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    if (BuildConfig.DEBUG) {
        ErrorDialogFragment.defaultOnError().call(throwable)
    } else {
        AndroidUtility.logToCrashlytics(throwable)
    }
}

val Async = Dispatchers.IO
val AsyncScope = CoroutineScope(Async) + LoggingCoroutineExceptionHandler

val Main = Dispatchers.Main
val MainScope = CoroutineScope(Main)

suspend fun <T> Observable<T>.await(): T {
    val def = CompletableDeferred<T>()

    val sub = this.single().subscribe({ def.complete(it) }, { def.completeExceptionally(it) })

    def.invokeOnCompletion {
        if (def.isCancelled) {
            sub.unsubscribe()
        }
    }

    return def.await()
}

fun <T> Deferred<T>.toObservable(): Observable<T> {
    return toObservable { await() }
}

fun <T> toObservable(block: suspend () -> T): Observable<T> {
    return Observable.fromCallable {
        runBlocking { block() }
    }
}

inline fun <T> retryUpTo(tryCount: Int, delay: () -> Unit, block: () -> T): T? {
    var error: Throwable by Delegates.notNull()

    repeat(tryCount) {
        try {
            return@retryUpTo block()
        } catch (err: Throwable) {
            error = err
            delay()
        }
    }

    throw error
}
