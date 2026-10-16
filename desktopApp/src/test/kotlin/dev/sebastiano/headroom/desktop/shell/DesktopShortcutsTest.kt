package dev.sebastiano.headroom.desktop.shell

import androidx.compose.ui.input.key.Key
import dev.sebastiano.headroom.ui.platform.UiBackDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopShortcutsTest {
    @Test
    fun `macOS text editing arrows are never global navigation shortcuts`() {
        assertFalse(isBackNavigationKey(Key.DirectionLeft, meta = true))
        assertFalse(isBackNavigationKey(Key.DirectionLeft, meta = false))
        assertFalse(isBackNavigationKey(Key.A, meta = true))
        assertTrue(isBackNavigationKey(Key.Escape, meta = false))
    }

    @Test
    fun `unhandled window back uses the same nested shared dispatcher as focused content`() {
        val back = UiBackDispatcher()
        val calls = mutableListOf<String>()
        val parent = back.register { calls += "page" }
        val child = back.register { calls += "editor" }

        assertTrue(dispatchBackNavigation(Key.Escape, false, back))
        assertEquals(listOf("editor"), calls)
        child()
        assertTrue(dispatchBackNavigation(Key.Escape, false, back))
        assertEquals(listOf("editor", "page"), calls)
        parent()
        assertFalse(dispatchBackNavigation(Key.Escape, false, back))
        assertFalse(dispatchBackNavigation(Key.DirectionLeft, true, back))
    }
}
