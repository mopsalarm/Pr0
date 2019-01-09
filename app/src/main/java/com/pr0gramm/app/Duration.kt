package com.pr0gramm.app

import java.util.concurrent.TimeUnit

inline class Duration(val millis: Long) {
    fun convertTo(unit: TimeUnit): Long {
        return unit.convert(millis, TimeUnit.MILLISECONDS)
    }

    companion object {
        val Zero = Duration(0)

        fun between(first: Instant, second: Instant): Duration {
            return Duration(first.millis - second.millis)
        }

        fun millis(millis: Long): Duration {
            return Duration(millis)
        }

        fun seconds(seconds: Long): Duration {
            return Duration(TimeUnit.SECONDS.toMillis(seconds))
        }

        fun minutes(minutes: Long): Duration {
            return Duration(TimeUnit.MINUTES.toMillis(minutes))
        }

        fun hours(hours: Long): Duration {
            return Duration(TimeUnit.HOURS.toMillis(hours))
        }

        fun days(days: Long): Duration {
            return Duration(TimeUnit.DAYS.toMillis(days))
        }
    }
}