package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.InMemoryTokenStore
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.auth.ZCodeCredential
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttempt
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAttemptMemory
import dev.sebastiano.headroom.model.ResetAttemptStore
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetEvent
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.ResetPolicy
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.model.SyncFrequency
import dev.sebastiano.headroom.model.ThemeMode
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.model.UsagePoint
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.signin.BrowserSession
import dev.sebastiano.headroom.signin.DeviceSession
import dev.sebastiano.headroom.signin.SignInKind
import dev.sebastiano.headroom.signin.SignInState
import dev.sebastiano.headroom.signin.SignInSteps
import dev.sebastiano.headroom.signin.ZCodeSignInState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class HeadroomDataTest {
    @Test
    fun `raw accounts retain their state flow and committed read freshness`() = runTest {
        val fixture = Fixture(this)
        val raw = fixture.headroom.data.repository
        assertSame(fixture.repository.accounts, raw.accounts)
        val committed = DemoData.accounts(NOW)
        fixture.repository.committed = committed
        assertSame(committed, raw.current())
        assertTrue(raw.accounts.value.isEmpty(), "current must not be reconstructed from the flow")
        fixture.repository.accounts.value = committed
        assertSame(committed, raw.accounts.value)
        val points = listOf(UsagePoint(NOW, 42.0))
        fixture.repository.points = points
        assertSame(points, raw.history("a", "weekly").first())
        fixture.headroom.closeAndJoin()
    }

    @Test
    fun `screen actions and facade share accounts settings and alerts`() = runTest {
        val fixture = Fixture(this)
        val headroom = fixture.headroom
        val data = headroom.data
        fixture.repository.accounts.value = DemoData.accounts(NOW).take(2)
        val ids = data.repository.accounts.value.map { it.account.id }
        var overview: OverviewUi? = null
        val watch = headroom.watchOverview { overview = it }
        data.renameAccount(ids.first(), "Work")
        data.reorderAccounts(ids.reversed())
        data.settings.setQuotaDisplay(QuotaDisplay.Left)
        val window =
            data.repository.accounts.value.last().snapshot!!.windows.first {
                ResetPolicy.canAlert(it)
            }
        data.alertPreferences.setEnabled(ids.first(), window.id, false)
        runCurrent()
        val shown = checkNotNull(overview)
        assertEquals(ids.reversed(), shown.yourOrder)
        assertEquals("Work", shown.accounts.first { it.id == ids.first() }.title)
        assertEquals("left", shown.display)
        assertFalse(
            shown.accounts
                .first { it.id == ids.first() }
                .windows
                .first { it.id == window.id }
                .alertOn
        )
        headroom.accounts.rename(ids.first(), "Personal")
        headroom.settings.setQuotaDisplay("used")
        runCurrent()
        assertEquals("Personal", data.repository.accounts.value.last().account.nickname)
        assertEquals(QuotaDisplay.Used, data.settings.settings.first().quotaDisplay)
        watch.cancel()
        headroom.closeAndJoin()
    }

    @Test
    fun `raw sign in and facade operate the same controller and removal clears both credentials`() =
        runTest {
            val fixture = Fixture(this)
            val headroom = fixture.headroom
            var shown: SignInUi? = null
            headroom.signIn.watch { shown = it }
            headroom.data.signInController.start(Provider.ZAi)
            runCurrent()
            assertTrue(shown is SignInUi.ApiKey)
            headroom.signIn.submitApiKey("fake-api-key")
            runCurrent()
            assertTrue(headroom.data.signInController.state.value is SignInState.Success)
            val account = headroom.data.repository.accounts.value.single().account
            assertTrue(fixture.tokens.load(account.id) != null)
            headroom.resets.zCode.start(account.id)
            runCurrent()
            fixture.zCodeTokens.complete(
                TokenSet(Provider.ZAi, CredentialKind.OAuth, "fake-zcode", null, null)
            )
            runCurrent()
            assertTrue(fixture.tokens.load(ZCodeCredential.idFor(account.id)) != null)
            headroom.data.repository.removeAccount(account.id)
            assertNull(fixture.tokens.load(account.id))
            assertNull(fixture.tokens.load(ZCodeCredential.idFor(account.id)))
            assertTrue(headroom.data.repository.accounts.value.isEmpty())
            headroom.signIn.cancel()
            assertEquals(SignInState.Idle, headroom.data.signInController.state.value)
            headroom.closeAndJoin()
        }

    @Test
    fun `raw ZCode controller and facade share observable state`() = runTest {
        val fixture = Fixture(this)
        val headroom = fixture.headroom
        val controller = checkNotNull(headroom.data.zCodeSignIn)
        var shown: ZCodeUi? = null
        headroom.resets.zCode.watch { shown = it }
        controller.start("a")
        runCurrent()
        assertEquals(ZCodeUi.Waiting("https://example.test/zcode", 1), shown)
        headroom.resets.zCode.start("a")
        runCurrent()
        assertEquals(
            ZCodeSignInState.Waiting("a", "https://example.test/zcode", 2),
            controller.state.value,
        )
        controller.cancel()
        runCurrent()
        assertEquals(ZCodeUi.Idle, shown)
        headroom.closeAndJoin()
    }

    @Test
    fun `reset memory and store are shared with the facade`() = runTest {
        val fixture = Fixture(this)
        val data = fixture.headroom.data
        assertSame(fixture.memory, data.resetMemory)
        assertSame(fixture.attempts, data.resetAttempts)
        val state = DemoData.accounts(NOW).first { it.account.provider == Provider.Codex }
        fixture.repository.accounts.value =
            listOf(
                state.copy(
                    snapshot =
                        state.snapshot!!.copy(
                            resets =
                                ResetAvailability(
                                    listOf(
                                        ResetPool(
                                            "weekly",
                                            "Weekly",
                                            1,
                                            ResetScope.of(WindowKind.Weekly),
                                        )
                                    )
                                )
                        )
                )
            )
        data.resetMemory.remember(state.account, "weekly", KEY)
        assertEquals(KEY.value, data.resetAttempts.load().single().key)
        fixture.headroom.resets.redeem.start(state.account.id, askForMore = false)
        runCurrent()
        fixture.headroom.resets.redeem.confirm()
        runCurrent()
        assertEquals(KEY, fixture.resets.lastAttemptKey)
        fixture.headroom.closeAndJoin()
    }

    @Test
    fun `every externally awaited port operation cancels and finishes cleanup before storage closes`() =
        runTest {
            for ((name, action) in operations()) {
                val fixture = Fixture(this)
                runCurrent()
                val probe = fixture.probe
                probe.block = true
                val writing = launch { action(fixture.headroom.data) }
                runCurrent()
                assertTrue(probe.entered.isCompleted, name)
                val closing = launch { fixture.headroom.closeAndJoin() }
                runCurrent()
                assertTrue(closing.isActive, name)
                assertTrue(probe.events.isEmpty(), name)
                probe.cleanup.complete(Unit)
                runCurrent()
                assertEquals(listOf("operation stopped", "closed"), probe.events, name)
                assertTrue(writing.isCancelled, name)
                val calls = probe.calls
                val afterClose = launch { action(fixture.headroom.data) }
                runCurrent()
                assertTrue(afterClose.isCancelled, name)
                assertEquals(calls, probe.calls, "$name must not reach closed storage")
            }
        }

    @Test
    fun `cold storage flows are owned including flow creation and cleanup`() = runTest {
        val flows: List<(HeadroomData) -> Flow<*>> =
            listOf(
                { it.repository.history("a", "weekly") },
                { it.settings.settings },
                { it.alertPreferences.isEnabled("a", WINDOW) },
                { it.resetEvents.events(NOW) },
            )
        for (flow in flows) {
            val fixture = Fixture(this)
            runCurrent()
            fixture.probe.block = true
            fixture.reads.block = true
            val reading = launch { flow(fixture.headroom.data).collect {} }
            runCurrent()
            assertTrue(fixture.reads.entered.isCompleted)
            val closing = launch { fixture.headroom.closeAndJoin() }
            runCurrent()
            assertTrue(closing.isActive)
            assertTrue(fixture.probe.events.isEmpty())
            fixture.reads.cleanup.complete(Unit)
            runCurrent()
            assertTrue(reading.isCancelled)
            assertEquals(listOf("operation stopped", "closed"), fixture.probe.events)
            val calls = fixture.reads.calls
            val afterClose = launch { flow(fixture.headroom.data).collect {} }
            runCurrent()
            assertTrue(afterClose.isCancelled)
            assertEquals(calls, fixture.reads.calls)
        }
    }

    @Test
    fun `cancelling an external caller also cancels its owned operation`() = runTest {
        val fixture = Fixture(this)
        runCurrent()
        fixture.probe.block = true
        val writing = launch { fixture.headroom.data.settings.setTheme(ThemeMode.Dark) }
        runCurrent()
        writing.cancel()
        fixture.probe.cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("operation stopped"), fixture.probe.events)
        fixture.headroom.closeAndJoin()
    }

    private fun operations(): List<Pair<String, suspend (HeadroomData) -> Unit>> =
        listOf(
            "add" to { it.repository.addAccount(ACCOUNT) },
            "remove repository" to { it.repository.removeAccount("a") },
            "rename repository" to { it.repository.renameAccount("a", "Work") },
            "relabel" to { it.repository.relabelAccount("a", "new label") },
            "reorder repository" to { it.repository.reorderAccounts(listOf("a")) },
            "refresh" to { it.repository.refresh() },
            "current" to { it.repository.current() },
            "remove action" to { it.removeAccount("a") },
            "rename action" to { it.renameAccount("a", "Work") },
            "reorder action" to { it.reorderAccounts(listOf("a")) },
            "display" to { it.settings.setQuotaDisplay(QuotaDisplay.Left) },
            "frequency" to { it.settings.setSyncFrequency(SyncFrequency.Hour1) },
            "theme" to { it.settings.setTheme(ThemeMode.Dark) },
            "motion" to { it.settings.setMotion(MotionPreference.Reduced) },
            "palette" to { it.settings.setPalette(ThemePalette.Coral) },
            "sort" to { it.settings.setOverviewSort(OverviewSort.MostUsedFirst) },
            "shimmer" to { it.settings.setRefreshShimmer(false) },
            "confetti" to { it.settings.setResetConfetti(false) },
            "island" to { it.settings.setResetIsland(false) },
            "claude resets" to { it.settings.setRedeemClaudeResets(false) },
            "expiry" to { it.settings.setResetExpiryReminders(false) },
            "alert" to { it.alertPreferences.setEnabled("a", "weekly", false) },
            "availability" to { it.resetProvider.availability(ACCOUNT) },
            "redeem" to { it.resetProvider.redeem(ACCOUNT, "weekly", KEY) },
            "check" to { it.resetProvider.check(ACCOUNT, "weekly", KEY) },
            "ask" to { it.resetProvider.askForMore(ACCOUNT) },
            "record" to { it.resetEvents.redeemed(ACCOUNT, "weekly", KEY) },
        )
}

