package dev.sebastiano.headroom.desktop.shell

import dev.sebastiano.headroom.desktop.DesktopNotificationLedger
import dev.sebastiano.headroom.shared.ConfirmedResetUi
import dev.sebastiano.headroom.shared.ResetCheckOutcomeUi
import dev.sebastiano.headroom.shared.ResetCheckUi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ResidentResetChecks(
    private val checkOutcome: suspend (ResetCheckUi) -> ResetCheckOutcomeUi,
    private val ledger: DesktopNotificationLedger,
    private val notifications: NotificationSink,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val queue = ResetCheckQueue()

    fun replan(plan: List<ResetCheckUi>, now: Long) = queue.update(plan, now)

    suspend fun run(plan: List<ResetCheckUi>, now: Long) {
        replan(plan, now)
        // Each check refreshes its account. Stagger other windows until the next poll.
        for (check in queue.due(now).distinctBy { it.accountId }) {
            queue.attempted(check, now)
            when (val outcome = checkOutcome(check)) {
                ResetCheckOutcomeUi.Discard -> queue.complete(check)
                ResetCheckOutcomeUi.Retry -> Unit
                is ResetCheckOutcomeUi.Confirmed -> {
                    if (deliver(outcome.reset)) queue.complete(check)
                }
            }
        }
    }

    private suspend fun deliver(reset: ConfirmedResetUi): Boolean {
        if (ledger.hasReset(reset.dedupeKey)) return true
        val period = if (reset.monthly) "monthly" else "weekly"
        val accepted =
            notifications.send(
                reset.dedupeKey,
                "Your $period quota reset",
                "Open Headroom to see the refreshed allowance for ${reset.accountName}.",
            )
        if (accepted) withContext(io) { ledger.recordReset(reset.dedupeKey) }
        return accepted
    }
}
