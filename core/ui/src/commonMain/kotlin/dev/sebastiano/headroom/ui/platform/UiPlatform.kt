package dev.sebastiano.headroom.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow

/** Host services only; screens never need an Activity, Context, or desktop toolkit. */
interface UiPlatform {
    val capabilities: UiCapabilities
    val is24Hour: Boolean
    val restrictedInstall: Boolean
        get() = false

    val accessibilityAvailable: Boolean
        get() = false

    fun openUrl(url: String)

    fun copy(text: String)

    fun openAppInfo() {}

    fun openAccessibilitySettings() {}

    fun openOverlaySettings() {}

    suspend fun loadLicences(): String
}

data class UiCapabilities(
    val widgets: Boolean = false,
    val quickSettingsTile: Boolean = false,
    val resetIsland: Boolean = false,
)

@Suppress("CompositionLocalAllowlist") // A host-level service boundary, not screen state.
val LocalUiPlatform = staticCompositionLocalOf<UiPlatform?> { null }

@Composable expect fun rememberUiPlatform(): UiPlatform

@Composable internal expect fun SystemBarsFollow(dark: Boolean)

@Composable internal expect fun wallpaperSwatch(dark: Boolean): Pair<Color, Color>

/** The desktop shell dispatches Escape here; nested controls take precedence over pages. */
class UiBackDispatcher {
    private val handlers = mutableListOf<() -> Unit>()

    fun register(handler: () -> Unit): () -> Unit {
        handlers += handler
        return { handlers.remove(handler) }
    }

    fun dispatch(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }
}

@Suppress("CompositionLocalAllowlist") // Each desktop window installs its own Escape dispatcher.
val LocalUiBackDispatcher = staticCompositionLocalOf { UiBackDispatcher() }

data class UiBackEvent(val progress: Float)

@Composable expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
expect fun PredictiveBackHandler(
    enabled: Boolean = true,
    onBack: suspend (Flow<UiBackEvent>) -> Unit,
)
