package dev.sebastiano.headroom.designsystem

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class QuotaBarResizeJvmTest {
    @Test
    fun `pace ticks tolerate a pane shrinking below tick width during resize`() =
        runSkikoComposeUiTest(size = Size(480f, 640f), density = Density(2f)) {
            var width by mutableStateOf(200.dp)
            setContent {
                HeadroomTheme(dynamicColor = false, reduceMotion = true) {
                    QuotaBar(
                        progress = 0.71f,
                        paceFraction = 0.5f,
                        animationSpec = snap(),
                        modifier = Modifier.requiredWidth(width).testTag("resizing-bar"),
                    )
                }
            }
            listOf(1.dp, 0.dp, 2.dp, 200.dp, 0.dp, 1.dp).forEach { next ->
                runOnIdle { width = next }
                waitForIdle()
                onNodeWithTag("resizing-bar").assertWidthIsEqualTo(next)
            }
        }
}
