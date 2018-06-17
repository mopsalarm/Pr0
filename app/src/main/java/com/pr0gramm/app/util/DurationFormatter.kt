package com.pr0gramm.app.util

import android.content.Context
import android.support.annotation.StringRes
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import java.util.concurrent.TimeUnit

private const val Second: Long = 1L
private const val Minute: Long = 60 * Second
private const val Hour: Long = 60 * Minute
private const val Day: Long = 24 * Hour
private const val Week: Long = 7 * Day
private const val Month: Long = 30 * Day
private const val Year: Long = 12 * Month
private const val LongTime: Long = 37 * Year

internal class Magnitude(
        val threshold: Long,
        @StringRes val since: Int,
        @StringRes val duration: Int,
        val factor: Long = 1,
        val rest: Long? = null) {

    val short get() = rest == null
}

private val Magnitudes = listOf(
        Magnitude(10 * Second, R.string.dt_since_few_second, R.string.dt_for_few_second),
        Magnitude(Minute, R.string.dt_since_n_seconds, R.string.dt_for_n_seconds, Second),

        Magnitude(2 * Minute, R.string.dt_since_one_minute, R.string.dt_for_one_minute),
        Magnitude(Hour, R.string.dt_since_n_minutes, R.string.dt_for_n_minutes, Minute),

        Magnitude(Hour + Minute, R.string.dt_since_one_hour, R.string.dt_for_one_hour),
        Magnitude(2 * Hour, R.string.dt_since_one_hour_n_minutes, R.string.dt_for_one_hour_n_minutes, Hour, rest = Minute),
        Magnitude(2 * Hour, R.string.dt_since_one_hour, R.string.dt_for_one_hour),
        Magnitude(Day, R.string.dt_since_n_hours, R.string.dt_for_n_hours, Hour),

        Magnitude(Day + Hour, R.string.dt_since_one_day, R.string.dt_for_one_day),
        Magnitude(2 * Day, R.string.dt_since_one_day_n_hours, R.string.dt_for_one_day_n_hours, Day, rest = Hour),
        Magnitude(2 * Day, R.string.dt_since_one_day, R.string.dt_for_one_day),
        Magnitude(Week, R.string.dt_since_n_days, R.string.dt_for_n_days, Day),

        Magnitude(Week + Day, R.string.dt_since_one_week, R.string.dt_for_one_week),
        Magnitude(2 * Week, R.string.dt_since_one_week_n_days, R.string.dt_for_one_week_n_days, Week, rest = Day),
        Magnitude(2 * Week, R.string.dt_since_one_week, R.string.dt_for_one_week),
        Magnitude(Month, R.string.dt_since_n_weeks, R.string.dt_for_n_weeks, Week),

        Magnitude(Month + Week, R.string.dt_since_one_month, R.string.dt_for_one_month),
        Magnitude(2 * Month, R.string.dt_since_one_month_n_weeks, R.string.dt_for_one_month_n_weeks, Month, rest = Week),
        Magnitude(2 * Month, R.string.dt_since_one_month, R.string.dt_for_one_month),
        Magnitude(Year, R.string.dt_since_n_month, R.string.dt_for_n_month, Month),

        Magnitude(Year + Month, R.string.dt_since_one_year, R.string.dt_for_one_year),
        Magnitude(2 * Year, R.string.dt_since_one_year_n_month, R.string.dt_for_one_year_n_month, Year, rest = Month),

        Magnitude(2 * Year + Month, R.string.dt_since_two_years, R.string.dt_for_two_years),
        Magnitude(3 * Year, R.string.dt_since_two_years_n_month, R.string.dt_for_two_years_n_month, 2 * Year, rest = Month),

        Magnitude(LongTime, R.string.dt_since_n_years, R.string.dt_for_n_years, Year),

        Magnitude(Long.MAX_VALUE, R.string.dt_since_long_white, R.string.dt_for_long_white))

enum class TimeMode {
    DURATION,
    SINCE;

    internal fun select(m: Magnitude): Int {
        return when (this) {
            DURATION -> m.duration
            SINCE -> m.since
        }
    }
}

private fun formatTimeSpan(ctx: Context, time: Long, timeUnit: TimeUnit, mode: TimeMode, short: Boolean = false): String {
    val diff = timeUnit.toSeconds(Math.abs(time)).toDouble()
    val magnitude = Magnitudes.first { it.threshold >= diff && (!short || it.short) }
    val format = ctx.getString(mode.select(magnitude))

    val rest = magnitude.rest?.let { (diff - magnitude.factor) / it }

    return format
            .replace("\$d", Math.round(diff / magnitude.factor).toString())
            .replace("\$r", Math.round(rest ?: 0.0).toString())

    // val label = if (time < 0) R.string.dt_label_past else R.string.dt_label_future
    // return ctx.getString(label, formatted)
}

fun formatTimeTo(ctx: Context, time: Instant, mode: TimeMode, short: Boolean = false): String {
    val timeMillis = Instant.now().millis - time.millis
    return formatTimeSpan(ctx, timeMillis, TimeUnit.MILLISECONDS, mode, short)
}
