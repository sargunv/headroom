package dev.sebastiano.headroom.desktop.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.ui.SharedUiDestination
import dev.sebastiano.headroom.ui.home.HomeUiState
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal fun HeadroomTray(controller: ShellController) {
    val scope = rememberCoroutineScope()
    val onUi: (() -> Unit) -> Unit = { action -> scope.launch { action() } }
    val overview = controller.overview
    val accounts = controller.presentation?.graph?.quotaRepository?.accounts
    val domainAccounts =
        if (accounts != null) {
            val current by accounts.collectAsState()
            current
        } else emptyList()
    val startupError = controller.startupError
    val issue = controller.issue
    val notificationStatus = controller.notificationStatus
    val ready = controller.headroom != null && !controller.quitting
    val menu: TrayMenuBuilder.() -> Unit = {
        Item("Open Headroom", onClick = { onUi { controller.show(SharedUiDestination.Overview) } })
        Divider()
        trayAccounts(overview, domainAccounts, startupError) { account ->
            onUi { controller.showAccount(account.account.id, account.isSignInExpired) }
        }
        issue?.let { Item(it, isEnabled = false) }
        notificationStatus?.let { Item(it, isEnabled = false) }
        Divider()
        Item(
            "Refresh now",
            isEnabled = ready,
            onClick = { onUi(controller::refresh) },
        )
        Item("Accounts", onClick = { onUi { controller.show(SharedUiDestination.Accounts) } })
        Item("Settings", onClick = { onUi { controller.show(SharedUiDestination.Settings) } })
        Divider()
        Item("Quit Headroom", onClick = { onUi(controller.lifecycle::quit) })
    }
    MacStatusTray(
        tooltip = trayTooltip(controller),
        onOpen = { onUi { controller.show(SharedUiDestination.Overview) } },
        menu = menu,
    )
}

private fun TrayMenuBuilder.trayAccounts(
    overview: HomeUiState?,
    accounts: List<AccountState>,
    startupError: Boolean,
    onOpen: (AccountState) -> Unit,
) {
    when {
        startupError -> Item("Headroom could not start", isEnabled = false)
        overview == null || !overview.accountsLoaded ->
            Item("Opening Headroom...", isEnabled = false)
        else -> {
            if (overview.isDemo) Item("Demo accounts - add yours to start", isEnabled = false)
            accounts.forEach { account ->
                SubMenu(trayAccountSummary(account, overview.display.name.lowercase())) {
                    Item(account.account.label, isEnabled = false)
                    account.snapshot?.windows.orEmpty().forEach { window ->
                        val label =
                            if (window.isUnlimited) "${window.label}: Unlimited"
                            else
                                trayQuotaSummary(
                                    window.label,
                                    window.usedPercent,
                                    overview.display.name.lowercase(),
                                )
                        Item(label, isEnabled = false)
                    }
                    Item("Open account", onClick = { onOpen(account) })
                }
            }
        }
    }
}

private fun trayTooltip(controller: ShellController): String {
    val overview = controller.overview ?: return "Headroom"
    if (overview.isDemo) return "Headroom - demo accounts"
    return "Headroom\n" +
        controller.presentation?.graph?.quotaRepository?.accounts?.value.orEmpty().joinToString(
            "\n"
        ) {
            trayAccountSummary(it, overview.display.name.lowercase())
        }
}

internal fun shellPainter(resource: String): Painter {
    val bytes =
        checkNotNull(ShellController::class.java.getResourceAsStream("/$resource")).use {
            it.readBytes()
        }
    return BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
}
