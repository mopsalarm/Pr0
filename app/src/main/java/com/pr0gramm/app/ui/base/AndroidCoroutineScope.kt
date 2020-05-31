package com.pr0gramm.app.ui.base

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Logger
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment
import com.pr0gramm.app.ui.fragments.withBusyDialog
import com.pr0gramm.app.util.*
import com.pr0gramm.app.warnWithStack
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import rx.Observable
import rx.Scheduler
import rx.schedulers.Schedulers
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.properties.Delegates


inline fun withErrorDialog(block: () -> Unit): Unit {
    try {
        return block()
    } catch (err: Throwable) {
        if (err !is CancellationException) {
            ErrorDialogFragment.defaultOnError().call(err)
        }
    }
}


fun CoroutineScope.launchIgnoreErrors(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit): Job {

    return launch(context, start) {
        catchAll { block() }
    }
}

inline fun <T> withViewDisabled(vararg views: View, block: () -> T): T {
    checkMainThread()

    views.forEach { it.isEnabled = false }
    try {
        return block()
    } finally {
        views.forEach { it.isEnabled = true }
    }
}

suspend inline fun <T> withBackgroundContext(
        context: CoroutineContext? = null,
        noinline block: suspend CoroutineScope.() -> T): T {

    val newContext = if (context != null) context + Async else Async
    return withContext(newContext, block)
}

private val DefaultCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    if (throwable is CancellationException) {
        return@CoroutineExceptionHandler
    }

    throwable.causalChain.let { causalChain ->
        if (causalChain.containsType<IOException>() || causalChain.containsType<HttpException>()) {
            Logger("Background").warn(throwable) {
                "Ignoring uncaught IOException in background coroutine"
            }

            return@CoroutineExceptionHandler
        }
    }

    if (BuildConfig.DEBUG) {
        ErrorDialogFragment.defaultOnError().call(throwable)
    } else {
        AndroidUtility.logToCrashlytics(throwable)
    }
}

val Async = Dispatchers.IO
val AsyncScope = CoroutineScope(Async) + SupervisorJob() + DefaultCoroutineExceptionHandler

val Main = Dispatchers.Main.immediate
val MainScope = CoroutineScope(Main + SupervisorJob() + DefaultCoroutineExceptionHandler)

/**
 * Runs a job as long as the view is attached to some window.
 */
fun View.whileIsAttachedScope(block: suspend CoroutineScope.() -> Unit): Job {
    if (!isAttachedToWindow) {
        Logger(javaClass.directName).warn { "whileIsAttachedScope called on view that is currently not attached." }
        return Job()
    }

    val job = SupervisorJob()

    CoroutineScope(job + Main.immediate + DefaultCoroutineExceptionHandler).launch(block = block)

    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewDetachedFromWindow(v: View?) {
            removeOnAttachStateChangeListener(this)
            job.cancel()
        }

        override fun onViewAttachedToWindow(v: View?) {
        }
    })

    return job
}

/**
 * Executes the given code block everytime the view is attached.
 * It will cancel the scope once the view is detached.
 */
fun View.onAttachedScope(block: suspend CoroutineScope.() -> Unit) {
    var job: Job? = null

    val listener: (Boolean) -> Unit = { isAttached ->
        if (isAttached) {
            // someone did not cancel the previous job?
            job?.cancel()

            // create a new job
            val newJob = Job()
            job = newJob

            // and launch the new coroutine
            CoroutineScope(newJob + Main + DefaultCoroutineExceptionHandler).launch(block = block)
        } else {
            job?.cancel()
            job = null
        }
    }

    addOnAttachStateChangeListener(listener)

    // initial event if already attached
    if (isAttachedToWindow) {
        listener(true)
    }
}

fun <T> toObservable(scheduler: Scheduler = Schedulers.computation(), block: suspend () -> T): Observable<T> {
    val observable = createObservable<T> { emitter ->
        val job = AsyncScope.launch {
            try {
                emitter.onNext(block())
                emitter.onCompleted()
            } catch (err: CancellationException) {
                // ignored
            } catch (err: Throwable) {
                emitter.onError(err)
            }
        }

        emitter.setCancellation {
            job.cancel()
        }
    }

    return observable.observeOn(scheduler)
}

