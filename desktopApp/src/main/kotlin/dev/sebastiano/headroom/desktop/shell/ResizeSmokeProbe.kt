package dev.sebastiano.headroom.desktop.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import dev.nucleusframework.application.NucleusWindow
import dev.sebastiano.headroom.ui.LayoutWidth
import dev.sebastiano.headroom.ui.layoutWidth
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** No snapshot-state writes: observing a draw must not cause another frame or recomposition. */
internal class ResizeSmokeProbe {
    val info = AtomicReference<ResizeSmokeInfo?>()
    val measuredPx = AtomicReference(IntSize.Zero)
    val nativeCount = AtomicInteger()
    val drawCount = AtomicInteger()
    val nativeSizes = CopyOnWriteArrayList<ResizeSmokeNative>()
    val draws = CopyOnWriteArrayList<ResizeSmokeDraw>()

    fun resized(width: Int, height: Int) {
        nativeSizes +=
            ResizeSmokeNative(
                nativeCount.incrementAndGet(),
                System.nanoTime(),
                IntSize(width, height),
            )
    }

    fun drawn() {
        draws +=
            ResizeSmokeDraw(
                drawCount.incrementAndGet(),
                System.nanoTime(),
                measuredPx.get(),
                info.get(),
            )
    }

    fun matchingDraw(request: ResizeSmokeRequest): ResizeSmokeDraw? {
        val native = matchingNative(request) ?: return null
        return draws.firstOrNull { request.completed(native.sequence, native.sizePx, it) }
    }

    fun matchingNative(request: ResizeSmokeRequest): ResizeSmokeNative? = nativeSizes.firstOrNull {
        it.sequence > request.nativeSequence && it.sizePx == request.expectedPx
    }
}

@Composable
internal fun resizeSmokeModifier(probe: ResizeSmokeProbe?): Modifier {
    if (probe == null) return Modifier
    val window = LocalWindowInfo.current
    val density = LocalDensity.current
    // The shared public helper calls currentWindowAdaptiveInfoV2; Adaptive is not exported by
    // :core:ui.
    val layout = layoutWidth()
    val snapshot =
        ResizeSmokeInfo(
            windowPx = window.containerSize,
            windowDp = window.containerDpSize,
            density = density.density,
            // Desktop font scaling is linear; the direct fontScale getter is library-restricted.
            fontScale = with(density) { 1.sp.toPx() / density.density },
            adaptive = resizeSmokeWidth(window.containerSize.width / density.density),
            layout = layout,
        )
    SideEffect { if (probe.info.get() != snapshot) probe.info.set(snapshot) }
    return Modifier.onSizeChanged { probe.measuredPx.set(it) }
        .drawWithContent {
            drawContent()
            probe.drawn()
        }
}

internal suspend fun runResizeSmokeSweep(window: NucleusWindow, probe: ResizeSmokeProbe): Boolean {
    val tao = checkNotNull(window.unsafe.taoWindow)
    val scale = tao.scaleFactor
    val heightDp = (probe.measuredPx.get().height / scale).roundToInt()
    val requests = mutableListOf<ResizeSmokeRequest>()
    fun request(width: Int): ResizeSmokeRequest {
        val next =
            ResizeSmokeRequest(
                width,
                IntSize((width * scale).roundToInt(), (heightDp * scale).roundToInt()),
                System.nanoTime(),
                probe.nativeCount.get(),
                probe.drawCount.get(),
            )
        tao.setInnerSize(width.toDouble(), heightDp.toDouble())
        return next
    }
    println(
        "HEADROOM_RESIZE_START: scale=$scale heightDp=$heightDp bounds=${window.boundsOnScreen()}"
    )
    // Force a genuine callback before the first 1040dp endpoint, which is also the initial size.
    var passed = awaitResizeDraw(request(INITIAL_NUDGE_DP), probe) != null
    for (width in ENDPOINTS) {
        val next = request(width)
        requests += next
        val drawn = awaitResizeDraw(next, probe)
        delay(ENDPOINT_SETTLE_MILLIS)
        val info = probe.info.get()
        val diagnosis = resizeDiagnosis(next, info, scale, drawn)
        if (diagnosis != "consistent") passed = false
        println(
            "HEADROOM_RESIZE_ENDPOINT: ${resizeReport(next, drawn, info, probe.matchingNative(next), scale)} " +
                "latestNativePx=${probe.nativeSizes.lastOrNull()?.sizePx} diagnosis=$diagnosis"
        )
    }
    val continuous = mutableListOf<ResizeSmokeRequest>()
    val sweepStart = System.nanoTime()
    for (width in continuousWidths()) {
        continuous += request(width)
        delay(SWEEP_DELAY_MILLIS)
    }
    val sweepElapsedMillis = (System.nanoTime() - sweepStart) / NANOS_PER_MILLI
    delay(SWEEP_SETTLE_MILLIS)
    val latencies =
        continuous
            .mapNotNull { next ->
                val drawn = probe.matchingDraw(next)
                val diagnosis = resizeDiagnosis(next, drawn?.info, scale, drawn)
                if (diagnosis != "consistent") passed = false
                val report =
                    resizeReport(next, drawn, drawn?.info, probe.matchingNative(next), scale)
                println("HEADROOM_RESIZE_STEP: $report diagnosis=$diagnosis")
                drawn?.let { (it.nanos - next.nanos) / NANOS_PER_MILLI }
            }
            .sorted()
    val allDrawn = latencies.size == continuous.size
    println(
        "HEADROOM_RESIZE_RESULT: endpoints=${requests.size} steps=${continuous.size} " +
            "matchedDraws=${latencies.size} minMs=${latencies.firstOrNull()} " +
            "medianMs=${latencies.getOrNull(latencies.size / 2)} maxMs=${latencies.lastOrNull()} " +
            "sweepElapsedMs=$sweepElapsedMillis nominalDelayMs=${continuous.size * SWEEP_DELAY_MILLIS} " +
            "geometryAndAdaptiveConsistent=$passed allStepsDrawn=$allDrawn " +
            "timing=request-to-compose-draw-not-metal-present"
    )
    return passed && allDrawn
}

