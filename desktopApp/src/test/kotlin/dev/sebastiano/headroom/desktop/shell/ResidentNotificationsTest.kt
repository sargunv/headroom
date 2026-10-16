package dev.sebastiano.headroom.desktop.shell

import dev.sebastiano.headroom.desktop.DesktopNotificationLedger
import dev.sebastiano.headroom.shared.NotificationPlanUi
import dev.sebastiano.headroom.shared.ReminderUi
import dev.sebastiano.headroom.shared.SignInAlertUi
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir

class ResidentNotificationsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `future reminder stays unsent when the plan crosses the due boundary`() = runBlocking {
        val ledger = DesktopNotificationLedger(directory.resolve("ledger.json"))
        val notifier = RecordingNotifications()
        var reminder = ReminderUi("reminder:today", 100, emptyList(), listOf("a/pool/200000000"))
        val delivery =
            ResidentNotifications(
                { _, reminded ->
                    assertEquals(emptyList(), reminded)
                    plan(reminders = listOf(reminder), reminded = reminder.resetKeys)
                },
                ledger,
                notifier,
            )
        delivery.deliver(90_000)
        assertEquals(emptyList(), ledger.reminded)
        assertEquals(emptyList(), notifier.sent)
        reminder = reminder.copy(fireAtEpochSeconds = 110)
        delivery.deliver(105_000)
        assertEquals(listOf("reminder:today"), notifier.sent)
        assertEquals(reminder.resetKeys, ledger.reminded)
    }

    @Test
    fun `native rejection or denied permission never marks an alert accepted`() = runBlocking {
        val ledger = DesktopNotificationLedger(directory.resolve("ledger.json"))
        val notifier = RecordingNotifications(accepted = false)
        val reminder = ReminderUi("reminder:today", 100, emptyList(), listOf("a/pool/200000000"))
        val signIn = SignInAlertUi("account", "Claude", null, null)
        val delivery =
            ResidentNotifications(
                { _, _ ->
                    plan(
                        reminders = listOf(reminder),
                        alerts = listOf(signIn),
                        reminded = reminder.resetKeys,
                    )
                },
                ledger,
                notifier,
            )
        delivery.deliver(100_000)
        delivery.deliver(115_000)
        assertEquals(emptyList(), ledger.reminded)
        assertEquals(emptyList(), ledger.signInNotified)
        notifier.accepted = true
        delivery.deliver(130_000)
        assertEquals(reminder.resetKeys, ledger.reminded)
        assertEquals(listOf("account"), ledger.signInNotified)
    }

    @Test
    fun `sign in recovery cancels native warning before forgetting its ledger entry`() =
        runBlocking {
            val ledger = DesktopNotificationLedger(directory.resolve("ledger.json"))
            ledger.update(listOf("account"), emptyList())
            val notifier = RecordingNotifications()
            val delivery =
                ResidentNotifications(
                    { _, _ -> plan(cancels = listOf("account")) },
                    ledger,
                    notifier,
                )
            delivery.deliver(100_000)
            assertEquals(listOf("signin:account"), notifier.dismissed)
            assertEquals(emptyList(), ledger.signInNotified)
        }

    private fun plan(
        reminders: List<ReminderUi> = emptyList(),
        alerts: List<SignInAlertUi> = emptyList(),
        cancels: List<String> = emptyList(),
        reminded: List<String> = emptyList(),
    ): NotificationPlanUi =
        NotificationPlanUi(emptyList(), reminders, alerts, cancels, emptyList(), reminded)
}

internal class RecordingNotifications(var accepted: Boolean = true) : NotificationSink {
    val sent = mutableListOf<String>()
    val dismissed = mutableListOf<String>()

    override suspend fun send(id: String, title: String, body: String): Boolean {
        sent += id
        return accepted
    }

    override fun dismiss(id: String) {
        dismissed += id
    }
}
