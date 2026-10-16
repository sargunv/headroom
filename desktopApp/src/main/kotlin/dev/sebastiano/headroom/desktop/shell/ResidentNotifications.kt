package dev.sebastiano.headroom.desktop.shell

import dev.sebastiano.headroom.desktop.DesktopNotificationLedger
import dev.sebastiano.headroom.shared.Headroom
import dev.sebastiano.headroom.shared.NotificationPlanUi
import dev.sebastiano.headroom.shared.ReminderUi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uses only sign-in and expiry reminders from the iOS plan; predicted resets never notify here. */
internal class ResidentNotifications(
    private val planNotifications: suspend (List<String>, List<String>) -> NotificationPlanUi,
    private val ledger: DesktopNotificationLedger,
    private val notifications: NotificationSink,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(
        headroom: Headroom,
        ledger: DesktopNotificationLedger,
        notifications: NotificationSink,
    ) : this(headroom.notifications::plan, ledger, notifications)

    private val reminders = linkedMapOf<String, ReminderUi>()

    suspend fun deliver(now: Long) {
        val plan = planNotifications(ledger.signInNotified, ledger.reminded)
        plan.signInCancels.forEach { notifications.dismiss("signin:$it") }
        var signed = ledger.signInNotified - plan.signInCancels.toSet()
        for (alert in plan.signInAlerts) {
            if (
                notifications.send(
                    "signin:${alert.accountId}",
                    "Sign in again",
                    "Open Headroom to reconnect ${alert.providerName}.",
                )
            )
                signed = signed + alert.accountId
        }
        val plannedIds = plan.reminders.map { it.id }.toSet()
        reminders.keys.retainAll(plannedIds)
        plan.reminders.forEach { next ->
            val previous = reminders[next.id]
            reminders[next.id] =
                next.copy(
                    fireAtEpochSeconds =
                        minOf(
                            previous?.fireAtEpochSeconds ?: Long.MAX_VALUE,
                            next.fireAtEpochSeconds,
                        )
                )
        }
        var reminded = ledger.reminded.intersect(plan.reminded.toSet()).toList()
        for (reminder in reminders.values.toList()) {
            if (reminder.fireAtEpochSeconds > now / MILLIS_PER_SECOND) continue
            if (
                notifications.send(
                    reminder.id,
                    "Resets expire soon",
                    "Open Headroom to review your available resets.",
                )
            ) {
                reminded = (reminded + reminder.resetKeys).distinct()
                reminders.remove(reminder.id)
            }
        }
        withContext(io) { ledger.update(signed.distinct(), reminded) }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
