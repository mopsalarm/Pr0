package com.pr0gramm.app.services

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.*
import com.pr0gramm.app.db.ParcelableStoreQueries
import com.pr0gramm.app.parcel.Freezable
import com.pr0gramm.app.parcel.Unfreezable
import com.pr0gramm.app.parcel.parcelableCreator
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class ParcelStore(private val db: ParcelableStoreQueries) {
    private val logger = Logger("ParcelStore")

    private val backgroundStoreCh = Channel<Pair<String, Parcelable>>(Channel.UNLIMITED)

    init {
        // consume the backgroundStore channel
        backgroundStoreAsync()

        // start cleanup of database
        backgroundCleanupAsync()
    }

    fun store(p: Parcelable): String {
        val id = generateId(p)

        // schedule a background store for the data
        backgroundStoreCh.offer(Pair(id, p))

        synchronized(this) {
            // put the parcelable in memory cache too
            memoryCache[id] = ParcelableWithExpireTime(p)
        }

        return id
    }

    fun <T : Parcelable> load(id: String, decode: (Parcel) -> T): T? {
        // check lru cache first
        val cachedValue = synchronized(this) { memoryCache[id] }

        if (cachedValue != null) {
            // update expire time. Let it expire much sooner now, cause it is really unlikely,
            // that this value will be read ever again
            cachedValue.expireTime = Instant.now().plus(Duration.seconds(10))

            logger.debug { "Got cached value for $id in memory cache, returning that one" }
            return cachedValue.p as? T
        }

        val bytes = logger.time("Load parcel data for $id from database") {
            doOnDifferentThreadWhileBlockingCallerThread {
                db.load(id).executeAsOneOrNull()
            }
        }

        if (bytes == null) {
            return null
        }

        withParcel { parcel ->
            logger.time("Unparcel $id (${bytes.size.formatSize()})") {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                return decode(parcel)
            }
        }
    }

    private fun backgroundStoreAsync() = doInBackground {
        for ((id, p) in backgroundStoreCh) {
            catchAll {
                val expireTime = Instant.now().plus(Duration.days(1)).millis

                val data = logger.time("Parcel data $id") {
                    p.parcelToByteArray()
                }

                logger.time("Store parcel data for $id (${data.size.formatSize()}) in database") {
                    db.store(id, expireTime, data)
                }
            }
        }
    }

    private fun backgroundCleanupAsync() = doInBackground {
        while (true) {
            delay(Duration.minutes(5L))

            // remove all entries that are expired
            catchAll { db.deleteExpired(Instant.now().millis) }

            // remove all expired values from the memory cache
            synchronized(this) {
                memoryCache.values.removeAll { it.isExpired }
            }
        }
    }

    private val memoryCache = object : LinkedHashMap<String, ParcelableWithExpireTime>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ParcelableWithExpireTime>?): Boolean {
            debugOnly {
                return size > 2
            }

            // lets say 25kb per entry (and that's a really bad case), so this is around ~6mb, maybe less
            return size > 256
        }
    }

    private class ParcelableWithExpireTime(val p: Parcelable) {
        var expireTime = Instant.now().plus(Duration.days(1))
        val isExpired get() = expireTime.isBefore(Instant.now())
    }
}


private inline fun <T> doOnDifferentThreadWhileBlockingCallerThread(crossinline block: () -> T): T {
    // this is really bad, but for now we really need to do an sql query on the main thread,
    // and not doing this workaround might let android kill our app, cause it is detecting
    // IO on the main thread, which is not allowed!
    //
    // So to work around this, we execute the actual action somewhere else and just do a
    // blocking wait for the result on the caller/main thread.
    val deferred = AsyncScope.async { block() }
    return runBlocking { deferred.await() }
}

private inline fun <T> withParcel(block: (Parcel) -> T): T {
    val parcel = Parcel.obtain()
    try {
        return block(parcel)
    } finally {
        parcel.recycle()
    }
}

private fun Parcelable.parcelToByteArray(): ByteArray {
    return withParcel { parcel ->
        writeToParcel(parcel, 0)
        parcel.marshall()
    }
}

private fun generateId(value: Any): String {
    val prefix = value.javaClass.simpleName
    val random = ByteArray(9).apply { ThreadLocalRandom.current().nextBytes(this) }
    return prefix + ":" + random.encodeBase64()
}

fun <T : Parcelable> Bundle.getExternalValue(parcelStore: ParcelStore, name: String, creator: Parcelable.Creator<T>): T? {
    val id = getString(name) ?: return null
    return parcelStore.load(id) { parcel -> creator.createFromParcel(parcel) }
}

inline fun <reified T : Freezable> Bundle.getExternalValue(context: Context, name: String, creator: Unfreezable<T>): T? {
    val parcelStore = context.injector.instance<ParcelStore>()
    return getExternalValue(parcelStore, name, creator.parcelableCreator())
}

fun Bundle.putExternalValue(context: Context, name: String, value: Parcelable) {
    putString(name, context.injector.instance<ParcelStore>().store(value))
}

fun Bundle.putExternalValue(parcelStore: ParcelStore, name: String, value: Parcelable) {
    putString(name, parcelStore.store(value))
}
