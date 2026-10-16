package dev.sebastiano.headroom.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackDispatcherTest {
    @Test
    fun lastRegisteredHandlerWinsAndDisposalRestoresPrevious() {
        val dispatcher = UiBackDispatcher()
        val calls = mutableListOf<String>()
        val first = dispatcher.register { calls += "page" }
        val second = dispatcher.register { calls += "sheet" }
        assertTrue(dispatcher.dispatch())
        assertEquals(listOf("sheet"), calls)
        second()
        assertTrue(dispatcher.dispatch())
        assertEquals(listOf("sheet", "page"), calls)
        first()
        assertFalse(dispatcher.dispatch())
    }
}
