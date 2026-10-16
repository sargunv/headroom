package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.Countdown
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Reset labels in the host's language, timezone, and preferred clock style. */
class ResetFormatter(private val zone: TimeZone, locale: String, is24Hour: Boolean) {
    private val time = if (is24Hour) "HH:mm" else "h:mm a"
    private val formats = platformDateFormats(zone, locale)

    fun short(at: Instant, now: Instant): String =
        formats.instant(at, if (at - now > NEAR) "d MMM" else "EEE $time")

    fun long(at: Instant): String = formats.instant(at, "EEE d MMM, $time")

    /** A date with its year, for an expiry that can be months away: "5 Nov 2026". */
    fun date(at: Instant): String = formats.instant(at, "d MMM yyyy")

    fun countdown(now: Instant, at: Instant): String = Countdown.format(at - now)

    /** The local day of [at], with its weekday: "Wed 30 Sep". */
    fun day(at: Instant): String = day(at.toLocalDateTime(zone).date)

    fun day(date: LocalDate): String = formats.date(date, "EEE d MMM")

    /** The start of an hour of the day, in the user's clock style: "14:00" or "2:00 PM". */
    fun hour(hour: Int): String = formats.hour(hour, time)

    /** The local day of the week of [at], for chart labels. */
    fun dayOfWeek(at: Instant): DayOfWeek = at.toLocalDateTime(zone).dayOfWeek

    private companion object {
        val NEAR = 6.days
    }
}

internal interface ResetDateFormats {
    fun instant(at: Instant, pattern: String): String

    fun date(date: LocalDate, pattern: String): String

    fun hour(hour: Int, pattern: String): String
}

internal expect fun platformDateFormats(zone: TimeZone, locale: String): ResetDateFormats
