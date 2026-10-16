package dev.sebastiano.headroom.ui.platform

import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

actual fun DayOfWeek.displayName(style: DayNameStyle, locale: String): String =
    java.time.DayOfWeek.of(isoDayNumber)
        .getDisplayName(TextStyle.valueOf(style.name), Locale.forLanguageTag(locale))
