package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.AuthMethods
import dev.sebastiano.headroom.data.HeadroomStorage
import dev.sebastiano.headroom.data.account.AccountQuotaFetcher
import dev.sebastiano.headroom.data.account.SyncedResetProvider
import dev.sebastiano.headroom.data.prefs.FileAttemptTargetStore
import dev.sebastiano.headroom.data.prefs.FileResetAttemptStore
import dev.sebastiano.headroom.quota.QuotaFetchers
import dev.sebastiano.headroom.quota.ResetClients
import dev.sebastiano.headroom.signin.AuthSignInSteps
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

/** The shared wiring for iOS and desktop, with platform-owned files and lifetime. */
internal fun createHeadroom(
    directory: String,
    secrets: SecureValueStore,
    storage: HeadroomStorage,
    scope: CoroutineScope,
): Headroom {
    val folder = directory.toPath()
    val tokenStore = SecureTokenStore(secrets)
    val authMethods = AuthMethods()
    val clients =
        ResetClients.create(
            zAiReachedUse = FileAttemptTargetStore(folder / "zai-reached-use.json"),
            grokPinnedTokens = FileAttemptTargetStore(folder / "grok-pinned-tokens.json"),
        )
    val fetcher =
        AccountQuotaFetcher(
            authMethods.credentialProvider(tokenStore),
            QuotaFetchers.create(),
            clients,
        )
    val repository = storage.accounts(fetcher::fetch, Clock.System::now, scope)
    val parts =
        HeadroomParts(
            repository = repository,
            tokenStore = tokenStore,
            signInSteps = AuthSignInSteps(authMethods),
            settings = storage.settings,
            alerts = storage.alertPreferences,
            resetEvents = storage.resetEvents(Clock.System::now),
            resetProvider =
                SyncedResetProvider(
                    accounts = { repository.current() },
                    settings = { storage.settings.settings.first() },
                    fetcher = fetcher,
                    clients = clients,
                ),
            resetAttempts = FileResetAttemptStore(folder / "reset-attempts.json"),
            startZCode = { AuthSignInSteps(authMethods).startZCode() },
            onClose = storage::close,
        )
    return Headroom(parts, scope)
}
