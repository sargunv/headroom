package dev.sebastiano.headroom.desktop.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.nucleusframework.composenativetray.tray.impl.MacTrayInitializer
import dev.nucleusframework.composenativetray.utils.isMenuBarInDarkMode

/** The high-level Tray initializes AppKit on IO in 2.1.6. Keep native ownership on Tao's thread. */
@Composable
internal fun MacStatusTray(tooltip: String, onOpen: () -> Unit, menu: TrayMenuBuilder.() -> Unit) {
    val icons = remember { TrayIconFiles() }
    val id = remember { "headroom-${System.identityHashCode(icons)}" }
    val dark = isMenuBarInDarkMode()
    val icon = if (dark) icons.dark.toString() else icons.light.toString()
    DisposableEffect(id) {
        MacTrayInitializer.initialize(id, icon, tooltip, onOpen, menu)
        MacTrayInitializer.setAppearanceIcons(id, icons.light.toString(), icons.dark.toString())
        onDispose {
            MacTrayInitializer.dispose(id)
            icons.close()
        }
    }
    SideEffect { MacTrayInitializer.update(id, icon, tooltip, onOpen, menu) }
}
