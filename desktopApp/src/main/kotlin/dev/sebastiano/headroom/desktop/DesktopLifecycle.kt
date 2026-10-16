package dev.sebastiano.headroom.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Window lifetime is separate from the lifetime of the resident menu-bar app. */
internal class DesktopLifecycle(startHidden: Boolean, private val onQuit: () -> Unit) {
    private val lock = Any()
    private var stopped = false
    private val windowVisible = MutableStateFlow(!startHidden)

    val visible: StateFlow<Boolean> = windowVisible.asStateFlow()

    fun showWindow() {
        synchronized(lock) { if (!stopped) windowVisible.value = true }
    }

    fun closeWindow() {
        synchronized(lock) { windowVisible.value = false }
    }

    fun quit() {
        val shouldQuit =
            synchronized(lock) {
                if (stopped) {
                    false
                } else {
                    stopped = true
                    windowVisible.value = false
                    true
                }
            }
        if (shouldQuit) onQuit()
    }
}
