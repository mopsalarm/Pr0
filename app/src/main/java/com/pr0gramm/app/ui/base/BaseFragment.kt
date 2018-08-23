package com.pr0gramm.app.ui.base

import android.content.Context
import com.pr0gramm.app.ui.dialogs.OnComplete
import com.pr0gramm.app.ui.dialogs.OnNext
import com.pr0gramm.app.ui.dialogs.subscribeWithErrorHandling
import com.pr0gramm.app.util.debug
import com.pr0gramm.app.util.kodein
import com.pr0gramm.app.util.time
import com.trello.rxlifecycle.android.FragmentEvent
import com.trello.rxlifecycle.components.support.RxFragment
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.KodeinTrigger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription

/**
 * A fragment that provides lifecycle events as an observable.
 */
abstract class BaseFragment(name: String) : RxFragment(), HasViewCache, KodeinAware {
    protected val logger: Logger = LoggerFactory.getLogger(name)

    override val kodein: Kodein by lazy { requireContext().kodein }
    override val kodeinTrigger = KodeinTrigger()

    override val viewCache: ViewCache = ViewCache { view?.findViewById(it) }

    init {
        debug {
            lifecycle().subscribe { event ->
                logger.info("Lifecycle {}: {}", System.identityHashCode(this), event)
            }
        }
    }

    override fun getContext(): Context {
        return super.getContext()!!
    }

    fun <T> bindUntilEventAsync(event: FragmentEvent): AsyncLifecycleTransformer<T> {
        return AsyncLifecycleTransformer(bindUntilEvent<T>(event))
    }

    fun <T> bindToLifecycleAsync(): AsyncLifecycleTransformer<T> {
        return AsyncLifecycleTransformer(bindToLifecycle<T>())
    }

    fun <T> Observable<T>.bindToLifecycle(): Observable<T> = compose(this@BaseFragment.bindToLifecycle())

    fun <T> Observable<T>.bindToLifecycleAsync(): Observable<T> = compose(this@BaseFragment.bindToLifecycleAsync())

    override fun onAttach(context: Context?) {
        logger.time("Injecting services") { kodeinTrigger.trigger() }
        super.onAttach(context)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        this.viewCache.reset()
    }

    fun <T> Observable<T>.subscribeWithErrorHandling(
            onComplete: OnComplete = {}, onNext: OnNext<T> = {}): Subscription {

        return subscribeWithErrorHandling(childFragmentManager, onComplete, onNext)
    }
}
