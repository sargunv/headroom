package dev.sebastiano.headroom.desktop.shell

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import dev.sebastiano.headroom.ui.LayoutWidth
import kotlin.math.abs

internal data class ResizeSmokeInfo(
    val windowPx: IntSize,
    val windowDp: DpSize,
    val density: Float,
    val fontScale: Float,
    val adaptive: LayoutWidth,
    val layout: LayoutWidth,
)

internal data class ResizeSmokeDraw(
    val sequence: Int,
    val nanos: Long,
    val measuredPx: IntSize,
    val info: ResizeSmokeInfo?,
)

internal data class ResizeSmokeNative(val sequence: Int, val nanos: Long, val sizePx: IntSize)

internal data class ResizeSmokeRequest(
    val widthDp: Int,
    val expectedPx: IntSize,
    val nanos: Long,
    val nativeSequence: Int,
    val drawSequence: Int,
) {
    fun completed(nativeCount: Int, nativePx: IntSize, draw: ResizeSmokeDraw): Boolean =
        nativeCount > nativeSequence &&
            nativePx == expectedPx &&
            draw.sequence > drawSequence &&
            draw.nanos >= nanos &&
            draw.measuredPx == expectedPx
}

internal fun resizeSmokeWidth(widthDp: Float): LayoutWidth =
    when {
        widthDp >= EXPANDED_MIN_DP -> LayoutWidth.Expanded
        widthDp >= MEDIUM_MIN_DP -> LayoutWidth.Medium
        else -> LayoutWidth.Compact
    }

internal fun resizeSmokeDiagnosis(
    nativePx: IntSize,
    nativeScale: Float,
    windowPx: IntSize,
    density: Float,
    adaptive: LayoutWidth,
): String =
    when {
        nativePx != windowPx -> "stale-window-info"
        abs(nativeScale - density) > DENSITY_TOLERANCE -> "density-mismatch"
        resizeSmokeWidth(nativePx.width / nativeScale) != adaptive -> "adaptive-mismatch"
        else -> "consistent"
    }

private const val DENSITY_TOLERANCE = 0.01f
private const val MEDIUM_MIN_DP = 600
private const val EXPANDED_MIN_DP = 840