private val NOW = Instant.parse("2026-09-27T12:32:00Z")
private val ACCOUNT = Account("a", Provider.ZAi, "fixture")
private val KEY = ResetAttemptKey("fixture-attempt")
private val WINDOW = QuotaWindow("weekly", "Weekly", WindowKind.Weekly, 42.0, null, null)

private class Probe(val events: MutableList<String> = mutableListOf()) {
    var block = false
    var calls = 0
    val entered = CompletableDeferred<Unit>()
    val cleanup = CompletableDeferred<Unit>()

    suspend fun work() {
        calls++
        if (!block) return
        entered.complete(Unit)
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                cleanup.await()
                events += "operation stopped"
            }
        }
    }
}

private class Fixture(scope: TestScope) {
    val probe = Probe()
    val reads = Probe(probe.events)
    val tokens = InMemoryTokenStore()
    val repository = RawAccounts(probe, reads)
    val resets = RawResets(probe)
    val attempts =
        object : ResetAttemptStore {
            var values = emptyList<ResetAttempt>()

            override fun load(): List<ResetAttempt> = values

            override fun save(attempts: List<ResetAttempt>) {
                values = attempts
            }
        }
    val memory = ResetAttemptMemory(clock = { NOW }, store = attempts)
    val zCodeTokens = CompletableDeferred<TokenSet>()
    val headroom =
        Headroom(
            HeadroomParts(
                repository = repository,
                tokenStore = tokens,
                signInSteps = RawApiKeySteps,
                settings = RawSettings(probe, reads),
                alerts = RawAlerts(probe, reads),
                resetProvider = resets,
                resetEvents = RawEvents(probe, reads),
                resetAttempts = attempts,
                resetMemory = memory,
                startZCode = {
                    object : DeviceSession {
                        override val userCode: String = "fixture"
                        override val verificationUrl: String = "https://example.test/zcode"

                        override suspend fun awaitTokens(): TokenSet = zCodeTokens.await()
                    }
                },
                clock = { NOW },
                onClose = { probe.events += "closed" },
            ),
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(scope.testScheduler)),
        )
}

