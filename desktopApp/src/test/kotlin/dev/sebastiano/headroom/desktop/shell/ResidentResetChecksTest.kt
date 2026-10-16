package dev.sebastiano.headroom.desktop.shell

import dev.sebastiano.headroom.desktop.DesktopNotificationLedger
import dev.sebastiano.headroom.shared.ConfirmedResetUi
import dev.sebastiano.headroom.shared.ResetCheckOutcomeUi
import dev.sebastiano.headroom.shared.ResetCheckUi
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir

class ResidentResetChecksTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `disabled or expired account discard leaves no retry or delivery`() = runBlocking {
        var checked = 0
        val notifications = RecordingNotifications()
        val runner =
            ResidentResetChecks(
                {
                    checked++
                    ResetCheckOutcomeUi.Discard
                },
                DesktopNotificationLedger(directory.resolve("ledger.json")),
                notifications,
            )
        val check = ResetCheckUi("account", "weekly", 100, 100, 95.0)
        runner.run(listOf(check), 100)
        runner.run(listOf(check), 100_000)
        assertEquals(1, checked)
        assertEquals(emptyList(), notifications.sent)
    }

    @Test
    fun `due windows for one account are staggered rather than refetched in the same cycle`() =
        runBlocking {
            val checked = mutableListOf<String>()
            val runner =
                ResidentResetChecks(
                    {
                        checked += it.windowId
                        ResetCheckOutcomeUi.Retry
                    },
                    DesktopNotificationLedger(directory.resolve("ledger.json")),
                    RecordingNotifications(),
                )
            val plan =
                listOf(
                    ResetCheckUi("account", "weekly", 100, 100, 95.0),
                    ResetCheckUi("account", "monthly", 100, 100, 95.0),
                )
            runner.run(plan, 100)
            assertEquals(listOf("weekly"), checked)
            runner.run(plan, 15_100)
            assertEquals(listOf("weekly", "monthly"), checked)
        }

    @Test
    fun `confirmed reset is recorded only after native acceptance and stale plans cannot repost`() =
        runBlocking {
            val ledger = DesktopNotificationLedger(directory.resolve("ledger.json"))
            val notifications = RecordingNotifications(accepted = false)
            val reset =
                ConfirmedResetUi(
                    "reset:a/weekly/100",
                    "a",
                    "weekly",
                    "Claude",
                    false,
                    95.0,
                    0.0,
                    null,
                )
            val runner =
                ResidentResetChecks({ ResetCheckOutcomeUi.Confirmed(reset) }, ledger, notifications)
            val check = ResetCheckUi("a", "weekly", 100, 100, 95.0)
            runner.run(listOf(check), 100)
            assertEquals(false, ledger.hasReset(reset.dedupeKey))
            notifications.accepted = true
            runner.run(listOf(check), 60_100)
            assertEquals(true, ledger.hasReset(reset.dedupeKey))
            runner.run(listOf(check), 120_100)
            assertEquals(listOf(reset.dedupeKey, reset.dedupeKey), notifications.sent)
        }
}
