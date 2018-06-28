package com.pr0gramm.app.ui.base

import android.content.Context
import com.github.salomonbrys.kodein.KodeinInjector
import com.github.salomonbrys.kodein.android.SupportFragmentInjector
import com.pr0gramm.app.ui.dialogs.OnComplete
import com.pr0gramm.app.ui.dialogs.OnNext
import com.pr0gramm.app.ui.dialogs.subscribeWithErrorHandling
import com.pr0gramm.app.util.time
import com.trello.rxlifecycle.android.FragmentEvent
import com.trello.rxlifecycle.components.support.RxFragment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription

/**
 * A fragment that provides lifecycle events as an observable.
 */
abstract class BaseFragment(name: String) : RxFragment(), HasViewCache, SupportFragmentInjector {
    protected val logger: Logger = LoggerFactory.getLogger(name)

    final override val injector = KodeinInjector()
    final override val kodeinScope = super.kodeinScope
    final override val kodeinComponent = super.kodeinComponent

    final override fun initializeInjector() = super.initializeInjector()
    final override fun destroyInjector() = super.destroyInjector()

    override val viewCache: ViewCache = ViewCache { view?.findViewById(it) }

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
        logger.time("Injecting services") { initializeInjector() }
        super.onAttach(context)
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyInjector()
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
