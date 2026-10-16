package dev.sebastiano.headroom.designsystem

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect

internal actual fun createRuntimeShaderBackend(source: String): RuntimeShaderBackend =
    AndroidRuntimeShaderBackend(source)

private class AndroidRuntimeShaderBackend(source: String) : RuntimeShaderBackend {
    private val shader = RuntimeShader(source)

    override fun setFloatUniform(name: String, values: FloatArray) {
        shader.setFloatUniform(name, values)
    }

    override fun setColorUniform(name: String, argb: Int) {
        shader.setColorUniform(name, argb)
    }

    override fun asBrush(): ShaderBrush = ShaderBrush(shader)

    override fun asRenderEffect(contentName: String): RenderEffect =
        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, contentName)
            .asComposeRenderEffect()
}
