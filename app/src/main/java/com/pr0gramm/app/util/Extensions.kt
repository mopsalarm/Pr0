package com.pr0gramm.app.util

import android.content.SharedPreferences
import android.database.Cursor
import android.os.PowerManager
import android.util.Log
import com.google.common.base.Optional
import com.google.common.io.ByteStreams
import rx.Emitter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.InputStream
import java.util.concurrent.TimeUnit


inline fun <T> Optional<T>.ifAbsent(fn: () -> Unit): Unit {
    if (!this.isPresent) {
        fn()
    }
}

inline fun <T> Optional<T>.ifPresent(fn: (T) -> Unit): Unit {
    if (this.isPresent) {
        fn(get())
    }
}

inline fun <T, R> Optional<T>.map(noinline fn: (T) -> R): Optional<R> {
    return transform { fn(it!!) }
}

fun <T> createObservable(mode: Emitter.BackpressureMode,
                         fn: (emitter: Emitter<T>) -> Unit): Observable<T> {

    return Observable.create(fn, mode)
}

fun <T> Observable<T>.onErrorResumeEmpty(): Observable<T> = onErrorResumeNext(Observable.empty())

fun <T> Observable<T>.subscribeOnBackground(): Observable<T> = subscribeOn(BackgroundScheduler.instance())

fun <T> Observable<T>.observeOnMain(): Observable<T> = observeOn(AndroidSchedulers.mainThread())

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

inline fun SharedPreferences.edit(fn: SharedPreferences.Editor.() -> Unit): Unit {
    val editor = edit()
    editor.fn()
    editor.apply();
}

inline fun <R> PowerManager.WakeLock.use(timeValue: Long, timeUnit: TimeUnit, fn: () -> R): R {
    acquire(timeUnit.toMillis(timeValue))
    try {
        return fn()
    } finally {
        try {
            Log.i("pr0", "Releasing wake lock")
            release()
        } catch (ignored: RuntimeException) {
        }
    }
}

inline fun <R> Cursor.mapToList(fn: Cursor.() -> R): List<R> {
    val values = mutableListOf<R>()
    while (moveToNext()) {
        values.add(fn())
    }

    return values
}

fun arrayOfStrings(vararg args: Any): Array<String> {
    return Array<String>(args.size) { args[it].toString() }
}

fun <T> T?.toOptional(): Optional<T> {
    return Optional.fromNullable(this)
}
