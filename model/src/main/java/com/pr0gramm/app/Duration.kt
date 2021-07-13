package com.pr0gramm.app

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import java.util.concurrent.TimeUnit

class Duration(private val nanos: Long) {
    fun convertTo(unit: TimeUnit): Long {
        return unit.convert(nanos, TimeUnit.NANOSECONDS)
    }

    // value in milliseconds
    val inMillis: Long get() = convertTo(TimeUnit.MILLISECONDS)

    override fun toString(): String {
        val unit = chooseUnit(nanos)
        val value = nanos.toDouble() / TimeUnit.NANOSECONDS.convert(1, unit)
        return format.format(value) + abbreviate(unit)
    }

    companion object {
        val Zero = Duration(0)

        fun between(later: Instant, earlier: Instant): Duration {
            return Duration.millis(later.millis - earlier.millis)
        }

        fun since(other: Instant): Duration {
            return between(Instant.now(), other)
        }

        fun millis(millis: Long): Duration {
            return Duration(TimeUnit.MILLISECONDS.toNanos(millis))
        }

        fun seconds(seconds: Long): Duration {
            return Duration(TimeUnit.SECONDS.toNanos(seconds))
        }

        fun minutes(minutes: Long): Duration {
            return Duration(TimeUnit.MINUTES.toNanos(minutes))
        }

        fun hours(hours: Long): Duration {
            return Duration(TimeUnit.HOURS.toNanos(hours))
        }

        fun days(days: Long): Duration {
            return Duration(TimeUnit.DAYS.toNanos(days))
        }

        private val format = DecimalFormat("#.0000", DecimalFormatSymbols.getInstance(Locale.ROOT))

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
    }
}

val Number.milliseconds: Duration get() = Duration.millis(toLong())
val Number.seconds: Duration get() = Duration.seconds(toLong())

suspend fun delay(amount: Duration) {
    kotlinx.coroutines.delay(amount.inMillis)
}