private class RawAccounts(private val probe: Probe, private val reads: Probe) : AccountsRepository {
    override val accounts = MutableStateFlow(emptyList<AccountState>())
    var committed: List<AccountState>? = null
    var points = emptyList<UsagePoint>()

    override suspend fun current(): List<AccountState> {
        probe.work()
        return committed ?: accounts.value
    }

    override suspend fun addAccount(account: Account) {
        probe.work()
        accounts.value += AccountState(account, null)
    }

    override suspend fun removeAccount(accountId: String) {
        probe.work()
        accounts.value = accounts.value.filterNot { it.account.id == accountId }
    }

    override suspend fun renameAccount(accountId: String, nickname: String?) {
        probe.work()
        accounts.value =
            accounts.value.map {
                if (it.account.id == accountId)
                    it.copy(account = it.account.copy(nickname = nickname))
                else it
            }
    }

    override suspend fun relabelAccount(accountId: String, label: String) {
        probe.work()
    }

    override suspend fun reorderAccounts(orderedIds: List<String>) {
        probe.work()
        accounts.value = accounts.value.sortedBy { orderedIds.indexOf(it.account.id) }
    }

    override suspend fun refresh(accountId: String?) {
        probe.work()
    }

    override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> {
        check(!reads.block || !reads.cleanup.isCompleted) { "Flow created against closed storage" }
        return flow {
            reads.work()
            emit(points)
        }
    }
}

