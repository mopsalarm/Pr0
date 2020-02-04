package com.pr0gramm.app.ui.base

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.dialogs.OnComplete
import com.pr0gramm.app.ui.dialogs.OnNext
import com.pr0gramm.app.ui.dialogs.subscribeWithErrorHandling
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import com.trello.rxlifecycle.android.FragmentEvent
import rx.Observable
import rx.Subscription

/**
 * A fragment that provides lifecycle events as an observable.
 */
abstract class BaseFragment(name: String) : RxFragment(), HasViewCache, LazyInjectorAware {
    protected val logger = Logger(name)

    override val injector: PropertyInjector = PropertyInjector()

    override val viewCache: ViewCache = ViewCache { view?.findViewById(it) }

    fun <T> bindUntilEventAsync(event: FragmentEvent): Observable.Transformer<T, T> {
        return AsyncLifecycleTransformer(bindUntilEvent<T>(event))
    }


    fun <T> Observable<T>.bindToLifecycle(): Observable<T> = compose(this@BaseFragment.bindToLifecycle())

    override fun onAttach(context: Context) {
        logger.time("Injecting services") { injector.inject(context) }
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

    fun setTitle(title: String) {
        val activity = activity as? AppCompatActivity ?: return
        activity.title = title
    }
}
