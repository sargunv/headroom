package dev.sebastiano.headroom.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopUiPlatformTest {
    @Test
    fun deviceActionsUseInjectedHostCallbacksAndAndroidActionsAreUnavailable() {
        val urls = mutableListOf<String>()
        val copied = mutableListOf<String>()
        val platform = DesktopUiPlatform(onOpenUrl = urls::add, onCopy = copied::add)
        platform.openUrl("https://example.test/sign-in")
        platform.copy("test device code")
        assertEquals(listOf("https://example.test/sign-in"), urls)
        assertEquals(listOf("test device code"), copied)
        assertFalse(platform.capabilities.widgets)
        assertFalse(platform.capabilities.quickSettingsTile)
        assertFalse(platform.capabilities.resetIsland)
    }
}
