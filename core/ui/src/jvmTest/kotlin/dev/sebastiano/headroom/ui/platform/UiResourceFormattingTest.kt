package dev.sebastiano.headroom.ui.platform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.platform.sharedGetString
import dev.sebastiano.headroom.ui.resources.Res
import dev.sebastiano.headroom.ui.resources.accounts_moved
import dev.sebastiano.headroom.ui.resources.age_minutes
import dev.sebastiano.headroom.ui.resources.ic_tile_headroom
import dev.sebastiano.headroom.ui.resources.island_setup_overlay_title
import dev.sebastiano.headroom.ui.resources.percent
import dev.sebastiano.headroom.ui.resources.settings_tile_tightest_body
import dev.sebastiano.headroom.ui.resources.signin_again_body
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalTestApi::class) // Compose's desktop UI test runner is experimental.
class UiResourceFormattingTest {
    @Test
    fun resourceTemplatesRetainAndroidTextWithoutChangingUserArguments() = runComposeUiTest {
        setContent {
            assertEquals("71%", uiStringResource(Res.string.percent, 71))
            assertEquals(
                "Blocked by your device's admin? Use Display over other apps instead",
                uiStringResource(Res.string.island_setup_overlay_title),
            )
            assertEquals(
                "Sign in as name\\'%%. Headroom keeps this account's history, name and alerts.",
                uiStringResource(Res.string.signin_again_body, "name\\'%%"),
            )
            assertTrue(uiStringResource(Res.string.settings_tile_tightest_body).contains("\""))
            assertEquals("1 minute ago", uiPluralStringResource(Res.plurals.age_minutes, 1, 1))
            assertEquals("2 minutes ago", uiPluralStringResource(Res.plurals.age_minutes, 2, 2))
            assertTrue(uiPainterResource(Res.drawable.ic_tile_headroom).intrinsicSize.width > 0)
            assertTrue(uiPainterResource(HeadroomIcons.Add).intrinsicSize.width > 0)
        }
        waitForIdle()
    }

    @Test
    fun suspendedCallbackFormattingPreservesUserArguments() = runTest {
        assertEquals(
            "name%% moved to position 2 of 3",
            sharedGetString(Res.string.accounts_moved, "name%%", 2, 3),
        )
        assertEquals(
            "Sign in as name\\'%%. Headroom keeps this account's history, name and alerts.",
            sharedGetString(Res.string.signin_again_body, "name\\'%%"),
        )
    }
}
