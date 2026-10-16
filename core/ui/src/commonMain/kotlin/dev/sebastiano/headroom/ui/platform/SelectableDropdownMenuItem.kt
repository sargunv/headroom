package dev.sebastiano.headroom.ui.platform

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Keeps Android's expressive selected item while CMP catches up with that API. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal expect fun SelectableDropdownMenuItem(
    selected: Boolean,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    shapes: MenuItemShapes,
    modifier: Modifier = Modifier,
    selectedLeadingIcon: @Composable (() -> Unit)? = null,
)
