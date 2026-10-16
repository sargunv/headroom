package dev.sebastiano.headroom.ui.platform

import android.content.Context
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.ui.R
import dev.sebastiano.headroom.ui.resources.NativeComposeResources
import dev.sebastiano.headroom.ui.resources.Res
import dev.sebastiano.headroom.ui.resources.age_minutes
import dev.sebastiano.headroom.ui.resources.allDrawableResources
import dev.sebastiano.headroom.ui.resources.allPluralStringResources
import dev.sebastiano.headroom.ui.resources.allStringResources
import dev.sebastiano.headroom.ui.resources.detail_unknown_quota_explanation
import dev.sebastiano.headroom.ui.resources.ic_tile_headroom
import dev.sebastiano.headroom.ui.resources.island_setup_overlay_title
import dev.sebastiano.headroom.ui.resources.percent
import dev.sebastiano.headroom.ui.resources.settings_tile_tightest_body
import dev.sebastiano.headroom.ui.resources.signin_again_body
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UiResourceFormattingTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun everyCanonicalResourceMapsToItsOriginalAndroidId() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        Res.allStringResources.forEach { (name, resource) ->
            val id = assertNotNull(NativeComposeResources.stringId(resource), name)
            assertEquals(name, resources.getResourceEntryName(id))
        }
        Res.allPluralStringResources.forEach { (name, resource) ->
            val id = assertNotNull(NativeComposeResources.pluralId(resource), name)
            assertEquals(name, resources.getResourceEntryName(id))
        }
        Res.allDrawableResources.forEach { (name, resource) ->
            val id = assertNotNull(NativeComposeResources.drawableId(resource), name)
            assertEquals(name, resources.getResourceEntryName(id))
        }
    }

    @Test
    fun nativeFormattingPreservesQuotesPercentArgumentsAndPluralSelection() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        rule.setContent {
            assertEquals("71%", uiStringResource(Res.string.percent, TEST_PERCENT))
            assertEquals(
                resources.getString(R.string.detail_unknown_quota_explanation),
                uiStringResource(Res.string.detail_unknown_quota_explanation),
            )
            assertEquals(
                resources.getString(R.string.island_setup_overlay_title),
                uiStringResource(Res.string.island_setup_overlay_title),
            )
            assertEquals(
                resources.getString(R.string.signin_again_body, "name\\'%%"),
                uiStringResource(Res.string.signin_again_body, "name\\'%%"),
            )
            assertEquals(
                resources.getString(R.string.settings_tile_tightest_body),
                uiStringResource(Res.string.settings_tile_tightest_body),
            )
            for (quantity in listOf(1, 2)) {
                assertEquals(
                    resources.getQuantityString(R.plurals.age_minutes, quantity, quantity),
                    uiPluralStringResource(Res.plurals.age_minutes, quantity, quantity),
                )
            }
            assertEquals(
                painterResource(R.drawable.ic_tile_headroom).intrinsicSize,
                uiPainterResource(Res.drawable.ic_tile_headroom).intrinsicSize,
            )
            assertEquals(
                painterResource(dev.sebastiano.headroom.designsystem.R.drawable.ic_add)
                    .intrinsicSize,
                uiPainterResource(HeadroomIcons.Add).intrinsicSize,
            )
        }
        rule.waitForIdle()
    }
}

private const val TEST_PERCENT = 71
