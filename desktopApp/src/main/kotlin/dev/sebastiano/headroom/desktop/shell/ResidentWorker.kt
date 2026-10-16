package dev.sebastiano.headroom.desktop.shell

import dev.sebastiano.headroom.desktop.DesktopNotificationLedger
import dev.sebastiano.headroom.shared.Headroom
import dev.sebastiano.headroom.shared.SettingsUi
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

internal class ResidentWorker(
    private val headroom: Headroom,
    ledger: DesktopNotificationLedger,
    notifications: MacNotifications,
    private val onIssue: (String?) -> Unit,
) {
    private val schedule = ResidentSchedule()
    private val resets =
        ResidentResetChecks(headroom.resetChecks::checkOutcome, ledger, notifications)
    private val otherNotifications = ResidentNotifications(headroom, ledger, notifications)
    private var syncMinutes: Int? = DEFAULT_SYNC_MINUTES

    suspend fun run() {
        val settings = headroom.settings.watch(::settingsChanged)
        val resetPlan =
            headroom.resetChecks.watchPlan { resets.replan(it, System.currentTimeMillis()) }
        try {
            resets.replan(headroom.resetChecks.plan(), System.currentTimeMillis())
            while (currentCoroutineContext().isActive) {
                cycle()
                delay(POLL_MILLIS)
            }
        } finally {
            settings.cancel()
            resetPlan.cancel()
        }
    }

    private fun settingsChanged(settings: SettingsUi) {
        syncMinutes = settings.syncMinutes
    }

    private suspend fun cycle() {
        try {
            val now = System.currentTimeMillis()
            resets.run(headroom.resetChecks.plan(), now)
            if (schedule.poll(now, syncMinutes)) headroom.refreshAll()
            currentCoroutineContext().ensureActive()
            otherNotifications.deliver(now)
            onIssue(null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            onIssue("Background sync is unavailable. Headroom will retry.")
        } catch (_: RuntimeException) {
            onIssue("Background sync could not finish. Headroom will retry.")
        }
    }

    private companion object {
        const val DEFAULT_SYNC_MINUTES = 15
        const val POLL_MILLIS = 15_000L
    }
}
