package dev.sebastiano.headroom.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.ThemeMode
import dev.sebastiano.headroom.ui.platform.SystemBarsFollow

/**
 * The theme the user chose in the appearance settings: light, dark or the device's, the colour
 * palette, and reduced motion. The system bar icons follow the chosen light or dark, so the status
 * bar stays readable. [dynamicColor] = false replaces the wallpaper colours with fixed cobalt, for
 * tests and screenshots.
 */
@Composable
fun AppearanceTheme(
    settings: AppSettings,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark =
        when (settings.theme) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    SystemBarsFollow(dark)
    HeadroomTheme(
        darkTheme = dark,
        dynamicColor = dynamicColor,
        palette = settings.palette,
        reduceMotion = settings.motion == MotionPreference.Reduced,
        content = content,
    )
}
