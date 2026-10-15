package dev.ikram.sandbox.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Formatters {
    private const val UNIT = 1000.0

    /** Decimal units as Android's own file size formatter uses: "980 B", "2 KB", "2.4 MB", "1.1 GB". */
    fun size(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < UNIT) return "$bytes B"
        val kilobytes = bytes / UNIT
        if (kilobytes < UNIT) return "${kilobytes.toInt()} KB"
        val megabytes = kilobytes / UNIT
        if (megabytes < UNIT) return String.format(locale, "%.1f MB", megabytes)
        return String.format(locale, "%.1f GB", megabytes / UNIT)
    }

    /** "14:32", or "2:32 PM" where that is the local habit. */
    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)

    /** "14 Oct 2026, 14:32". */
    fun dateTime(
        instant: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(instant)

    /** Playback times as players show them: "04:12", or "1:02:03" from an hour on. */
    fun duration(millis: Long, locale: Locale = Locale.getDefault()): String {
        val totalSeconds = millis.coerceAtLeast(0) / MILLIS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return if (hours > 0) {
            String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(locale, "%02d:%02d", minutes, seconds)
        }
    }

    /** "1×", "1.25×", "0.75×". */
    fun speed(speed: Float, locale: Locale = Locale.getDefault()): String {
        val text = String.format(locale, "%.2f", speed).trimEnd('0').trimEnd('.', ',')
        return "$text×"
    }

    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3600L

    /** "12 Oct" this year, "12 Oct 2025" before it. */
    fun day(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val pattern = if (date.year == today.year) "d MMM" else "d MMM yyyy"
        return DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }
}
