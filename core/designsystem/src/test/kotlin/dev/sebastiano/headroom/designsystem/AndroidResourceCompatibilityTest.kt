package dev.sebastiano.headroom.designsystem

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.designsystem.resources.Res
import dev.sebastiano.headroom.model.ThemePalette
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidResourceCompatibilityTest {
    @Test
    @SuppressLint(
        "ResourceType"
    ) // openRawResource reads packaged fonts too; this test compares their actual bytes.
    fun `legacy R fonts and common fonts contain identical data`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy =
            context.resources.openRawResource(R.font.google_sans_flex).use { it.readBytes() }
        assertContentEquals(legacy, Res.readBytes("font/google_sans_flex.ttf"))
        assertTrue(R.drawable.ic_add != 0)
        assertEquals(
            "11 pts over pace",
            context.resources.getQuantityString(R.plurals.designsystem_pace_over, 11, 11),
        )
    }

    @Test
    fun `the widget palette entrypoint retains the common fixed and cobalt palettes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertSame(
            seededColorScheme(ThemePalette.Lagoon, dark = false),
            headroomColorScheme(context, ThemePalette.Lagoon, dark = false),
        )
        assertSame(
            CobaltDark,
            headroomColorScheme(context, ThemePalette.Wallpaper, dark = true, dynamicColor = false),
        )
    }
}
