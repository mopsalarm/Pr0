package com.pr0gramm.app.ui.base

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import com.pr0gramm.app.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


fun AppCompatActivity.launchUntilPause(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    return launchUntil(this, lifecycle, ignoreErrors, busyIndicator, block, Lifecycle.Event.ON_PAUSE)
}

fun AppCompatActivity.launchUntilStop(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    return launchUntil(this, lifecycle, ignoreErrors, busyIndicator, block, Lifecycle.Event.ON_STOP)
}

fun AppCompatActivity.launchUntilDestroy(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    return lifecycleScope.launch(block = decorate(this, ignoreErrors, busyIndicator, block))
}


fun Fragment.launchUntilPause(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    return launchUntil(requireContext(), lifecycle, ignoreErrors, busyIndicator, block, Lifecycle.Event.ON_PAUSE)
}

fun Fragment.launchUntilStop(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    return launchUntil(requireContext(), lifecycle, ignoreErrors, busyIndicator, block, Lifecycle.Event.ON_STOP)
}

fun Fragment.launchUntilViewDestroy(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    return launchInViewScope(ignoreErrors, busyIndicator, block)
}

fun Fragment.launchInViewScope(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    return viewLifecycleOwner.lifecycleScope.launch(
            block = decorate(requireContext(), ignoreErrors, busyIndicator, block)
    )
}

fun Fragment.launchUntilDestroy(
        ignoreErrors: Boolean = false,
        busyIndicator: Boolean = false,
        block: suspend CoroutineScope.() -> Unit): Job {

    return launchUntil(requireContext(), lifecycle, ignoreErrors, busyIndicator, block, Lifecycle.Event.ON_DESTROY)
}


private class UntilEventController(
        private val lifecycle: Lifecycle,
        private val job: Job, private val event: Lifecycle.Event) : LifecycleEventObserver {

    private val logger = Logger("UntilEventController")

    init {
        lifecycle.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (this.event === event) {
            logger.debug { "Stop job $job at event $event" }
            job.cancel()
            finish()
        }
    }

    fun finish() {
        lifecycle.removeObserver(this)
    }
}

private fun launchUntil(context: Context, lifecycle: Lifecycle, ignoreErrors: Boolean, busyIndicator: Boolean,
                        block: suspend CoroutineScope.() -> Unit, cancelOnEvent: Lifecycle.Event): Job {

    return lifecycle.coroutineScope.launch {
        val job = SupervisorJob(parent = coroutineContext[Job])

        val controller = withContext(Dispatchers.Main.immediate) {
            UntilEventController(lifecycle, job, cancelOnEvent)
        }

        try {
            withContext(job, decorate(context, ignoreErrors, busyIndicator, block))
        } finally {
            controller.finish()
        }
    }
}
