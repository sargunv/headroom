package dev.sebastiano.headroom

import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sebastiano.headroom.appdata.RepositoryUsageHistory
import dev.sebastiano.headroom.appdata.ResetCenter
import dev.sebastiano.headroom.appdata.ResetHistory
import dev.sebastiano.headroom.appdata.UsageHistory
import dev.sebastiano.headroom.island.ResetIslandAccess
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.NoResets
import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.ResetAttemptMemory
import dev.sebastiano.headroom.model.ResetAttemptStore
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.prototype.PrototypeTools
import dev.sebastiano.headroom.signin.SignInController
import dev.sebastiano.headroom.signin.ZCodeSignIn
import dev.sebastiano.headroom.tile.TileAdder
import dev.sebastiano.headroom.tile.TilePreferences
import dev.sebastiano.headroom.ui.accounts.AccountsViewModel
import dev.sebastiano.headroom.ui.home.HomeViewModel
import dev.sebastiano.headroom.ui.settings.SettingsViewModel
import dev.sebastiano.headroom.ui.stats.StatsViewModel
import dev.sebastiano.headroom.widgets.WidgetPinner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone

/**
 * The original Compose presentation graph. Platform shells supply data and device adapters once;
 * every screen uses these same ViewModels and factories on Android and desktop.
 */
open class HeadroomUiGraph(
    val quotaRepository: QuotaRepository,
    val isDemo: StateFlow<Boolean>,
    /** True once the stored accounts have been read, so an empty list really means none. */
    val accountsLoaded: StateFlow<Boolean>,
    val alertPreferences: AlertPreferences,
    val clock: () -> Instant,
    val zone: TimeZone,
    val signInController: SignInController,
    val widgetPinner: WidgetPinner,
    val resetHistory: ResetHistory,
    /** Stores the name the user gives an account. Does nothing without the data layer. */
    val renameAccount: suspend (accountId: String, name: String?) -> Unit = { _, _ -> },
    /** Signs an account out and forgets it. Does nothing without the data layer. */
    val removeAccount: suspend (accountId: String) -> Unit = {},
    /** Stores the order the user put the accounts in. Does nothing without the data layer. */
    val reorderAccounts: suspend (orderedIds: List<String>) -> Unit = {},
    /** How often countdowns re-read the clock. Null turns it off, for tests with a fixed clock. */
    val tickInterval: Duration? = 1.minutes,
    /** Used or left, how often to sync in the background, and how the overview sorts accounts. */
    val settings: SettingsRepository = InMemorySettingsRepository(),
    /** The version name the settings screen shows. */
    val appVersion: String = "",
    /** The usage history the Stats tab is computed from. */
    val usageHistory: UsageHistory = RepositoryUsageHistory(quotaRepository),
    /** Where the stats are computed: off the main thread, as they read weeks of history. */
    val statsDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** The reset island: its accessibility service's state, and the Try button. */
    val resetIsland: ResetIslandAccess = ResetIslandAccess.Unavailable,
    resetProvider: ResetProvider = NoResets,
    /** Keeps the keys of unsettled redeem attempts across restarts. */
    resetAttempts: ResetAttemptStore = ResetAttemptStore.None,
    /** Where the resets are read in the background. */
    resetScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val tileAdder: TileAdder = TileAdder.Unavailable,
    /** What the tile's subtitle shows, or null without a tile. */
    val tileSettings: TilePreferences? = null,
    /** Debug builds' prototype tools; null in release builds. */
    val prototypes: PrototypeTools? = null,
    /** The demo accounts, so debug tools can show a scenario such as an expired sign-in. */
    val demoAccounts: FakeQuotaRepository? = null,
    /** The ZCode sign-in that Z.AI resets need; null without the data layer. */
    val zCodeSignIn: ZCodeSignIn? = null,
    /** The resets used and expired, for the Stats tab; redeems in Headroom are recorded here. */
    val resetEvents: ResetEventLog = ResetEventLog.None,
) {
    /** The resets of every account, read through the reset provider. */
    val resets: ResetCenter =
        ResetCenter(
            provider = resetProvider,
            accounts = quotaRepository.accounts,
            scope = resetScope,
            refreshUsage = { accountId -> quotaRepository.refresh(accountId) },
            recordRedeem = resetEvents::redeemed,
        )

    /** The keys of unsettled redeem attempts, so a retry never uses a second reset. */
    val resetMemory: ResetAttemptMemory = ResetAttemptMemory(clock, store = resetAttempts)

    val homeViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                repository = quotaRepository,
                alertPreferences = alertPreferences,
                clock = clock,
                isDemo = isDemo,
                accountsLoaded = accountsLoaded,
                resetHistory = resetHistory,
                tickInterval = tickInterval,
                savedStateHandle = presentationSavedStateHandle(),
                quotaDisplay = settings.settings.map { it.quotaDisplay }.distinctUntilChanged(),
                overviewSort = settings.settings.map { it.overviewSort }.distinctUntilChanged(),
                saveOverviewSort = settings::setOverviewSort,
            )
        }
    }

    val statsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            StatsViewModel(
                repository = quotaRepository,
                history = usageHistory,
                isDemo = isDemo,
                clock = clock,
                zone = zone,
                computeDispatcher = statsDispatcher,
                resetEvents = resetEvents,
            )
        }
    }

    val settingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SettingsViewModel(settings = settings, appVersion = appVersion) }
    }

    val accountsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AccountsViewModel(
                repository = quotaRepository,
                signInController = signInController,
                isDemo = isDemo,
                renameAccount = renameAccount,
                removeAccount = removeAccount,
                reorderAccounts = reorderAccounts,
            )
        }
    }
}

/** A plain desktop owner has no saved-state registry; Android keeps its normal restoration. */
private fun CreationExtras.presentationSavedStateHandle(): SavedStateHandle =
    if (
        this[SAVED_STATE_REGISTRY_OWNER_KEY] != null &&
            this[VIEW_MODEL_STORE_OWNER_KEY] != null &&
            this[ViewModelProvider.VIEW_MODEL_KEY] != null
    ) {
        createSavedStateHandle()
    } else {
        inMemoryPresentationSavedStateHandle()
    }

// Both lifecycle constructors are test-visible because they cannot restore process state. A plain
// desktop owner has no registry to restore; registry-backed owners always use the branch above.
@Suppress("VisibleForTests")
private fun inMemoryPresentationSavedStateHandle(): SavedStateHandle = SavedStateHandle()
