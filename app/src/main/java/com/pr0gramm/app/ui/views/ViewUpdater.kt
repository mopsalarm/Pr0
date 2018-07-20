package com.pr0gramm.app.ui.views

import android.support.v4.view.ViewCompat
import android.view.View
import com.jakewharton.rxbinding.view.ViewAttachEvent
import com.jakewharton.rxbinding.view.attachEvents
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

typealias Action = () -> Unit

object ViewUpdater {
    private val logger = LoggerFactory.getLogger("ViewUpdater")

    private val tickerSeconds: Observable<Unit> = Observable
            .interval(1, 1, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .map { Unit }
            .share()

    private val tickerMinute: Observable<Unit> = Observable
            .interval(1, 1, TimeUnit.MINUTES, AndroidSchedulers.mainThread())
            .map { Unit }
            .share()

    private fun ofView(view: View, ticker: Observable<Unit>): Observable<Unit> {
        val currentlyAttached = ViewCompat.isAttachedToWindow(view)

        return view.attachEvents()
                .map { it.kind() == ViewAttachEvent.Kind.ATTACH }
                .startWith(currentlyAttached)
                .switchMap { attached ->
                    val selectedTicker = if (attached) ticker else Observable.empty()
                    selectedTicker.startWith(Unit)
                }
    }

    fun ofView(view: View): Observable<Unit> {
        return ofView(view, tickerSeconds)
    }

    fun ofView(view: View, instant: Instant): Observable<Unit> {
        val delta = Duration.between(Instant.now(), instant)
                .convertTo(TimeUnit.SECONDS)
                .absoluteValue

        val ticker = when {
            delta > 3600 -> Observable.empty<Unit>()
            delta > 60 -> tickerMinute
            else -> tickerSeconds
        }

        return ofView(view, ticker)
    }
}