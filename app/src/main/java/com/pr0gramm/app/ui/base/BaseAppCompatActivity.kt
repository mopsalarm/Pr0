package com.pr0gramm.app.ui.base

import android.os.Bundle
import butterknife.ButterKnife
import butterknife.Unbinder
import com.f2prateek.dart.Dart
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.Dagger
import com.pr0gramm.app.util.AndroidUtility.checkMainThread
import com.trello.rxlifecycle.LifecycleTransformer
import com.trello.rxlifecycle.android.ActivityEvent
import com.trello.rxlifecycle.components.support.RxAppCompatActivity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable

/**
 * A [android.support.v7.app.AppCompatActivity] with dagger injection and stuff.
 */
abstract class BaseAppCompatActivity : RxAppCompatActivity() {
    @JvmField
    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    private var unbinder: Unbinder? = null

    val activityComponent: ActivityComponent by lazy {
        checkMainThread()
        Dagger.newActivityComponent(this)
    }

    fun <T> bindUntilEventAsync(event: ActivityEvent): Observable.Transformer<T, T> {
        return AsyncLifecycleTransformer(bindUntilEvent<T>(event))
    }

    fun <T> bindToLifecycleAsync(): LifecycleTransformer<T> {
        return AsyncLifecycleTransformer(bindToLifecycle<T>())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        injectComponent(activityComponent)

        Dart.inject(this)
        super.onCreate(savedInstanceState)
    }

    protected abstract fun injectComponent(appComponent: ActivityComponent)

    override fun onDestroy() {
        super.onDestroy()

        unbinder?.unbind()
        unbinder = null
    }

    override fun onContentChanged() {
        super.onContentChanged()
        unbinder = ButterKnife.bind(this)
    }

}
