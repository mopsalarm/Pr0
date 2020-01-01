package com.pr0gramm.app.services

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.db.ParcelableStoreQueries
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.delay
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.formatSize
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.*

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
        val id = UUID.randomUUID().toString()

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

        logger.debug { "Got ${bytes?.size?.formatSize()} of data" }

        if (bytes == null) {
            return null
        }

        withParcel { parcel ->
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            return decode(parcel)
        }
    }

    private fun backgroundStoreAsync() = doInBackground {
        for ((id, p) in backgroundStoreCh) {
            catchAll {
                val data = p.parcelToByteArray()
                val expireTime = Instant.now().plus(Duration.days(1)).millis

                logger.debug { "Store parcel data for $id with ${data.size.formatSize()} data in database" }
                db.store(id, expireTime, data)
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
            // lets say 100kb per entry (and thats a really bad case), so this is around ~6mb, maybe less
            return size > 64
        }
    }

    private class ParcelableWithExpireTime(val p: Parcelable) {
        var expireTime = Instant.now().plus(Duration.days(1))
        val isExpired get() = expireTime.isInPast
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

fun <T : Parcelable> Bundle.getExternalValue(parcelStore: ParcelStore, name: String, creator: Parcelable.Creator<T>): T? {
    val id = getString(name) ?: return null
    return parcelStore.load(id) { parcel -> creator.createFromParcel(parcel) }
}

fun Bundle.putExternalValue(parcelStore: ParcelStore, name: String, value: Parcelable) {
    val id = parcelStore.store(value)
    putString(name, id)
}