inline fun <T> retryUpTo(tryCount: Int, delay: () -> Unit = {}, block: () -> T): T {
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

fun AppCompatActivity.launchWhenCreated(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    debugVerifyLifecycle(this, Lifecycle.State.CREATED)

    return lifecycleScope.launchWhenCreated(decorate(this, ignoreErrors, busyIndicator, block))
}

fun AppCompatActivity.launchWhenStarted(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    debugVerifyLifecycle(this, Lifecycle.State.STARTED)

    return lifecycleScope.launchWhenStarted(decorate(this, ignoreErrors, busyIndicator, block))
}

fun AppCompatActivity.launchWhenResumed(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    debugVerifyLifecycle(this, Lifecycle.State.RESUMED)

    return lifecycleScope.launchWhenResumed(decorate(this, ignoreErrors, busyIndicator, block))
}


fun Fragment.launchWhenCreated(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    debugVerifyLifecycle(this, Lifecycle.State.CREATED)

    return lifecycleScope.launchWhenCreated {
        decorate(requireActivity(), ignoreErrors, busyIndicator, block)()
    }
}

fun Fragment.launchWhenViewCreated(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    debugVerifyLifecycle(viewLifecycleOwner, Lifecycle.State.CREATED)

    return viewLifecycleOwner.lifecycleScope.launchWhenCreated {
        decorate(requireActivity(), ignoreErrors, busyIndicator, block)()
    }
}

fun Fragment.launchWhenStarted(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    debugVerifyLifecycle(this, Lifecycle.State.STARTED)

    return lifecycleScope.launchWhenStarted {
        decorate(requireActivity(), ignoreErrors, busyIndicator, block)()
    }
}

fun Fragment.launchWhenResumed(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    debugVerifyLifecycle(this, Lifecycle.State.RESUMED)

    return lifecycleScope.launchWhenResumed {
        decorate(requireActivity(), ignoreErrors, busyIndicator, block)()
    }
}


fun View.launchWhenCreated(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    return CoroutineScope(Job() + Main.immediate + DefaultCoroutineExceptionHandler).launch {
        decorate(context, ignoreErrors, busyIndicator, block)()
    }
}

fun View.launchWhenStarted(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    debugOnly {
        if (!isAttachedToWindow) {
            Logger(javaClass.directName).warnWithStack(1) {
                "View is not currently attached but launchWhenStarted is called"
            }
        }
    }

    return whileIsAttachedScope(decorate(context, ignoreErrors, busyIndicator, block))
}

fun decorate(context: Context, ignoreErrors: Boolean, busyIndicator: Boolean,
             block: suspend CoroutineScope.() -> Unit): suspend CoroutineScope.() -> Unit {

    return {
        try {
            if (busyIndicator) {
                withBusyDialog(context) { block() }
            } else {
                block()
            }
        } catch (err: Throwable) {
            if (err !is CancellationException) {
                if (ignoreErrors) {
                    Logger(javaClass.simpleName).warn(err) { "Ignoring error in coroutine" }
                } else {
                    ErrorDialogFragment.defaultOnError().call(err)
                }
            }
        }
    }
}

private fun Any.debugVerifyLifecycle(lifecycleOwner: LifecycleOwner, expectedState: Lifecycle.State) {
    debugOnly {
        val currentState = lifecycleOwner.lifecycle.currentState
        if (!currentState.isAtLeast(expectedState)) {
            Logger(javaClass.directName).warnWithStack(2) {
                "Expected at least '$expectedState', but current state is '$currentState'"
            }
        }
    }
}


fun Lifecycle.asEventFlow(): Flow<Lifecycle.Event> = channelFlow {
    val observer = withContext(Dispatchers.Main.immediate) {
        val observer = LifecycleEventObserver { _, event ->
            channel.offer(event)
        }

        addObserver(observer)
        observer
    }

    awaitClose {
        removeObserver(observer)
    }
}

fun Lifecycle.asStateFlow(): Flow<Lifecycle.State> {
    return asEventFlow().map { currentState }.flowOn(Dispatchers.Main.immediate)
}
