package com.pr0gramm.app.util

import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

class Stopwatch {
    private val startTime = System.nanoTime()

    private val nanos get() = System.nanoTime() - startTime

    fun elapsed(unit: TimeUnit) = unit.convert(nanos, TimeUnit.NANOSECONDS)

    /** Returns a string representation of the current elapsed time.  */
    override fun toString(): String {
        val nanos = nanos

        val unit = chooseUnit(nanos)
        val value = nanos.toDouble() / TimeUnit.NANOSECONDS.convert(1, unit)
        return format.format(value) + abbreviate(unit)
    }

    private fun chooseUnit(nanos: Long): TimeUnit {
        return when {
            TimeUnit.DAYS.convert(nanos, TimeUnit.NANOSECONDS) > 0 -> TimeUnit.DAYS
            TimeUnit.HOURS.convert(nanos, TimeUnit.NANOSECONDS) > 0 -> TimeUnit.HOURS
            TimeUnit.MINUTES.convert(nanos, TimeUnit.NANOSECONDS) > 0 -> TimeUnit.MINUTES
            TimeUnit.SECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 0 -> TimeUnit.SECONDS
            TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 0 -> TimeUnit.MILLISECONDS
            TimeUnit.MICROSECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 0 -> TimeUnit.MICROSECONDS
            else -> TimeUnit.NANOSECONDS
        }
    }

    private fun abbreviate(unit: TimeUnit): String {
        return when (unit) {
            TimeUnit.NANOSECONDS -> "ns"
            TimeUnit.MICROSECONDS -> "\u03bcs" // μs
            TimeUnit.MILLISECONDS -> "ms"
            TimeUnit.SECONDS -> "s"
            TimeUnit.MINUTES -> "min"
            TimeUnit.HOURS -> "h"
            TimeUnit.DAYS -> "d"
            else -> throw AssertionError()
        }
    }

    companion object {
        private val format = DecimalFormat("#.0000")

        fun createStarted() = Stopwatch()
    }
}