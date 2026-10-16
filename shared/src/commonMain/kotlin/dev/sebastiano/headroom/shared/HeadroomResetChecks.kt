package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.data.reset.ResetAlarmPlanner
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AlertPreferences
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetPolicy
import dev.sebastiano.headroom.model.WindowKind
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/** A scheduled fetch, not permission to post a notification. Keep its baseline across retries. */
public data class ResetCheckUi(
    public val accountId: String,
    public val windowId: String,
    public val triggerAtEpochMillis: Long,
    public val expectedResetAtEpochMillis: Long,
    public val usedBefore: Double,
)

/** A real reset confirmed by a successful fresh fetch, with its alert switch still enabled. */
public data class ConfirmedResetUi(
    /** Stable for this occurrence. The platform records it durably only after posting succeeds. */
    public val dedupeKey: String,
    public val accountId: String,
    public val windowId: String,
    public val accountName: String,
    public val monthly: Boolean,
    public val usedBefore: Double,
    public val usedAfter: Double,
    public val nextResetAtEpochMillis: Long?,
)

/** Whether a scheduled check can notify, should be retried, or must leave the platform's queue. */
public sealed interface ResetCheckOutcomeUi {
    public data class Confirmed(public val reset: ConfirmedResetUi) : ResetCheckOutcomeUi

    /**
     * Not due yet, a fetch failed, or fresh usage has not confirmed the reset. Use bounded retries.
     */
    public data object Retry : ResetCheckOutcomeUi

    /** Removed account/window, expired sign-in, disabled alert, or a window that cannot alert. */
    public data object Discard : ResetCheckOutcomeUi
}

/**
 * Desktop reset checks over the real repository. Scheduling, retries and durable notification
 * dedupe belong to the platform. Demo accounts are never planned, refreshed or notified here.
 */
public class HeadroomResetChecks
internal constructor(
    private val repository: AccountsRepository,
    private val alerts: AlertPreferences,
    private val clock: () -> Instant,
    private val scope: CoroutineScope,
) {
    /** Replans when real accounts or their alert switches change, until [Watch.cancel]. */
    public fun watchPlan(onChange: (List<ResetCheckUi>) -> Unit): Watch =
        scope.watch(
            combine(
                    repository.loadedAccounts(),
                    AlertStates.of(repository.loadedAccounts(), alerts),
                ) { accounts, switches ->
                    planned(accounts, switches)
                }
                .distinctUntilChanged(),
            onChange,
        )

    /**
     * Future checks for real accounts. Retain due checks until checked, including across retries.
     */
    public suspend fun plan(): List<ResetCheckUi> = scope.owned {
        val accounts = repository.current()
        planned(accounts, AlertStates.of(flowOf(accounts), alerts).first())
    }

    /**
     * Compatibility form of [checkOutcome]. Null means no notification; use [checkOutcome] to
     * distinguish retryable checks from ones that must be discarded.
     */
    public suspend fun check(check: ResetCheckUi): ConfirmedResetUi? =
        (checkOutcome(check) as? ResetCheckOutcomeUi.Confirmed)?.reset

    /**
     * Checks eligibility before contacting the provider, then refreshes and checks it again. Only
     * successful fresh usage can confirm a reset. The platform owns retry timing, limits and
     * durable dedupe, retaining the original [check] baseline while retrying.
     */
    public suspend fun checkOutcome(check: ResetCheckUi): ResetCheckOutcomeUi = scope.owned {
        checkReset(check)
    }

    private suspend fun checkReset(check: ResetCheckUi): ResetCheckOutcomeUi {
        val started = clock()
        val before =
            repository.current().firstOrNull { it.account.id == check.accountId }
                ?: return ResetCheckOutcomeUi.Discard
        if (eligibleWindow(before, check) == null) return ResetCheckOutcomeUi.Discard
        if (started.toEpochMilliseconds() < check.triggerAtEpochMillis)
            return ResetCheckOutcomeUi.Retry
        repository.refresh(check.accountId)
        val accounts = repository.current()
        val after =
            accounts.firstOrNull { it.account.id == check.accountId }
                ?: return ResetCheckOutcomeUi.Discard
        val window = eligibleWindow(after, check) ?: return ResetCheckOutcomeUi.Discard
        if (!ConfirmedResetPolicy.hasReset(check, before, after, window, started))
            return ResetCheckOutcomeUi.Retry
        val several = accounts.count { it.account.provider == after.account.provider } > 1
        return ResetCheckOutcomeUi.Confirmed(
            ConfirmedResetUi(
                dedupeKey =
                    "reset:${check.accountId}/${check.windowId}/${check.expectedResetAtEpochMillis}",
                accountId = check.accountId,
                windowId = check.windowId,
                accountName =
                    if (several) "${after.account.name} \u00b7 ${after.account.label}"
                    else after.account.name,
                monthly = window.kind == WindowKind.Monthly,
                usedBefore = check.usedBefore,
                usedAfter = window.usedPercent,
                nextResetAtEpochMillis = window.resetsAt?.toEpochMilliseconds(),
            )
        )
    }

    private suspend fun eligibleWindow(state: AccountState, check: ResetCheckUi): QuotaWindow? {
        if (state.isSignInExpired) return null
        val window = state.snapshot?.windows?.firstOrNull { it.id == check.windowId } ?: return null
        if (!ResetPolicy.canAlert(window)) return null
        // Repeat after fetching: a notification's alert may have been switched off in flight.
        return window.takeIf { alerts.isEnabled(check.accountId, window).first() }
    }

    private fun planned(accounts: List<AccountState>, switches: AlertStates): List<ResetCheckUi> =
        ResetAlarmPlanner.plan(accounts, clock()) { accountId, window ->
                switches.isOn(accountId, window)
            }
            .map { alarm ->
                ResetCheckUi(
                    alarm.accountId,
                    alarm.windowId,
                    alarm.triggerAt.toEpochMilliseconds(),
                    alarm.expectedResetAt.toEpochMilliseconds(),
                    alarm.usedBefore,
                )
            }
}

/** Pure confirmation policy: the same thresholds as Android, with explicit fetch validity. */
internal object ConfirmedResetPolicy {
    private const val MIN_DROP_POINTS = 5.0
    private val MIN_RESET_SHIFT = 5.days

    fun hasReset(
        check: ResetCheckUi,
        before: AccountState,
        after: AccountState,
        window: QuotaWindow,
        started: Instant,
    ): Boolean {
        val fetchedAt = after.snapshot?.fetchedAt ?: return false
        if (after.lastError != null || after.isSignInExpired || after.isRefreshing) return false
        if (!ResetPolicy.canAlert(window)) return false
        if (fetchedAt.toEpochMilliseconds() < started.toEpochMilliseconds()) return false
        if (fetchedAt.toEpochMilliseconds() < check.expectedResetAtEpochMillis) return false
        if (before.snapshot?.fetchedAt?.let { fetchedAt <= it } == true) return false
        val dropped = check.usedBefore - window.usedPercent >= MIN_DROP_POINTS
        val moved =
            window.resetsAt?.let {
                it - Instant.fromEpochMilliseconds(check.expectedResetAtEpochMillis) >=
                    MIN_RESET_SHIFT
            } == true
        return dropped || moved
    }
}
