package dev.sebastiano.headroom.desktop.integration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize

/** Keep one window-info identity while changing its observed size, as the native host does. */
@Composable
internal fun TestWindowSize(size: DpSize?, content: @Composable () -> Unit) {
    val parent = LocalWindowInfo.current
    val density = LocalDensity.current
    val currentSize by rememberUpdatedState(size)
    val window =
        remember(parent, density) {
            object : WindowInfo by parent {
                override val containerSize: IntSize
                    get() =
                        with(density) {
                            currentSize?.let {
                                IntSize(it.width.roundToPx(), it.height.roundToPx())
                            } ?: parent.containerSize
                        }

                override val containerDpSize: DpSize
                    get() = currentSize ?: parent.containerDpSize
            }
        }
    CompositionLocalProvider(LocalWindowInfo provides if (size == null) parent else window) {
        Box(size?.let { Modifier.requiredSize(it.width, it.height) } ?: Modifier) { content() }
    }
}
