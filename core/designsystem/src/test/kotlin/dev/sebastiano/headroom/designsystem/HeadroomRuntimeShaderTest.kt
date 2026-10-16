package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeadroomRuntimeShaderTest {
    @Test
    fun `a shared shader creates a brush and a content effect`() {
        val brushShader =
            HeadroomRuntimeShader(
                "layout(color) uniform half4 tint; half4 main(float2 p) { return tint; }"
            )
        brushShader.setColorUniform("tint", Color.Red.toArgb())
        assertNotNull(brushShader.asBrush())

        val effectShader =
            HeadroomRuntimeShader(
                """
                uniform shader content;
                uniform float amount;
                half4 main(float2 p) { return content.eval(p) * amount; }
                """
            )
        effectShader.setFloatUniform("amount", 1f)
        assertNotNull(effectShader.asRenderEffect("content"))
    }
}
