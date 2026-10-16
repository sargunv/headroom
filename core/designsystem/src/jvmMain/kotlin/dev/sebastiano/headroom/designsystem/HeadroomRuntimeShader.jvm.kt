package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual fun createRuntimeShaderBackend(source: String): RuntimeShaderBackend =
    SkiaRuntimeShaderBackend(source)

private class SkiaRuntimeShaderBackend(source: String) : RuntimeShaderBackend {
    private val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(source))
    private val invalidationPaint = Paint()
    private val brush =
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader = builder.makeShader().asComposeShader()
        }

    override fun setFloatUniform(name: String, values: FloatArray) {
        builder.uniform(name, values)
        invalidateBrush()
    }

    override fun setColorUniform(name: String, argb: Int) {
        val color = Color(argb)
        builder.uniform(name, color.red, color.green, color.blue, color.alpha)
        invalidateBrush()
    }

    override fun asBrush(): ShaderBrush = brush

    override fun asRenderEffect(contentName: String): RenderEffect =
        ImageFilter.makeRuntimeShader(builder, contentName, null).asComposeRenderEffect()

    private fun invalidateBrush() {
        // Skia shaders snapshot uniforms; an empty draw clears ShaderBrush's same-size cache so a
        // retained brush, just like Android's mutable RuntimeShader brush, sees the next frame.
        brush.applyTo(Size.Zero, invalidationPaint, 1f)
    }
}
