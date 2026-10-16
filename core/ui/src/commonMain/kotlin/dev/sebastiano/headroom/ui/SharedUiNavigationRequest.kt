package dev.sebastiano.headroom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * A host menu command. A new serial permits selecting the same destination again. When an
 * [OpenAccountRequest] is also supplied, the serials share an order and the newer request wins.
 */
@Immutable
data class SharedUiNavigationRequest(val serial: Long, val destination: SharedUiDestination)

enum class SharedUiDestination {
    Overview,
    Resets,
    Stats,
    Accounts,
    Settings,
}

@Composable
internal fun ConsumeTabRequest(
    requested: HomeTab?,
    onConsume: () -> Unit,
    closeDetail: (HomeTab) -> Unit,
    select: (HomeTab) -> Unit,
) {
    androidx.compose.runtime.SideEffect {
        requested?.let {
            closeDetail(it)
            select(it)
            onConsume()
        }
    }
}
