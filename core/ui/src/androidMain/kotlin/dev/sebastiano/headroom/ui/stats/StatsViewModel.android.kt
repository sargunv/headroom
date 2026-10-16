package dev.sebastiano.headroom.ui.stats

import dev.sebastiano.headroom.appdata.UsageHistory
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.ResetEventLog
import java.time.ZoneId
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.toKotlinTimeZone

/** Keeps the original Android constructor's Java time argument at the platform boundary. */
@Suppress("FunctionNaming") // Constructor-compatible factory for the old Java time boundary.
fun StatsViewModel(
    repository: QuotaRepository,
    history: UsageHistory,
    isDemo: StateFlow<Boolean>,
    clock: () -> Instant,
    zone: ZoneId,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    resetEvents: ResetEventLog = ResetEventLog.None,
): StatsViewModel =
    StatsViewModel(
        repository = repository,
        history = history,
        isDemo = isDemo,
        clock = clock,
        zone = zone.toKotlinTimeZone(),
        computeDispatcher = computeDispatcher,
        resetEvents = resetEvents,
    )
