package dev.sebastiano.headroom.desktop.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.nucleusframework.autolaunch.AutoLaunch
import dev.nucleusframework.autolaunch.AutoLaunchResult
import dev.sebastiano.headroom.desktop.DesktopLifecycle
import dev.sebastiano.headroom.desktop.DesktopNotificationLedger
import dev.sebastiano.headroom.desktop.platform.DesktopPaths
import dev.sebastiano.headroom.desktop.platform.MacBrowser
import dev.sebastiano.headroom.desktop.platform.MacKeychainStore
import dev.sebastiano.headroom.shared.Headroom
import dev.sebastiano.headroom.shared.openHeadroom
import dev.sebastiano.headroom.ui.OpenAccountRequest
import dev.sebastiano.headroom.ui.SharedUiDestination
import dev.sebastiano.headroom.ui.SharedUiNavigationRequest
import dev.sebastiano.headroom.ui.home.HomeUiState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ShellController(
    private val rootScope: CoroutineScope,
    private val args: Array<String>,
    private val onExit: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val main: CoroutineDispatcher = Dispatchers.Main,
) {
    private val work = SupervisorJob(rootScope.coroutineContext[Job])
    private val scope = CoroutineScope(work + main)
    private val browser = MacBrowser()
    private val options = ShellOptions.parse(args)
    private val pasteboard = MacPasteboard()
    val clipboard = MacClipboard(pasteboard)
    val clipboardManager = MacClipboardManager(pasteboard)
    val lifecycle = DesktopLifecycle(startHidden = true, onQuit = ::stop)
    val activationHandler = ShellActivation { show() }
    var headroom: Headroom? by mutableStateOf(null)
        private set

    var overview: HomeUiState? by mutableStateOf(null)
        private set

    var presentation: DesktopPresentation? by mutableStateOf(null)
        private set

    var navigationRequest: SharedUiNavigationRequest? by mutableStateOf(null)
        private set

    var openAccountRequest: OpenAccountRequest? by mutableStateOf(null)
        private set

    private var requestSerial = 0L
    var launchAtLogin: Boolean by mutableStateOf(false)
        private set

    var startupError: Boolean by mutableStateOf(false)
        private set

    var issue: String? by mutableStateOf(null)
        private set

    var notificationStatus: String? by mutableStateOf(null)
        private set

    var quitting: Boolean by mutableStateOf(false)
        private set

    var activation: Int by mutableIntStateOf(0)
        private set

    fun start() {
        scope.launch { openSession() }
    }

    fun show(destination: SharedUiDestination? = null) {
        if (quitting) return
        if (destination != null) {
            openAccountRequest = null
            navigationRequest =
                SharedUiNavigationRequest(serial = ++requestSerial, destination = destination)
        }
        lifecycle.showWindow()
        activation++
    }

    fun refresh() {
        if (!quitting) presentation?.homeViewModel?.refresh()
    }

    fun showAccount(accountId: String, signInAgain: Boolean) {
        if (quitting) return
        navigationRequest = null
        openAccountRequest = OpenAccountRequest(accountId, ++requestSerial, signInAgain)
        show()
    }

    fun consumeNavigation() {
        navigationRequest = null
    }

    fun consumeOpenAccount() {
        openAccountRequest = null
    }

    fun openUrl(url: String) {
        if (quitting) return
        scope.launch {
            if (!withContext(io) { browser.open(url) }) {
                issue = "Could not open the browser. Check the address and try again."
            }
        }
    }

    fun copyText(text: String) {
        if (!quitting && !pasteboard.write(text)) issue = "Could not copy to the macOS clipboard."
    }

    fun changeLaunchAtLogin(enabled: Boolean) {
        if (quitting) return
        scope.launch {
            try {
                updateLaunchAtLogin(enabled)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                issue = "Could not change launch at login. Check macOS Login Items and try again."
            } catch (_: LinkageError) {
                issue = "Launch at login is unavailable on this Mac."
            }
        }
    }

    private suspend fun updateLaunchAtLogin(enabled: Boolean) {
        val result =
            withContext(io) {
                if (enabled && AutoLaunch.isUserLocked()) AutoLaunchResult.BLOCKED_BY_USER
                else if (enabled) AutoLaunch.enable() else AutoLaunch.disable()
            }
        if (result == AutoLaunchResult.BLOCKED_BY_USER) {
            issue = "Allow Headroom in macOS Login Items to enable launch at login."
            withContext(io) { AutoLaunch.openSystemSettings() }
        } else if (result == AutoLaunchResult.ERROR || result == AutoLaunchResult.UNSUPPORTED) {
            issue =
                "Launch at login is unavailable. Use the installed Headroom app on macOS 13 or later."
        }
        launchAtLogin = withContext(io) { AutoLaunch.isEnabled() }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun stop() {
        quitting = true
        rootScope.launch {
            work.cancelAndJoin()
            onExit()
        }
    }

    private suspend fun openSession() {
        var opened: Headroom? = null
        var models: DesktopPresentation? = null
        var notifier: MacNotifications? = null
        try {
            val atLogin = withContext(io) { AutoLaunch.wasStartedAtLogin(args) }
            if (!atLogin && !options.background) lifecycle.showWindow()
            launchAtLogin = withContext(io) { AutoLaunch.isEnabled() }
            val directory = withContext(io) { DesktopPaths.resolve(options.dataDirectory) }
            val ledger =
                withContext(io) {
                    DesktopNotificationLedger(directory.resolve("notifications.json"))
                }
            withContext(io) {
                opened = openHeadroom(directory.toString(), MacKeychainStore(), main)
            }
            val session = checkNotNull(opened)
            val ui = DesktopPresentation(session, scope, main = main)
            models = ui
            presentation = ui
            headroom = session
            val notifications = MacNotifications(scope, { show() }, { notificationStatus = it })
            notifier = notifications
            notifications.install()
            val watch = scope.launch { ui.homeViewModel.state.collect { overview = it } }
            val signIn = session.signIn.watch(activationHandler::signInChanged)
            val zCode = session.resets.zCode.watch(activationHandler::zCodeChanged)
            try {
                ResidentWorker(session, ledger, notifications, { issue = it }).run()
            } finally {
                watch.cancel()
                signIn.cancel()
                zCode.cancel()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            failStartup()
        } catch (_: RuntimeException) {
            failStartup()
        } catch (_: LinkageError) {
            failStartup()
        } finally {
            headroom = null
            presentation = null
            try {
                notifier?.close()
            } finally {
                withContext(NonCancellable) {
                    models?.close()
                    opened?.closeAndJoin()
                }
            }
        }
    }

    private fun failStartup() {
        startupError = true
        issue = "Headroom could not open its local data or macOS Keychain. Quit and reopen the app."
        lifecycle.showWindow()
    }
}
