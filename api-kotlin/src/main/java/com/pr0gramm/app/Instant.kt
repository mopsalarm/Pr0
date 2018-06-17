package com.pr0gramm.app

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class Instant(val millis: Long) : Comparable<Instant> {
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

    val isAfterNow: Boolean
        get() {
            return millis > System.currentTimeMillis()
        }

    val isBeforeNow: Boolean
        get() {
            return millis < System.currentTimeMillis()
        }

    override fun compareTo(other: Instant): Int {
        return millis.compareTo(other.millis)
    }

    override fun hashCode(): Int {
        return millis.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is Instant && other.millis == millis
    }

    fun toString(format: SimpleDateFormat): String {
        return format.format(Date(millis))
    }

    companion object {
        @JvmStatic
        fun now(): Instant = Instant(System.currentTimeMillis())
    }
}