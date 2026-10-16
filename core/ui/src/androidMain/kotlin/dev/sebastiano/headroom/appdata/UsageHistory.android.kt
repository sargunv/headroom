package dev.sebastiano.headroom.appdata

import java.time.ZoneId
import kotlin.time.Instant
import kotlinx.datetime.toKotlinTimeZone

/** Keeps Android shell and test call sites source-compatible with the original constructor. */
@Suppress("FunctionNaming") // Constructor-compatible factory for the old Java time boundary.
fun DemoUsageHistory(
    resets: ResetHistory,
    clock: () -> Instant,
    zone: ZoneId,
): DemoUsageHistory = DemoUsageHistory(resets, clock, zone.toKotlinTimeZone())
