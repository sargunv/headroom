package dev.sebastiano.headroom.designsystem

import android.content.Context
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.designsystem.platform.sharedPainterResource
import dev.sebastiano.headroom.designsystem.platform.sharedPluralStringResource
import dev.sebastiano.headroom.designsystem.platform.sharedStringResource
import dev.sebastiano.headroom.designsystem.resources.NativeComposeResources
import dev.sebastiano.headroom.designsystem.resources.Res
import dev.sebastiano.headroom.designsystem.resources.allDrawableResources
import dev.sebastiano.headroom.designsystem.resources.designsystem_pace_on
import dev.sebastiano.headroom.designsystem.resources.designsystem_pace_over
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NativeResourceFormattingTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun nativeStringsPluralsAndEveryDrawableKeepTheirOriginalAndroidResource() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        assertEquals(28, Res.allDrawableResources.size)
        rule.setContent {
            assertEquals(
                resources.getString(R.string.designsystem_pace_on),
                sharedStringResource(Res.string.designsystem_pace_on),
            )
            for (quantity in listOf(1, 11)) {
                assertEquals(
                    resources.getQuantityString(
                        R.plurals.designsystem_pace_over,
                        quantity,
                        quantity,
                    ),
                    sharedPluralStringResource(
                        Res.plurals.designsystem_pace_over,
                        quantity,
                        quantity,
                    ),
                )
            }
            for ((name, resource) in Res.allDrawableResources) {
                val id = assertNotNull(NativeComposeResources.drawableId(resource), name)
                assertEquals(name, resources.getResourceEntryName(id))
                val native = painterResource(id)
                val shared = sharedPainterResource(resource)
                assertEquals(native::class, shared::class, name)
                assertEquals(native.intrinsicSize, shared.intrinsicSize, name)
            }
        }
        rule.waitForIdle()
    }
}
