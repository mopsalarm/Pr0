package com.pr0gramm.app

import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.parcel.Freezable
import com.pr0gramm.app.parcel.Unfreezable
import com.pr0gramm.app.parcel.creator
import com.pr0gramm.app.util.Logger
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class Instant(val millis: Long) : Comparable<Instant>, Freezable, Parcelable {
    override fun freeze(sink: Freezable.Sink) {
        sink.writeLong(millis)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(millis)
    }

    fun plus(offsetInMillis: Long, unit: TimeUnit): Instant {
        return Instant(millis + unit.toMillis(offsetInMillis))
    }

    fun minus(offsetInMillis: Long, unit: TimeUnit): Instant {
        return Instant(millis - unit.toMillis(offsetInMillis))
    }

    operator fun plus(d: Duration): Instant {
        return Instant(millis + d.millis)
    }

    operator fun minus(d: Duration): Instant {
        return Instant(millis - d.millis)
    }

    fun isBefore(other: Instant): Boolean {
        return millis < other.millis
    }

    fun isAfter(other: Instant): Boolean {
        return millis > other.millis
    }

    val isInFuture: Boolean
        get() {
            return millis > TimeFactory.currentTimeMillis()
        }

    val isInPast: Boolean
        get() {
            return millis < TimeFactory.currentTimeMillis()
        }

    val epochSeconds: Long get() = millis / 1000

    constructor(parcel: Parcel) : this(parcel.readLong())

    override fun compareTo(other: Instant): Int {
        return millis.compareTo(other.millis)
    }

    override fun toString(): String {
        return Date(millis).toString()
    }

    fun toString(format: DateFormat): String {
        return format.format(Date(millis))
    }

    companion object : Unfreezable<Instant> {
        override fun unfreeze(source: Freezable.Source): Instant {
            return Instant(source.readLong())
        }


        fun now(): Instant = Instant(TimeFactory.currentTimeMillis())

        @JvmField
        val CREATOR = creator { p -> Instant(p.readLong()) }
    }
}

object TimeFactory {
    private val logger = Logger("TimeFactory")

    private val buffer = androidx.collection.CircularArray<Long>(16)
    private val deltaInMillis = AtomicLong(0)

    fun updateServerTime(serverTime: Instant) {
        synchronized(buffer) {
            if (buffer.size() >= 16) {
                buffer.popFirst()
            }

            val delta = serverTime.millis - System.currentTimeMillis()

            logger.info { "Storing time delta of ${this.deltaInMillis}ms" }
            buffer.addLast(delta)

            // calculate average server/client delta
            var sum = 0L
            for (idx in 0 until buffer.size()) {
                sum += buffer.get(idx)
            }

            this.deltaInMillis.set(sum / buffer.size())
        }

        Stats().time("server.time.delta", this.deltaInMillis.get())
    }

    fun currentTimeMillis(): Long = System.currentTimeMillis() + this.deltaInMillis.get()
}
