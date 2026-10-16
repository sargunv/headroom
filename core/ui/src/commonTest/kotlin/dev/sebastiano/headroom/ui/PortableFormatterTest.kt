package dev.sebastiano.headroom.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class PortableFormatterTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    @Test
    fun originalResetLabelsUseTheHostsClockAndZone() {
        val formatter = ResetFormatter(TimeZone.UTC, "en-US", is24Hour = true)
        assertEquals("Wed 07:00", formatter.short(Instant.parse("2026-09-30T07:00:00Z"), now))
        assertEquals("11 Oct", formatter.short(Instant.parse("2026-10-11T07:00:00Z"), now))
        assertEquals("Wed 30 Sep", formatter.day(LocalDate(2026, 9, 30)))
        assertEquals("2d 18h", formatter.countdown(now, Instant.parse("2026-09-30T07:00:00Z")))
        val tokyo = ResetFormatter(TimeZone.of("Asia/Tokyo"), "en-US", is24Hour = false)
        val instant = Instant.parse("2026-11-05T20:00:00Z")
        assertEquals("6 Nov 2026", tokyo.date(instant))
        assertEquals(DayOfWeek.FRIDAY, tokyo.dayOfWeek(instant))
        assertEquals("2:00 PM", tokyo.hour(14))
    }

    @Test
    fun localizedAmountsKeepOriginalRoundingAndMinimumPositiveAmount() {
        assertEquals("0.01", formatAmount(0.001, "en-US"))
        assertEquals("9.99", formatAmount(9.994, "en-US"))
        assertEquals("9,99", formatAmount(9.994, "de-DE"))
        assertEquals("$102", formatMoney(102.0, "USD", "en-US"))
        assertEquals("$102.50", formatMoney(102.5, "USD", "en-US"))
    }
}
