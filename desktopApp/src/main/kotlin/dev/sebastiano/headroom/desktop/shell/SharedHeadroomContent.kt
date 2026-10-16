package dev.sebastiano.headroom.desktop.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.sebastiano.headroom.ui.AppearanceTheme
import dev.sebastiano.headroom.ui.HeadroomApp
import dev.sebastiano.headroom.ui.OpenAccountRequest
import dev.sebastiano.headroom.ui.SharedUiNavigationRequest
import dev.sebastiano.headroom.ui.ThemeRevealHost
import dev.sebastiano.headroom.ui.delights.DelightsHost
import dev.sebastiano.headroom.ui.platform.LocalUiBackDispatcher
import dev.sebastiano.headroom.ui.platform.LocalUiPlatform
import dev.sebastiano.headroom.ui.platform.UiPlatform

/** Window/lifecycle hosting only. All screens, transitions and ViewModels are shared originals. */
@Composable
internal fun SharedHeadroomContent(
    presentation: DesktopPresentation,
    platform: UiPlatform,
    navigationRequest: SharedUiNavigationRequest? = null,
    onConsumeNavigation: () -> Unit = {},
    openAccountRequest: OpenAccountRequest? = null,
    onConsumeOpenAccount: () -> Unit = {},
    visible: Boolean = true,
    notices: List<String> = emptyList(),
    desktopSettings: (@Composable (Modifier) -> Unit)? = null,
    onRender: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val owner = remember(presentation) { DesktopWindowLifecycle() }
    val back = presentation.backDispatcher
    DisposableEffect(owner) {
        onDispose { owner.registry.currentState = Lifecycle.State.DESTROYED }
    }
    SideEffect(visible) {
        owner.registry.currentState =
            if (visible) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
    }
    CompositionLocalProvider(
        LocalLifecycleOwner provides owner,
        LocalViewModelStoreOwner provides presentation,
        LocalUiPlatform provides platform,
        LocalUiBackDispatcher provides back,
    ) {
        val settings by presentation.graph.settings.settings.collectAsState(initial = null)
        val appearance = settings
        if (appearance != null) {
            ThemeRevealHost(themeKey = appearance.theme to appearance.palette) {
                AppearanceTheme(appearance) {
                    Column(
                        modifier.fillMaxSize().onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) false
                            else
                                when {
                                    event.isMetaPressed && event.key == Key.R -> {
                                        presentation.homeViewModel.refresh()
                                        true
                                    }
                                    isBackNavigationKey(event.key, event.isMetaPressed) ->
                                        back.dispatch()
                                    else -> false
                                }
                        }
                    ) {
                        notices.forEach { Text(it, Modifier.fillMaxWidth().padding(12.dp)) }
                        DelightsHost(
                            refreshShimmer = appearance.refreshShimmer,
                            resetConfetti = appearance.resetConfetti,
                            modifier = Modifier.weight(1f),
                        ) {
                            HeadroomApp(
                                graph = presentation.graph,
                                modifier =
                                    Modifier.fillMaxSize().drawWithContent {
                                        drawContent()
                                        onRender()
                                    },
                                navigationRequest = navigationRequest,
                                onConsumeNavigation = onConsumeNavigation,
                                openAccountRequest = openAccountRequest,
                                onConsumeOpenAccount = onConsumeOpenAccount,
                                desktopSettings = desktopSettings,
                                homeViewModel = presentation.homeViewModel,
                                accountsViewModel = presentation.accountsViewModel,
                                settingsViewModel = presentation.settingsViewModel,
                                statsViewModel = presentation.statsViewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MacSettingsContent(
    launchAtLogin: Boolean,
    onLaunchAtLoginChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text("Launch at login") },
        supportingContent = { Text("Keep Headroom in the menu bar when you sign in to this Mac.") },
        trailingContent = {
            Switch(checked = launchAtLogin, onCheckedChange = onLaunchAtLoginChange)
        },
    )
}

private class DesktopWindowLifecycle : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = registry
}
