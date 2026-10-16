package dev.sebastiano.headroom.ui.platform

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal actual fun urlHost(url: String): String? = runCatching {
    java.net.URI(url).host
}
    .getOrNull()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal actual fun toggleButtonColors(
    containerColor: Color,
    contentColor: Color,
): ToggleButtonColors =
    ToggleButtonDefaults.toggleButtonColors(
        containerColor = containerColor,
        contentColor = contentColor,
    )
