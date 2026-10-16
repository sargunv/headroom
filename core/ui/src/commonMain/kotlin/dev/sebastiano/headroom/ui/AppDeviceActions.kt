package dev.sebastiano.headroom.ui

import androidx.compose.runtime.Composable
import dev.sebastiano.headroom.ui.accounts.AccountsActions
import dev.sebastiano.headroom.ui.platform.rememberUiPlatform

/** These actions, with copying to the clipboard and opening links in a browser tab wired up. */
@Composable
internal fun AccountsActions.withDeviceActions(): AccountsActions {
    val platform = rememberUiPlatform()
    return copy(onCopy = platform::copy, onOpenUrl = platform::openUrl)
}
