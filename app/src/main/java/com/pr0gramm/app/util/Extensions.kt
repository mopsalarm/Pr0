package com.pr0gramm.app.util

import android.content.SharedPreferences
import com.google.common.base.Optional
import com.google.common.io.ByteStreams
import rx.Emitter
import rx.Observable
import java.io.InputStream


inline fun <T> Optional<T>.ifAbsent(fn: () -> Unit): Unit {
    if (!this.isPresent) {
        fn()
    }
}

inline fun <T> Optional<T>.ifPresent(fn: () -> Unit): Unit {
    if (this.isPresent) {
        fn()
    }
}


fun <T> createObservable(mode: Emitter.BackpressureMode,
                         fn: (emitter: Emitter<T>) -> Unit): Observable<T> {

    return Observable.create(fn, mode)
}

inline fun readStream(stream: InputStream, bufferSize: Int = 16 * 1042, fn: (ByteArray, Int) -> Unit): Unit {
    val buffer = ByteArray(bufferSize)

    while (true) {
        val read = ByteStreams.read(stream, buffer, 0, buffer.size)
        if (read <= 0) {
            break
        }

        fn(buffer, read);
    }
}

fun SharedPreferences.edit(fn: SharedPreferences.Editor.() -> Unit): Unit {
    val editor = edit()
    editor.fn()
    editor.apply();
}
