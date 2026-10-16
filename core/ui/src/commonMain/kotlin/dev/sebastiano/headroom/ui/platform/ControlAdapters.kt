package dev.sebastiano.headroom.ui.platform

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal expect fun urlHost(url: String): String?

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal expect fun toggleButtonColors(
    containerColor: Color,
    contentColor: Color,
): ToggleButtonColors
