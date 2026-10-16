package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetPolicy
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class HeadroomResetChecksTest {
    private val resetAt = Instant.parse("2026-10-15T12:00:00Z")
    private var now = resetAt - 60.seconds
    private val repository = ResetCheckAccounts()
    private val alerts = CheckAlerts()
    private val weekly = QuotaWindow("weekly", "Weekly", WindowKind.Weekly, 80.0, resetAt, 7.days)

    private fun state(window: QuotaWindow = weekly, fetchedAt: Instant = resetAt - 60.seconds) =
        AccountState(
            Account("a", Provider.Claude, "fixture@example.com"),
            QuotaSnapshot(Provider.Claude, "a", null, listOf(window), fetchedAt),
        )

    private fun service(scope: CoroutineScope) =
        HeadroomResetChecks(repository, alerts, { now }, scope)

    @Test
    fun `plans observe only real eligible windows and alert switches`() = runTest {
        val service = service(backgroundScope)
        assertTrue(service.plan().isEmpty())
        repository.accounts.value = listOf(state())
        val plan = service.plan()
        assertEquals(1, plan.size)
        assertEquals(resetAt.toEpochMilliseconds() + 10_000, plan.single().triggerAtEpochMillis)
        assertEquals(80.0, plan.single().usedBefore)
        alerts.enabled.value = false
        assertTrue(service.plan().isEmpty())
        alerts.enabled.value = true
        repository.accounts.value = listOf(state().copy(lastError = QuotaErrorKind.Auth))
        assertTrue(service.plan().isEmpty())
    }

    @Test
    fun `a five point drop confirms only after the scheduled check time`() = runTest {
        repository.accounts.value = listOf(state())
        val service = service(backgroundScope)
        val check = service.plan().single()
        repository.after = { listOf(state(weekly.copy(usedPercent = 75.0), now)) }
        assertNull(service.check(check))
        assertTrue(repository.refreshes.isEmpty())
        now = resetAt + 10.seconds
        val confirmed = assertNotNull(service.check(check))
        assertEquals(listOf("a"), repository.refreshes)
        assertEquals("a", confirmed.accountId)
        assertEquals("weekly", confirmed.windowId)
        assertEquals("reset:a/weekly/${resetAt.toEpochMilliseconds()}", confirmed.dedupeKey)
        assertEquals(75.0, confirmed.usedAfter)
    }

    @Test
    fun `five day shift confirms and rounding noise does not`() = runTest {
        repository.accounts.value = listOf(state())
        val service = service(backgroundScope)
        val check = service.plan().single()
        now = resetAt + 10.seconds
        repository.after = { listOf(state(weekly.copy(usedPercent = 75.1), now)) }
        assertNull(service.check(check))
        now += 1.seconds
        repository.after = { listOf(state(weekly.copy(resetsAt = resetAt + 5.days), now)) }
        assertNotNull(service.check(check))
    }

    @Test
    fun `errors expired sign-ins and stale snapshots never confirm`() = runTest {
        repository.accounts.value = listOf(state())
        val service = service(backgroundScope)
        val check = service.plan().single()
        now = resetAt + 10.seconds
        val fresh = state(weekly.copy(usedPercent = 0.0), now)
        for (kind in QuotaErrorKind.entries) {
            repository.after = { listOf(fresh.copy(lastError = kind)) }
            assertNull(service.check(check), "error $kind")
        }
        repository.after = {
            listOf(fresh.copy(snapshot = fresh.snapshot!!.copy(fetchedAt = resetAt - 60.seconds)))
        }
        assertNull(service.check(check))
        repository.after = { emptyList() }
        assertNull(service.check(check))
    }

    @Test
    fun `alerts are checked after fetching and informational windows never notify`() = runTest {
        repository.accounts.value = listOf(state())
        val service = service(backgroundScope)
        val check = service.plan().single()
        now = resetAt + 10.seconds
        repository.after = {
            alerts.enabled.value = false
            listOf(state(weekly.copy(usedPercent = 0.0), now))
        }
        assertNull(service.check(check))
        alerts.enabled.value = true
        repository.after = {
            listOf(state(weekly.copy(usedPercent = 0.0, isRecognised = false), now))
        }
        assertNull(service.check(check))
    }

    @Test
    fun `disabled auth expired missing and informational alerts never refresh`() = runTest {
        repository.accounts.value = listOf(state())
        val service = service(backgroundScope)
        val check = service.plan().single()
        now = resetAt + 10.seconds
        alerts.enabled.value = false
        assertNull(service.check(check))
        alerts.enabled.value = true
        repository.accounts.value = listOf(state().copy(lastError = QuotaErrorKind.Auth))
        assertNull(service.check(check))
        repository.accounts.value = emptyList()
        assertNull(service.check(check))
        repository.accounts.value = listOf(state(weekly.copy(isRecognised = false)))
        assertNull(service.check(check))
        repository.accounts.value = listOf(state(weekly.copy(id = "another-window")))
        assertNull(service.check(check))
        assertTrue(repository.refreshes.isEmpty(), "Impossible alerts must not contact providers")
    }

    @Test
    fun `outcome retries early checks network failures and unconfirmed resets`() = runTest {
        repository.accounts.value = listOf(state())
        val service = service(backgroundScope)
        val check = service.plan().single()
        assertEquals(ResetCheckOutcomeUi.Retry, service.checkOutcome(check))
        assertTrue(repository.refreshes.isEmpty())
        now = resetAt + 10.seconds
        repository.after = { listOf(state().copy(lastError = QuotaErrorKind.Network)) }
        assertEquals(ResetCheckOutcomeUi.Retry, service.checkOutcome(check))
        repository.after = { listOf(state(fetchedAt = now)) }
        assertEquals(ResetCheckOutcomeUi.Retry, service.checkOutcome(check))
        now += 1.seconds
        repository.after = { listOf(state(weekly.copy(usedPercent = 0.0), now)) }
        val confirmed = assertIs<ResetCheckOutcomeUi.Confirmed>(service.checkOutcome(check))
        assertEquals("a", confirmed.reset.accountId)
        assertEquals(0.0, confirmed.reset.usedAfter)
    }

    @Test
    fun `outcome discards disabled removed auth expired and ineligible checks before fetching`() =
        runTest {
            repository.accounts.value = listOf(state())
            val service = service(backgroundScope)
            val check = service.plan().single()
            now = resetAt + 10.seconds
            alerts.enabled.value = false
            assertEquals(ResetCheckOutcomeUi.Discard, service.checkOutcome(check))
            alerts.enabled.value = true
            repository.accounts.value = listOf(state().copy(lastError = QuotaErrorKind.Auth))
            assertEquals(ResetCheckOutcomeUi.Discard, service.checkOutcome(check))
            repository.accounts.value = emptyList()
            assertEquals(ResetCheckOutcomeUi.Discard, service.checkOutcome(check))
            repository.accounts.value = listOf(state(weekly.copy(isRecognised = false)))
            assertEquals(ResetCheckOutcomeUi.Discard, service.checkOutcome(check))
            repository.accounts.value = listOf(state(weekly.copy(id = "another-window")))
            assertEquals(ResetCheckOutcomeUi.Discard, service.checkOutcome(check))
            assertTrue(repository.refreshes.isEmpty())
        }

    @Test
    fun `outcome discards alerts turned off removed or auth expired during fetching`() = runTest {
        repository.accounts.value = listOf(state())
        val service = service(backgroundScope)
        val check = service.plan().single()
        now = resetAt + 10.seconds
        repository.after = {
            alerts.enabled.value = false
            listOf(state(weekly.copy(usedPercent = 0.0), now))
        }
        assertEquals(ResetCheckOutcomeUi.Discard, service.checkOutcome(check))
        alerts.enabled.value = true
        repository.after = { listOf(state().copy(lastError = QuotaErrorKind.Auth)) }
        assertEquals(ResetCheckOutcomeUi.Discard, service.checkOutcome(check))
        repository.accounts.value = listOf(state())
        repository.after = { emptyList() }
        assertEquals(ResetCheckOutcomeUi.Discard, service.checkOutcome(check))
        assertEquals(listOf("a", "a", "a"), repository.refreshes)
    }

    @Test
    fun `watch plan follows per account monthly switches and cancel stops callbacks`() = runTest {
        val monthly = weekly.copy(id = "monthly", kind = WindowKind.Monthly, length = 30.days)
        repository.accounts.value =
            listOf(
                state(monthly),
                state(monthly).copy(account = Account("b", Provider.Claude, "other@example.com")),
            )
        val plans = mutableListOf<List<ResetCheckUi>>()
        val watch = service(backgroundScope).watchPlan { plans += it }
        runCurrent()
        assertTrue(repository.accounts.subscriptionCount.value > 0)
        assertEquals(emptyList(), plans.last())
        alerts.setEnabled("a", "monthly", true)
        runCurrent()
        assertEquals(listOf("a"), plans.last().map { it.accountId })
        alerts.setEnabled("b", "monthly", true)
        runCurrent()
        assertEquals(listOf("a", "b"), plans.last().map { it.accountId })
        alerts.setEnabled("a", "monthly", false)
        runCurrent()
        assertEquals(listOf("b"), plans.last().map { it.accountId })
        watch.cancel()
        runCurrent()
        assertEquals(0, repository.accounts.subscriptionCount.value)
        val stoppedAt = plans.size
        alerts.setEnabled("b", "monthly", false)
        repository.accounts.value = emptyList()
        runCurrent()
        assertEquals(stoppedAt, plans.size)
    }
}

