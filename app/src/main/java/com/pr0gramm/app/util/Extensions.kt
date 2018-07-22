package com.pr0gramm.app.util

import android.app.Activity
import android.content.ContentProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.content.res.TypedArray
import android.database.Cursor
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.annotation.LayoutRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v4.content.ContextCompat
import android.support.v4.util.LruCache
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.ui.dialogs.ignoreError
import org.apache.commons.io.IOUtils
import org.kodein.di.DKodein
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.direct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.*
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Action1
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import rx.subjects.ReplaySubject
import java.io.File
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

inline fun <T> createObservable(mode: Emitter.BackpressureMode = Emitter.BackpressureMode.NONE,
                                crossinline block: (emitter: Emitter<T>) -> Unit): Observable<T> {

    return Observable.create({ block(it) }, mode)
}

fun <T> Observable<T>.onErrorResumeEmpty(): Observable<T> = ignoreError()

fun <T> Observable<T>.subscribeOnBackground(): Observable<T> = subscribeOn(BackgroundScheduler.instance())

fun <T> Observable<T>.observeOnMainThread(firstIsSync: Boolean = false): Observable<T> {
    if (firstIsSync) {
        val shared = share()
        return shared.take(1).concatWith(shared.skip(1).observeOnMainThread())
    }

    return observeOn(AndroidSchedulers.mainThread())
}


fun InputStream.readSimple(b: ByteArray, off: Int = 0, len: Int = b.size): Int {
    return IOUtils.read(this, b, off, len)
}

fun InputStream.skipSimple(len: Long): Long = IOUtils.skip(this, len)

inline fun readStream(stream: InputStream, bufferSize: Int = 16 * 1024, fn: (ByteArray, Int) -> Unit) {
    val buffer = ByteArray(bufferSize)

    while (true) {
        val read = stream.readSimple(buffer)
        if (read <= 0) {
            break
        }

        fn(buffer, read)
    }
}

