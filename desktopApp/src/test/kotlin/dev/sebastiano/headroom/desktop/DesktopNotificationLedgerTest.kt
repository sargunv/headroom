package dev.sebastiano.headroom.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class DesktopNotificationLedgerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `accepted reset notifications survive a restart`() {
        val ledger = DesktopNotificationLedger(directory.resolve("notifications.json"))
        ledger.recordReset("account/window/123")

        assertTrue(
            DesktopNotificationLedger(directory.resolve("notifications.json"))
                .hasReset("account/window/123")
        )
        assertFalse(ledger.hasReset("account/window/456"))
    }

    @Test
    fun `sign in and reminder state survives a restart`() {
        val file = directory.resolve("notifications.json")
        DesktopNotificationLedger(file)
            .update(listOf("expired-account"), listOf("account/pool/123"))

        val state = DesktopNotificationLedger(file)
        assertEquals(listOf("expired-account"), state.signInNotified)
        assertEquals(listOf("account/pool/123"), state.reminded)
    }

    @Test
    fun `unknown or corrupt stored state does not prevent startup`() {
        val file = directory.resolve("notifications.json")
        Files.writeString(file, "not json")

        val ledger = DesktopNotificationLedger(file)
        assertEquals(emptyList(), ledger.signInNotified)
        assertEquals(emptyList(), ledger.reminded)
        assertFalse(ledger.hasReset("anything"))
    }

    @Test
    fun `reset ledger retains a bounded recent history`() {
        val ledger = DesktopNotificationLedger(directory.resolve("notifications.json"))
        repeat(1_010) { ledger.recordReset("reset-$it") }

        assertFalse(ledger.hasReset("reset-0"))
        assertTrue(ledger.hasReset("reset-1009"))
    }
}