private class CheckAlerts : AlertPreferences {
    val enabled = MutableStateFlow(true)
    private val switches = MutableStateFlow(emptyMap<Pair<String, String>, Boolean>())

    override fun isEnabled(accountId: String, window: QuotaWindow): Flow<Boolean> =
        combine(enabled, switches) { enabled, switches ->
            enabled && (switches[accountId to window.id] ?: ResetPolicy.alertsByDefault(window))
        }

    override suspend fun setEnabled(accountId: String, windowId: String, enabled: Boolean) {
        switches.value += (accountId to windowId) to enabled
    }
}

private class ResetCheckAccounts : AccountsRepository {
    override val accounts = MutableStateFlow(emptyList<AccountState>())
    var after: () -> List<AccountState> = { accounts.value }
    val refreshes = mutableListOf<String>()

    override suspend fun current(): List<AccountState> = accounts.value

    override suspend fun refresh(accountId: String?) {
        refreshes += checkNotNull(accountId)
        accounts.value = after()
    }

    override suspend fun addAccount(account: Account) = Unit

    override suspend fun removeAccount(accountId: String) = Unit

    override suspend fun renameAccount(accountId: String, nickname: String?) = Unit

    override suspend fun relabelAccount(accountId: String, label: String) = Unit

    override suspend fun reorderAccounts(orderedIds: List<String>) = Unit

    override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
        flowOf(emptyList())
}
