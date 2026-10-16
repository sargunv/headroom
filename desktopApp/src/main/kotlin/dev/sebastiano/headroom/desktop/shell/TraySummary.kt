package dev.sebastiano.headroom.desktop.shell

import dev.sebastiano.headroom.model.AccountState
import kotlin.math.roundToInt

internal fun trayQuotaSummary(title: String, used: Double?, display: String): String {
    if (used == null || !used.isFinite()) return "$title: Waiting for sync"
    val percentage = if (display == "left") (100.0 - used).coerceAtLeast(0.0) else used
    val unit = if (display == "left") "left" else "used"
    return "$title: ${percentage.roundToInt()}% $unit"
}

internal fun trayErrorSummary(error: String): String =
    when (error) {
        "auth" -> "Sign in again"
        "access" -> "Access unavailable"
        "rateLimited" -> "Sync rate limited"
        "network" -> "Connection unavailable"
        "parse" -> "Quota data unavailable"
        else -> "Could not sync"
    }

internal fun trayAccountSummary(account: AccountState, display: String): String {
    val title = account.account.name
    return when {
        account.isSignInExpired -> "$title: Sign in again"
        account.lastError != null ->
            "$title: ${trayErrorSummary(checkNotNull(account.lastError).name.replaceFirstChar { it.lowercase() })}"
        account.isRefreshing -> "$title: Syncing"
        account.primaryWindow?.isUnlimited == true -> "$title: Unlimited"
        else -> trayQuotaSummary(title, account.primaryWindow?.usedPercent, display)
    }
}
