package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.ui.resources.Res as R
import dev.sebastiano.headroom.ui.resources.tab_overview
import dev.sebastiano.headroom.ui.resources.tab_resets
import dev.sebastiano.headroom.ui.resources.tab_stats
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** The three top-level sections. */
enum class HomeTab(
    val label: StringResource,
    val icon: DrawableResource,
    val selectedIcon: DrawableResource,
) {
    Overview(R.string.tab_overview, HeadroomIcons.Dashboard, HeadroomIcons.DashboardFilled),
    Resets(R.string.tab_resets, HeadroomIcons.Monitoring, HeadroomIcons.MonitoringFilled),
    Stats(R.string.tab_stats, HeadroomIcons.PieChart, HeadroomIcons.PieChartFilled),
}

/** The stable test tag for a navigation destination, e.g. "nav-item-overview". */
internal fun navigationItemTag(item: HomeTab): String = "nav-item-${item.name.lowercase()}"
