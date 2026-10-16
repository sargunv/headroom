package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.skiaImageFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

class HeadroomRuntimeShaderJvmTest {
    @Test
    fun `brushes execute color and float uniforms and update on the next frame`() {
        val shader = HeadroomRuntimeShader(COLOR_SHADER)
        val brush = shader.asBrush()
        shader.setFloatUniform("channels", 1f, 0f, 0f)
        shader.setFloatUniform("gain", 1f)
        shader.setColorUniform("tint", Color.White.toArgb())
        assertEquals(Color.Red, drawBrush(brush))

        shader.setFloatUniform("channels", 0f, 1f, 0f)
        assertEquals(Color.Green, drawBrush(brush))
        shader.setFloatUniform("gain", 0f)
        assertEquals(Color.Black, drawBrush(brush))
    }

    @Test
    fun `content effects really transform their input`() {
        val shader = HeadroomRuntimeShader(DIM_SHADER)
        shader.setFloatUniform("gain", 1f)
        val original = drawEffect(shader.asRenderEffect("content"))
        assertEquals(Color.Red.toArgb(), original.first())

        shader.setFloatUniform("gain", 0.5f)
        val dimmed = drawEffect(shader.asRenderEffect("content"))
        assertNotEquals(original, dimmed)
        assertEquals(0x80, dimmed.first().ushr(ALPHA_SHIFT))
    }

    @Test
    fun `the original ripple shader is identity at rest and refracts while active`() {
        val shader = HeadroomRuntimeShader(LIQUID_RIPPLE_SHADER)
        shader.setFloatUniform("size", SIDE.toFloat(), SIDE.toFloat())
        shader.setFloatUniform("origin", HALF_SIDE, HALF_SIDE)
        shader.setFloatUniform("maxRadius", 80f)
        shader.setFloatUniform("amplitude", 3f)
        shader.setFloatUniform("wavelength", 28f)
        shader.setColorUniform("highlight", Color.White.toArgb())
        shader.setFloatUniform("highlightStrength", 0.28f)
        shader.setFloatUniform("progress", 0f)
        val before = drawEffect(shader.asRenderEffect("content"))
        shader.setFloatUniform("progress", 0.4f)
        val during = drawEffect(shader.asRenderEffect("content"))
        shader.setFloatUniform("progress", 1f)
        val after = drawEffect(shader.asRenderEffect("content"))
        assertEquals(before, after)
        assertTrue(before.zip(during).any { (a, b) -> a != b })
    }

    private fun drawBrush(brush: ShaderBrush): Color {
        val image = ImageBitmap(1, 1)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(1f, 1f)) {
            drawRect(brush)
        }
        return image.toPixelMap()[0, 0]
    }

    private fun drawEffect(effect: RenderEffect): List<Int> =
        Surface.makeRasterN32Premul(SIDE, SIDE).use { surface ->
            Paint().use { paint ->
                paint.imageFilter = effect.skiaImageFilter
                surface.canvas.saveLayer(Rect.makeWH(SIDE.toFloat(), SIDE.toFloat()), paint)
                paint.imageFilter = null
                paint.color = Color.Red.toArgb()
                surface.canvas.drawRect(Rect.makeWH(SIDE.toFloat(), SIDE.toFloat()), paint)
                paint.color = Color.Blue.toArgb()
                surface.canvas.drawRect(Rect.makeWH(HALF_SIDE, HALF_SIDE), paint)
                surface.canvas.restore()
            }
            Bitmap().use { bitmap ->
                bitmap.allocN32Pixels(SIDE, SIDE)
                check(surface.readPixels(bitmap, 0, 0))
                // Start in the solid red corner; the upper-left corner is blue for refraction.
                (0 until SIDE * SIDE).map { index ->
                    bitmap.getColor(SIDE - 1 - index % SIDE, SIDE - 1 - index / SIDE)
                }
            }
        }

    private companion object {
        const val SIDE = 64
        const val HALF_SIDE = 32f
        const val ALPHA_SHIFT = 24
        const val COLOR_SHADER =
            """
            layout(color) uniform half4 tint;
            uniform float3 channels;
            uniform float gain;
            half4 main(float2 p) { return half4(tint.rgb * channels * gain, tint.a); }
        """
        const val DIM_SHADER =
            """
            uniform shader content;
            uniform float gain;
            half4 main(float2 p) { return content.eval(p) * gain; }
        """
    }
}
