package com.pr0gramm.app.util

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.database.Cursor
import android.graphics.Canvas
import android.os.PowerManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import com.google.common.base.Optional
import com.google.common.io.ByteStreams
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import rx.Emitter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty


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

inline fun <T, R> Optional<T>.map(fn: (T) -> R?): Optional<R> {
    if (isPresent) {
        return Optional.fromNullable(fn(get()))
    } else {
        return Optional.absent()
    }
}

inline fun <T> Optional<T>.filter(fn: (T) -> Boolean): Optional<T> {
    if (isPresent && fn(get())) {
        return this
    } else {
        return Optional.absent()
    }
}

inline fun <T> createObservable(mode: Emitter.BackpressureMode = Emitter.BackpressureMode.NONE,
                                crossinline block: (emitter: Emitter<T>) -> Unit): Observable<T> {

    return Observable.create({ block(it) }, mode)
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
    return use {
        val values = mutableListOf<R>()
        while (moveToNext()) {
            values.add(fn())
        }

        values
    }
}

@Suppress("ConvertTryFinallyToUseCall")
inline fun <R> Cursor.use(fn: (Cursor) -> R): R {
    try {
        return fn(this)
    } finally {
        close()
    }
}

inline fun <R> TypedArray.use(block: (TypedArray) -> R): R {
    try {
        return block(this)
    } finally {
        this.recycle()
    }
}

fun arrayOfStrings(vararg args: Any): Array<String> {
    return Array<String>(args.size) { args[it].toString() }
}

fun <T> T?.toOptional(): Optional<T> {
    return Optional.fromNullable(this)
}

fun JsonObject.getIfPrimitive(key: String): JsonPrimitive? {
    return get(key)?.takeIf { it is JsonPrimitive } as JsonPrimitive?
}

inline fun <R, T> observeChange(def: T, crossinline onChange: () -> Unit): ReadWriteProperty<R, T> {
    return Delegates.observable(def) { _, old, new ->
        onChange()
    }
}

inline fun <R, T> observeChangeEx(def: T, crossinline onChange: (T, T) -> Unit): ReadWriteProperty<R, T> {
    return Delegates.observable(def) { _, old, new ->
        onChange(old, new)
    }
}

val View.layoutInflater: LayoutInflater get() = LayoutInflater.from(context)

interface CachedValue<T> {
    val value: T

    fun invalidate(): Unit
}

inline fun <T> cached(crossinline fn: () -> T): CachedValue<T> = object : CachedValue<T> {
    private var _value: T? = null

    override val value: T get() {
        if (_value == null) {
            _value = fn()
        }

        return _value!!
    }

    override fun invalidate(): Unit {
        _value = null
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : View> View.find(id: Int): T {
    return findViewById(id) as T
}

@Suppress("UNCHECKED_CAST")
fun <T : View> Activity.find(id: Int): T {
    return findViewById(id) as T
}

var View.visible: Boolean
    get() = visibility == View.VISIBLE
    set(v) {
        visibility = if (v) View.VISIBLE else View.GONE
    }

fun Canvas.save(block: () -> Unit) {
    val count = save()
    try {
        block()
    } finally {
        restoreToCount(count)
    }
}

fun CharSequence.matches(pattern: Pattern): Boolean {
    return pattern.matcher(this).matches()
}
