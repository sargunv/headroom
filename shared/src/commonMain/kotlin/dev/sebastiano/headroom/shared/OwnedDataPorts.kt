package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.data.account.SignInManager
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetEvent
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.model.SyncFrequency
import dev.sebastiano.headroom.model.ThemeMode
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.model.UsagePoint
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow

/**
 * The StateFlow is already cached by the storage graph; only cold reads need an owned collector.
 */
internal class OwnedAccountsRepository(
    private val inner: AccountsRepository,
    private val signInManager: SignInManager,
    private val scope: CoroutineScope,
) : AccountsRepository {
    override val accounts: StateFlow<List<AccountState>> = inner.accounts

    override suspend fun current(): List<AccountState> = scope.owned { inner.current() }

    override suspend fun addAccount(account: Account): Unit = scope.owned {
        inner.addAccount(account)
    }

    override suspend fun removeAccount(accountId: String): Unit = scope.owned {
        signInManager.signOut(accountId)
    }

    override suspend fun renameAccount(accountId: String, nickname: String?): Unit = scope.owned {
        inner.renameAccount(accountId, nickname)
    }

    override suspend fun relabelAccount(accountId: String, label: String): Unit = scope.owned {
        inner.relabelAccount(accountId, label)
    }

    override suspend fun reorderAccounts(orderedIds: List<String>): Unit = scope.owned {
        inner.reorderAccounts(orderedIds)
    }

    override suspend fun refresh(accountId: String?): Unit = scope.owned {
        inner.refresh(accountId)
    }

    override fun history(accountId: String, windowId: String): Flow<List<UsagePoint>> =
        scope.ownedFlow {
            inner.history(accountId, windowId)
        }
}

internal class OwnedSettingsRepository(
    private val inner: SettingsRepository,
    private val scope: CoroutineScope,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = scope.ownedFlow { inner.settings }

    override suspend fun setQuotaDisplay(display: QuotaDisplay): Unit = scope.owned {
        inner.setQuotaDisplay(display)
    }

    override suspend fun setSyncFrequency(frequency: SyncFrequency): Unit = scope.owned {
        inner.setSyncFrequency(frequency)
    }

    override suspend fun setTheme(theme: ThemeMode): Unit = scope.owned { inner.setTheme(theme) }

    override suspend fun setMotion(motion: MotionPreference): Unit = scope.owned {
        inner.setMotion(motion)
    }

    override suspend fun setPalette(palette: ThemePalette): Unit = scope.owned {
        inner.setPalette(palette)
    }

    override suspend fun setOverviewSort(sort: OverviewSort): Unit = scope.owned {
        inner.setOverviewSort(sort)
    }

    override suspend fun setRefreshShimmer(enabled: Boolean): Unit = scope.owned {
        inner.setRefreshShimmer(enabled)
    }

    override suspend fun setResetConfetti(enabled: Boolean): Unit = scope.owned {
        inner.setResetConfetti(enabled)
    }

    override suspend fun setResetIsland(enabled: Boolean): Unit = scope.owned {
        inner.setResetIsland(enabled)
    }

    override suspend fun setRedeemClaudeResets(enabled: Boolean): Unit = scope.owned {
        inner.setRedeemClaudeResets(enabled)
    }

    override suspend fun setResetExpiryReminders(enabled: Boolean): Unit = scope.owned {
        inner.setResetExpiryReminders(enabled)
    }
}

internal class OwnedAlertPreferences(
    private val inner: AlertPreferences,
    private val scope: CoroutineScope,
) : AlertPreferences {
    override fun isEnabled(accountId: String, window: QuotaWindow): Flow<Boolean> =
        scope.ownedFlow {
            inner.isEnabled(accountId, window)
        }

    override suspend fun setEnabled(accountId: String, windowId: String, enabled: Boolean): Unit =
        scope.owned {
            inner.setEnabled(accountId, windowId, enabled)
        }
}

internal class OwnedResetProvider(
    private val inner: ResetProvider,
    private val scope: CoroutineScope,
) : ResetProvider {
    override suspend fun availability(account: Account): ResetAvailability? = scope.owned {
        inner.availability(account)
    }

    override suspend fun redeem(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome = scope.owned { inner.redeem(account, poolId, attemptKey) }

    override suspend fun check(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome = scope.owned { inner.check(account, poolId, attemptKey) }

    override suspend fun askForMore(account: Account): AskOutcome = scope.owned {
        inner.askForMore(account)
    }
}

internal class OwnedResetEventLog(
    private val inner: ResetEventLog,
    private val scope: CoroutineScope,
) : ResetEventLog {
    override fun events(since: Instant): Flow<List<ResetEvent>> = scope.ownedFlow {
        inner.events(since)
    }

    override suspend fun redeemed(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): Unit = scope.owned { inner.redeemed(account, poolId, attemptKey) }
}

/** Defer even flow creation until ownership is established, and never emit across flow contexts. */
private fun <T> CoroutineScope.ownedFlow(source: () -> Flow<T>): Flow<T> = channelFlow {
    this@ownedFlow.owned { source().collect { send(it) } }
}
