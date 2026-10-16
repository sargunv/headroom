package dev.sebastiano.headroom.ui.platform

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

enum class DayNameStyle {
    FULL,
    SHORT,
    NARROW,
}

expect fun DayOfWeek.displayName(style: DayNameStyle, locale: String): String

fun DayOfWeek.plus(days: Long): DayOfWeek =
    DayOfWeek(((isoDayNumber - 1 + days) % WEEK_DAYS).toInt() + 1)

private const val WEEK_DAYS = 7
