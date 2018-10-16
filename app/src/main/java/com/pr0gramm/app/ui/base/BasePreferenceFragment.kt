package com.pr0gramm.app.ui.base

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.CheckResult
import androidx.preference.PreferenceFragmentCompat
import com.pr0gramm.app.util.kodein
import com.pr0gramm.app.util.logger
import com.pr0gramm.app.util.time
import com.trello.rxlifecycle.LifecycleProvider
import com.trello.rxlifecycle.LifecycleTransformer
import com.trello.rxlifecycle.RxLifecycle
import com.trello.rxlifecycle.android.FragmentEvent
import com.trello.rxlifecycle.android.RxLifecycleAndroid
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.KodeinTrigger
import org.slf4j.Logger
import rx.Observable
import rx.subjects.BehaviorSubject

abstract class BasePreferenceFragment(name: String) : PreferenceFragmentCompat(), LifecycleProvider<FragmentEvent>, KodeinAware {
    protected val logger: Logger = logger(name)

    private val lifecycleSubject = BehaviorSubject.create<FragmentEvent>()

    override val kodein: Kodein by lazy { requireContext().kodein }
    override val kodeinTrigger = KodeinTrigger()

    @CheckResult
    override fun lifecycle(): Observable<FragmentEvent> {
        return lifecycleSubject.asObservable()
    }

    @CheckResult
    override fun <T> bindUntilEvent(event: FragmentEvent): LifecycleTransformer<T> {
        return RxLifecycle.bindUntilEvent(lifecycleSubject, event)
    }

    @CheckResult
    override fun <T> bindToLifecycle(): LifecycleTransformer<T> {
        return RxLifecycleAndroid.bindFragment(lifecycleSubject)
    }

    @CallSuper
    override fun onAttach(context: Context) {
        logger.time("Injecting services") { kodeinTrigger.trigger() }
        super.onAttach(context)
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)
        lifecycleSubject.onNext(FragmentEvent.ATTACH)
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleSubject.onNext(FragmentEvent.CREATE)
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleSubject.onNext(FragmentEvent.CREATE_VIEW)
    }

    @CallSuper
    override fun onStart() {
        super.onStart()
        lifecycleSubject.onNext(FragmentEvent.START)
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        lifecycleSubject.onNext(FragmentEvent.RESUME)
    }

    @CallSuper
    override fun onPause() {
        lifecycleSubject.onNext(FragmentEvent.PAUSE)
        super.onPause()
    }

    @CallSuper
    override fun onStop() {
        lifecycleSubject.onNext(FragmentEvent.STOP)
        super.onStop()
    }

    @CallSuper
    override fun onDestroyView() {
        lifecycleSubject.onNext(FragmentEvent.DESTROY_VIEW)
        super.onDestroyView()
    }

    @CallSuper
    override fun onDestroy() {
        lifecycleSubject.onNext(FragmentEvent.DESTROY)
        super.onDestroy()
    }

    @CallSuper
    override fun onDetach() {
        lifecycleSubject.onNext(FragmentEvent.DETACH)
        super.onDetach()
    }
}