package com.pr0gramm.app.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import rx.Observable


fun <T> MutableLiveData<T>.readOnly(): LiveData<T> {
    return this
}

fun <T> Observable<T>.asFlow(): Flow<T> {
    return channelFlow {
        val sub = subscribe({ sendBlocking(it) }, { err -> close(err) }, { close() })
        awaitClose { sub.unsubscribe() }
    }
}