private class RawSettings(private val probe: Probe, private val reads: Probe) : SettingsRepository {
    private val inner = InMemorySettingsRepository()
    override val settings: Flow<AppSettings>
        get() = flow {
            reads.work()
            emitAll(inner.settings)
        }

    override suspend fun setQuotaDisplay(display: QuotaDisplay) {
        probe.work()
        inner.setQuotaDisplay(display)
    }

    override suspend fun setSyncFrequency(frequency: SyncFrequency) {
        probe.work()
        inner.setSyncFrequency(frequency)
    }

    override suspend fun setTheme(theme: ThemeMode) {
        probe.work()
        inner.setTheme(theme)
    }

    override suspend fun setMotion(motion: MotionPreference) {
        probe.work()
        inner.setMotion(motion)
    }

    override suspend fun setPalette(palette: ThemePalette) {
        probe.work()
        inner.setPalette(palette)
    }

    override suspend fun setOverviewSort(sort: OverviewSort) {
        probe.work()
        inner.setOverviewSort(sort)
    }

    override suspend fun setRefreshShimmer(enabled: Boolean) {
        probe.work()
        inner.setRefreshShimmer(enabled)
    }

    override suspend fun setResetConfetti(enabled: Boolean) {
        probe.work()
        inner.setResetConfetti(enabled)
    }

    override suspend fun setResetIsland(enabled: Boolean) {
        probe.work()
        inner.setResetIsland(enabled)
    }

    override suspend fun setRedeemClaudeResets(enabled: Boolean) {
        probe.work()
        inner.setRedeemClaudeResets(enabled)
    }

    override suspend fun setResetExpiryReminders(enabled: Boolean) {
        probe.work()
        inner.setResetExpiryReminders(enabled)
    }
}

private class RawAlerts(private val probe: Probe, private val reads: Probe) : AlertPreferences {
    private val enabled = MutableStateFlow(true)

    override fun isEnabled(accountId: String, window: QuotaWindow): Flow<Boolean> = flow {
        reads.work()
        emitAll(enabled)
    }

    override suspend fun setEnabled(accountId: String, windowId: String, enabled: Boolean) {
        probe.work()
        this.enabled.value = enabled
    }
}

private class RawResets(private val probe: Probe) : ResetProvider {
    var lastAttemptKey: ResetAttemptKey? = null

    override suspend fun availability(account: Account): ResetAvailability? {
        probe.work()
        return null
    }

    override suspend fun redeem(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome {
        probe.work()
        lastAttemptKey = attemptKey
        return RedeemOutcome.Unsupported
    }

    override suspend fun check(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome {
        probe.work()
        return RedeemOutcome.Unconfirmed
    }

    override suspend fun askForMore(account: Account): AskOutcome {
        probe.work()
        return AskOutcome.Unsupported
    }
}

private class RawEvents(private val probe: Probe, private val reads: Probe) : ResetEventLog {
    override fun events(since: Instant): Flow<List<ResetEvent>> = flow {
        reads.work()
        emit(emptyList())
    }

    override suspend fun redeemed(account: Account, poolId: String, attemptKey: ResetAttemptKey) {
        probe.work()
    }
}

private object RawApiKeySteps : SignInSteps {
    override fun kindOf(provider: Provider): SignInKind = SignInKind.ApiKey

    override suspend fun startBrowser(provider: Provider): BrowserSession = error("unused")

    override suspend fun startDeviceCode(provider: Provider): DeviceSession = error("unused")

    override fun apiKeyTokens(provider: Provider, key: String): TokenSet =
        TokenSet(provider, CredentialKind.ApiKey, key, null, null)
}
