package com.pr0gramm.app.util

import android.content.Context
import android.support.annotation.StringRes
import com.pr0gramm.app.R
import org.joda.time.Instant
import java.util.concurrent.TimeUnit

private const val Second: Long = 1L
private const val Minute: Long = 60 * Second
private const val Hour: Long = 60 * Minute
private const val Day: Long = 24 * Hour
private const val Week: Long = 7 * Day
private const val Month: Long = 30 * Day
private const val Year: Long = 12 * Month
private const val LongTime: Long = 37 * Year

private data class Magnitude(
        val threshold: Long, @StringRes val formatResId: Int, val factor: Long, val rest: Long? = null) {

    val short get() = rest == null
}

private val Magnitudes = listOf(
        Magnitude(Second, R.string.dt_now, Second),
        Magnitude(10 * Second, R.string.dt_few_second, 1),
        Magnitude(Minute, R.string.dt_n_seconds, Second),

        Magnitude(2 * Minute, R.string.dt_one_minute, 1),
        Magnitude(Hour, R.string.dt_n_minutes, Minute),

        Magnitude(Hour + Minute, R.string.dt_one_hour, 1),
        Magnitude(2 * Hour, R.string.dt_one_hour_n_minutes, Hour, rest = Second),
        Magnitude(Day, R.string.dt_n_hours, Hour),

        Magnitude(Day + Hour, R.string.dt_one_day, 1),
        Magnitude(2 * Day, R.string.dt_one_day_n_hours, Day, rest = Minute),
        Magnitude(Week, R.string.dt_n_days, Day),

        Magnitude(Week + Day, R.string.dt_one_week, 1),
        Magnitude(2 * Week, R.string.dt_one_week_n_days, Week, rest = Day),
        Magnitude(Month, R.string.dt_n_weeks, Week),

        Magnitude(Month + Week, R.string.dt_one_month, 1),
        Magnitude(2 * Month, R.string.dt_one_month_n_weeks, Month, rest = Week),
        Magnitude(Year, R.string.dt_n_month, Month),

        Magnitude(Year + Month, R.string.dt_one_year, 1),
        Magnitude(2 * Year, R.string.dt_one_year_n_month, Year, rest = Month),

        Magnitude(2 * Year + Month, R.string.dt_two_years, 1),
        Magnitude(3 * Year, R.string.dt_two_years_n_month, 2 * Year, rest = Month),

        Magnitude(LongTime, R.string.dt_n_years, Year),

        Magnitude(Long.MAX_VALUE, R.string.dt_long_white, 1))

fun formatTimeSpan(ctx: Context, time: Long, timeUnit: TimeUnit, short: Boolean = false): String {
    val diff = Math.abs(timeUnit.toSeconds(time)).toDouble()
    val magnitude = Magnitudes.first { it.threshold >= diff && (!short || it.short) }
    val format = ctx.getString(magnitude.formatResId)

    val rest = magnitude.rest?.let { (diff - magnitude.factor) / it }

    return format
            .replace("\$d", Math.round(diff / magnitude.factor).toString())
            .replace("\$r", Math.round(rest ?: 0.0).toString())

    // val label = if (time < 0) R.string.dt_label_past else R.string.dt_label_future
    // return ctx.getString(label, formatted)
}

fun formatTimeTo(ctx: Context, time: Instant, short: Boolean = false): String {
    val timeMillis = Instant.now().millis - time.millis
    return formatTimeSpan(ctx, timeMillis, TimeUnit.MILLISECONDS, short)
}
