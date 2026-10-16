package dev.sebastiano.headroom.ui.resets

import androidx.compose.runtime.Composable
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.platform.uiStringResource as stringResource
import dev.sebastiano.headroom.ui.resources.Res as R
import dev.sebastiano.headroom.ui.resources.reset_clears_five_hour
import dev.sebastiano.headroom.ui.resources.reset_clears_other
import dev.sebastiano.headroom.ui.resources.reset_clears_seven_day
import dev.sebastiano.headroom.ui.resources.reset_clears_seven_day_opus
import dev.sebastiano.headroom.ui.resources.reset_clears_seven_day_sonnet
import dev.sebastiano.headroom.ui.resources.reset_note_week_day_stays
import dev.sebastiano.headroom.ui.resources.reset_scope_and
import dev.sebastiano.headroom.ui.resources.reset_scope_codex
import dev.sebastiano.headroom.ui.resources.reset_scope_codex_monthly
import dev.sebastiano.headroom.ui.resources.reset_scope_grok
import dev.sebastiano.headroom.ui.resources.reset_scope_refills
import dev.sebastiano.headroom.ui.resources.reset_scope_unknown
import dev.sebastiano.headroom.ui.resources.reset_scope_zai_five_hour
import dev.sebastiano.headroom.ui.resources.reset_scope_zai_week
import dev.sebastiano.headroom.ui.resources.resets_pool_not_yet
import dev.sebastiano.headroom.ui.resources.resets_pool_paused
import dev.sebastiano.headroom.ui.resources.resets_pool_queued
import dev.sebastiano.headroom.ui.resources.resets_pool_waiting
import org.jetbrains.compose.resources.StringResource

/**
 * The words each provider supplies for its resets. The sheet, the detail card and the Resets tab
 * are shared; only these lines change from one provider to another. A pool whose scope is not known
 * gets the neutral line.
 */
internal object ResetCopy {
    /** What a reset from [pool] restores, in one sentence. */
    @Composable
    fun scope(provider: Provider, pool: ResetPool): String {
        val scope = pool.scope
        if (scope.windowIds.isNotEmpty()) return refills(scope.windowIds.sorted())
        val kinds = scope.kinds
        return stringResource(
            when {
                kinds == null -> R.string.reset_scope_unknown
                provider == Provider.Codex && kinds == setOf(WindowKind.Monthly) ->
                    R.string.reset_scope_codex_monthly
                provider == Provider.Codex -> R.string.reset_scope_codex
                provider == Provider.Grok -> R.string.reset_scope_grok
                provider == Provider.ZAi && kinds == setOf(WindowKind.Session) ->
                    R.string.reset_scope_zai_five_hour
                provider == Provider.ZAi -> R.string.reset_scope_zai_week
                else -> R.string.reset_scope_unknown
            }
        )
    }

    /** "Refills your 5-hour session and your weekly limit.", from the window ids a reset clears. */
    @Composable
    private fun refills(windowIds: List<String>): String {
        val parts = windowIds.map { id -> clears(id) }
        val joined =
            parts.drop(1).fold(parts.first()) { all, next ->
                stringResource(R.string.reset_scope_and, all, next)
            }
        return stringResource(R.string.reset_scope_refills, joined)
    }

    @Composable
    private fun clears(windowId: String): String =
        when (windowId) {
            "five_hour" -> stringResource(R.string.reset_clears_five_hour)
            "seven_day" -> stringResource(R.string.reset_clears_seven_day)
            "seven_day_opus" -> stringResource(R.string.reset_clears_seven_day_opus)
            "seven_day_sonnet" -> stringResource(R.string.reset_clears_seven_day_sonnet)
            else -> stringResource(R.string.reset_clears_other, windowId.replace('_', ' '))
        }

    /** A provider's own note for the confirmation, or null. Claude's weekly day does not move. */
    fun confirmNote(provider: Provider): StringResource? =
        if (provider == Provider.Claude) R.string.reset_note_week_day_stays else null

    /** The label of a pool that cannot be used now, or null when it can. */
    fun status(status: ResetPoolStatus): StringResource? =
        when (status) {
            ResetPoolStatus.Ready -> null
            ResetPoolStatus.WaitingForLimit -> R.string.resets_pool_waiting
            ResetPoolStatus.Queued -> R.string.resets_pool_queued
            ResetPoolStatus.Paused -> R.string.resets_pool_paused
            ResetPoolStatus.NotUsableYet -> R.string.resets_pool_not_yet
        }

    /** The service a provider's resets need a separate sign-in to, such as Z.AI's ZCode. */
    fun signInService(provider: Provider): String =
        when (provider) {
            Provider.ZAi -> "ZCode"
            else -> provider.displayName
        }
}
