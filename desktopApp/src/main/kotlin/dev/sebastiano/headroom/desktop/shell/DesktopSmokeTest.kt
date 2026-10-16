package dev.sebastiano.headroom.desktop.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.composenativetray.tray.impl.MacTrayInitializer
import dev.sebastiano.headroom.shared.Headroom
import dev.sebastiano.headroom.shared.openHeadroom
import dev.sebastiano.headroom.ui.platform.DesktopUiPlatform
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Explicit opt-in: no real credentials, resident worker, notification API or login-item backend.
 */
internal fun runDesktopSmokeTest(args: Array<String>) {
    val resources = SmokeResources.create()
    val result = SmokeResult()
    val originalType = System.getProperty(EXECUTABLE_TYPE_PROPERTY)
    // Nucleus probes AutoLaunch during bootstrap. DEV short-circuits that probe before native
    // access.
    System.setProperty(EXECUTABLE_TYPE_PROPERTY, "dev")
    try {
        nucleusApplication(
            args = args,
            enableSingleInstance = false,
            dockIconFollowsWindows = true,
            exitProcessOnExit = false,
            initializeFileKit = false,
        ) {
            SmokeShell(resources, result, resizeSweep = "--resize-sweep" in args)
        }
    } finally {
        result.headroom?.close()
        resources.close()
        if (originalType == null) System.clearProperty(EXECUTABLE_TYPE_PROPERTY)
        else System.setProperty(EXECUTABLE_TYPE_PROPERTY, originalType)
    }
    check(result.passed) {
        "HEADROOM_SMOKE_TEST_FAILED: native demo window/tray did not become ready"
    }
    println(
        "HEADROOM_SMOKE_TEST_OK: native demo window rendered and status item initialized; temporary data removed"
    )
}

@Composable
private fun NucleusApplicationScope.SmokeShell(
    resources: SmokeResources,
    result: SmokeResult,
    resizeSweep: Boolean,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    val state = remember { SmokeState() }
    val resizeProbe = remember(resizeSweep) { if (resizeSweep) ResizeSmokeProbe() else null }
    if (state.headroom != null) SmokeTray()
    SmokeWindow(state, resizeProbe)
    LaunchedEffect(resources) {
        try {
            withContext(io) {
                result.headroom =
                    openHeadroom(
                        resources.directory.toString(),
                        resources.secrets,
                        Dispatchers.Main,
                    )
            }
            val headroom = checkNotNull(result.headroom)
            state.headroom = headroom
            val presentation = DesktopPresentation(headroom, this)
            state.presentation = presentation
            val watch = launch {
                presentation.homeViewModel.state.collect {
                    state.demoReady = it.accountsLoaded && it.isDemo && it.accounts.isNotEmpty()
                }
            }
            try {
                withTimeout(if (resizeSweep) RESIZE_TIMEOUT_MILLIS else SMOKE_TIMEOUT_MILLIS) {
                    while (!state.ready()) {
                        state.requestedFrame++
                        delay(FRAME_POLL_MILLIS)
                    }
                    result.passed =
                        if (resizeProbe == null) true
                        else runResizeSmokeSweep(checkNotNull(state.window), resizeProbe)
                }
            } finally {
                watch.cancelAndJoin()
            }
        } catch (_: TimeoutCancellationException) {
            result.passed = false
            System.err.println("HEADROOM_SMOKE_TEST_TIMEOUT: ${state.diagnostic()}")
        } catch (_: IOException) {
            result.passed = false
        } catch (_: RuntimeException) {
            result.passed = false
        } catch (_: LinkageError) {
            result.passed = false
        } finally {
            withContext(NonCancellable) {
                state.presentation?.close()
                result.headroom?.closeAndJoin()
            }
            state.headroom = null
            exitApplication()
        }
    }
}

@Composable
private fun NucleusApplicationScope.SmokeWindow(state: SmokeState, resizeProbe: ResizeSmokeProbe?) {
    val windowState = rememberWindowState(size = DpSize(1040.dp, 800.dp))
    DecoratedWindow(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Headroom - smoke test",
        minimumSize = DpSize(480.dp, 520.dp),
    ) {
        DisposableEffect(nucleusWindow) {
            state.window = nucleusWindow
            if (resizeProbe != null) {
                checkNotNull(nucleusWindow.unsafe.taoWindow).onResized(resizeProbe::resized)
            }
            onDispose { state.window = null }
        }
        Column(Modifier.fillMaxSize().then(resizeSmokeModifier(resizeProbe))) {
            Text("Isolated demo smoke test - frame ${state.requestedFrame}")
            val presentation = state.presentation
            if (presentation != null) {
                val platform = remember {
                    DesktopUiPlatform(
                        onOpenUrl = { error("The native demo smoke test must not open a browser") },
                        onCopy = {
                            error("The native demo smoke test must not change the clipboard")
                        },
                    )
                }
                SharedHeadroomContent(
                    presentation = presentation,
                    platform = platform,
                    modifier = Modifier.weight(1f),
                    onRender = { state.drawnFrames.incrementAndGet() },
                )
            }
        }
    }
}

@Composable
private fun SmokeTray() {
    MacStatusTray("Headroom smoke test", {}) { Item("Headroom smoke test", isEnabled = false) }
}

private class SmokeResult {
    var passed = false
    var headroom: Headroom? = null
}

private class SmokeState {
    var headroom: Headroom? by mutableStateOf(null)
    var presentation: DesktopPresentation? by mutableStateOf(null)
    var requestedFrame: Int by mutableIntStateOf(0)
    var demoReady = false
    var window: NucleusWindow? = null
    val drawnFrames = AtomicInteger()

    fun ready(): Boolean =
        demoReady &&
            drawnFrames.get() >= REQUIRED_FRAMES &&
            window?.boundsOnScreen() != null &&
            NativeTrayProbe.initialized()

    fun diagnostic(): String =
        "demo=$demoReady frames=${drawnFrames.get()} window=${window?.boundsOnScreen() != null} " +
            "tray=${NativeTrayProbe.initialized()} geometry=${MacTrayInitializer.statusItemPosition(IntArray(2))}"
}

private const val EXECUTABLE_TYPE_PROPERTY = "nucleus.executable.type"
private const val SMOKE_TIMEOUT_MILLIS = 20_000L
private const val RESIZE_TIMEOUT_MILLIS = 40_000L
private const val FRAME_POLL_MILLIS = 100L
private const val REQUIRED_FRAMES = 3
