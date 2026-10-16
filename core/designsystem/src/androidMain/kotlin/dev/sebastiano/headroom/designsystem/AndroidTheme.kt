package dev.sebastiano.headroom.designsystem

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.sebastiano.headroom.model.ThemePalette

@Composable
internal actual fun platformColorScheme(
    palette: ThemePalette,
    dark: Boolean,
    dynamicColor: Boolean,
): ColorScheme {
    val context = LocalContext.current
    return remember(context, palette, dark, dynamicColor) {
        headroomColorScheme(context, palette, dark, dynamicColor)
    }
}

/** Shared with Android widgets, including their wallpaper-derived colours. */
fun headroomColorScheme(
    context: Context,
    palette: ThemePalette,
    dark: Boolean,
    dynamicColor: Boolean = true,
): ColorScheme =
    when {
        palette != ThemePalette.Wallpaper || !dynamicColor -> headroomColorScheme(palette, dark)
        dark -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
