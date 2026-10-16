package dev.sebastiano.headroom.desktop.shell

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import dev.sebastiano.headroom.HeadroomUiGraph
import dev.sebastiano.headroom.appdata.DemoAwareResetEvents
import dev.sebastiano.headroom.appdata.DemoAwareResetHistory
import dev.sebastiano.headroom.appdata.DemoAwareUsageHistory
import dev.sebastiano.headroom.appdata.DemoModeQuotaRepository
import dev.sebastiano.headroom.appdata.DemoResetEvents
import dev.sebastiano.headroom.appdata.DemoResetHistory
import dev.sebastiano.headroom.appdata.DemoUsageHistory
import dev.sebastiano.headroom.appdata.HistoryResetHistory
import dev.sebastiano.headroom.appdata.RealOrDemoResetProvider
import dev.sebastiano.headroom.appdata.RepositoryUsageHistory
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.NoResets
import dev.sebastiano.headroom.shared.Headroom
import dev.sebastiano.headroom.ui.accounts.AccountsViewModel
import dev.sebastiano.headroom.ui.home.HomeViewModel
import dev.sebastiano.headroom.ui.platform.UiBackDispatcher
import dev.sebastiano.headroom.ui.settings.SettingsViewModel
import dev.sebastiano.headroom.ui.stats.StatsViewModel
import dev.sebastiano.headroom.widgets.WidgetPinner
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.datetime.TimeZone

/** Platform assembly only: all presentation and factories come from the original shared UI. */
internal class DesktopPresentation(
    headroom: Headroom,
    parentScope: CoroutineScope,
    simulatedLatency: Duration = 900.milliseconds,
    clock: () -> Instant = Clock.System::now,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    main: CoroutineDispatcher = Dispatchers.Main,
    compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModelStoreOwner {
    private val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(lifetime + main)
    private val data = headroom.data
    private val real = data.repository
    private val demo = FakeQuotaRepository(clock)
    private val repository = DemoModeQuotaRepository(real, demo, scope, simulatedLatency)
    private val resetEvents =
        DemoAwareResetEvents(repository.isDemo, data.resetEvents, DemoResetEvents(clock))
    private var closed = false

    override val viewModelStore = ViewModelStore()
    val backDispatcher = UiBackDispatcher()

    val graph =
        HeadroomUiGraph(
            quotaRepository = repository,
            isDemo = repository.isDemo,
            accountsLoaded = repository.isLoaded,
            alertPreferences = data.alertPreferences,
            clock = clock,
            zone = zone,
            signInController = data.signInController,
            widgetPinner = WidgetPinner { false },
            resetHistory =
                DemoAwareResetHistory(
                    repository.isDemo,
                    HistoryResetHistory(real),
                    DemoResetHistory,
                ),
            renameAccount = data::renameAccount,
            removeAccount = data::removeAccount,
            reorderAccounts = data::reorderAccounts,
            settings = data.settings,
            appVersion = "1.2.0",
            usageHistory =
                DemoAwareUsageHistory(
                    repository.isDemo,
                    RepositoryUsageHistory(real),
                    DemoUsageHistory(DemoResetHistory, clock, zone),
                ),
            statsDispatcher = compute,
            resetProvider =
                RealOrDemoResetProvider(
                    isReal = { account -> real.accounts.value.any { it.account.id == account.id } },
                    real = data.resetProvider,
                    demo = NoResets,
                ),
            resetAttempts = data.resetAttempts,
            resetScope = scope,
            demoAccounts = demo,
            zCodeSignIn = data.zCodeSignIn,
            resetEvents = resetEvents,
        )

    val homeViewModel =
        ViewModelProvider.create(this, graph.homeViewModelFactory)[HomeViewModel::class]
    val accountsViewModel =
        ViewModelProvider.create(this, graph.accountsViewModelFactory)[AccountsViewModel::class]
    val settingsViewModel =
        ViewModelProvider.create(this, graph.settingsViewModelFactory)[SettingsViewModel::class]
    val statsViewModel =
        ViewModelProvider.create(this, graph.statsViewModelFactory)[StatsViewModel::class]

    suspend fun close() {
        if (closed) return
        closed = true
        val jobs =
            listOf(homeViewModel, accountsViewModel, settingsViewModel, statsViewModel).mapNotNull {
                it.viewModelScope.coroutineContext[Job]
            }
        viewModelStore.clear()
        lifetime.cancelAndJoin()
        jobs.joinAll()
    }
}
