package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RuntimeShaderAdapterTest {
    @Test
    fun `the shared adapter forwards uniforms and content names without platform assumptions`() {
        val backend = RecordingShaderBackend()
        val shader = HeadroomRuntimeShader(backend)
        shader.setFloatUniform("size", 100f, 200f)
        shader.setColorUniform("highlight", Color.Red.toArgb())
        assertSame(backend.brush, shader.asBrush())
        assertSame(backend.effect, shader.asRenderEffect("content"))
        assertEquals("size", backend.floatName)
        assertContentEquals(floatArrayOf(100f, 200f), backend.values)
        assertEquals("highlight", backend.colorName)
        assertEquals(Color.Red.toArgb(), backend.argb)
        assertEquals("content", backend.contentName)
    }
}

private class RecordingShaderBackend : RuntimeShaderBackend {
    var floatName: String? = null
    var values: FloatArray? = null
    var colorName: String? = null
    var argb: Int? = null
    var contentName: String? = null
    val brush =
        object : ShaderBrush() {
            override fun createShader(size: Size) =
                LinearGradientShader(
                    Offset.Zero,
                    Offset(size.width, 0f),
                    listOf(Color.Red, Color.Blue),
                )
        }
    val effect = BlurEffect(1f, 1f)

    override fun setFloatUniform(name: String, values: FloatArray) {
        floatName = name
        this.values = values.copyOf()
    }

    override fun setColorUniform(name: String, argb: Int) {
        colorName = name
        this.argb = argb
    }

    override fun asBrush(): ShaderBrush = brush

    override fun asRenderEffect(contentName: String): RenderEffect {
        this.contentName = contentName
        return effect
    }
}
