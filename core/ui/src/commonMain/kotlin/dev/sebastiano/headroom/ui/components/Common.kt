package dev.sebastiano.headroom.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.platform.rememberUiPlatform
import dev.sebastiano.headroom.ui.platform.uiPainterResource as painterResource
import dev.sebastiano.headroom.ui.platform.uiStringResource as stringResource
import dev.sebastiano.headroom.ui.resources.Res as R
import dev.sebastiano.headroom.ui.resources.account_error_access
import dev.sebastiano.headroom.ui.resources.account_error_auth
import dev.sebastiano.headroom.ui.resources.account_error_network
import dev.sebastiano.headroom.ui.resources.account_error_parse
import dev.sebastiano.headroom.ui.resources.account_error_rate_limited
import dev.sebastiano.headroom.ui.resources.account_error_unknown
import dev.sebastiano.headroom.ui.resources.left_credit
import dev.sebastiano.headroom.ui.resources.left_daily
import dev.sebastiano.headroom.ui.resources.left_monthly
import dev.sebastiano.headroom.ui.resources.left_other
import dev.sebastiano.headroom.ui.resources.left_session
import dev.sebastiano.headroom.ui.resources.left_weekly
import dev.sebastiano.headroom.ui.resources.percent_left
import dev.sebastiano.headroom.ui.resources.percent_used
import dev.sebastiano.headroom.ui.resources.used_credit
import dev.sebastiano.headroom.ui.resources.used_daily
import dev.sebastiano.headroom.ui.resources.used_monthly
import dev.sebastiano.headroom.ui.resources.used_other
import dev.sebastiano.headroom.ui.resources.used_session
import dev.sebastiano.headroom.ui.resources.used_weekly
import dev.sebastiano.headroom.ui.resources.window_credit
import dev.sebastiano.headroom.ui.resources.window_daily
import dev.sebastiano.headroom.ui.resources.window_monthly
import dev.sebastiano.headroom.ui.resources.window_other
import dev.sebastiano.headroom.ui.resources.window_session
import dev.sebastiano.headroom.ui.resources.window_weekly
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.DrawableResource

/** A large rounded screen title with an optional subtitle and trailing content. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/** A small label above a group of content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, icon: DrawableResource? = null) {
    // The top padding sits on the row, so an icon centres on the text and not on the padding.
    Row(
        modifier = modifier.padding(top = 6.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** A rounded group of rows on the container surface. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

/** A formatter for the user's zone, language and 12 or 24 hour clock. */
@Composable
fun rememberResetFormatter(zone: TimeZone): ResetFormatter {
    val platform = rememberUiPlatform()
    val locale = LocalLocale.current.toLanguageTag()
    val is24Hour = platform.is24Hour
    return remember(zone, locale, is24Hour) { ResetFormatter(zone, locale, is24Hour) }
}

@Composable
fun windowKindLabel(kind: WindowKind): String =
    stringResource(
        when (kind) {
            WindowKind.Weekly -> R.string.window_weekly
            WindowKind.Monthly -> R.string.window_monthly
            WindowKind.Daily -> R.string.window_daily
            WindowKind.Session -> R.string.window_session
            WindowKind.Other -> R.string.window_other
            WindowKind.Credit -> R.string.window_credit
        }
    )

/** The words under a big percentage: "weekly used", or "weekly left" in left mode. */
@Composable
fun quotaLabel(kind: WindowKind, display: QuotaDisplay): String =
    stringResource(
        when (display) {
            QuotaDisplay.Used ->
                when (kind) {
                    WindowKind.Weekly -> R.string.used_weekly
                    WindowKind.Monthly -> R.string.used_monthly
                    WindowKind.Daily -> R.string.used_daily
                    WindowKind.Session -> R.string.used_session
                    WindowKind.Other -> R.string.used_other
                    WindowKind.Credit -> R.string.used_credit
                }
            QuotaDisplay.Left ->
                when (kind) {
                    WindowKind.Weekly -> R.string.left_weekly
                    WindowKind.Monthly -> R.string.left_monthly
                    WindowKind.Daily -> R.string.left_daily
                    WindowKind.Session -> R.string.left_session
                    WindowKind.Other -> R.string.left_other
                    WindowKind.Credit -> R.string.left_credit
                }
        }
    )

/** A percentage with its meaning, for screen readers: "71% used" or "29% left". */
@Composable
fun percentDescription(percent: Int, display: QuotaDisplay): String =
    stringResource(
        when (display) {
            QuotaDisplay.Used -> R.string.percent_used
            QuotaDisplay.Left -> R.string.percent_left
        },
        percent,
    )

@Composable
fun errorText(kind: QuotaErrorKind): String =
    stringResource(
        when (kind) {
            QuotaErrorKind.Auth -> R.string.account_error_auth
            QuotaErrorKind.Access -> R.string.account_error_access
            QuotaErrorKind.RateLimited -> R.string.account_error_rate_limited
            QuotaErrorKind.Network -> R.string.account_error_network
            QuotaErrorKind.Parse -> R.string.account_error_parse
            QuotaErrorKind.Unknown -> R.string.account_error_unknown
        }
    )
