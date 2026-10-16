package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.data.account.SignInManager
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.ResetAttemptMemory
import dev.sebastiano.headroom.model.ResetAttemptStore
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.SettingsRepository
import dev.sebastiano.headroom.signin.SignInController
import dev.sebastiano.headroom.signin.ZCodeSignIn
import kotlinx.coroutines.CoroutineScope

/**
 * The real business ports of one [Headroom], without demo substitution or UI mapping. Native Kotlin
 * UI graphs can apply their own demo repository and keep their existing ViewModels.
 *
 * Suspend operations and cold storage flow collections belong to Headroom's lifetime as well as the
 * caller's. [Headroom.closeAndJoin] cancels and joins them before closing storage; new work after
 * close is cancelled without reaching storage. The accounts StateFlow is the repository's original
 * in-memory state, not another asynchronously maintained copy.
 *
 * Sign-in controllers launch only in Headroom's scope. [resetAttempts] and [resetMemory] are the
 * same synchronous, file-backed attempt state used by the facade, not database handles. Clear UI
 * graphs before closing Headroom and do not retain these ports for a subsequent session.
 */
public class HeadroomData
internal constructor(
    parts: HeadroomParts,
    signInManager: SignInManager,
    public val signInController: SignInController,
    public val zCodeSignIn: ZCodeSignIn?,
    scope: CoroutineScope,
) {
    /** Removing through this port signs out too, including the account's ZCode credential. */
    public val repository: AccountsRepository =
        OwnedAccountsRepository(parts.repository, signInManager, scope)

    public val settings: SettingsRepository = OwnedSettingsRepository(parts.settings, scope)
    public val alertPreferences: AlertPreferences = OwnedAlertPreferences(parts.alerts, scope)
    public val resetProvider: ResetProvider = OwnedResetProvider(parts.resetProvider, scope)
    public val resetEvents: ResetEventLog = OwnedResetEventLog(parts.resetEvents, scope)
    public val resetAttempts: ResetAttemptStore = parts.resetAttempts
    public val resetMemory: ResetAttemptMemory = parts.resetMemory

    /** Names the account; a blank or null name removes the nickname. */
    public suspend fun renameAccount(accountId: String, name: String?) {
        repository.renameAccount(accountId, name)
    }

    /** Returns only after credentials and the stored account have been removed. */
    public suspend fun removeAccount(accountId: String) {
        repository.removeAccount(accountId)
    }

    public suspend fun reorderAccounts(orderedIds: List<String>) {
        repository.reorderAccounts(orderedIds)
    }
}