private suspend fun awaitResizeDraw(
    request: ResizeSmokeRequest,
    probe: ResizeSmokeProbe,
): ResizeSmokeDraw? =
    withTimeoutOrNull(ENDPOINT_TIMEOUT_MILLIS) {
        var drawn = probe.matchingDraw(request)
        while (drawn == null) {
            delay(DRAW_POLL_MILLIS)
            drawn = probe.matchingDraw(request)
        }
        drawn
    }

private fun resizeDiagnosis(
    request: ResizeSmokeRequest,
    info: ResizeSmokeInfo?,
    scale: Float,
    draw: ResizeSmokeDraw?,
): String =
    when {
        draw == null -> "native-or-measure-or-draw-timeout"
        info == null -> "missing-compose-info"
        info.layout != info.adaptive -> "layout-adaptive-disagreement"
        else ->
            resizeSmokeDiagnosis(
                request.expectedPx,
                scale,
                info.windowPx,
                info.density,
                info.adaptive,
            )
    }

private fun resizeReport(
    request: ResizeSmokeRequest,
    draw: ResizeSmokeDraw?,
    info: ResizeSmokeInfo?,
    native: ResizeSmokeNative?,
    scale: Float,
): String {
    val elapsed = draw?.let {
        String.format(Locale.ROOT, "%.2f", (it.nanos - request.nanos) / NANOS_PER_MILLI)
    }
    val callback = native?.let {
        String.format(Locale.ROOT, "%.2f", (it.nanos - request.nanos) / NANOS_PER_MILLI)
    }
    return "requestedDp=${request.widthDp} expectedPx=${request.expectedPx} measuredPx=${draw?.measuredPx} " +
        "nativePx=${native?.sizePx} callbackLatencyMs=$callback " +
        "drawSequence=${draw?.sequence} latencyMs=$elapsed windowPx=${info?.windowPx} " +
        "windowDp=${info?.windowDp} density=${info?.density} fontScale=${info?.fontScale} " +
        "computedAdaptive=${info?.adaptive} sharedLayoutWidth=${info?.layout} " +
        "expectedDefaultPartitions=${if (info?.layout == LayoutWidth.Expanded) 2 else 1} " +
        "expectedLayout=${resizeSmokeWidth((native?.sizePx?.width ?: request.expectedPx.width) / scale)}"
}

private fun continuousWidths(): List<Int> {
    val down =
        (1..SWEEP_HALF_STEPS).map { step ->
            WIDE_DP - (WIDE_DP - NARROW_DP) * step / SWEEP_HALF_STEPS
        }
    val up =
        (1..SWEEP_HALF_STEPS).map { step ->
            NARROW_DP + (WIDE_DP - NARROW_DP) * step / SWEEP_HALF_STEPS
        }
    return down + up
}

private val ENDPOINTS = listOf(1040, 840, 839, 600, 599, 500, 480, 1040)
private const val INITIAL_NUDGE_DP = 1041
private const val WIDE_DP = 1040
private const val NARROW_DP = 480
private const val SWEEP_HALF_STEPS = 12
private const val ENDPOINT_TIMEOUT_MILLIS = 2_000L
private const val ENDPOINT_SETTLE_MILLIS = 200L
private const val DRAW_POLL_MILLIS = 10L
private const val SWEEP_DELAY_MILLIS = 33L
private const val SWEEP_SETTLE_MILLIS = 500L
private const val NANOS_PER_MILLI = 1_000_000.0
