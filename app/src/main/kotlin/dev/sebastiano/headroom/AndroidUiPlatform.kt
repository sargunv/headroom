package dev.sebastiano.headroom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import androidx.core.net.toUri
import dev.sebastiano.headroom.island.appInfoIntent
import dev.sebastiano.headroom.island.hasIslandService
import dev.sebastiano.headroom.island.installIsRestricted
import dev.sebastiano.headroom.island.islandServiceComponent
import dev.sebastiano.headroom.island.openAccessibilitySettings as openIslandAccessibilitySettings
import dev.sebastiano.headroom.island.openOverlaySettings as openIslandOverlaySettings
import dev.sebastiano.headroom.signin.signInTabIntent
import dev.sebastiano.headroom.ui.platform.UiCapabilities
import dev.sebastiano.headroom.ui.platform.UiPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android shell bindings retain the existing Custom Tabs, service and settings implementations. */
internal class AndroidUiPlatform(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : UiPlatform {
    override val capabilities =
        UiCapabilities(widgets = true, quickSettingsTile = true, resetIsland = true)
    override val is24Hour: Boolean
        get() = DateFormat.is24HourFormat(context)

    override val restrictedInstall: Boolean
        get() = context.installIsRestricted()

    override val accessibilityAvailable: Boolean
        get() = context.hasIslandService()

    override fun openUrl(url: String) {
        signInTabIntent(context).launchUrl(context, url.toUri())
    }

    override fun copy(text: String) {
        context
            .getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(null, text))
    }

    override fun openAppInfo() {
        context.startActivity(appInfoIntent(context.packageName))
    }

    override fun openAccessibilitySettings() {
        openIslandAccessibilitySettings(islandServiceComponent(context), context::startActivity)
    }

    override fun openOverlaySettings() {
        openIslandOverlaySettings(context.packageName, context::startActivity)
    }

    override suspend fun loadLicences(): String =
        withContext(io) {
            context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use {
                it.readText()
            }
        }
}
