package com.pr0gramm.app.ui.views

import android.support.v4.view.ViewCompat
import android.view.View
import com.jakewharton.rxbinding.view.ViewAttachEvent
import com.jakewharton.rxbinding.view.attachEvents
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

typealias Action = () -> Unit

object ViewUpdater {
    private val logger = LoggerFactory.getLogger("ViewUpdater")

    private val ticker: Observable<Unit> = Observable
            .interval(1, 1, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .map { Unit }
            .share()
            .startWith(Unit)

    fun ofView(view: View): Observable<Unit> {
        val currentlyAttached = ViewCompat.isAttachedToWindow(view)

        return view.attachEvents()
                .map { it.kind() == ViewAttachEvent.Kind.ATTACH }
                .startWith(currentlyAttached)
                .switchMap { attached -> if (attached) ticker else Observable.empty() }
    }
}