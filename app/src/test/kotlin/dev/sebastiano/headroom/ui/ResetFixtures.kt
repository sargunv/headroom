package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.AccountState
import kotlin.time.Duration.Companion.days

/** Grok's weekly window, reset: nearly unused, and resetting a week later. */
internal fun List<AccountState>.withGrokReset(): List<AccountState> = map { state ->
    if (state.account.id != "demo-grok") return@map state
    val snapshot = requireNotNull(state.snapshot)
    state.copy(
        snapshot =
            snapshot.copy(
                windows =
                    snapshot.windows.map {
                        it.copy(usedPercent = 1.0, resetsAt = it.resetsAt?.plus(7.days))
                    }
            )
    )
}