inline fun SharedPreferences.edit(fn: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    editor.fn()
    editor.apply()
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

inline fun <R> Cursor.forEach(crossinline fn: Cursor.() -> R): Unit {
    return use {
        while (moveToNext()) {
            fn()
        }
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
    return Array(args.size) { args[it].toString() }
}

inline fun <R, T> observeChange(def: T, crossinline onChange: () -> Unit): ReadWriteProperty<R, T> {
    return Delegates.observable(def) { _, _, _ ->
        onChange()
    }
}

inline fun <R, T> observeChangeEx(def: T, crossinline onChange: (T, T) -> Unit): ReadWriteProperty<R, T> {
    return Delegates.observable(def) { _, old, new ->
        onChange(old, new)
    }
}

val View.layoutInflater: LayoutInflater get() = LayoutInflater.from(context)

fun LayoutInflater.inflate(@LayoutRes id: Int): View = inflate(id, null)

interface CachedValue<out T> {
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
    return findViewById(id) ?: throw Resources.NotFoundException(
            "View ${resources.getResourceName(id)} not found")
}

inline fun <reified T : View> View.find(id: Int): T {
    return findViewById(id) ?: throw Resources.NotFoundException(
            "View ${resources.getResourceName(id)} not found")
}

inline fun <reified T : View> View.findOptional(id: Int): T? {
    return findViewById(id)
}

inline fun <reified T : View> RecyclerView.ViewHolder.find(id: Int): T {
    return itemView.findViewById(id) ?: throw Resources.NotFoundException(
            "View ${itemView.resources.getResourceName(id)} not found")
}

inline fun <reified T : View> RecyclerView.ViewHolder.findOptional(id: Int): T? {
    return itemView.findViewById(id)
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
    (arguments ?: Bundle().also { arguments = it }).builder()
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

fun View?.removeFromParent() {
    val parent = this?.parent as? ViewGroup
    parent?.removeView(this)
}

fun <T : Any> T?.justObservable(): Observable<T> {
    return this?.let { Observable.just(it) } ?: Observable.empty()
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

fun <T> Observable<T>.decoupleSubscribe(replay: Boolean = false, scheduler: Scheduler = Schedulers.io()): Observable<T> {
    val subject = if (replay) ReplaySubject.create<T>() else BehaviorSubject.create<T>()
    this.subscribeOn(scheduler).subscribe(subject)
    return subject
}

fun Completable.decoupleSubscribe(): Observable<Unit> {
    return toObservable<Unit>().decoupleSubscribe()
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


@ColorInt
fun Context.getColorCompat(@ColorRes id: Int): Int {
    return ContextCompat.getColor(this, id)
}

fun Context.dp2px(dpValue: Float): Float {
    val density = resources.displayMetrics.density
    return dpValue * density
}

inline fun FragmentManager.transaction(now: Boolean = false, block: FragmentTransaction.() -> Unit) {
    val tr = beginTransaction()
    tr.block()

    if (now) {
        tr.commitNow()
    } else {
        tr.commit()
    }
}

/**
 * Converts a boolean to either 'one' or 'zero'.
 */
fun Boolean.toInt(): Int {
    return if (this) 1 else 0
}

inline fun <reified R> Observable<*>.ofType(): Observable<R> {
    return ofType(R::class.java)
}

inline fun ignoreException(block: () -> Unit) {
    try {
        block()
    } catch (err: Throwable) {
        AndroidUtility.logToCrashlytics(err)
    }
}

//val <T : Result> PendingResult<T>.rx: Observable<T>
//    get() = createObservable<T> { emitter ->
//        emitter.setCancellation {
//            cancel()
//        }
//
//        this.setResultCallback { result ->
//            emitter.onNext(result)
//            emitter.onCompleted()
//        }
//    }

fun Context.canStartIntent(intent: Intent): Boolean {
    return packageManager.resolveActivity(intent, 0) != null
}


class ValueHolder<T>(private var value: T? = null) {
    fun update(newValue: T?): Boolean {
        val changed = value != newValue
        value = newValue
        return changed
    }
}

fun View.updatePadding(
        left: Int = paddingLeft, top: Int = paddingTop,
        right: Int = paddingRight, bottom: Int = paddingBottom) {

    setPadding(left, top, right, bottom)
}

fun <T : Any?, R : Any> Observable<T>.mapNotNull(fn: (T) -> R?): Observable<R> {
    @Suppress("UNCHECKED_CAST")
    return map { fn(it) }.filter { it != null } as Observable<R>
}

inline fun <R : Any> unless(b: Boolean, fn: () -> R?): R? {
    return if (!b) {
        fn()
    } else {
        null
    }
}

fun <T : Any?> T.withIf(b: Boolean, fn: T.() -> T): T {
    return if (b) this.fn() else this
}

inline val Context.kodein: Kodein get() = (applicationContext as KodeinAware).kodein

inline val Context.directKodein: DKodein get() = (applicationContext as KodeinAware).kodein.direct

inline val View.kodein: Kodein get() = context.kodein

inline val ContentProvider.kodein: Kodein get() = context.kodein


fun sleepUninterruptibly(duration: Long, unit: TimeUnit) {
    val deadline = System.nanoTime() + unit.toNanos(duration)

    while (true) {
        val amount = deadline - System.nanoTime()
        if (amount > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(amount)
            } catch (_: InterruptedException) {
            }
        }
    }
}

val Throwable.rootCause
    get(): Throwable {
        val c = this.cause
        if (c === null || c === this) {
            return this
        } else {
            return c.rootCause
        }
    }

val Throwable.causalChain
    get(): List<Throwable> {
        val chain = mutableListOf<Throwable>()

        var current = this
        while (true) {
            chain.add(current)

            val cause = current.cause
            if (cause === null || cause === current) {
                break
            }

            current = cause
        }

        return chain
    }

val Byte.unsigned: Int get() = this.toInt() and 0xff


inline fun <reified T : Enum<T>> tryEnumValueOf(key: String?): T? {
    if (key == null)
        return null

    try {
        return enumValueOf<T>(key)
    } catch (err: IllegalArgumentException) {
        return null
    }
}

fun replaceableSubscription(): ReadWriteProperty<Any?, Subscription?> {
    return Delegates.observable<Subscription?>(null) { _, oldValue, _ ->
        oldValue?.unsubscribe()
    }
}

fun updateTextView(view: TextView) = object : Action1<CharSequence?> {
    private var previousValue: CharSequence? = null

    override fun call(newValue: CharSequence?) {
        if (previousValue != newValue) {
            view.text = newValue
            previousValue = newValue
        }
    }
}

fun <T> threadLocal(supplier: () -> T): ReadOnlyProperty<Any, T> {
    return object : ThreadLocal<T>(), ReadOnlyProperty<Any, T> {
        override fun initialValue(): T = supplier()
        override fun getValue(thisRef: Any, property: KProperty<*>): T = get()
    }
}
