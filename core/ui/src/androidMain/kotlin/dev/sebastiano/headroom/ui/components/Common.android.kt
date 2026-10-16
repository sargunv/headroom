package dev.sebastiano.headroom.ui.components

import androidx.compose.runtime.Composable
import dev.sebastiano.headroom.ui.ResetFormatter
import java.time.ZoneId
import kotlinx.datetime.TimeZone

/** Compatibility for Android's debug tools and existing screen tests. */
@Composable
fun rememberResetFormatter(zone: ZoneId): ResetFormatter =
    rememberResetFormatter(TimeZone.of(zone.id))
