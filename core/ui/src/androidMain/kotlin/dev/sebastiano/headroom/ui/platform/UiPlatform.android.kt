package dev.sebastiano.headroom.ui.platform

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

private class AndroidUiPlatform(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UiPlatform {
    override val capabilities =
        UiCapabilities(widgets = true, quickSettingsTile = true, resetIsland = true)
    override val is24Hour: Boolean
        get() = DateFormat.is24HourFormat(context)

    private val islandService =
        ComponentName(context.packageName, "dev.sebastiano.headroom.island.ResetIslandService")

    override val restrictedInstall: Boolean
        get() =
            try {
                val source =
                    context.packageManager.getInstallSourceInfo(context.packageName).packageSource
                source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
                    source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
            } catch (_: PackageManager.NameNotFoundException) {
                true
            }

    override val accessibilityAvailable: Boolean
        get() =
            try {
                context.packageManager.getServiceInfo(
                    islandService,
                    PackageManager.ComponentInfoFlags.of(0),
                )
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }

    override fun copy(text: String) {
        context
            .getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(null, text))
    }

    override fun openUrl(url: String) {
        val query =
            Intent(Intent.ACTION_VIEW, "https://".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        val manager = context.packageManager
        val browsers =
            manager
                .queryIntentActivities(query, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
                .distinct()
        val preferred =
            CustomTabsClient.getPackageName(context, browsers)
                ?: manager
                    .resolveActivity(query, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo
                    ?.packageName
                    ?.takeIf { it != "android" && it in browsers }
        val tab = CustomTabsIntent.Builder().build()
        preferred?.let { tab.intent.setPackage(it) }
        tab.launchUrl(context, url.toUri())
    }

    override fun openAppInfo() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            )
        )
    }

    override fun openAccessibilitySettings() {
        val key = islandService.flattenToString()
        openSettings(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", key)
                .putExtra(
                    ":settings:show_fragment_args",
                    Bundle().apply { putString(":settings:fragment_args_key", key) },
                )
        )
    }

    override fun openOverlaySettings() {
        openSettings(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri(),
            )
        )
    }

    private fun openSettings(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            /* Device does not expose this settings page. */
        } catch (_: SecurityException) {
            /* Device policy can deny this settings page. */
        }
    }

    @SuppressLint(
        "DiscouragedApi"
    ) // The host generates this raw resource; its R is not visible to the UI library.
    override suspend fun loadLicences(): String =
        withContext(ioDispatcher) {
            val id = context.resources.getIdentifier("aboutlibraries", "raw", context.packageName)
            context.resources.openRawResource(id).bufferedReader().use { it.readText() }
        }
}

@Composable
actual fun rememberUiPlatform(): UiPlatform {
    val injected = LocalUiPlatform.current
    val context = LocalContext.current
    return injected ?: remember(context) { AndroidUiPlatform(context) }
}

@Composable
internal actual fun SystemBarsFollow(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity, dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
            navigationBarStyle =
                SystemBarStyle.auto(0xe6ffffff.toInt(), 0x801b1b1b.toInt()) { dark },
        )
        onDispose {}
    }
}

@Composable
internal actual fun wallpaperSwatch(dark: Boolean): Pair<Color, Color> {
    val context = LocalContext.current
    return remember(context, dark) {
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        scheme.primary to scheme.tertiary
    }
}

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled, onBack)
}

@Composable
actual fun PredictiveBackHandler(enabled: Boolean, onBack: suspend (Flow<UiBackEvent>) -> Unit) {
    androidx.activity.compose.PredictiveBackHandler(enabled) { events ->
        onBack(flow { events.collect { emit(UiBackEvent(it.progress)) } })
    }
}
