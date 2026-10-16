package dev.sebastiano.headroom.ui.platform

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldPaneScope
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal actual fun NavigableListDetailPaneScaffold(
    navigator: ThreePaneScaffoldNavigator<String>,
    listPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    detailPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
) {
    androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = listPane,
        detailPane = detailPane,
    )
}
