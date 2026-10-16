package dev.sebastiano.headroom.ui

import java.time.LocalDate as JavaLocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number

/** Keeps Android's original constructor call sites, including the screen tests. */
@Suppress("FunctionNaming") // A compatibility factory intentionally has the constructor's name.
fun ResetFormatter(zone: ZoneId, locale: Locale, is24Hour: Boolean): ResetFormatter =
    ResetFormatter(TimeZone.of(zone.id), locale.toLanguageTag(), is24Hour)

fun ResetFormatter.day(date: JavaLocalDate): String =
    day(LocalDate(date.year, date.monthValue, date.dayOfMonth))

internal actual fun platformDateFormats(zone: TimeZone, locale: String): ResetDateFormats =
    JavaResetDateFormats(ZoneId.of(zone.id), Locale.forLanguageTag(locale))

private class JavaResetDateFormats(private val zone: ZoneId, private val locale: Locale) :
    ResetDateFormats {
    private val patterns = ConcurrentHashMap<String, DateTimeFormatter>()

    private fun pattern(value: String): DateTimeFormatter =
        patterns.getOrPut(value) { DateTimeFormatter.ofPattern(value, locale).withZone(zone) }

    override fun instant(at: Instant, pattern: String): String =
        pattern(pattern).format(at.toJavaInstant())

    override fun date(date: LocalDate, pattern: String): String =
        pattern(pattern).format(JavaLocalDate.of(date.year, date.month.number, date.day))

    override fun hour(hour: Int, pattern: String): String =
        pattern(pattern).format(LocalTime.of(hour, 0))
}
