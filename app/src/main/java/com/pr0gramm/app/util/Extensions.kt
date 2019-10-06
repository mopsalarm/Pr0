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
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.annotation.NonNull
import androidx.appcompat.widget.AppCompatTextView
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import androidx.core.text.PrecomputedTextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.WorkRequest
import com.pr0gramm.app.*
import com.pr0gramm.app.ui.dialogs.ignoreError
import io.sentry.Sentry
import io.sentry.event.Breadcrumb
import io.sentry.event.BreadcrumbBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import rx.Emitter
import rx.Observable
import rx.Subscription
import rx.android.MainThreadSubscription
import rx.functions.Action1
import rx.subjects.BehaviorSubject
import rx.subjects.ReplaySubject
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

inline fun <T> createObservable(mode: Emitter.BackpressureMode = Emitter.BackpressureMode.NONE,
                                crossinline block: (emitter: Emitter<T>) -> Unit): Observable<T> {

    return Observable.create({ block(it) }, mode)
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Observable<T>.onErrorResumeEmpty(): Observable<T> = ignoreError()

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Observable<T>.subscribeOnBackground(): Observable<T> = subscribeOn(BackgroundScheduler)

fun <T> Observable<T>.observeOnMainThread(firstIsSync: Boolean = false): Observable<T> {
    if (firstIsSync) {
        val shared = share()
        return shared.take(1).concatWith(shared.skip(1).observeOnMainThread())
    }

    return observeOn(MainThreadScheduler)
}


fun InputStream.readSimple(b: ByteArray, off: Int = 0, len: Int = b.size): Int {
    if (len < 0) {
        throw IllegalArgumentException("Length must not be negative: $len")
    }

    var remaining = len
    while (remaining > 0) {
        val location = len - remaining
        val count = read(b, off + location, remaining)
        if (count == -1) {
            break
        }

        remaining -= count
    }

    return len - remaining
}

// scratch buffer for skipping
private val skipScratchBuffer = ByteArray(16 * 1024)

fun InputStream.skipSimple(len: Long): Long {
    if (len < 0) {
        throw IllegalArgumentException("Length must not be negative: $len")
    }

    // try to quickly skip using 'skip'
    val skipped = skip(len)
    if (skipped == len) {
        return skipped
    }

    // read the rest into a scratch buffer to skip the bytes we wanted to skip
    var remaining = len - skipped
    while (remaining > 0) {
        val count = read(skipScratchBuffer, 0, remaining.coerceAtMost(skipScratchBuffer.size.toLong()).toInt())
        if (count == -1) {
            break
        }

        remaining -= count
    }

    return len - remaining
}

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

inline fun <R> PowerManager.WakeLock.use(timeValue: Long, timeUnit: TimeUnit, fn: () -> R): R {
    acquire(timeUnit.toMillis(timeValue))

    try {
        return fn()
    } finally {
        runCatching { release() }
    }
}

inline fun <R> Cursor.mapToList(fn: Cursor.() -> R): List<R> {
    contract {
        callsInPlace(fn, InvocationKind.UNKNOWN)
    }

    val values = mutableListOf<R>()
    while (moveToNext()) {
        values.add(fn())
    }

    return values
}

inline fun <R> Cursor.forEach(crossinline fn: Cursor.() -> R) {
    return use {
        while (moveToNext()) {
            fn()
        }
    }
}

inline fun <R> Cursor.use(fn: (Cursor) -> R): R {
    contract {
        callsInPlace(fn, InvocationKind.EXACTLY_ONCE)
    }

    try {
        return fn(this)
    } finally {
        close()
    }
}

inline fun <R> TypedArray.use(block: (TypedArray) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

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
    return Delegates.observable(def) { _, _, _ -> onChange() }
}

inline fun <R, T> observeChangeEx(def: T, crossinline onChange: (T, T) -> Unit): ReadWriteProperty<R, T> {
    return Delegates.observable(def) { _, old, new -> onChange(old, new) }
}

inline val View.layoutInflater: LayoutInflater get() = LayoutInflater.from(context)

@Suppress("NOTHING_TO_INLINE")
inline fun LayoutInflater.inflate(@LayoutRes id: Int): View = inflate(id, null)

fun <V : View> ViewGroup.inflateDetachedChild(@LayoutRes res: Int): V {
    @Suppress("UNCHECKED_CAST")
    return layoutInflater.inflate(res, this, false) as V
}

interface CachedValue<out T> {
    val value: T

    fun invalidate()
}

object EmptyCache

inline fun <T> cached(crossinline fn: () -> T): CachedValue<T> = object : CachedValue<T> {
    private var theValue: Any? = EmptyCache

    override val value: T
        get() {
            if (theValue === EmptyCache) {
                theValue = fn()
            }

            @Suppress("UNCHECKED_CAST")
            return theValue as T
        }

    override fun invalidate() {
        theValue = EmptyCache
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

inline fun Canvas.save(block: () -> Unit) {
    val count = save()
    try {
        block()
    } finally {
        restoreToCount(count)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun CharSequence?.matches(pattern: Pattern): Boolean {
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

inline fun <T : Any?> Observable<T>.subscribeIgnoreError(crossinline onNext: (T) -> Unit): Subscription {
    return subscribe({ onNext(it) }, { err -> AndroidUtility.logToCrashlytics(err) })
}

fun <T> Observable<T>.decoupleSubscribe(replay: Boolean = false): Observable<T> {
    val subject = if (replay) ReplaySubject.create<T>() else BehaviorSubject.create<T>()
    subscribe(subject)
    return subject
}

@Suppress("NOTHING_TO_INLINE")
@ColorInt
inline fun Context.getColorCompat(@ColorRes id: Int): Int {
    return ContextCompat.getColor(this, id)
}

@Suppress("NOTHING_TO_INLINE")
inline fun Context.dip2px(dpValue: Float): Float {
    return dpValue * resources.displayMetrics.density
}

@Suppress("NOTHING_TO_INLINE")
inline fun Context.dip2px(dpValue: Int): Int {
    return dip2px(dpValue.toFloat()).toInt()
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

inline fun catchAll(block: () -> Unit) {
    try {
        block()

    } catch (err: CancellationException) {
        // ignore

    } catch (err: Throwable) {
        AndroidUtility.logToCrashlytics(err)
    }
}

inline fun ignoreAllExceptions(block: () -> Unit) {
    try {
        block()
    } catch (err: Throwable) {
        // i don't care.
    }
}

fun Context.canStartIntent(intent: Intent): Boolean {
    return packageManager.resolveActivity(intent, 0) != null
}

class LongValueHolder(private var value: Long) {
    fun update(newValue: Long): Boolean {
        val changed = value != newValue
        value = newValue
        return changed
    }
}

inline fun <T : Any?, R : Any> Observable<T>.mapNotNull(crossinline fn: (T) -> R?): Observable<R> {
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

inline fun <T : Any?> T.withIf(b: Boolean, fn: T.() -> T): T {
    return if (b) this.fn() else this
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

inline fun <reified T : Throwable> List<Throwable>.containsType(): Boolean {
    return any { it is T }
}

val Byte.unsigned: Int get() = this.toInt() and 0xff


inline fun <reified T : Enum<T>> tryEnumValueOf(key: String?): T? {
    if (key == null)
        return null

    return try {
        enumValueOf<T>(key)
    } catch (err: IllegalArgumentException) {
        null
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

inline fun <T> threadLocal(crossinline supplier: () -> T): ReadOnlyProperty<Any, T> {
    return object : ThreadLocal<T>(), ReadOnlyProperty<Any, T> {
        override fun initialValue(): T = supplier()
        override fun getValue(thisRef: Any, property: KProperty<*>): T = get()
                ?: throw IllegalStateException("No value in thread local.")
    }
}

val traceLogger = Logger("Trace")

inline fun <T : Any> T.trace(msg: () -> String) {
    if (BuildConfig.DEBUG) {
        // jump to parent class if inside a companion object.
        var clazz: Class<*> = javaClass
        if (clazz.directName == "Companion") {
            clazz = clazz.enclosingClass!!
        }

        val type = clazz.directName
        traceLogger.debug { "$type.${msg()}" }
    }
}

inline fun <T : Any, R : Any?> T.trace(msg: String, block: () -> R): R {
    if (BuildConfig.DEBUG) {
        val type = traceType(this)

        val watch = Stopwatch()
        try {
            return block()
        } finally {
            traceLogger.debug { "$type.$msg took $watch" }
        }

    } else {
        return block()
    }
}

fun traceType(obj: Any): String {
    // jump to parent class if inside a companion object.
    var clazz: Class<*> = obj.javaClass
    if (clazz.directName == "Companion") {
        clazz = clazz.enclosingClass ?: clazz
    }

    val type = clazz.directName
    return type
}

fun Closeable?.closeQuietly() {
    try {
        this?.close()
    } catch (err: Exception) {
        Logger("CloseQuietly").warn("Ignoring exception during close", err)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun SharedPreferences.getStringOrNull(key: String): String? {
    return getString(key, null)
}

inline fun <reified T : Activity> activityIntent(
        context: Context, uri: Uri? = null, configure: Intent.() -> Unit = {}): Intent {

    return Intent(context, T::class.java).apply { this.data = uri }.apply(configure)
}

inline fun <reified T : Activity> Context.startActivity(configureIntent: (Intent) -> Unit = {}) {
    startActivity(Intent(this, T::class.java).also(configureIntent))
}

inline fun <reified T : Activity> Activity.startActivity(requestCode: Int = -1, configureIntent: (Intent) -> Unit = {}) {
    val intent = Intent(this, T::class.java).also(configureIntent)
    startActivityForResult(intent, requestCode)
}

inline fun <reified T : Activity> Fragment.startActivity(configureIntent: (Intent) -> Unit = {}) {
    startActivity(Intent(requireContext(), T::class.java).also(configureIntent))
}

inline fun <reified T : Activity> Fragment.startActivity(requestCode: Int = -1, configureIntent: (Intent) -> Unit = {}) {
    val intent = Intent(requireContext(), T::class.java).also(configureIntent)
    startActivityForResult(intent, requestCode)
}

fun ContentProvider.requireContext(): Context {
    return context ?: throw IllegalStateException("context not set on ContentProvider")
}

val Class<*>.directName: String
    get() {
        return name.takeLastWhile { it != '.' }.replace('$', '.')
    }

inline fun TextView.addTextChangedListener(crossinline listener: (CharSequence) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {}

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            listener(s)
        }
    })
}

inline fun SeekBar.setOnProgressChanged(crossinline listener: (value: Int, fromUser: Boolean) -> Unit) {
    this.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            listener(progress, fromUser)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    })
}

inline fun View.addOnAttachStateChangeListener(crossinline listener: (isAttach: Boolean) -> Unit) {
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            listener(true)
        }

        override fun onViewDetachedFromWindow(v: View) {
            listener(false)
        }
    })
}

inline fun View.addOnAttachListener(crossinline listener: () -> Unit) {
    addOnAttachStateChangeListener { isAttach ->
        if (isAttach) {
            listener()
        }
    }
}

inline fun View.addOnDetachListener(crossinline listener: () -> Unit) {
    addOnAttachStateChangeListener { isAttach ->
        if (!isAttach) {
            listener()
        }
    }
}

fun View.attachEvents(): Observable<Boolean> {
    return createObservable(Emitter.BackpressureMode.LATEST) { emitter ->
        checkMainThread()


        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(@NonNull v: View) {
                emitter.onNext(true)
            }

            override fun onViewDetachedFromWindow(@NonNull v: View) {
                emitter.onNext(false)
            }
        }

        emitter.setSubscription(object : MainThreadSubscription() {
            override fun onUnsubscribe() {
                removeOnAttachStateChangeListener(listener)
            }
        })

        addOnAttachStateChangeListener(listener)
    }
}

suspend inline fun runEvery(period: Duration, initial: Duration = Duration.Zero, crossinline task: suspend () -> Unit) {
    if (initial.millis > 0) {
        delay(initial)
    }

    while (true) {
        try {
            supervisorScope { task() }

        } catch (err: CancellationException) {
            return
        } catch (err: Exception) {
            AndroidUtility.logToCrashlytics(err)
        }

        delay(period.millis)
    }
}

suspend fun delay(duration: Duration) {
    delay(duration.millis)
}

fun File.updateTimestamp(): Boolean {
    return setLastModified(System.currentTimeMillis())
}

val Uri.isLocalFile get(): Boolean = scheme == "file"

fun AppCompatTextView.setTextFuture(text: CharSequence) {
    setTextFuture(PrecomputedTextCompat.getTextFuture(text, textMetricsParamsCompat, null))
}

fun TextView.setTextFuture(text: CharSequence) {
    if (this is AppCompatTextView) {
        setTextFuture(text)
    } else {
        setText(text)
    }
}

inline fun <reified T : Any> SharedPreferences.getJSON(key: String): T? {
    val encoded = getStringOrNull(key) ?: return null
    return MoshiInstance.adapter<T>().fromJson(encoded)
}

inline fun <reified T : Any> SharedPreferences.Editor.setObject(key: String, value: T?): SharedPreferences.Editor {
    if (value == null) {
        remove(key)
    } else {
        putString(key, MoshiInstance.adapter<T>().toJson(value))
    }

    return this
}

/**
 * Mitigate crashes in older android versions that are relying on AlarmManager.
 * Changing a constraint leads to a crash of the application.
 */
fun <W : WorkRequest, B : WorkRequest.Builder<B, W>> B.setConstraintsCompat(constraints: Constraints): B {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1)
        return this

    return setConstraints(constraints)
}

inline fun recordBreadcrumb(type: Breadcrumb.Type = Breadcrumb.Type.DEFAULT, builder: BreadcrumbBuilder.() -> Unit) {
    Sentry.getStoredClient()?.context?.let { context ->
        val bc = BreadcrumbBuilder().setType(type).setLevel(Breadcrumb.Level.INFO).apply(builder).build()
        context.recordBreadcrumb(bc)
    }
}

fun DialogFragment.maybeShow(fm: FragmentManager?, tag: String? = null) {
    if (fm != null) {
        show(fm, tag)
    }
}

inline fun <T> Boolean.then(block: () -> T): T? = if (this) block() else null
