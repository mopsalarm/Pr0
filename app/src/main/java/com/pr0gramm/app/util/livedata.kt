package com.pr0gramm.app.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import rx.Observable


fun <T> liveDataOf(value: T): LiveData<T> {
    return MutableLiveData(value)
}

fun <T> MutableLiveData<T>.readOnly(): LiveData<T> {
    return this
}

fun <T> Observable<T>.asFlow(): Flow<T> {
    return channelFlow {
        val sub = subscribe({ sendBlocking(it) }, { err -> close(err) })
        invokeOnClose { sub.unsubscribe() }
    }
}
