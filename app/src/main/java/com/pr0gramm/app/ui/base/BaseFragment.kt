package com.pr0gramm.app.ui.base

import android.content.Context
import android.os.Bundle
import android.view.View
import butterknife.ButterKnife
import butterknife.Unbinder
import com.f2prateek.dart.Dart
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.Dagger
import com.trello.rxlifecycle.android.FragmentEvent
import com.trello.rxlifecycle.components.support.RxFragment

/**
 * A robo fragment that provides lifecycle events as an observable.
 */
abstract class BaseFragment : RxFragment() {
    private var unbinder: Unbinder? = null

    fun <T> bindUntilEventAsync(event: FragmentEvent): AsyncLifecycleTransformer<T> {
        return AsyncLifecycleTransformer(bindUntilEvent<T>(event))
    }

    fun <T> bindToLifecycleAsync(): AsyncLifecycleTransformer<T> {
        return AsyncLifecycleTransformer(bindToLifecycle<T>())
    }

    override fun onAttach(context: Context?) {
        injectComponent(Dagger.activityComponent(activity))
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        arguments?.let { Dart.inject(this, it) }
        super.onCreate(savedInstanceState)
    }

    protected abstract fun injectComponent(activityComponent: ActivityComponent)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        unbinder = ButterKnife.bind(this, view)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unbinder?.unbind()
        unbinder = null
    }
}
