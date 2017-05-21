package com.pr0gramm.app.util

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.database.Cursor
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.app.Fragment
import android.support.v4.util.LruCache
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Optional
import com.google.common.base.Stopwatch
import com.google.common.io.ByteStreams
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.pr0gramm.app.BuildConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Completable
import rx.Emitter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import java.io.File
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


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

fun JsonObject.getPrimitive(key: String): JsonPrimitive {
    val value = get(key)
    if (value !is JsonPrimitive) {
        throw JsonParseException("Expected primitive value, got $value")
    }

    return value
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

object EmptyCache

inline fun <T> cached(crossinline fn: () -> T): CachedValue<T> = object : CachedValue<T> {
    private var _value: Any? = EmptyCache

    override val value: T get() {
        if (_value === EmptyCache) {
            _value = fn()
        }

        @Suppress("UNCHECKED_CAST")
        return _value as T
    }

    override fun invalidate(): Unit {
        _value = EmptyCache
    }
}

inline fun <reified T : View> Activity.find(id: Int): T {
    return findViewById(id) as T
}

inline fun <reified T : View> View.find(id: Int): T {
    return findViewById(id) as T
}

inline fun <reified T : View> View.findOptional(id: Int): T? {
    return findViewById(id) as T?
}

inline fun <reified T : View> RecyclerView.ViewHolder.find(id: Int): T {
    return itemView.findViewById(id) as T
}

inline fun <reified T : View> RecyclerView.ViewHolder.findOptional(id: Int): T? {
    return itemView.findViewById(id) as T?
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

fun CharSequence?.matches(pattern: Pattern): Boolean {
    return this != null && pattern.matcher(this).matches()
}

inline fun bundle(builder: Bundle.() -> Unit): Bundle {
    val b = Bundle()
    b.builder()
    return b
}

inline fun <F : Fragment> F.arguments(builder: Bundle.() -> Unit): F {
    if (arguments != null) {
        arguments.builder()
    } else {
        arguments = bundle { builder() }
    }

    return this
}

inline fun <K, V> LruCache<K, V>.getOrPut(key: K, creator: (K) -> V): V {
    return get(key) ?: run {
        val value = creator(key)
        put(key, value)
        value
    }
}

inline fun <K, V> lruCache(maxSize: Int, crossinline creator: (K) -> V?): LruCache<K, V> {
    return object : LruCache<K, V>(maxSize) {
        override fun create(key: K): V? = creator(key)
    }
}

inline fun <T> T?.or(supplier: () -> T): T {
    if (this != null) {
        return this
    } else {
        return supplier()
    }
}

fun View?.removeFromParent() {
    val parent = this?.parent as? ViewGroup
    parent?.removeView(this)
}

fun <T> T?.justObservable(): Observable<T> {
    if (this != null) {
        return Observable.just(this)
    } else {
        return Observable.empty()
    }
}


inline fun <T> Logger.time(name: String, supplier: () -> T): T {
    if (BuildConfig.DEBUG) {
        val watch = Stopwatch.createStarted()
        try {
            return supplier()
        } finally {
            this.info("{} took {}", name, watch)
        }
    } else {
        return supplier()
    }
}

fun <T> weakref(value: T?): ReadWriteProperty<Any?, T?> = object : ReadWriteProperty<Any?, T?> {
    private var ref: WeakReference<T?> = WeakReference(value)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = ref.get()

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        ref.clear()
        ref = WeakReference(value)
    }
}


inline fun debug(block: () -> Unit) {
    if (BuildConfig.DEBUG) {
        block()
    }
}

fun <T> Observable<T>.detachSubscription(): Observable<T> {
    val subscribed = AtomicBoolean()

    val subject = BehaviorSubject.create<T>()
    return subject
            .doOnSubscribe {
                if (subscribed.compareAndSet(false, true)) {
                    debug("inner").subscribe(subject)
                }
            }
            .debug("outer")
}

fun Completable.detachSubscription(): Observable<Unit> {
    return toObservable<Unit>().detachSubscription()
}

fun <T> Observable<T>.debug(key: String, logger: Logger? = null): Observable<T> {
    debug {
        val log = logger ?: LoggerFactory.getLogger("Rx")
        return this
                .doOnSubscribe { log.info("$key: onSubscribe") }
                .doOnUnsubscribe { log.info("$key: onUnsubscribe") }
                .doOnCompleted { log.info("$key: onCompleted") }
                .doOnError { log.info("$key: onError({})", it) }
                .doOnNext { log.info("$key: onNext({})", it) }
                .doOnTerminate { log.info("$key: onTerminate") }
                .doAfterTerminate { log.info("$key: onAfterTerminate") }
    }

    // do nothing if not in debug build.
    return this
}

fun File.toUri(): Uri = Uri.fromFile(this)


