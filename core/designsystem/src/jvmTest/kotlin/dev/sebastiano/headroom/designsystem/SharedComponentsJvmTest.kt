package dev.sebastiano.headroom.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.platform.ResourceFont
import dev.sebastiano.headroom.model.ThemePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalTestApi::class) // Compose's desktop UI test runner is experimental.
class SharedComponentsJvmTest {
    @Test
    fun `the original chip words icons and typography load on desktop`() = runComposeUiTest {
        setContent {
            HeadroomTheme(dynamicColor = false) {
                Column {
                    PaceChip(PaceChipState.Over(11))
                    PaceChip(PaceChipState.Under(1))
                    PaceChip(PaceChipState.OnPace)
                    PaceChip(PaceChipState.JustReset)
                    Icon(painterResource(HeadroomIcons.DashboardFilled), "Dashboard")
                }
            }
        }
        onNodeWithText("11 pts over pace").assertExists()
        onNodeWithText("1 pt under pace").assertExists()
        onNodeWithText("On pace").assertExists()
        onNodeWithText("Just reset").assertExists()
    }

    @Test
    fun `reduced motion retains the original indicator semantics and values`() = runComposeUiTest {
        setContent {
            HeadroomTheme(palette = ThemePalette.Lagoon, reduceMotion = true) {
                Column {
                    QuotaBar(progress = 0.34f, wavy = true, modifier = Modifier.testTag("bar"))
                    QuotaRing(progress = 0.71f, wavy = true, modifier = Modifier.testTag("ring"))
                }
            }
        }
        onNode(indicatorIn("bar"))
            .assert(SemanticsMatcher.expectValue(IndicatorStyleKey, IndicatorStyle.Flat))
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.34f, 0f..1f))
        onNode(indicatorIn("ring"))
            .assert(SemanticsMatcher.expectValue(IndicatorStyleKey, IndicatorStyle.Flat))
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.71f, 0f..1f))
    }

    @Test
    fun `indicators that need attention remain wavy on desktop`() = runComposeUiTest {
        setContent {
            HeadroomTheme(dynamicColor = false) {
                Column {
                    QuotaBar(progress = 0.71f, wavy = true, modifier = Modifier.testTag("bar"))
                    QuotaRing(progress = 0.71f, wavy = true, modifier = Modifier.testTag("ring"))
                }
            }
        }
        onNode(indicatorIn("bar"))
            .assert(SemanticsMatcher.expectValue(IndicatorStyleKey, IndicatorStyle.Wavy))
        onNode(indicatorIn("ring"))
            .assert(SemanticsMatcher.expectValue(IndicatorStyleKey, IndicatorStyle.Wavy))
    }

    @Test
    fun `the bundled variable font retains its display axes`() {
        val family = HeadroomTypography.displayLarge.fontFamily as FontListFontFamily
        val font = family.fonts.single() as ResourceFont
        assertNotNull(javaClass.classLoader.getResource(font.name))
        assertNotNull(javaClass.classLoader.getResource("licenses/google_sans_flex_OFL.txt"))
        assertEquals(860, font.weight.weight)
        assertEquals(
            listOf("wght", "wdth", "ROND"),
            font.variationSettings.settings.map { it.axisName },
        )
        assertEquals(
            listOf(860f, 118f, 100f),
            font.variationSettings.settings.map { it.toVariationValue(null) },
        )
    }

    private fun indicatorIn(tag: String) =
        hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(IndicatorStyleKey)
}
