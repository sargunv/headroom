package dev.sebastiano.headroom.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.sebastiano.headroom.model.ThemePalette

@Composable
internal actual fun platformColorScheme(
    palette: ThemePalette,
    dark: Boolean,
    dynamicColor: Boolean,
): ColorScheme = remember(palette, dark) { headroomColorScheme(palette, dark) }
