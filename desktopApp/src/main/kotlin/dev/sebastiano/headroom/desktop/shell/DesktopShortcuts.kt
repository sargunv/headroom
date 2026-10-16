package dev.sebastiano.headroom.desktop.shell

import androidx.compose.ui.input.key.Key
import dev.sebastiano.headroom.ui.platform.UiBackDispatcher

/** Command-arrow belongs to macOS text editing, never to application navigation. */
internal fun isBackNavigationKey(key: Key, meta: Boolean): Boolean = key == Key.Escape && !meta

internal fun dispatchBackNavigation(key: Key, meta: Boolean, back: UiBackDispatcher): Boolean =
    isBackNavigationKey(key, meta) && back.dispatch()
