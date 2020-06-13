package com.pr0gramm.app.ui.dialogs

import com.pr0gramm.app.Logger
import rx.Observable

fun <T> Observable<T>.ignoreError(msg: String = "Ignoring error"): Observable<T> {
    return onErrorResumeNext { err ->
        Logger("Error").warn(msg, err)
        Observable.empty()
    }
}