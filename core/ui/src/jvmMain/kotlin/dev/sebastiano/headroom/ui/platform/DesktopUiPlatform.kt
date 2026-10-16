package dev.sebastiano.headroom.ui.platform

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Nucleus supplies native browser and clipboard callbacks; there is no AWT dependency here. */
class DesktopUiPlatform(
    private val onOpenUrl: (String) -> Unit,
    private val onCopy: (String) -> Unit,
    override val is24Hour: Boolean = default24HourClock(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UiPlatform {
    override val capabilities = UiCapabilities()

    override fun openUrl(url: String) = onOpenUrl(url)

    override fun copy(text: String) = onCopy(text)

    override suspend fun loadLicences(): String =
        withContext(ioDispatcher) {
            checkNotNull(
                    this@DesktopUiPlatform.javaClass.classLoader.getResourceAsStream(
                        "licenses/aboutlibraries.json"
                    )
                ) {
                    "Desktop dependency licence metadata is missing"
                }
                .bufferedReader()
                .use { it.readText() }
        }
}

private fun default24HourClock(): Boolean {
    val format = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
    val pattern = (format as? java.text.SimpleDateFormat)?.toPattern().orEmpty()
    return 'H' in pattern || 'k' in pattern
}

@androidx.compose.runtime.ReadOnlyComposable
@Composable
actual fun rememberUiPlatform(): UiPlatform {
    return checkNotNull(LocalUiPlatform.current) {
        "The desktop shell must provide UiPlatform with its native browser and clipboard actions"
    }
}

@Composable internal actual fun SystemBarsFollow(dark: Boolean) = Unit

@androidx.compose.runtime.ReadOnlyComposable
@Composable
internal actual fun wallpaperSwatch(dark: Boolean): Pair<Color, Color> =
    MaterialTheme.colorScheme.let { it.primary to it.tertiary }

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val dispatcher = LocalUiBackDispatcher.current
    val current = rememberUpdatedState(onBack)
    DisposableEffect(dispatcher, enabled) {
        val dispose = if (enabled) dispatcher.register { current.value() } else ({})
        onDispose { dispose() }
    }
}

@Composable
actual fun PredictiveBackHandler(enabled: Boolean, onBack: suspend (Flow<UiBackEvent>) -> Unit) {
    val scope = rememberCoroutineScope()
    BackHandler(enabled) { scope.launch { onBack(emptyFlow()) } }
}
