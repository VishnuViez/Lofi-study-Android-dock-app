package com.vishnu.lofidock.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

object TimeFormatting {
    /** Session length as H:MM:SS. Hours keep counting past 24 instead of wrapping. */
    fun session(seconds: Double): String {
        val total = if (seconds.isFinite()) max(0.0, seconds).toLong() else 0L
        return String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }

    /**
     * Countdown display: "25:00" under an hour, "1:05:00" from an hour up.
     * Rounds up, so a fresh 25-minute timer reads 25:00 and its last second 0:01.
     */
    fun countdown(remainingSeconds: Double): String {
        val total = if (remainingSeconds.isFinite()) ceil(max(0.0, remainingSeconds)).toLong() else 0L
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }

    /** The time split in two so the AM/PM marker can be set smaller. */
    data class Clock(val time: String, val period: String?) {
        val joined: String get() = if (period == null) time else "$time $period"
    }

    /**
     * [use24Hour] comes from the phone's 24-hour setting
     * (`DateFormat.is24HourFormat`), so the clock follows the user's choice,
     * not just their region.
     */
    fun clock(
        epochMillis: Long,
        use24Hour: Boolean,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Clock {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone)
        if (use24Hour) {
            return Clock(DateTimeFormatter.ofPattern("HH:mm", locale).format(time), null)
        }
        return Clock(
            DateTimeFormatter.ofPattern("h:mm", locale).format(time),
            DateTimeFormatter.ofPattern("a", locale).format(time),
        )
    }

    /**
     * "Saturday, November 21". The app passes the locale's best pattern for
     * weekday + month + day (`DateFormat.getBestDateTimePattern`); tests use
     * the English default.
     */
    fun dateLine(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
        pattern: String = "EEEE, MMMM d",
    ): String = DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
