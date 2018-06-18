package com.pr0gramm.app.util

import android.content.Context
import android.support.annotation.StringRes
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

private const val Second: Long = 1L
private const val Minute: Long = 60 * Second
private const val Hour: Long = 60 * Minute
private const val Day: Long = 24 * Hour
private const val Week: Long = 7 * Day
private const val Month: Long = 30 * Day
private const val Year: Long = 12 * Month
private const val LongTime: Long = 37 * Year

private class Magnitude(
        val threshold: Long,
        @StringRes val since: Int,
        @StringRes val duration: Int,
        val factor: Long = 1,
        val rest: Long? = null) {

    val short get() = rest == null
}

private val MagnitudesShort = listOf(
        Magnitude(10 * Second, R.string.dt_since_few_second, R.string.dt_for_few_second),
        Magnitude(Minute, R.string.dt_since_n_seconds, R.string.dt_for_n_seconds, Second),

        Magnitude(2 * Minute, R.string.dt_since_one_minute, R.string.dt_for_one_minute),
        Magnitude(Hour, R.string.dt_since_n_minutes, R.string.dt_for_n_minutes, Minute),

        Magnitude(2 * Hour, R.string.dt_since_one_hour, R.string.dt_for_one_hour),
        Magnitude(Day, R.string.dt_since_n_hours, R.string.dt_for_n_hours, Hour),

        Magnitude(2 * Day, R.string.dt_since_one_day, R.string.dt_for_one_day),
        Magnitude(Week, R.string.dt_since_n_days, R.string.dt_for_n_days, Day),

        Magnitude(2 * Week, R.string.dt_since_one_week, R.string.dt_for_one_week),
        Magnitude(Month, R.string.dt_since_n_weeks, R.string.dt_for_n_weeks, Week),

        Magnitude(2 * Month, R.string.dt_since_one_month, R.string.dt_for_one_month),
        Magnitude(Year, R.string.dt_since_n_month, R.string.dt_for_n_month, Month),

        Magnitude(2 * Year, R.string.dt_since_one_year, R.string.dt_for_one_year),
        Magnitude(LongTime, R.string.dt_since_n_years, R.string.dt_for_n_years, Year))


private val MagnitudesLong = listOf(
        Magnitude(10 * Second, R.string.dt_since_few_second, R.string.dt_for_few_second),
        Magnitude(Minute, R.string.dt_since_n_seconds, R.string.dt_for_n_seconds, Second),

        Magnitude(2 * Minute, R.string.dt_since_one_minute, R.string.dt_for_one_minute),
        Magnitude(Hour, R.string.dt_since_n_minutes, R.string.dt_for_n_minutes, Minute),

        Magnitude(Hour + Minute, R.string.dt_since_one_hour, R.string.dt_for_one_hour),
        Magnitude(Hour + 2 * Minute, R.string.dt_since_one_hour_1_minutes, R.string.dt_for_one_hour_1_minutes, Hour, rest = Minute),
        Magnitude(2 * Hour, R.string.dt_since_one_hour_n_minutes, R.string.dt_for_one_hour_n_minutes, Hour, rest = Minute),
        Magnitude(Day, R.string.dt_since_n_hours, R.string.dt_for_n_hours, Hour),

        Magnitude(Day + Hour, R.string.dt_since_one_day, R.string.dt_for_one_day),
        Magnitude(Day + 2 * Hour, R.string.dt_since_one_day_1_hours, R.string.dt_for_one_day_1_hours, Day, rest = Hour),
        Magnitude(2 * Day, R.string.dt_since_one_day_n_hours, R.string.dt_for_one_day_n_hours, Day, rest = Hour),
        Magnitude(Week, R.string.dt_since_n_days, R.string.dt_for_n_days, Day),

        Magnitude(Week + Day, R.string.dt_since_one_week, R.string.dt_for_one_week),
        Magnitude(Week + 2 * Day, R.string.dt_since_one_week_1_days, R.string.dt_for_one_week_1_days, Week, rest = Day),
        Magnitude(2 * Week, R.string.dt_since_one_week_n_days, R.string.dt_for_one_week_n_days, Week, rest = Day),
        Magnitude(Month, R.string.dt_since_n_weeks, R.string.dt_for_n_weeks, Week),

        Magnitude(Month + Week, R.string.dt_since_one_month, R.string.dt_for_one_month),
        Magnitude(Month + 2 * Week, R.string.dt_since_one_month_1_weeks, R.string.dt_for_one_month_1_weeks, Month, rest = Week),
        Magnitude(2 * Month, R.string.dt_since_one_month_n_weeks, R.string.dt_for_one_month_n_weeks, Month, rest = Week),
        Magnitude(Year, R.string.dt_since_n_month, R.string.dt_for_n_month, Month),

        Magnitude(Year + Month, R.string.dt_since_one_year, R.string.dt_for_one_year),
        Magnitude(Year + 2 * Month, R.string.dt_since_one_year_1_month, R.string.dt_for_one_year_1_month, Year, rest = Month),
        Magnitude(2 * Year, R.string.dt_since_one_year_n_month, R.string.dt_for_one_year_n_month, Year, rest = Month),

        Magnitude(LongTime, R.string.dt_since_n_years, R.string.dt_for_n_years, Year))


private enum class TimeMode {
    DURATION, POINT_IN_TIME;

    fun select(m: Magnitude): Int {
        return when (this) {
            DURATION -> m.duration
            POINT_IN_TIME -> m.since
        }
    }
}

private fun format(ctx: Context, duration: Duration, mode: TimeMode, short: Boolean): String {
    val magnitudes = if (short) MagnitudesShort else MagnitudesLong

    val diff = duration.convertTo(TimeUnit.SECONDS).absoluteValue
    val magnitude = magnitudes.first { it.threshold >= diff }
    val format = ctx.getString(mode.select(magnitude))

    val rest = magnitude.rest?.let { (diff - magnitude.factor) / it }

    return format
            .replace("\$d", (diff / magnitude.factor).toString())
            .replace("\$r", (rest ?: 0).toString())
}

object DurationFormat {
    /**
     * Format a duration (some process took 2 hours and five minutes)
     *   * compiling the code ran for 45 minutes
     *   * you are banned to use the app for one 1 day and 8 hours
     *   * (the user is banned) for 20 minutes
     *   * (der Benutzer ist) für 20 Minuten (gesperrt)
     */
    fun timeSpan(ctx: Context, duration: Duration, short: Boolean): String {
        return format(ctx, duration, TimeMode.DURATION, short)
    }

    fun timeSpan(ctx: Context, instant: Instant, short: Boolean): String {
        return timeSpan(ctx, Duration.between(instant, Instant.now()), short)
    }

    /**
     * Format the time to or from a point in time
     *   * your appointment was 5 minutes ago
     *   * your bus leaves in 20 minutes
     *   * i need this in 4 days
     *   * (the comment was written) 20 minutes ago
     *   * (der kommentar wurde) vor 20 Minuten (geschrieben)
     */
    fun timeToPointInTime(ctx: Context, time: Instant, short: Boolean = false): String {
        val duration = Duration.between(time, Instant.now())
        return format(ctx, duration, TimeMode.POINT_IN_TIME, short)
    }
}