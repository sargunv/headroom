package dev.sebastiano.headroom.ui.platform

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal actual fun SelectableDropdownMenuItem(
    selected: Boolean,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    shapes: MenuItemShapes,
    modifier: Modifier,
    selectedLeadingIcon: @Composable (() -> Unit)?,
) {
    androidx.compose.material3.SelectableDropdownMenuItem(
        selected = selected,
        onClick = onClick,
        text = text,
        shapes = shapes,
        modifier = modifier,
        selectedLeadingIcon = selectedLeadingIcon,
    )
}
