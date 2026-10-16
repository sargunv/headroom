package dev.sebastiano.headroom.desktop.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.SingleInstanceRestoreEffect
import dev.sebastiano.headroom.ui.platform.DesktopUiPlatform
import kotlinx.coroutines.launch

@Composable
internal fun NucleusApplicationScope.HeadroomShell(args: Array<String>) {
    val scope = rememberCoroutineScope()
    val controller = remember { ShellController(scope, args, ::exitApplication) }
    DisposableEffect(controller) {
        onDeepLink { uri -> scope.launch { controller.activationHandler.openUri(uri) } }
        controller.start()
        onDispose {
            onDeepLink {}
            controller.dispose()
        }
    }
    SingleInstanceRestoreEffect { controller.show() }
    HeadroomTray(controller)
    ShellWindow(controller, onSystemQuit = controller.lifecycle::quit)
}

@Composable
private fun NucleusApplicationScope.ShellWindow(
    controller: ShellController,
    onSystemQuit: () -> Unit,
) {
    val visible by controller.lifecycle.visible.collectAsState()
    val state = rememberWindowState(size = DpSize(1040.dp, 800.dp))
    val icon = remember { shellPainter("app-icon.png") }
    DecoratedWindow(
        onCloseRequest = { if (isQuitting) onSystemQuit() else controller.lifecycle.closeWindow() },
        state = state,
        visible = visible,
        title = "Headroom",
        icon = icon,
        minimumSize = DpSize(480.dp, 520.dp),
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) false
            else
                controller.presentation?.let {
                    dispatchBackNavigation(event.key, event.isMetaPressed, it.backDispatcher)
                } ?: false
        },
    ) {
        SideEffect(visible, controller.activation) {
            if (visible) {
                nucleusWindow.setMinimized(false)
                nucleusWindow.toFront()
                nucleusWindow.requestFocus()
                controller.refresh()
            }
        }
        LaunchedEffect(nucleusWindow) {
            nucleusWindow.focusFlow.collect { focused ->
                if (focused && !controller.lifecycle.visible.value) controller.show()
            }
        }
        ShellContent(controller, visible)
    }
}

// Compose still has legacy text fields; overriding both interfaces avoids its AWT clipboard.
@Suppress("DEPRECATION")
@Composable
private fun ShellContent(controller: ShellController, visible: Boolean) {
    val uriHandler =
        remember(controller) {
            object : UriHandler {
                override fun openUri(uri: String) = controller.openUrl(uri)
            }
        }
    CompositionLocalProvider(
        LocalUriHandler provides uriHandler,
        LocalClipboard provides controller.clipboard,
        LocalClipboardManager provides controller.clipboardManager,
    ) {
        val presentation = controller.presentation
        if (presentation == null) {
            StartupContent(controller)
        } else {
            val platform =
                remember(controller) {
                    DesktopUiPlatform(controller::openUrl, controller::copyText)
                }
            SharedHeadroomContent(
                presentation = presentation,
                platform = platform,
                navigationRequest = controller.navigationRequest,
                onConsumeNavigation = controller::consumeNavigation,
                openAccountRequest = controller.openAccountRequest,
                onConsumeOpenAccount = controller::consumeOpenAccount,
                visible = visible,
                notices = listOfNotNull(controller.issue, controller.notificationStatus),
                desktopSettings = { modifier ->
                    MacSettingsContent(
                        controller.launchAtLogin,
                        controller::changeLaunchAtLogin,
                        modifier,
                    )
                },
            )
        }
    }
}

@Composable
private fun StartupContent(controller: ShellController) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(32.dp)) {
                Text("Headroom", style = MaterialTheme.typography.headlineLarge)
                Text(if (controller.startupError) "Unable to start" else "Opening your accounts...")
                ShellNotice(controller.issue)
                TextButton(onClick = controller.lifecycle::quit) { Text("Quit Headroom") }
            }
        }
    }
}

@Composable
private fun ShellNotice(message: String?) {
    if (message != null) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                message,
                Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
