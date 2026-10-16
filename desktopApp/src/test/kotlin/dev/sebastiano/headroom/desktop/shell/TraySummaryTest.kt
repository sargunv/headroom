package dev.sebastiano.headroom.desktop.shell

import kotlin.test.Test
import kotlin.test.assertEquals

class TraySummaryTest {
    @Test
    fun `summary honors quota display and preserves over limit usage`() {
        assertEquals("Claude: 18% left", trayQuotaSummary("Claude", 82.0, "left"))
        assertEquals("Claude: 104% used", trayQuotaSummary("Claude", 104.0, "used"))
        assertEquals("Claude: 0% left", trayQuotaSummary("Claude", 104.0, "left"))
    }

    @Test
    fun `unavailable data is never described as zero usage`() {
        assertEquals("Claude: Waiting for sync", trayQuotaSummary("Claude", null, "used"))
        assertEquals("Claude: Waiting for sync", trayQuotaSummary("Claude", Double.NaN, "used"))
    }

    @Test
    fun `error ids map to safe readable status without raw provider details`() {
        assertEquals("Sign in again", trayErrorSummary("auth"))
        assertEquals("Connection unavailable", trayErrorSummary("network"))
        assertEquals("Could not sync", trayErrorSummary("unexpected-secret-bearing-message"))
    }
}
