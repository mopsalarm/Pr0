package com.pr0gramm.app

import android.os.Parcel
import com.pr0gramm.app.parcel.DefaultParcelable
import com.pr0gramm.app.parcel.SimpleCreator
import com.pr0gramm.app.parcel.javaClassOf
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class Instant(val millis: Long) : Comparable<Instant>, DefaultParcelable {
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

    val epochSeconds: Long get() = millis / 1000

    constructor(parcel: Parcel) : this(parcel.readLong())

    override fun compareTo(other: Instant): Int {
        return millis.compareTo(other.millis)
    }

    override fun hashCode(): Int {
        return millis.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is Instant && other.millis == millis
    }

    override fun toString(): String {
        return Date(millis).toString()
    }

    fun toString(format: DateFormat): String {
        return format.format(Date(millis))
    }

    companion object CREATOR : SimpleCreator<Instant>(javaClassOf()) {
        fun now(): Instant = Instant(TimeFactory.currentTimeMillis())

        fun ofEpochSeconds(epochSeconds: Long): Instant {
            return Instant(1000 * epochSeconds)
        }

        override fun createFromParcel(source: Parcel): Instant {
            return Instant(source.readLong())
        }
    }
}

object TimeFactory {
    private val logger = Logger("TimeFactory")

    private val buffer = LongArray(16)
    private var bufferIdx: Int = 0

    private val deltaInMillis = AtomicLong(0)

    fun updateServerTime(serverTime: Instant) {
        synchronized(buffer) {
            val delta = serverTime.millis - System.currentTimeMillis()

            logger.debug { "Storing time delta of ${deltaInMillis}ms" }
            buffer[bufferIdx++ % buffer.size] = delta

            // calculate average server/client delta
            deltaInMillis.set(buffer.sum() / buffer.size)
        }
    }

    fun currentTimeMillis(): Long = System.currentTimeMillis() + deltaInMillis.get()
}
